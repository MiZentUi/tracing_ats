from __future__ import annotations

import json
import logging
from collections.abc import Iterator
from contextlib import contextmanager

import grpc
import pytest
from google.type import date_pb2

from api.config import Settings
from api.extraction.extractor import ResumeExtractor
from api.llm.client import LlmUnavailable
from api.server import create_server
from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import (
    common_pb2,
    education_pb2,
    experience_pb2,
    skill_pb2,
)

TEXT = "Python developer\nМГУ, 2019\nООО Ромашка, 2020-2023"

PAYLOAD = {
    "schema_version": 2,
    "skills": [{"name": "Python", "years": 3, "quote": "Python developer"}],
    "education": [
        {
            "institution": "МГУ",
            "field": "Прикладная математика",
            "level": "master",
            "graduation_year": 2019,
            "quote": "МГУ, 2019",
        }
    ],
    "experience": [
        {
            "company": "Ромашка",
            "position": "Developer",
            "category": "backend",
            "begin_year": 2020,
            "end_year": 2023,
            "quote": "ООО Ромашка, 2020-2023",
            "metrics": ["Python developer"],
        }
    ],
}


class FakeLlm:
    model = "fake"

    def __init__(self, payload: dict) -> None:
        self._payload = json.dumps(payload, ensure_ascii=False)
        self.calls = 0

    def complete(self, system: str, user: str) -> str:
        self.calls += 1
        return self._payload


class UnavailableLlm:
    model = "unavailable"

    def complete(self, system: str, user: str) -> str:
        raise LlmUnavailable("boom")


@contextmanager
def _running(extractor: ResumeExtractor) -> Iterator[scoring_pb2_grpc.ScoringServiceStub]:
    handle = create_server(
        Settings(grpc_host="127.0.0.1", grpc_port=0, _env_file=None),
        extractor=extractor,
    )
    handle.server.start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{handle.port}") as channel:
            yield scoring_pb2_grpc.ScoringServiceStub(channel)
    finally:
        handle.server.stop(grace=None)


def _reference_date() -> date_pb2.Date:
    return date_pb2.Date(year=2026, month=11, day=12)


def _vacancy() -> messages_pb2.Vacancy:
    return messages_pb2.Vacancy(
        tenure=5,
        text_description="Python backend",
        requirements={
            "python": features_pb2.FeatureRequirement(
                criticality=common_pb2.FEATURE_CRITICALITY_REQUIRED,
                skill=skill_pb2.SkillRequirement(name="Python", tenure=3),
            ),
            "education": features_pb2.FeatureRequirement(
                criticality=common_pb2.FEATURE_CRITICALITY_NICE,
                education=education_pb2.EducationRequirement(
                    master="Прикладная математика",
                    level=education_pb2.EDUCATION_LEVEL_MASTER,
                ),
            ),
            "experience": features_pb2.FeatureRequirement(
                criticality=common_pb2.FEATURE_CRITICALITY_REQUIRED,
                experience=experience_pb2.PriorExperienceRequirement(
                    tenure=3,
                    category="backend",
                ),
            ),
        },
    )


def _score_request(text: str = TEXT) -> messages_pb2.ScoreResumeRequest:
    return messages_pb2.ScoreResumeRequest(
        vacancy=_vacancy(),
        resume=messages_pb2.UnprocessedResume(text=text),
        reference_date=_reference_date(),
    )


def _extract_request(text: str = TEXT) -> messages_pb2.ExtractResumeRequest:
    return messages_pb2.ExtractResumeRequest(
        resume=messages_pb2.UnprocessedResume(text=text),
        reference_date=_reference_date(),
    )


def _parsed_resume() -> messages_pb2.ParsedResume:
    parsed = messages_pb2.ParsedResume(schema_version=2)
    parsed.skills["skill:0"].CopyFrom(
        skill_pb2.SkillPerResume(
            name="Python",
            normalized_name="python",
            tenure=3,
            evidence=common_pb2.FeatureEvidence(text="Python developer"),
        )
    )
    parsed.education["education:0"].CopyFrom(
        education_pb2.EducationPerResume(
            institution="МГУ",
            master="Прикладная математика",
            level=education_pb2.EDUCATION_LEVEL_MASTER,
            evidence=common_pb2.FeatureEvidence(text="МГУ, 2019"),
        )
    )
    experience = experience_pb2.PriorExperiencePerResume(
        company="Ромашка",
        position="Developer",
        category="backend",
    )
    experience.metrics.add().CopyFrom(common_pb2.FeatureEvidence(text="ООО Ромашка, 2020-2023"))
    experience.begin.CopyFrom(date_pb2.Date(year=2020))
    experience.end.CopyFrom(date_pb2.Date(year=2023))
    parsed.experience["experience:0"].CopyFrom(experience)
    return parsed


