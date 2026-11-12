from __future__ import annotations

from conftest import REFERENCE_DATE, RESUME_TEXT, skill_requirement, soft_requirement, vacancy

from mock_scoring.extraction import extract
from scoring_service.v1.models import messages_pb2
from scoring_service.v1.models.features import common_pb2


def evaluate(stub, requirements, text: str = RESUME_TEXT):
    parsed = stub.ExtractResume(
        messages_pb2.ExtractResumeRequest(
            resume=messages_pb2.UnprocessedResume(text=text),
            reference_date=REFERENCE_DATE,
        )
    ).parsed
    return stub.EvaluateResumeMatch(
        messages_pb2.EvaluateResumeMatchRequest(
            resume=parsed,
            unprocessed_resume=messages_pb2.UnprocessedResume(text=text),
            vacancy=vacancy(requirements),
            reference_date=REFERENCE_DATE,
        )
    )


def test_statuses_follow_extracted_features(stub) -> None:
    requirements = {
        "req-java": skill_requirement("Java"),
        "req-kafka": skill_requirement("Kafka", tenure=5),
        "req-go": skill_requirement("Go"),
        "req-soft": soft_requirement("стрессоустойчивость"),
    }

    response = evaluate(stub, requirements)

    assert set(response.matches) == set(requirements)
    assert response.matches["req-java"].status == common_pb2.CORRESPONDENCE_FULL
    assert response.matches["req-kafka"].status == common_pb2.CORRESPONDENCE_PARTIAL
    assert response.matches["req-kafka"].years_found == 3
    assert response.matches["req-go"].status == common_pb2.CORRESPONDENCE_NOT_STATED
    assert response.matches["req-soft"].status == common_pb2.CORRESPONDENCE_NOT_STATED

    gap_reasons = {gap.requirement_id: gap.reason for gap in response.gaps}
    assert gap_reasons["req-kafka"] == common_pb2.GAP_REASON_INSUFFICIENT_TENURE
    assert gap_reasons["req-go"] == common_pb2.GAP_REASON_NOT_STATED
    assert all(gap.requirement_id in requirements for gap in response.gaps)

    assert response.matches["req-java"].evidence.text in RESUME_TEXT
    assert response.strengths
    assert response.recruiter_note


def test_general_score_is_ordered(stub) -> None:
    response = evaluate(stub, {"req-java": skill_requirement("Java")})
    score = response.general_score

    assert 0.0 <= score.score_low <= score.score <= score.score_high <= 1.0


def test_explicit_absence_is_absent_not_full(stub) -> None:
    text = "Опыт работы\nбез опыта с Go\n"
    response = evaluate(stub, {"req-go": skill_requirement("Go")}, text=text)

    assert response.matches["req-go"].status == common_pb2.CORRESPONDENCE_ABSENT
    assert response.gaps[0].reason == common_pb2.GAP_REASON_ABSENT


def test_dropped_quote_is_not_used(stub) -> None:
    text = "Навыки: Java"
    parsed = stub.ExtractResume(
        messages_pb2.ExtractResumeRequest(
            resume=messages_pb2.UnprocessedResume(text=text),
            reference_date=REFERENCE_DATE,
        )
    ).parsed
    # Подменяем цитату на отсутствующую в тексте — заглушка обязана её отбросить.
    skill = next(iter(parsed.skills.values()))
    skill.evidence.text = "цитата, которой нет в тексте"

    response = stub.EvaluateResumeMatch(
        messages_pb2.EvaluateResumeMatchRequest(
            resume=parsed,
            unprocessed_resume=messages_pb2.UnprocessedResume(text=text),
            vacancy=vacancy({"req-java": skill_requirement("Java")}),
            reference_date=REFERENCE_DATE,
        )
    )

    assert response.matches["req-java"].status == common_pb2.CORRESPONDENCE_NOT_STATED
    assert not response.matches["req-java"].HasField("evidence")


def test_batch_isolates_vacancy_error(stub) -> None:
    text = RESUME_TEXT
    parsed = stub.ExtractResume(
        messages_pb2.ExtractResumeRequest(
            resume=messages_pb2.UnprocessedResume(text=text),
            reference_date=REFERENCE_DATE,
        )
    ).parsed
    requirements = {"req-java": skill_requirement("Java")}

    response = stub.EvaluateResumeMatches(
        messages_pb2.EvaluateResumeMatchesRequest(
            resume=parsed,
            unprocessed_resume=messages_pb2.UnprocessedResume(text=text),
            vacancies={
                "vacancy-ok": vacancy(requirements),
                "vacancy-bad": vacancy(requirements, text="MOCK_VACANCY_ERROR"),
            },
            reference_date=REFERENCE_DATE,
        )
    )

    assert set(response.results) == {"vacancy-ok", "vacancy-bad"}
    assert response.results["vacancy-ok"].WhichOneof("result") == "match"
    assert response.results["vacancy-bad"].WhichOneof("result") == "error"
    assert response.results["vacancy-bad"].error.code == "INVALID_ARGUMENT"


def test_extraction_ignores_absent_skills_from_other_fields() -> None:
    parsed = extract("Java\nPython", 2026)
    assert [skill.normalized_name for skill in parsed.skills] == ["Java", "Python"]
