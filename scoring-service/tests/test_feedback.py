from __future__ import annotations

from collections import Counter
from datetime import date

import pytest
from google.type import date_pb2

from api.scoring.feedback import (
    FEEDBACK_TEMPLATE_VERSION,
    FEEDBACK_TEMPLATES,
    FeedbackBuilder,
    _format_years,
    _quote,
    _stable_index,
)
from api.scoring.model import GeneralScore, RequirementScore
from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import (
    common_pb2,
    education_pb2,
    experience_pb2,
    qualification_pb2,
    skill_pb2,
)

REFERENCE = date(2026, 11, 13)


def _vacancy(
    requirements: dict[str, features_pb2.FeatureRequirement] | None = None,
) -> messages_pb2.Vacancy:
    return messages_pb2.Vacancy(requirements=requirements or {})


def _skill_requirement(
    name: str,
    tenure: int = 0,
    criticality: int = common_pb2.FEATURE_CRITICALITY_REQUIRED,
) -> features_pb2.FeatureRequirement:
    return features_pb2.FeatureRequirement(
        criticality=criticality,
        skill=skill_pb2.SkillRequirement(name=name, tenure=tenure),
    )


def _experience_requirement(
    category: str,
    tenure: int = 0,
    criticality: int = common_pb2.FEATURE_CRITICALITY_REQUIRED,
) -> features_pb2.FeatureRequirement:
    return features_pb2.FeatureRequirement(
        criticality=criticality,
        experience=experience_pb2.PriorExperienceRequirement(
            category=category,
            tenure=tenure,
        ),
    )


def _education_requirement(
    master: str,
    criticality: int = common_pb2.FEATURE_CRITICALITY_REQUIRED,
) -> features_pb2.FeatureRequirement:
    return features_pb2.FeatureRequirement(
        criticality=criticality,
        education=education_pb2.EducationRequirement(
            master=master,
            level=education_pb2.EDUCATION_LEVEL_MASTER,
        ),
    )


def _qualification_requirement(
    label: str,
    criticality: int = common_pb2.FEATURE_CRITICALITY_REQUIRED,
) -> features_pb2.FeatureRequirement:
    return features_pb2.FeatureRequirement(
        criticality=criticality,
        qualification=qualification_pb2.QualificationRequirement(label=label),
    )


def _text_requirement(
    text: str,
    criticality: int = common_pb2.FEATURE_CRITICALITY_REQUIRED,
) -> features_pb2.FeatureRequirement:
    return features_pb2.FeatureRequirement(
        criticality=criticality,
        text_requirement=features_pb2.TextRequirement(
            category=common_pb2.REQUIREMENT_CATEGORY_SOFT,
            text=text,
        ),
    )


def _match(
    requirement_id: str,
    *,
    criticality: int = common_pb2.FEATURE_CRITICALITY_REQUIRED,
    status: int = common_pb2.CORRESPONDENCE_FULL,
    label: str = "Python",
    evidence: str | None = "Python developer",
    years_found: float | None = 3.0,
    gap_reason: int | None = None,
    resume_kind: str | None = "skill",
) -> RequirementScore:
    return RequirementScore(
        requirement_id=requirement_id,
        criticality=criticality,
        status=status,
        relevance=1.0,
        coverage=1.0 if status == common_pb2.CORRESPONDENCE_FULL else 0.5,
        coverage_low=0.5,
        coverage_high=1.0,
        comment="",
        label=label,
        evidence=common_pb2.FeatureEvidence(text=evidence) if evidence else None,
        years_found=years_found,
        gap_reason=gap_reason,
        resume_kind=resume_kind,
    )


def _general(score: float = 0.5) -> GeneralScore:
    return GeneralScore(
        relevance=score,
        score=score,
        score_low=max(0.0, score - 0.1),
        score_high=min(1.0, score + 0.1),
        tenure=None,
    )


