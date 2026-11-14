#!/usr/bin/env python3
# ruff: noqa: RUF002
"""Пакетный прогон scoring-service по локальным txt-файлам.

Скрипт рассчитан на ручную проверку и хранит промежуточные результаты на диске:

1. Резюме из ``--resumes-dir`` один раз отправляются в ``ExtractResume``.
   Ответ сохраняется в ``--cache-dir/resumes/<stem>.json``.
2. Вакансии из ``--vacancies-dir`` один раз разбираются локально из
   структурированного txt в ``Vacancy``. Результат сохраняется в
   ``--cache-dir/vacancies/<stem>.json``.
3. Для каждого резюме вызывается ``EvaluateResumeMatches`` со всеми вакансиями.
   Результаты сохраняются в ``--output-dir/<run>/``.

При повторном запуске кэш используется, если SHA-256 исходного txt совпадает.
Это позволяет не вызывать LLM повторно и быстро менять только вакансии или
правила матчинга.

Пример:
    cd scoring-service
    uv run python scripts/benchmark.py \
      --resumes-dir scripts/benchmark/examples/resumes \
      --vacancies-dir scripts/benchmark/examples/vacancies
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import os
import re
import time
import uuid
from datetime import UTC, date, datetime
from pathlib import Path

import grpc
from google.protobuf import json_format
from google.type import date_pb2

from api.extraction.normalize import normalize_key
from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import (
    common_pb2,
    education_pb2,
    experience_pb2,
    qualification_pb2,
    skill_pb2,
)

LOGGER = logging.getLogger("benchmark")
DEFAULT_TARGET = os.getenv("SCORING_GRPC_TARGET", "127.0.0.1:8082")
DEFAULT_TIMEOUT = 120.0
BASE_DIR = Path(__file__).resolve().parent / "benchmark"
DEFAULT_RESUMES_DIR = BASE_DIR / "resumes"
DEFAULT_VACANCIES_DIR = BASE_DIR / "vacancies"
DEFAULT_CACHE_DIR = BASE_DIR / "cache"
DEFAULT_OUTPUT_DIR = BASE_DIR / "results"
REQUEST_ID_HEADER = "x-request-id"

CRITICALITY = {
    "critical": common_pb2.FEATURE_CRITICALITY_CRITICAL,
    "required": common_pb2.FEATURE_CRITICALITY_REQUIRED,
    "nice": common_pb2.FEATURE_CRITICALITY_NICE,
    "optional": common_pb2.FEATURE_CRITICALITY_OPTIONAL,
}
POSITION = {
    "intern": messages_pb2.JOB_POSITION_INTERN,
    "junior": messages_pb2.JOB_POSITION_JUNIOR,
    "middle": messages_pb2.JOB_POSITION_MIDDLE,
    "senior": messages_pb2.JOB_POSITION_SENIOR,
}
EDUCATION_LEVEL = {
    "secondary": education_pb2.EDUCATION_LEVEL_SECONDARY,
    "secondary_specialized": education_pb2.EDUCATION_LEVEL_SECONDARY_SPECIALIZED,
    "bachelor": education_pb2.EDUCATION_LEVEL_BACHELOR,
    "master": education_pb2.EDUCATION_LEVEL_MASTER,
    "doctor": education_pb2.EDUCATION_LEVEL_DOCTOR,
    "courses": education_pb2.EDUCATION_LEVEL_COURSES,
}
TEXT_CATEGORY = {
    "soft": common_pb2.REQUIREMENT_CATEGORY_SOFT,
    "other": common_pb2.REQUIREMENT_CATEGORY_OTHER,
}
SAFE_NAME = re.compile(r"[^a-z0-9._-]+")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Пакетный прогон scoring-service.")
    parser.add_argument("--target", default=DEFAULT_TARGET)
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT)
    parser.add_argument("--tls", action="store_true")
    parser.add_argument("--resumes-dir", type=Path, default=DEFAULT_RESUMES_DIR)
    parser.add_argument("--vacancies-dir", type=Path, default=DEFAULT_VACANCIES_DIR)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--reference-date", help="Дата расчёта в формате YYYY-MM-DD.")
    parser.add_argument("--force-parse", action="store_true")
    parser.add_argument(
        "--run-name",
        help="Имя каталога результатов. По умолчанию — UTC timestamp.",
    )
    return parser.parse_args(argv)


def safe_name(value: str) -> str:
    result = SAFE_NAME.sub("-", value.strip().lower()).strip("-")
    return result or "item"


def source_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def read_json(path: Path) -> dict | None:
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        LOGGER.warning("cache_ignored file=%s error=%s", path.name, type(exc).__name__)
        return None


def reference_date(value: str | None) -> date:
    return date.fromisoformat(value) if value else date.today()


def proto_reference_date(value: date) -> date_pb2.Date:
    return date_pb2.Date(year=value.year, month=value.month, day=value.day)


def requirement_parts(value: str, line_no: int) -> list[str]:
    parts = [part.strip() for part in value.split("|")]
    if any(not part for part in parts):
        raise ValueError(f"строка {line_no}: пустое поле в требовании")
    return parts


def criticality(value: str, line_no: int) -> int:
    normalized = value.strip().lower()
    if normalized not in CRITICALITY:
        raise ValueError(f"строка {line_no}: неизвестная критичность «{value}»")
    return CRITICALITY[normalized]


def parse_vacancy_text(path: Path, text: str) -> messages_pb2.Vacancy:
    """Разобрать структурированный txt вакансии в protobuf `Vacancy`.

    Формат описан в ``scripts/benchmark/README.md``. Это не LLM-разбор:
    файл задаёт требования детерминированно, чтобы можно было проверять
    матчинг без Java-сервиса.
    """
    vacancy = messages_pb2.Vacancy(text_description=text)
    requirements: dict[str, features_pb2.FeatureRequirement] = {}
    for line_no, raw_line in enumerate(text.splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if ":" not in line:
            raise ValueError(f"строка {line_no}: ожидается 'ключ: значение'")
        key, value = (part.strip() for part in line.split(":", 1))
        key = key.lower()
        if key == "tenure":
            try:
                vacancy.tenure = int(value)
            except ValueError as exc:
                raise ValueError(f"строка {line_no}: tenure должен быть целым") from exc
            continue
        if key == "position":
            normalized = value.lower()
            if normalized not in POSITION:
                raise ValueError(f"строка {line_no}: неизвестная позиция «{value}»")
            vacancy.position = POSITION[normalized]
            continue
        if key == "category":
            vacancy.category = value
            continue

        parts = requirement_parts(value, line_no)
        requirement_id = f"req-{len(requirements) + 1:02d}"
        if key == "skill":
            if len(parts) < 2:
                raise ValueError(f"строка {line_no}: skill: criticality | name [| tenure]")
            name = parts[1]
            years = int(parts[2]) if len(parts) > 2 else 0
            requirement = features_pb2.FeatureRequirement(
                criticality=criticality(parts[0], line_no)
            )
            requirement.skill.CopyFrom(
                skill_pb2.SkillRequirement(
                    name=name,
                    normalized_name=normalize_key(name),
                    tenure=max(0, years),
                )
            )
        elif key == "qualification":
            if len(parts) != 2:
                raise ValueError(
                    f"строка {line_no}: qualification: criticality | label"
                )
            requirement = features_pb2.FeatureRequirement(
                criticality=criticality(parts[0], line_no)
            )
            requirement.qualification.CopyFrom(
                qualification_pb2.QualificationRequirement(label=parts[1])
            )
        elif key == "education":
            if len(parts) != 3:
                raise ValueError(
                    f"строка {line_no}: education: criticality | level | specialty"
                )
            level = parts[1].lower()
            if level not in EDUCATION_LEVEL:
                raise ValueError(f"строка {line_no}: неизвестный уровень «{parts[1]}»")
            requirement = features_pb2.FeatureRequirement(
                criticality=criticality(parts[0], line_no)
            )
            requirement.education.CopyFrom(
                education_pb2.EducationRequirement(
                    master=parts[2],
                    level=EDUCATION_LEVEL[level],
                )
            )
        elif key == "experience":
            if len(parts) < 2 or len(parts) > 3:
                raise ValueError(
                    f"строка {line_no}: experience: criticality | category [| tenure]"
                )
            years = int(parts[2]) if len(parts) == 3 else 0
            requirement = features_pb2.FeatureRequirement(
                criticality=criticality(parts[0], line_no)
            )
            requirement.experience.CopyFrom(
                experience_pb2.PriorExperienceRequirement(
                    category=parts[1],
                    tenure=max(0, years),
                )
            )
        elif key == "text":
            if len(parts) != 3:
                raise ValueError(
                    f"строка {line_no}: text: criticality | soft|other | text"
                )
            category = parts[1].lower()
            if category not in TEXT_CATEGORY:
                raise ValueError(f"строка {line_no}: неизвестная категория «{parts[1]}»")
            requirement = features_pb2.FeatureRequirement(
                criticality=criticality(parts[0], line_no)
            )
            requirement.text_requirement.CopyFrom(
                features_pb2.TextRequirement(
                    category=TEXT_CATEGORY[category],
                    text=parts[2],
                )
            )
        else:
            raise ValueError(f"строка {line_no}: неизвестный ключ «{key}»")
        requirements[requirement_id] = requirement

    if not requirements:
        raise ValueError("в вакансии нет требований")
    for requirement_id, requirement in requirements.items():
        vacancy.requirements[requirement_id].CopyFrom(requirement)
    return vacancy


def load_cached_vacancy(
    path: Path,
    cache_dir: Path,
    force_parse: bool,
) -> tuple[messages_pb2.Vacancy, bool] | None:
    sha = source_sha256(path)
    cache_path = cache_dir / "vacancies" / f"{safe_name(path.stem)}.json"
    if not force_parse:
        cached = read_json(cache_path)
        if cached and cached.get("sha256") == sha:
            vacancy = messages_pb2.Vacancy()
            try:
                json_format.ParseDict(
                    cached["vacancy"],
                    vacancy,
                    ignore_unknown_fields=True,
                )
                return vacancy, True
            except (KeyError, json_format.ParseError, ValueError) as exc:
                LOGGER.warning(
                    "vacancy_cache_ignored file=%s error=%s",
                    path.name,
                    type(exc).__name__,
                )
        elif cached:
            LOGGER.info("vacancy_cache_stale file=%s", path.name)

    text = path.read_text(encoding="utf-8")
    if not text.strip():
        raise ValueError("пустой файл вакансии")
    vacancy = parse_vacancy_text(path, text)
    write_json(
        cache_path,
        {
            "source": str(path),
            "sha256": sha,
            "vacancy": json_format.MessageToDict(
                vacancy,
                preserving_proto_field_name=True,
            ),
        },
    )
    return vacancy, False


def extract_resume(
    stub: scoring_pb2_grpc.ScoringServiceStub,
    text: str,
    target: str,
    timeout: float,
    reference: date_pb2.Date,
) -> messages_pb2.ExtractResumeResponse:
    request = messages_pb2.ExtractResumeRequest(
        resume=messages_pb2.UnprocessedResume(text=text),
        reference_date=reference,
    )
    request_id = uuid.uuid4().hex
    started = time.perf_counter()
    LOGGER.info(
        "outbound_request sent method=ExtractResume target=%s request_id=%s",
        target,
        request_id,
    )
    try:
        response = stub.ExtractResume(
            request,
            timeout=timeout,
            metadata=((REQUEST_ID_HEADER, request_id),),
        )
    except grpc.RpcError as exc:
        LOGGER.warning(
            "outbound_request failed method=ExtractResume target=%s request_id=%s "
            "code=%s elapsed_ms=%.1f",
            target,
            request_id,
            exc.code().name if exc.code() else "UNKNOWN",
            (time.perf_counter() - started) * 1000,
        )
        raise
    LOGGER.info(
        "outbound_response received method=ExtractResume request_id=%s elapsed_ms=%.1f",
        request_id,
        (time.perf_counter() - started) * 1000,
    )
    return response


def load_cached_resume(
    path: Path,
    cache_dir: Path,
    force_parse: bool,
    stub: scoring_pb2_grpc.ScoringServiceStub,
    target: str,
    timeout: float,
    reference: date_pb2.Date,
) -> tuple[messages_pb2.ExtractResumeResponse, bool]:
    sha = source_sha256(path)
    cache_path = cache_dir / "resumes" / f"{safe_name(path.stem)}.json"
    if not force_parse:
        cached = read_json(cache_path)
        if cached and cached.get("sha256") == sha:
            response = messages_pb2.ExtractResumeResponse()
            try:
                json_format.ParseDict(
                    cached["response"],
                    response,
                    ignore_unknown_fields=True,
                )
                return response, True
            except (KeyError, json_format.ParseError, ValueError) as exc:
                LOGGER.warning(
                    "resume_cache_ignored file=%s error=%s",
                    path.name,
                    type(exc).__name__,
                )
        elif cached:
            LOGGER.info("resume_cache_stale file=%s", path.name)

    text = path.read_text(encoding="utf-8")
    if not text.strip():
        raise ValueError("пустой файл резюме")
    response = extract_resume(stub, text, target, timeout, reference)
    write_json(
        cache_path,
        {
            "source": str(path),
            "sha256": sha,
            "response": json_format.MessageToDict(
                response,
                preserving_proto_field_name=True,
            ),
        },
    )
    return response, False


def make_channel(target: str, use_tls: bool) -> grpc.Channel:
    if use_tls:
        return grpc.secure_channel(target, grpc.ssl_channel_credentials())
    return grpc.insecure_channel(target)


def evaluate(
    stub: scoring_pb2_grpc.ScoringServiceStub,
    response: messages_pb2.ExtractResumeResponse,
    vacancies: dict[str, messages_pb2.Vacancy],
    resume_text: str,
    reference: date_pb2.Date,
    timeout: float,
) -> messages_pb2.EvaluateResumeMatchesResponse:
    request = messages_pb2.EvaluateResumeMatchesRequest(
        resume=response.parsed,
        unprocessed_resume=messages_pb2.UnprocessedResume(text=resume_text),
        reference_date=reference,
    )
    for key, vacancy in vacancies.items():
        request.vacancies[key].CopyFrom(vacancy)
    request_id = uuid.uuid4().hex
    started = time.perf_counter()
    LOGGER.info(
        "outbound_request sent method=EvaluateResumeMatches request_id=%s vacancies=%d",
        request_id,
        len(vacancies),
    )
    try:
        result = stub.EvaluateResumeMatches(
            request,
            timeout=timeout,
            metadata=((REQUEST_ID_HEADER, request_id),),
        )
    except grpc.RpcError as exc:
        LOGGER.warning(
            "outbound_request failed method=EvaluateResumeMatches request_id=%s "
            "code=%s elapsed_ms=%.1f",
            request_id,
            exc.code().name if exc.code() else "UNKNOWN",
            (time.perf_counter() - started) * 1000,
        )
        raise
    LOGGER.info(
        "outbound_response received method=EvaluateResumeMatches request_id=%s elapsed_ms=%.1f",
        request_id,
        (time.perf_counter() - started) * 1000,
    )
    return result


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    reference = reference_date(args.reference_date)
    proto_date = proto_reference_date(reference)

    resume_paths = sorted(args.resumes_dir.glob("*.txt"))
    vacancy_paths = sorted(args.vacancies_dir.glob("*.txt"))
    if not resume_paths:
        LOGGER.error("no_resumes dir=%s", args.resumes_dir)
        return 2
    if not vacancy_paths:
        LOGGER.error("no_vacancies dir=%s", args.vacancies_dir)
        return 2

    args.cache_dir.mkdir(parents=True, exist_ok=True)
    run_id = args.run_name or datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
    run_dir = args.output_dir / safe_name(run_id)
    run_dir.mkdir(parents=True, exist_ok=True)

    vacancies: dict[str, messages_pb2.Vacancy] = {}
    vacancy_report: dict[str, dict] = {}
    for path in vacancy_paths:
        key = safe_name(path.stem)
        try:
            loaded = load_cached_vacancy(path, args.cache_dir, args.force_parse)
            if loaded is None:
                continue
            vacancy, cached = loaded
            vacancies[key] = vacancy
            vacancy_report[key] = {"source": str(path), "cached": cached}
            LOGGER.info(
                "vacancy_ready key=%s requirements=%d cached=%s",
                key,
                len(vacancy.requirements),
                cached,
            )
        except (OSError, ValueError) as exc:
            vacancy_report[key] = {"source": str(path), "error": str(exc)}
            LOGGER.error("vacancy_parse_failed key=%s error=%s", key, exc)

    if not vacancies:
        write_json(run_dir / "manifest.json", {"vacancies": vacancy_report})
        LOGGER.error("no_parsed_vacancies")
        return 1

    resume_report: dict[str, dict] = {}
    summary: list[dict] = []
    with make_channel(args.target, args.tls) as channel:
        stub = scoring_pb2_grpc.ScoringServiceStub(channel)
        for path in resume_paths:
            key = safe_name(path.stem)
            try:
                response, cached = load_cached_resume(
                    path,
                    args.cache_dir,
                    args.force_parse,
                    stub,
                    args.target,
                    args.timeout,
                    proto_date,
                )
            except (OSError, ValueError, grpc.RpcError) as exc:
                resume_report[key] = {"source": str(path), "error": str(exc)}
                LOGGER.error("resume_parse_failed key=%s error=%s", key, exc)
                continue

            resume_report[key] = {
                "source": str(path),
                "cached": cached,
                "skills": len(response.parsed.skills),
                "qualifications": len(response.parsed.qualifications),
                "education": len(response.parsed.education),
                "experience": len(response.parsed.experience),
                "dropped": response.dropped,
                "warnings": list(response.warnings),
            }
            text = path.read_text(encoding="utf-8")
            try:
                result = evaluate(
                    stub,
                    response,
                    vacancies,
                    text,
                    proto_date,
                    args.timeout,
                )
            except grpc.RpcError as exc:
                resume_report[key]["match_error"] = (
                    exc.code().name if exc.code() else "UNKNOWN"
                )
                LOGGER.error("resume_match_failed key=%s", key)
                continue

            payload = {
                "resume": key,
                "source": str(path),
                "cached_parse": cached,
                "reference_date": reference.isoformat(),
                "vacancies": {
                    vacancy_key: json_format.MessageToDict(
                        item,
                        preserving_proto_field_name=True,
                    )
                    for vacancy_key, item in result.results.items()
                },
                "resolver_version": result.resolver_version,
                "dictionary_mode": result.dictionary_mode,
            }
            write_json(run_dir / "resumes" / f"{key}.json", payload)
            for vacancy_key, item in result.results.items():
                if item.HasField("error"):
                    summary.append(
                        {
                            "resume": key,
                            "vacancy": vacancy_key,
                            "status": "error",
                            "error": item.error.code,
                            "message": item.error.message,
                        }
                    )
                else:
                    summary.append(
                        {
                            "resume": key,
                            "vacancy": vacancy_key,
                            "status": "ok",
                            "score": item.match.general_score.score,
                            "score_low": item.match.general_score.score_low,
                            "score_high": item.match.general_score.score_high,
                            "matches": len(item.match.matches),
                            "gaps": len(item.match.gaps),
                        }
                    )
            LOGGER.info(
                "resume_matched key=%s vacancies=%d cached=%s",
                key,
                len(result.results),
                cached,
            )

    write_json(
        run_dir / "manifest.json",
        {
            "run": run_id,
            "target": args.target,
            "reference_date": reference.isoformat(),
            "resumes": resume_report,
            "vacancies": vacancy_report,
        },
    )
    write_json(run_dir / "summary.json", {"items": summary})
    LOGGER.info("benchmark_finished run=%s results=%s", run_id, run_dir)
    return 0 if summary and all(item["status"] == "ok" for item in summary) else 1


if __name__ == "__main__":
    raise SystemExit(main())
