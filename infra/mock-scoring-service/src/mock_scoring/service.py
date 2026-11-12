"""Реализация контракта `scoring_service.v1.ScoringService` без модели скоринга.

Заглушка детерминирована: один вход даёт один и тот же ответ. Она не ходит в
LLM, БД и файлы, не получает PII и не логирует текст резюме или цитаты.
"""

from __future__ import annotations

import logging
import re
import time

import grpc
from google.type import date_pb2

from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import (
    common_pb2,
    education_pb2,
    experience_pb2,
    skill_pb2,
)

from .config import Settings
from .extraction import (
    Evidence,
    ParsedEducation,
    ParsedExperience,
    ParsedResume,
    ParsedSkill,
    extract,
    reference_year,
)
from .matching import Evaluation, MatchOutcome, evaluate

logger = logging.getLogger(__name__)

SCHEMA_VERSION = 1
SCENARIO_RE = re.compile(r"MOCK_SCORING_SCENARIO:\s*([a-z-]+)", re.IGNORECASE)
VACANCY_ERROR_MARKER = "MOCK_VACANCY_ERROR"
HEALTH_PREFIXES = ("/grpc.health.v1.Health/", "/grpc.reflection.")


class MockScoringService(scoring_pb2_grpc.ScoringServiceServicer):
    """Детерминированная заглушка scoring-service."""

    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or Settings.from_env()

    # --- RPC -------------------------------------------------------------

    def ExtractResume(
        self,
        request: messages_pb2.ExtractResumeRequest,
        context: grpc.ServicerContext,
    ) -> messages_pb2.ExtractResumeResponse:
        request_id = _request_id(context)
        scenario = self._apply_scenario(request.resume.text, context)
        text = request.resume.text
        if not text.strip():
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "resume text is empty")

        year = reference_year(request.reference_date)
        parsed = ParsedResume() if scenario == "empty" else extract(text, year)
        logger.info(
            "extract_resume_completed request_id=%s skills=%d education=%d "
            "experience=%d warnings=%d",
            request_id,
            len(parsed.skills),
            len(parsed.education),
            len(parsed.experience),
            len(parsed.warnings),
        )
        return messages_pb2.ExtractResumeResponse(
            parsed=_parsed_proto(parsed),
            dropped=0,
            warnings=list(parsed.warnings),
        )

    def ScoreResume(
        self,
        request: messages_pb2.ScoreResumeRequest,
        context: grpc.ServicerContext,
    ) -> messages_pb2.ScoreResumeResponse:
        request_id = _request_id(context)
        scenario = self._apply_scenario(request.resume.text, context)
        text = request.resume.text
        if not text.strip():
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "resume text is empty")

        year = reference_year(request.reference_date)
        parsed = ParsedResume() if scenario == "empty" else extract(text, year)
        evaluation = evaluate(parsed, text, request.vacancy, year)
        logger.info(
            "score_resume_completed request_id=%s requirements=%d features=%d gaps=%d",
            request_id,
            len(request.vacancy.requirements),
            len(evaluation.outcomes),
            sum(1 for outcome in evaluation.outcomes if outcome.gap_reason is not None),
        )
        return messages_pb2.ScoreResumeResponse(
            general_score=_general_score_proto(evaluation),
            features={
                outcome.requirement_id: _assessment_proto(outcome)
                for outcome in evaluation.outcomes
            },
        )

    def EvaluateResumeMatch(
        self,
        request: messages_pb2.EvaluateResumeMatchRequest,
        context: grpc.ServicerContext,
    ) -> messages_pb2.EvaluateResumeMatchResponse:
        request_id = _request_id(context)
        self._apply_scenario(request.unprocessed_resume.text, context)
        text = request.unprocessed_resume.text
        if not text.strip():
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "resume text is empty")

        year = reference_year(request.reference_date)
        parsed = _parsed_from_proto(request.resume, text)
        evaluation = evaluate(parsed, text, request.vacancy, year)
        logger.info(
            "evaluate_resume_match_completed request_id=%s requirements=%d gaps=%d",
            request_id,
            len(request.vacancy.requirements),
            sum(1 for outcome in evaluation.outcomes if outcome.gap_reason),
        )
        return _match_response(evaluation)

    def EvaluateResumeMatches(
        self,
        request: messages_pb2.EvaluateResumeMatchesRequest,
        context: grpc.ServicerContext,
    ) -> messages_pb2.EvaluateResumeMatchesResponse:
        request_id = _request_id(context)
        self._apply_scenario(request.unprocessed_resume.text, context)
        text = request.unprocessed_resume.text
        if not text.strip():
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "resume text is empty")

        year = reference_year(request.reference_date)
        parsed = _parsed_from_proto(request.resume, text)
        results: dict[str, messages_pb2.EvaluateResumeMatchResult] = {}
        errors = 0
        for vacancy_id, vacancy in request.vacancies.items():
            try:
                if VACANCY_ERROR_MARKER in vacancy.text_description:
                    raise ValueError("vacancy rejected by mock scenario")
                evaluation = evaluate(parsed, text, vacancy, year)
            except Exception as exc:
                errors += 1
                results[vacancy_id] = messages_pb2.EvaluateResumeMatchResult(
                    error=messages_pb2.BatchItemError(
                        code=grpc.StatusCode.INVALID_ARGUMENT.name,
                        message=str(exc),
                    )
                )
            else:
                results[vacancy_id] = messages_pb2.EvaluateResumeMatchResult(
                    match=_match_response(evaluation)
                )
        logger.info(
            "evaluate_resume_matches_completed request_id=%s vacancies=%d errors=%d",
            request_id,
            len(request.vacancies),
            errors,
        )
        return messages_pb2.EvaluateResumeMatchesResponse(results=results)

    # --- scenarios -------------------------------------------------------

    def _apply_scenario(self, text: str, context: grpc.ServicerContext) -> str:
        scenario = _scenario(text, context)
        if scenario == "unavailable":
            context.abort(grpc.StatusCode.UNAVAILABLE, "mock scoring unavailable")
        if scenario == "invalid-argument":
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "mock scoring rejected the request")
        if scenario == "internal":
            context.abort(grpc.StatusCode.INTERNAL, "mock scoring failed")
        if scenario == "slow":
            time.sleep(self._settings.slow_seconds)
        return scenario