def _parsed_resume(
    *,
    skills: dict[str, skill_pb2.SkillPerResume] | None = None,
    qualifications: dict[str, qualification_pb2.QualificationPerResume] | None = None,
    education: dict[str, education_pb2.EducationPerResume] | None = None,
    experience: dict[str, experience_pb2.PriorExperiencePerResume] | None = None,
) -> messages_pb2.ParsedResume:
    parsed = messages_pb2.ParsedResume()
    for key, value in (skills or {}).items():
        parsed.skills[key].CopyFrom(value)
    for key, value in (qualifications or {}).items():
        parsed.qualifications[key].CopyFrom(value)
    for key, value in (education or {}).items():
        parsed.education[key].CopyFrom(value)
    for key, value in (experience or {}).items():
        parsed.experience[key].CopyFrom(value)
    return parsed


def _experience(
    begin_year: int | None, end_year: int | None
) -> experience_pb2.PriorExperiencePerResume:
    item = experience_pb2.PriorExperiencePerResume(category="backend")
    if begin_year is not None:
        item.begin.CopyFrom(date_pb2.Date(year=begin_year))
    if end_year is not None:
        item.end.CopyFrom(date_pb2.Date(year=end_year))
    return item


def _build(
    *,
    resume: messages_pb2.ParsedResume | None = None,
    vacancy: messages_pb2.Vacancy | None = None,
    matches: dict[str, RequirementScore] | None = None,
    general: GeneralScore | None = None,
    total_years: float | None = None,
):
    return FeedbackBuilder().build(
        resume=resume or _parsed_resume(),
        vacancy=vacancy or _vacancy(),
        matches=matches or {},
        general=general or _general(),
        reference_date=REFERENCE,
        total_years=total_years,
    )


def test_catalog_has_32_unique_templates() -> None:
    assert FEEDBACK_TEMPLATE_VERSION == "feedback-templates.v2"
    assert len(FEEDBACK_TEMPLATES) == 32
    ids = [template.id for template in FEEDBACK_TEMPLATES]
    assert len(ids) == len(set(ids))
    assert Counter(template.kind for template in FEEDBACK_TEMPLATES) == {
        "strength": 6,
        "risk": 8,
        "question": 10,
        "note": 6,
        "fallback": 2,
    }


def test_strengths_put_critical_first_and_include_evidence() -> None:
    vacancy = _vacancy(
        {
            "python": _skill_requirement("Python", tenure=3),
            "kafka": _skill_requirement(
                "Kafka",
                tenure=2,
                criticality=common_pb2.FEATURE_CRITICALITY_CRITICAL,
            ),
            "education": _education_requirement(
                "Прикладная математика",
                criticality=common_pb2.FEATURE_CRITICALITY_NICE,
            ),
        }
    )
    matches = {
        "python": _match("python", label="Python", evidence="Python developer", years_found=3.0),
        "kafka": _match(
            "kafka",
            criticality=common_pb2.FEATURE_CRITICALITY_CRITICAL,
            label="Kafka",
            evidence="Kafka producer",
            years_found=2.0,
        ),
        "education": _match(
            "education",
            criticality=common_pb2.FEATURE_CRITICALITY_NICE,
            label="Прикладная математика",
            evidence="МГУ, 2019",
            years_found=None,
            resume_kind="education",
        ),
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.9))

    assert feedback.strengths[0].startswith("Критичное требование «Kafka»")
    assert "Kafka producer" in feedback.strengths[0]
    assert "Найденный стаж — 2 года, требуется 2 года" in feedback.strengths[0]
    assert any("Python" in text for text in feedback.strengths)
    assert any("Прикладная математика" in text for text in feedback.strengths)


def test_qualification_strength_and_not_stated_question() -> None:
    label = "III группа по электробезопасности"
    vacancy = _vacancy({"electrical": _qualification_requirement(label)})
    resume = _parsed_resume(
        qualifications={
            "qualification:0": qualification_pb2.QualificationPerResume(
                label=label,
                evidence=common_pb2.FeatureEvidence(text=label),
            )
        }
    )
    matches = {
        "electrical": _match(
            "electrical",
            label=label,
            evidence=label,
            years_found=None,
            resume_kind="qualification",
        )
    }

    feedback = _build(resume=resume, vacancy=vacancy, matches=matches, general=_general(0.9))

    assert any(
        "Квалификация «III группа по электробезопасности» подтверждена" in item
        for item in feedback.strengths
    )

    not_stated = {
        "electrical": _match(
            "electrical",
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label=label,
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
            resume_kind=None,
        )
    }
    feedback = _build(resume=_parsed_resume(), vacancy=vacancy, matches=not_stated)

    assert any("не указана квалификация" in item for item in feedback.questions)


