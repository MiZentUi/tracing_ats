"""Сборка protobuf-сообщений из внутренних моделей и extraction-схемы."""

from __future__ import annotations

from datetime import date

from google.type import date_pb2

from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import (
    common_pb2,
    education_pb2,
    experience_pb2,
    skill_pb2,
)

from ..extraction.normalize import normalize_key
from ..extraction.quotes import QuoteIndex
from ..extraction.schema import EducationLevel
from ..extraction.schema import ParsedResume as ExtractedResume
from .dates import end_date, start_date, total_experience_years
from .model import Evaluation, GeneralScore, RequirementScore


class ExtractionMapper:
    """Переводит ответ LLM в `ParsedResume` с проверенными цитатами и ключами."""

    def to_parsed_resume(
        self,
        extraction: ExtractedResume,
        text: str,
    ) -> messages_pb2.ParsedResume:
        index = QuoteIndex(text)
        parsed = messages_pb2.ParsedResume(schema_version=extraction.schema_version)

        for position, skill in enumerate(extraction.skills):
            evidence = _evidence(index, skill.quote)
            if evidence is None:
                continue
            message = skill_pb2.SkillPerResume(
                name=skill.name,
                normalized_name=normalize_key(skill.name),
                evidence=evidence,
            )
            if skill.years is not None:
                message.tenure = skill.years
            parsed.skills[f"skill:{position}"].CopyFrom(message)

        for position, item in enumerate(extraction.education):
            evidence = _evidence(index, item.quote)
            if evidence is None:
                continue
            message = education_pb2.EducationPerResume(
                institution=item.institution or "",
                master=item.field or "",
                level=_education_level(item.level),
                evidence=evidence,
            )
            if item.graduation_year is not None:
                message.graduation_year.CopyFrom(date_pb2.Date(year=item.graduation_year))
            parsed.education[f"education:{position}"].CopyFrom(message)

        for position, item in enumerate(extraction.experience):
            evidence = _evidence(index, item.quote)
            if evidence is None:
                continue
            message = experience_pb2.PriorExperiencePerResume(
                company=item.company or "",
                position=item.position or "",
                category=item.category or "",
            )
            message.metrics.add().CopyFrom(evidence)
            for metric in item.metrics:
                metric_evidence = _evidence(index, metric)
                if metric_evidence is not None:
                    message.metrics.add().CopyFrom(metric_evidence)
            if item.begin_year is not None:
                message.begin.CopyFrom(date_pb2.Date(year=item.begin_year))
            if item.end_year is not None:
                message.end.CopyFrom(date_pb2.Date(year=item.end_year))
            parsed.experience[f"experience:{position}"].CopyFrom(message)

        return parsed

    def warnings(
        self,
        parsed: messages_pb2.ParsedResume,
        reference_date: date,
    ) -> list[str]:
        warnings: list[str] = []
        total_years = total_experience_years(parsed.experience.values(), reference_date)

        for key, skill in sorted(parsed.skills.items()):
            if (
                total_years is not None
                and skill.HasField("tenure")
                and skill.tenure > total_years + 1.0
            ):
                warnings.append(f"{key}: skill tenure exceeds total experience")

        for key, item in sorted(parsed.experience.items()):
            begin = start_date(item.begin)
            if begin is None:
                warnings.append(f"{key}: begin date is missing")
                continue
            if begin > reference_date:
                warnings.append(f"{key}: begin date is in the future")
            if item.HasField("end"):
                end = end_date(item.end, reference_date)
                if end is not None and end < begin:
                    warnings.append(f"{key}: end date is before begin date")

        for key, item in sorted(parsed.education.items()):
            if item.HasField("graduation_year") and item.graduation_year.year > reference_date.year:
                warnings.append(f"{key}: graduation year is in the future")

        return warnings


def to_general_score(general: GeneralScore) -> messages_pb2.GeneralResumeScore:
    message = messages_pb2.GeneralResumeScore(
        relevance=general.relevance,
        score_low=general.score_low,
        score_high=general.score_high,
        score=general.score,
    )
    if general.tenure is not None:
        message.tenure = general.tenure
    return message