# --- proto mapping -------------------------------------------------------


def _evidence_proto(evidence: Evidence) -> common_pb2.FeatureEvidence:
    return common_pb2.FeatureEvidence(
        start_index=evidence.start,
        end_index=evidence.end,
        text=evidence.text,
    )


def _skill_proto(skill: ParsedSkill) -> skill_pb2.SkillPerResume:
    message = skill_pb2.SkillPerResume(
        name=skill.name,
        normalized_name=skill.normalized_name,
        evidence=_evidence_proto(skill.evidence),
    )
    if skill.tenure is not None:
        message.tenure = skill.tenure
    return message


def _education_proto(item: ParsedEducation) -> education_pb2.EducationPerResume:
    message = education_pb2.EducationPerResume(
        level=item.level,
        evidence=_evidence_proto(item.evidence),
    )
    if item.graduation_year is not None:
        message.graduation_year.CopyFrom(_year_date(item.graduation_year))
    return message


def _experience_proto(item: ParsedExperience) -> experience_pb2.PriorExperiencePerResume:
    message = experience_pb2.PriorExperiencePerResume(metrics=[_evidence_proto(item.evidence)])
    if item.begin_year is not None:
        message.begin.CopyFrom(_year_date(item.begin_year))
    if item.end_year is not None:
        message.end.CopyFrom(_year_date(item.end_year))
    return message


def _year_date(year: int) -> date_pb2.Date:
    return date_pb2.Date(year=year, month=1, day=1)


def _parsed_proto(parsed: ParsedResume) -> messages_pb2.ParsedResume:
    return messages_pb2.ParsedResume(
        schema_version=SCHEMA_VERSION,
        skills={skill.key: _skill_proto(skill) for skill in parsed.skills},
        education={item.key: _education_proto(item) for item in parsed.education},
        experience={item.key: _experience_proto(item) for item in parsed.experience},
    )


