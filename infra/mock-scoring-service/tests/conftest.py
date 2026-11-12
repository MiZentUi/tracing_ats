from __future__ import annotations

import grpc
import pytest
from google.type import date_pb2

from mock_scoring.config import Settings
from mock_scoring.server import create_server
from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import common_pb2, skill_pb2

RESUME_TEXT = (
    "Опыт работы\n"
    "2019 - 2022: ООО «Ромашка», backend-разработчик\n"
    "Навыки: Java, Spring Boot, PostgreSQL\n"
    "Kafka — 3 года\n"
    "Образование: МГТУ им. Баумана, бакалавр, 2018\n"
)

REFERENCE_DATE = date_pb2.Date(year=2026, month=6, day=1)


@pytest.fixture()
def channel():
    handle = create_server(
        settings=Settings(host="127.0.0.1", port=0, log_level="WARNING"),
        port=0,
    )
    handle.server.start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{handle.port}") as client:
            yield client
    finally:
        handle.server.stop(grace=None)


@pytest.fixture()
def stub(channel):
    return scoring_pb2_grpc.ScoringServiceStub(channel)


def skill_requirement(
    name: str,
    tenure: int = 0,
    criticality: int = common_pb2.FEATURE_CRITICALITY_REQUIRED,
) -> features_pb2.FeatureRequirement:
    return features_pb2.FeatureRequirement(
        criticality=criticality,
        source=common_pb2.REQUIREMENT_SOURCE_REQUIREMENT,
        skill=skill_pb2.SkillRequirement(name=name, normalized_name=name, tenure=tenure),
    )


def soft_requirement(text: str) -> features_pb2.FeatureRequirement:
    return features_pb2.FeatureRequirement(
        criticality=common_pb2.FEATURE_CRITICALITY_NICE,
        text_requirement=features_pb2.TextRequirement(
            category=common_pb2.REQUIREMENT_CATEGORY_SOFT,
            text=text,
        ),
    )


def vacancy(
    requirements: dict[str, features_pb2.FeatureRequirement],
    text: str = "",
) -> messages_pb2.Vacancy:
    return messages_pb2.Vacancy(
        tenure=3,
        text_description=text,
        position=messages_pb2.JOB_POSITION_MIDDLE,
        category="backend",
        requirements=requirements,
    )
