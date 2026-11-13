"""Извлечение признаков резюме через LLM и проверка цитат."""

from __future__ import annotations

import logging
from dataclasses import dataclass

from pydantic import ValidationError

from ..llm.client import LlmClient, LlmInvalidResponse
from ..prompts import PROMPT_VERSION, resume_extraction
from .quotes import QuoteIndex, collapse
from .schema import (
    ExtractedEducation,
    ExtractedExperience,
    ExtractedQualification,
    ExtractedSkill,
    ParsedResume,
)

logger = logging.getLogger(__name__)


class ExtractionError(Exception):
    """LLM не вернула валидный по схеме ответ после одного повтора."""


@dataclass(frozen=True, slots=True)
class ExtractionResult:
    parsed: ParsedResume
    dropped: int


class ResumeExtractor:
    """Один LLM-вызов на резюме; невалидные цитаты отбрасываются."""

    def __init__(self, client: LlmClient, enforce_validity: bool = True) -> None:
        self._client = client
        self._enforce_quote_provenance = enforce_validity

    @property
    def model(self) -> str:
        return self._client.model

    @property
    def prompt_version(self) -> str:
        return PROMPT_VERSION

    def extract(self, text: str) -> ExtractionResult:
        index = QuoteIndex(text)
        system = resume_extraction.system_prompt()
        user = resume_extraction.user_prompt(text)
        last_error: Exception | None = None

        for attempt in range(2):
            prompt = system if attempt == 0 else f"{system}\n\n{resume_extraction.RETRY_HINT}"
            try:
                raw = self._client.complete(prompt, user)
                parsed = ParsedResume.model_validate_json(raw)
            except (LlmInvalidResponse, ValidationError) as exc:
                last_error = exc
                logger.warning(
                    "resume extraction failed attempt=%d error=%s",
                    attempt + 1,
                    type(exc).__name__,
                )
                continue
            if self._enforce_quote_provenance:
                extracted = self._drop_invalid(parsed, index)
                if extracted.dropped > 0:
                    logger.warning(
                        "some resume features dropped count=%s",
                        extracted.dropped,
                    )
                return extracted
            return ExtractionResult(parsed=parsed, dropped=0)

        raise ExtractionError("LLM did not return a valid resume extraction") from last_error

    @staticmethod
    def _drop_invalid(parsed: ParsedResume, index: QuoteIndex) -> ExtractionResult:
        dropped = 0

        skills: list[ExtractedSkill] = []
        for skill in parsed.skills:
            if index.locate(skill.quote) is None:
                dropped += 1
                logger.debug("skill dropped due to a bad quote")
                continue
            skills.append(skill)

        qualifications: list[ExtractedQualification] = []
        for qualification in parsed.qualifications:
            located = index.locate(qualification.quote)
            if located is None:
                dropped += 1
                logger.debug("qualification dropped due to a bad quote")
                continue
            quote = index.slice(*located)
            label, _ = collapse(qualification.label.strip())
            if not label.strip() or label.strip() not in collapse(quote)[0]:
                dropped += 1
                logger.debug("qualification dropped because label is not a quote fragment")
                continue
            qualifications.append(qualification)

        education: list[ExtractedEducation] = []
        for item in parsed.education:
            if index.locate(item.quote) is None:
                dropped += 1
                logger.debug("education dropped due to a bad quote")
                continue
            education.append(item)

        experience: list[ExtractedExperience] = []
        for item in parsed.experience:
            if index.locate(item.quote) is None:
                dropped += 1
                logger.debug("experience dropped due to a bad quote")
                continue
            metrics: list[str] = []
            for metric in item.metrics:
                if index.locate(metric) is None:
                    dropped += 1
                    continue
                metrics.append(metric)
            experience.append(item.model_copy(update={"metrics": metrics}))

        cleaned = parsed.model_copy(
            update={
                "skills": skills,
                "qualifications": qualifications,
                "education": education,
                "experience": experience,
            }
        )
        return ExtractionResult(parsed=cleaned, dropped=dropped)