def _evaluate_request(
    text: str = TEXT,
    resume: messages_pb2.ParsedResume | None = None,
) -> messages_pb2.EvaluateResumeMatchRequest:
    vacancy = _vacancy()
    vacancy.requirements["kafka"].CopyFrom(
        features_pb2.FeatureRequirement(
            criticality=common_pb2.FEATURE_CRITICALITY_REQUIRED,
            skill=skill_pb2.SkillRequirement(name="Kafka"),
        )
    )
    vacancy.requirements["soft"].CopyFrom(
        features_pb2.FeatureRequirement(
            criticality=common_pb2.FEATURE_CRITICALITY_NICE,
            text_requirement=features_pb2.TextRequirement(
                category=common_pb2.REQUIREMENT_CATEGORY_SOFT,
                text="коммуникация",
            ),
        )
    )
    return messages_pb2.EvaluateResumeMatchRequest(
        resume=resume or _parsed_resume(),
        unprocessed_resume=messages_pb2.UnprocessedResume(text=text),
        vacancy=vacancy,
        reference_date=_reference_date(),
    )


def test_score_resume_returns_general_score_and_features() -> None:
    with _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub:
        response = stub.ScoreResume(_score_request(), timeout=2)

    assert set(response.features) == {"python", "education", "experience"}
    assert response.general_score.relevance > 0.0
    assert 0.0 <= response.general_score.score_low <= response.general_score.score
    assert response.general_score.score <= response.general_score.score_high <= 1.0
    assert response.general_score.HasField("tenure")

    python = response.features["python"]
    assert python.WhichOneof("resume") == "skill"
    assert python.skill.name == "Python"
    assert python.skill.normalized_name == "python"
    assert python.skill.tenure == 3
    assert python.tenure == 3
    assert python.skill.evidence.text == "Python developer"
    assert python.skill.evidence.start_index == 0
    assert 0.0 < python.coverage < python.relevance

    education = response.features["education"].education
    assert education.institution == "МГУ"
    assert education.master == "Прикладная математика"
    assert education.level == education_pb2.EDUCATION_LEVEL_MASTER
    assert education.graduation_year.year == 2019
    assert education.evidence.text == "МГУ, 2019"

    experience = response.features["experience"].experience
    assert experience.company == "Ромашка"
    assert experience.category == "backend"
    assert experience.position == "Developer"
    assert experience.begin.year == 2020
    assert experience.end.year == 2023
    assert experience.metrics[0].text == "ООО Ромашка, 2020-2023"


def test_score_resume_is_deterministic_for_same_input() -> None:
    with _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub:
        first = stub.ScoreResume(_score_request(), timeout=2)
        second = stub.ScoreResume(_score_request(), timeout=2)

    assert first.SerializeToString(deterministic=True) == second.SerializeToString(
        deterministic=True
    )


def test_evaluate_resume_match_does_not_call_llm() -> None:
    llm = FakeLlm(PAYLOAD)
    with _running(ResumeExtractor(llm)) as stub:
        response = stub.EvaluateResumeMatch(_evaluate_request(), timeout=2)

    assert llm.calls == 0
    assert response.general_score.score > 0.0


def test_evaluate_resume_match_statuses_and_explanations() -> None:
    with _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub:
        response = stub.EvaluateResumeMatch(_evaluate_request(), timeout=2)

    python = response.matches["python"]
    assert python.status == common_pb2.CORRESPONDENCE_FULL
    assert python.resume_feature_key == "skill:0"
    assert python.evidence.text == "Python developer"

    assert response.matches["education"].status == common_pb2.CORRESPONDENCE_FULL
    assert response.matches["experience"].status == common_pb2.CORRESPONDENCE_FULL

    kafka = response.matches["kafka"]
    assert kafka.status == common_pb2.CORRESPONDENCE_NOT_STATED
    assert kafka.resume_feature_key == ""
    assert not kafka.HasField("evidence")
    assert kafka.coverage == 0.0

    soft = response.matches["soft"]
    assert soft.status == common_pb2.CORRESPONDENCE_NOT_STATED

    gap_ids = {gap.requirement_id for gap in response.gaps}
    assert gap_ids == {"kafka", "soft"}
    assert "Python" in response.strengths
    assert response.questions
    assert response.recruiter_note
    assert response.general_score.score_low < response.general_score.score_high


def test_evaluate_resume_match_marks_insufficient_tenure() -> None:
    resume = _parsed_resume()
    experience = resume.experience["experience:0"]
    experience.begin.CopyFrom(date_pb2.Date(year=2022))
    experience.end.CopyFrom(date_pb2.Date(year=2023))

    with _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub:
        response = stub.EvaluateResumeMatch(
            _evaluate_request(resume=resume),
            timeout=2,
        )

    match = response.matches["experience"]
    assert match.status == common_pb2.CORRESPONDENCE_PARTIAL
    assert match.years_found < 3
    assert any(
        gap.requirement_id == "experience"
        and gap.reason == common_pb2.GAP_REASON_INSUFFICIENT_TENURE
        for gap in response.gaps
    )


def test_evaluate_resume_match_ignores_unverified_evidence() -> None:
    resume = _parsed_resume()
    resume.skills["skill:0"].evidence.text = "Rust developer"

    with _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub:
        response = stub.EvaluateResumeMatch(
            _evaluate_request(resume=resume),
            timeout=2,
        )

    assert response.matches["python"].status == common_pb2.CORRESPONDENCE_NOT_STATED
    assert response.matches["python"].coverage == 0.0