def test_long_quote_stays_a_substring_and_has_no_ellipsis() -> None:
    quote = "Python developer with " + "very " * 50 + "long experience"
    vacancy = _vacancy({"python": _skill_requirement("Python")})
    matches = {"python": _match("python", label="Python", evidence=quote, years_found=3.0)}

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.8))
    evidence = _quote(matches["python"])

    assert evidence is not None
    assert len(evidence) <= 160
    assert "…" not in evidence
    assert evidence in " ".join(quote.split())
    assert evidence in feedback.strengths[0]


def test_not_stated_question_does_not_claim_missing_skill() -> None:
    vacancy = _vacancy({"kafka": _skill_requirement("Kafka")})
    matches = {
        "kafka": _match(
            "kafka",
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="Kafka",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        )
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.2))

    question = feedback.questions[0]
    assert "не указан навык «Kafka»" in question
    for forbidden in ("не знает", "не владеет", "нет навыка", "слабый"):
        assert forbidden not in question


def test_absent_question_mentions_related_experience() -> None:
    vacancy = _vacancy({"kafka": _skill_requirement("Kafka")})
    matches = {
        "kafka": _match(
            "kafka",
            status=common_pb2.CORRESPONDENCE_ABSENT,
            label="Kafka",
            evidence="RabbitMQ",
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_ABSENT,
        )
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.2))

    assert "близкий опыт" in feedback.questions[0]
    assert "Kafka" in feedback.questions[0]


def test_insufficient_tenure_risk_and_question_include_years() -> None:
    vacancy = _vacancy({"kafka": _skill_requirement("Kafka", tenure=3)})
    matches = {
        "kafka": _match(
            "kafka",
            status=common_pb2.CORRESPONDENCE_PARTIAL,
            label="Kafka",
            evidence="Kafka producer",
            years_found=1.0,
            gap_reason=common_pb2.GAP_REASON_INSUFFICIENT_TENURE,
        )
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.4))

    assert any("1 год" in text and "3 года" in text for text in feedback.risks)
    assert any("1 год" in text and "3 года" in text for text in feedback.questions)


def test_critical_gap_is_first_in_risks_and_questions() -> None:
    vacancy = _vacancy(
        {
            "kafka": _skill_requirement(
                "Kafka",
                criticality=common_pb2.FEATURE_CRITICALITY_CRITICAL,
            ),
            "python": _skill_requirement("Python"),
            "soft": _text_requirement(
                "коммуникация",
                criticality=common_pb2.FEATURE_CRITICALITY_NICE,
            ),
        }
    )
    matches = {
        "kafka": _match(
            "kafka",
            criticality=common_pb2.FEATURE_CRITICALITY_CRITICAL,
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="Kafka",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        ),
        "python": _match(
            "python",
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="Python",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        ),
        "soft": _match(
            "soft",
            criticality=common_pb2.FEATURE_CRITICALITY_NICE,
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="коммуникация",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
            resume_kind=None,
        ),
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.1))

    assert feedback.risks[0].startswith("Критичное требование «Kafka»")
    assert "в резюме не подтверждено" in feedback.risks[0]
    assert "Kafka" in feedback.questions[0]


def test_date_issue_risk_and_question() -> None:
    vacancy = _vacancy({"experience": _experience_requirement("backend")})
    matches = {
        "experience": _match(
            "experience",
            status=common_pb2.CORRESPONDENCE_FULL,
            label="backend",
            evidence="ООО Ромашка",
            years_found=1.0,
            resume_kind="experience",
        )
    }
    resume = _parsed_resume(experience={"experience:0": _experience(2024, 2023)})

    feedback = _build(resume=resume, vacancy=vacancy, matches=matches, general=_general(0.8))

    assert any("окончание периода раньше начала" in risk for risk in feedback.risks)
    assert any("датах опыта" in question for question in feedback.questions)


