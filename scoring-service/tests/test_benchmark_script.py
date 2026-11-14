from __future__ import annotations

from pathlib import Path

from scoring_service.v1.models import messages_pb2
from scoring_service.v1.models.features import (
    common_pb2,
    education_pb2,
    experience_pb2,
    qualification_pb2,
)
from scripts.benchmark import parse_vacancy_text, safe_name

TEXT = """\
tenure: 3
position: middle
category: backend

skill: required | Python | 3
skill: nice | Kafka | 2
qualification: required | III группа по электробезопасности
education: nice | bachelor | Прикладная математика
experience: required | backend | 3
text: nice | soft | Умение работать в команде
"""


def test_parse_vacancy_text() -> None:
    vacancy = parse_vacancy_text(Path("backend.txt"), TEXT)

    assert vacancy.tenure == 3
    assert vacancy.position == messages_pb2.JOB_POSITION_MIDDLE
    assert vacancy.category == "backend"
    assert set(vacancy.requirements) == {"req-01", "req-02", "req-03", "req-04", "req-05", "req-06"}

    python = vacancy.requirements["req-01"]
    assert python.WhichOneof("feature") == "skill"
    assert python.skill.name == "Python"
    assert python.skill.normalized_name == "python"
    assert python.skill.tenure == 3
    assert python.criticality == common_pb2.FEATURE_CRITICALITY_REQUIRED

    kafka = vacancy.requirements["req-02"]
    assert kafka.skill.tenure == 2
    assert kafka.criticality == common_pb2.FEATURE_CRITICALITY_NICE

    qualification = vacancy.requirements["req-03"]
    assert qualification.WhichOneof("feature") == "qualification"
    assert qualification.qualification.label == "III группа по электробезопасности"
    assert isinstance(qualification.qualification, qualification_pb2.QualificationRequirement)

    education = vacancy.requirements["req-04"]
    assert education.WhichOneof("feature") == "education"
    assert education.education.master == "Прикладная математика"
    assert education.education.level == education_pb2.EDUCATION_LEVEL_BACHELOR

    experience = vacancy.requirements["req-05"]
    assert experience.WhichOneof("feature") == "experience"
    assert experience.experience.category == "backend"
    assert experience.experience.tenure == 3
    assert isinstance(experience.experience, experience_pb2.PriorExperienceRequirement)

    text_requirement = vacancy.requirements["req-06"]
    assert text_requirement.WhichOneof("feature") == "text_requirement"
    assert text_requirement.text_requirement.category == common_pb2.REQUIREMENT_CATEGORY_SOFT
    assert text_requirement.text_requirement.text == "Умение работать в команде"


def test_safe_name() -> None:
    assert safe_name("Backend Middle") == "backend-middle"
    assert safe_name("  ") == "item"