def _parsed_from_proto(
    parsed: messages_pb2.ParsedResume,
    text: str,
) -> ParsedResume:
    """Переводит `ParsedResume` клиента в доменные объекты, отбрасывая чужие цитаты."""
    skills: list[ParsedSkill] = []
    for key, item in parsed.skills.items():
        evidence = _evidence_from_proto(item, text)
        if evidence is None:
            continue
        skills.append(
            ParsedSkill(
                key=key,
                name=item.name,
                normalized_name=item.normalized_name,
                evidence=evidence,
                tenure=int(item.tenure) if item.HasField("tenure") else None,
            )
        )

    education: list[ParsedEducation] = []
    for key, item in parsed.education.items():
        evidence = _evidence_from_proto(item, text)
        if evidence is None:
            continue
        education.append(
            ParsedEducation(
                key=key,
                level=int(item.level),
                evidence=evidence,
                graduation_year=item.graduation_year.year or None,
            )
        )

    experience: list[ParsedExperience] = []
    for key, item in parsed.experience.items():
        evidence = _first_metric(item, text)
        if evidence is None:
            continue
        experience.append(
            ParsedExperience(
                key=key,
                evidence=evidence,
                begin_year=item.begin.year or None,
                end_year=item.end.year or None,
            )
        )

    return ParsedResume(
        skills=tuple(skills),
        education=tuple(education),
        experience=tuple(experience),
    )


def _evidence_from_proto(item: object, text: str) -> Evidence | None:
    if not item.HasField("evidence") or not item.evidence.text:
        return None
    index = text.find(item.evidence.text)
    if index < 0:
        return None
    return Evidence(
        start=index,
        end=index + len(item.evidence.text),
        text=item.evidence.text,
    )


def _first_metric(item: experience_pb2.PriorExperiencePerResume, text: str) -> Evidence | None:
    for metric in item.metrics:
        if metric.text:
            index = text.find(metric.text)
            if index >= 0:
                return Evidence(start=index, end=index + len(metric.text), text=metric.text)
    return None


def _general_score_proto(evaluation: Evaluation) -> messages_pb2.GeneralResumeScore:
    message = messages_pb2.GeneralResumeScore(
        relevance=evaluation.relevance,
        score_low=evaluation.score_low,
        score_high=evaluation.score_high,
        score=evaluation.score,
    )
    if evaluation.tenure is not None:
        message.tenure = evaluation.tenure
    return message


def _assessment_proto(outcome: MatchOutcome) -> features_pb2.FeatureAssessment:
    message = features_pb2.FeatureAssessment(
        relevance=outcome.relevance,
        coverage=outcome.coverage,
    )
    if outcome.years_found is not None:
        message.tenure = int(outcome.years_found)
    matched = outcome.matched
    if isinstance(matched, ParsedSkill):
        message.skill.CopyFrom(_skill_proto(matched))
    elif isinstance(matched, ParsedEducation):
        message.education.CopyFrom(_education_proto(matched))
    elif isinstance(matched, ParsedExperience):
        message.experience.CopyFrom(_experience_proto(matched))
    return message


def _match_response(evaluation: Evaluation) -> messages_pb2.EvaluateResumeMatchResponse:
    matches: dict[str, messages_pb2.FeatureMatch] = {}
    gaps: list[messages_pb2.Gap] = []
    for outcome in evaluation.outcomes:
        match = messages_pb2.FeatureMatch(
            status=outcome.status,
            relevance=outcome.relevance,
            coverage=outcome.coverage,
            resume_feature_key=outcome.resume_feature_key,
            comment=outcome.comment,
        )
        if outcome.evidence is not None:
            match.evidence.CopyFrom(_evidence_proto(outcome.evidence))
        if outcome.years_found is not None:
            match.years_found = outcome.years_found
        matches[outcome.requirement_id] = match
        if outcome.gap_reason is not None:
            gaps.append(
                messages_pb2.Gap(
                    requirement_id=outcome.requirement_id,
                    reason=outcome.gap_reason,
                )
            )

    return messages_pb2.EvaluateResumeMatchResponse(
        general_score=_general_score_proto(evaluation),
        matches=matches,
        strengths=list(evaluation.strengths),
        gaps=gaps,
        risks=list(evaluation.risks),
        questions=list(evaluation.questions),
        recruiter_note=evaluation.recruiter_note,
    )


# --- helpers -------------------------------------------------------------


def _scenario(text: str, context: grpc.ServicerContext) -> str:
    for item in context.invocation_metadata() or ():  # type: ignore[union-attr]
        if item.key.lower() == "x-mock-scenario":
            return item.value.strip().lower()
    match = SCENARIO_RE.search(text)
    return match.group(1).lower() if match else "ok"


def _request_id(context: grpc.ServicerContext) -> str:
    for item in context.invocation_metadata() or ():  # type: ignore[union-attr]
        if item.key.lower() == "x-request-id":
            return item.value
    return "-"


def is_health_method(method: str) -> bool:
    return method.startswith(HEALTH_PREFIXES)


__all__ = ["MockScoringService", "is_health_method"]