def test_overclaim_risk_when_skill_tenure_exceeds_total_experience() -> None:
    vacancy = _vacancy({"python": _skill_requirement("Python")})
    matches = {
        "python": _match("python", label="Python", evidence="Python developer", years_found=10.0)
    }
    resume = _parsed_resume(
        skills={"skill:0": skill_pb2.SkillPerResume(name="Python", tenure=10)},
        experience={"experience:0": _experience(2024, 2026)},
    )

    feedback = _build(
        resume=resume,
        vacancy=vacancy,
        matches=matches,
        general=_general(0.8),
        total_years=2.0,
    )

    assert any("превышает общий стаж" in risk for risk in feedback.risks)


def test_wide_interval_risk_when_many_requirements_not_stated() -> None:
    vacancy = _vacancy(
        {
            "a": _skill_requirement("A"),
            "b": _skill_requirement("B"),
            "c": _skill_requirement("C"),
        }
    )
    matches = {
        "a": _match(
            "a",
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="A",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        ),
        "b": _match(
            "b",
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="B",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        ),
        "c": _match("c", label="C", evidence="C", years_found=1.0),
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.4))

    assert any("2 из 3" in risk for risk in feedback.risks)


def test_missing_data_risk_for_relevant_section() -> None:
    vacancy = _vacancy({"python": _skill_requirement("Python")})
    matches = {
        "python": _match(
            "python",
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="Python",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        )
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.0))

    assert any("нет раздела «навыки»" in risk for risk in feedback.risks)


def test_no_requirements_fallback() -> None:
    feedback = _build(general=_general(0.0))

    assert feedback.strengths == []
    assert feedback.risks == []
    assert feedback.questions == []
    assert feedback.recruiter_note == "В вакансии нет требований для оценки."


def test_no_data_fallback() -> None:
    vacancy = _vacancy({"python": _skill_requirement("Python")})
    matches = {
        "python": _match(
            "python",
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="Python",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        )
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.0))

    assert feedback.recruiter_note == "Недостаточно данных резюме для содержательных выводов."


def test_feedback_respects_limits_and_deduplicates() -> None:
    vacancy = _vacancy({f"req{i}": _skill_requirement(f"Skill{i}") for i in range(7)})
    matches = {
        f"req{i}": _match(f"req{i}", label=f"Skill{i}", evidence=f"Skill{i}", years_found=1.0)
        for i in range(7)
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.9))

    assert len(feedback.strengths) == 5
    assert len(set(feedback.strengths)) == 5


def test_questions_respect_limit() -> None:
    vacancy = _vacancy({f"req{i}": _skill_requirement(f"Skill{i}") for i in range(7)})
    matches = {
        f"req{i}": _match(
            f"req{i}",
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label=f"Skill{i}",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        )
        for i in range(7)
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.0))

    assert len(feedback.questions) == 5
    assert len(feedback.risks) <= 5


def test_feedback_is_deterministic_for_same_input() -> None:
    vacancy = _vacancy({"python": _skill_requirement("Python", tenure=3)})
    matches = {
        "python": _match("python", label="Python", evidence="Python developer", years_found=3.0)
    }
    builder = FeedbackBuilder()

    first = builder.build(
        resume=_parsed_resume(),
        vacancy=vacancy,
        matches=matches,
        general=_general(0.8),
        reference_date=REFERENCE,
    )
    second = builder.build(
        resume=_parsed_resume(),
        vacancy=vacancy,
        matches=matches,
        general=_general(0.8),
        reference_date=REFERENCE,
    )

    assert first == second