def test_evaluate_resume_match_rejects_empty_text() -> None:
    with (
        _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.EvaluateResumeMatch(_evaluate_request(text="   "), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_evaluate_resume_matches_isolates_errors() -> None:
    vacancy = _vacancy()
    bad = messages_pb2.Vacancy(
        requirements={
            "bad": features_pb2.FeatureRequirement(
                criticality=common_pb2.FEATURE_CRITICALITY_REQUIRED,
            )
        }
    )
    request = messages_pb2.EvaluateResumeMatchesRequest(
        resume=_parsed_resume(),
        unprocessed_resume=messages_pb2.UnprocessedResume(text=TEXT),
        vacancies={"good": vacancy, "bad": bad},
        reference_date=_reference_date(),
    )

    with _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub:
        response = stub.EvaluateResumeMatches(request, timeout=2)

    assert response.results["good"].WhichOneof("result") == "match"
    assert response.results["good"].match.general_score.score > 0.0
    assert response.results["bad"].WhichOneof("result") == "error"
    assert response.results["bad"].error.code == "INVALID_ARGUMENT"


def test_extract_resume_returns_parsed_resume() -> None:
    with _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub:
        response = stub.ExtractResume(_extract_request(), timeout=2)

    parsed = response.parsed
    assert parsed.schema_version == 2
    assert response.dropped == 0
    assert response.warnings == []

    assert set(parsed.skills) == {"skill:0"}
    skill = parsed.skills["skill:0"]
    assert skill.name == "Python"
    assert skill.normalized_name == "python"
    assert skill.tenure == 3
    assert skill.evidence.text == "Python developer"
    assert skill.evidence.start_index == 0

    assert set(parsed.education) == {"education:0"}
    education = parsed.education["education:0"]
    assert education.institution == "МГУ"
    assert education.master == "Прикладная математика"
    assert education.level == education_pb2.EDUCATION_LEVEL_MASTER
    assert education.graduation_year.year == 2019
    assert education.evidence.text == "МГУ, 2019"

    assert set(parsed.experience) == {"experience:0"}
    experience = parsed.experience["experience:0"]
    assert experience.company == "Ромашка"
    assert experience.position == "Developer"
    assert experience.category == "backend"
    assert experience.begin.year == 2020
    assert experience.end.year == 2023
    assert experience.metrics[0].text == "ООО Ромашка, 2020-2023"
    assert experience.metrics[1].text == "Python developer"


def test_extract_resume_logs_only_metadata(caplog) -> None:
    with (
        caplog.at_level(logging.INFO, logger="api.scoring.service"),
        _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub,
    ):
        stub.ExtractResume(_extract_request(), timeout=2)

    assert "extract resume model=fake prompt=resume-extraction.v2 schema=2" in caplog.text
    assert "skills=1 education=1 experience=1 dropped=0" in caplog.text
    assert TEXT not in caplog.text
    assert "Python developer" not in caplog.text


def test_extract_resume_drops_unverified_quotes() -> None:
    payload = {
        **PAYLOAD,
        "skills": [{"name": "Rust", "years": None, "quote": "Rust developer"}],
    }
    with _running(ResumeExtractor(FakeLlm(payload))) as stub:
        response = stub.ExtractResume(_extract_request(), timeout=2)

    assert not response.parsed.skills
    assert response.dropped == 1


def test_extract_resume_warns_about_inconsistent_dates() -> None:
    payload = {
        **PAYLOAD,
        "experience": [
            {
                **PAYLOAD["experience"][0],
                "begin_year": 2024,
                "end_year": 2020,
            }
        ],
    }
    with _running(ResumeExtractor(FakeLlm(payload))) as stub:
        response = stub.ExtractResume(_extract_request(), timeout=2)

    assert "experience:0: end date is before begin date" in response.warnings


def test_extract_resume_rejects_empty_text() -> None:
    with (
        _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ExtractResume(_extract_request(text="   "), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_extract_resume_reports_unavailable_llm() -> None:
    with (
        _running(ResumeExtractor(UnavailableLlm())) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ExtractResume(_extract_request(), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.UNAVAILABLE


def test_extract_resume_reports_invalid_llm_response() -> None:
    with (
        _running(ResumeExtractor(FakeLlm({"schema_version": 3}))) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ExtractResume(_extract_request(), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.INTERNAL


def test_score_resume_rejects_empty_text() -> None:
    with (
        _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ScoreResume(_score_request(text="   "), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_score_resume_reports_unavailable_llm() -> None:
    with (
        _running(ResumeExtractor(UnavailableLlm())) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ScoreResume(_score_request(), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.UNAVAILABLE


def test_score_resume_reports_invalid_llm_response() -> None:
    with (
        _running(ResumeExtractor(FakeLlm({"schema_version": 3}))) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ScoreResume(_score_request(), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.INTERNAL
