from __future__ import annotations

import json
import logging
from collections.abc import Iterator
from contextlib import contextmanager

import grpc
import pytest

from api.config import Settings
from api.extraction.extractor import ResumeExtractor
from api.llm.client import LlmUnavailable
from api.server import create_server
from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import common_pb2, education_pb2, experience_pb2, skill_pb2

TEXT = "Python developer\nМГУ, 2019\nООО Ромашка, 2020-2023"

PAYLOAD = {
    "schema_version": 1,
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

    def complete(self, system: str, user: str) -> str:
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


def _request(text: str = TEXT) -> messages_pb2.ScoreResumeRequest:
    return messages_pb2.ScoreResumeRequest(
        vacancy=messages_pb2.Vacancy(
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
        ),
        resume=messages_pb2.UnprocessedResume(text=text),
    )


def _extract_request(text: str = TEXT) -> messages_pb2.ExtractResumeRequest:
    return messages_pb2.ExtractResumeRequest(
        resume=messages_pb2.UnprocessedResume(text=text),
    )


def test_score_resume_returns_extracted_features() -> None:
    with _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub:
        response = stub.ScoreResume(_request(), timeout=2)

    assert set(response.features) == {"python", "education", "experience"}

    python = response.features["python"]
    assert python.WhichOneof("resume") == "skill"
    assert python.skill.name == "Python"
    assert python.skill.normalized_name == "python"
    assert python.skill.tenure == 3
    assert python.tenure == 3
    assert python.skill.evidence.text == "Python developer"
    assert python.skill.evidence.start_index == 0
    assert python.coverage == python.relevance

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

    assert response.score == 0.5
    assert response.score_low == 0.4
    assert response.score_high == 0.6
    assert response.refine is False
    assert not response.HasField("tenure")


def test_score_resume_is_deterministic_for_same_input() -> None:
    with _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub:
        first = stub.ScoreResume(_request(), timeout=2)
        second = stub.ScoreResume(_request(), timeout=2)

    assert first.SerializeToString(deterministic=True) == second.SerializeToString(
        deterministic=True
    )


def test_score_resume_marks_unassessed_requirements_for_refinement() -> None:
    empty = {"schema_version": 1, "skills": [], "education": [], "experience": []}
    with _running(ResumeExtractor(FakeLlm(empty))) as stub:
        response = stub.ScoreResume(_request(), timeout=2)

    assert response.refine is True
    assert response.features["python"].relevance == 0.0
    assert response.features["python"].coverage == 0.0
    assert response.features["python"].WhichOneof("resume") is None


def test_score_resume_drops_features_with_unverified_quotes() -> None:
    payload = {
        **PAYLOAD,
        "skills": [{"name": "Rust", "years": None, "quote": "Rust developer"}],
    }
    with _running(ResumeExtractor(FakeLlm(payload))) as stub:
        response = stub.ScoreResume(_request(), timeout=2)

    assert response.features["python"].relevance == 0.0
    assert response.features["python"].WhichOneof("resume") is None
    assert response.refine is True


def test_score_resume_rejects_empty_text() -> None:
    with (
        _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ScoreResume(_request(text="   "), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_score_resume_reports_unavailable_llm() -> None:
    with (
        _running(ResumeExtractor(UnavailableLlm())) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ScoreResume(_request(), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.UNAVAILABLE


def test_score_resume_reports_invalid_llm_response() -> None:
    with (
        _running(ResumeExtractor(FakeLlm({"schema_version": 2}))) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ScoreResume(_request(), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.INTERNAL


def test_extract_resume_returns_parsed_resume() -> None:
    with _running(ResumeExtractor(FakeLlm(PAYLOAD))) as stub:
        response = stub.ExtractResume(_extract_request(), timeout=2)

    parsed = response.parsed
    assert parsed.schema_version == 1
    assert response.dropped == 0

    assert len(parsed.skills) == 1
    skill = parsed.skills[0]
    assert skill.name == "Python"
    assert skill.normalized_name == "python"
    assert skill.tenure == 3
    assert skill.evidence.text == "Python developer"
    assert skill.evidence.start_index == 0

    assert len(parsed.education) == 1
    education = parsed.education[0]
    assert education.institution == "МГУ"
    assert education.master == "Прикладная математика"
    assert education.level == education_pb2.EDUCATION_LEVEL_MASTER
    assert education.graduation_year.year == 2019
    assert education.evidence.text == "МГУ, 2019"

    assert len(parsed.experience) == 1
    experience = parsed.experience[0]
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

    assert "extract_resume model=fake prompt=resume-extraction.v1 schema=1" in caplog.text
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
        _running(ResumeExtractor(FakeLlm({"schema_version": 2}))) as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ExtractResume(_extract_request(), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.INTERNAL