def test_required_gap_variant_is_stable_for_same_fact() -> None:
    vacancy = _vacancy({"python": _skill_requirement("Python")})
    matches = {
        "python": _match(
            "python",
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="Python",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        )
    }

    texts = {
        FeedbackBuilder()
        .build(
            resume=_parsed_resume(),
            vacancy=vacancy,
            matches=matches,
            general=_general(0.2),
            reference_date=REFERENCE,
        )
        .risks[0]
        for _ in range(5)
    }

    assert len(texts) == 1
    assert texts.pop() in {
        "Обязательное требование «Python» не закрыто полностью: в резюме нет сведений.",
        "По обязательному требованию «Python» в резюме нет сведений.",
    }


def test_recruiter_note_recommends_clarification_for_important_gaps() -> None:
    vacancy = _vacancy(
        {
            "kafka": _skill_requirement(
                "Kafka",
                criticality=common_pb2.FEATURE_CRITICALITY_CRITICAL,
            )
        }
    )
    matches = {
        "kafka": _match(
            "kafka",
            criticality=common_pb2.FEATURE_CRITICALITY_CRITICAL,
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="Kafka",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        )
    }

    feedback = _build(
        resume=_parsed_resume(skills={"skill:0": skill_pb2.SkillPerResume(name="Python")}),
        vacancy=vacancy,
        matches=matches,
        general=_general(0.2),
    )

    assert "На скрининге уточните" in feedback.recruiter_note
    assert "навык «Kafka»" in feedback.recruiter_note


def test_recruiter_note_recommends_interview_when_all_full() -> None:
    vacancy = _vacancy({"python": _skill_requirement("Python")})
    matches = {
        "python": _match("python", label="Python", evidence="Python developer", years_found=3.0)
    }
    resume = _parsed_resume(skills={"skill:0": skill_pb2.SkillPerResume(name="Python", tenure=3)})

    feedback = _build(
        resume=resume,
        vacancy=vacancy,
        matches=matches,
        general=_general(0.9),
    )

    assert "техническое интервью" in feedback.recruiter_note


def test_generated_texts_do_not_contain_forbidden_phrases() -> None:
    vacancy = _vacancy(
        {
            "kafka": _skill_requirement(
                "Kafka",
                criticality=common_pb2.FEATURE_CRITICALITY_CRITICAL,
            ),
            "python": _skill_requirement("Python", tenure=3),
            "soft": _text_requirement(
                "коммуникация",
                criticality=common_pb2.FEATURE_CRITICALITY_NICE,
            ),
        }
    )
    matches = {
        "kafka": _match(
            "kafka",
            criticality=common_pb2.FEATURE_CRITICALITY_CRITICAL,
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="Kafka",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
        ),
        "python": _match(
            "python",
            status=common_pb2.CORRESPONDENCE_PARTIAL,
            label="Python",
            evidence="Python developer",
            years_found=1.0,
            gap_reason=common_pb2.GAP_REASON_INSUFFICIENT_TENURE,
        ),
        "soft": _match(
            "soft",
            criticality=common_pb2.FEATURE_CRITICALITY_NICE,
            status=common_pb2.CORRESPONDENCE_NOT_STATED,
            label="коммуникация",
            evidence=None,
            years_found=None,
            gap_reason=common_pb2.GAP_REASON_NOT_STATED,
            resume_kind=None,
        ),
    }

    feedback = _build(vacancy=vacancy, matches=matches, general=_general(0.3))
    texts = [
        *feedback.strengths,
        *feedback.risks,
        *feedback.questions,
        feedback.recruiter_note,
    ]

    for text in texts:
        for forbidden in ("не знает", "не владеет", "слабый", "отказ", "пригласить", "{", "}"):
            assert forbidden not in text


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        (0.5, "менее года"),
        (1.0, "1 год"),
        (2.0, "2 года"),
        (5.0, "5 лет"),
        (21.0, "21 год"),
        (1.5, "1,5 года"),
        (2.5, "2,5 года"),
    ],
)
def test_format_years(value: float, expected: str) -> None:
    assert _format_years(value) == expected


def test_stable_index_is_deterministic_and_bounded() -> None:
    assert _stable_index("risk:required_gap:python", 2) == _stable_index(
        "risk:required_gap:python",
        2,
    )
    assert 0 <= _stable_index("x", 3) < 3