def to_feature_match(match: RequirementScore) -> messages_pb2.FeatureMatch:
    message = messages_pb2.FeatureMatch(
        status=match.status,
        relevance=match.relevance,
        coverage=match.coverage,
        comment=match.comment,
    )
    if match.feature_key:
        message.resume_feature_key = match.feature_key
    if match.evidence is not None:
        message.evidence.CopyFrom(match.evidence)
    if match.years_found is not None:
        message.years_found = match.years_found
    return message


def to_feature_assessment(match: RequirementScore) -> features_pb2.FeatureAssessment:
    message = features_pb2.FeatureAssessment(
        relevance=match.relevance,
        coverage=match.coverage,
    )
    if match.years_found is not None:
        message.tenure = round(match.years_found)
    if match.resume_kind == "skill" and isinstance(match.resume_feature, skill_pb2.SkillPerResume):
        message.skill.CopyFrom(match.resume_feature)
    elif match.resume_kind == "education" and isinstance(
        match.resume_feature, education_pb2.EducationPerResume
    ):
        message.education.CopyFrom(match.resume_feature)
    elif match.resume_kind == "experience" and isinstance(
        match.resume_feature, experience_pb2.PriorExperiencePerResume
    ):
        message.experience.CopyFrom(match.resume_feature)
    return message


def to_evaluate_response(
    evaluation: Evaluation,
) -> messages_pb2.EvaluateResumeMatchResponse:
    response = messages_pb2.EvaluateResumeMatchResponse(
        resolver_version=evaluation.resolver_version,
        dictionary_mode=evaluation.dictionary_mode,
    )
    response.general_score.CopyFrom(to_general_score(evaluation.general))
    for key, match in evaluation.matches.items():
        response.matches[key].CopyFrom(to_feature_match(match))
    response.strengths.extend(evaluation.strengths)
    for gap in evaluation.gaps:
        response.gaps.add(requirement_id=gap.requirement_id, reason=gap.reason)
    response.risks.extend(evaluation.risks)
    response.questions.extend(evaluation.questions)
    response.recruiter_note = evaluation.recruiter_note
    return response


def to_score_response(evaluation: Evaluation) -> messages_pb2.ScoreResumeResponse:
    response = messages_pb2.ScoreResumeResponse(
        resolver_version=evaluation.resolver_version,
        dictionary_mode=evaluation.dictionary_mode,
    )
    response.general_score.CopyFrom(to_general_score(evaluation.general))
    for key, match in evaluation.matches.items():
        response.features[key].CopyFrom(to_feature_assessment(match))
    return response


def _evidence(
    index: QuoteIndex,
    quote: str,
) -> common_pb2.FeatureEvidence | None:
    located = index.locate(quote)
    if located is None:
        return None
    start, end = located
    return common_pb2.FeatureEvidence(
        start_index=start,
        end_index=end,
        text=index.slice(start, end),
    )


def _education_level(level: EducationLevel) -> int:
    return {
        EducationLevel.UNKNOWN: education_pb2.EDUCATION_LEVEL_UNSPECIFIED,
        EducationLevel.SECONDARY: education_pb2.EDUCATION_LEVEL_SECONDARY,
        EducationLevel.SECONDARY_SPECIALIZED: (education_pb2.EDUCATION_LEVEL_SECONDARY_SPECIALIZED),
        EducationLevel.BACHELOR: education_pb2.EDUCATION_LEVEL_BACHELOR,
        EducationLevel.MASTER: education_pb2.EDUCATION_LEVEL_MASTER,
        EducationLevel.DOCTOR: education_pb2.EDUCATION_LEVEL_DOCTOR,
        EducationLevel.COURSES: education_pb2.EDUCATION_LEVEL_COURSES,
    }.get(level, education_pb2.EDUCATION_LEVEL_UNSPECIFIED)
