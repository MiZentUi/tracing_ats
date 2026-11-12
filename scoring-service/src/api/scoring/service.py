"""gRPC-сервис: тонкая оркестрация извлечения, оценки и сборки ответов."""

from __future__ import annotations

import logging
from datetime import date

import grpc
from google.type import date_pb2

from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import messages_pb2

from ..extraction.extractor import ExtractionError, ExtractionResult, ResumeExtractor
from ..llm.client import LlmUnavailable
from .dates import resolve_reference_date
from .errors import ScoringError
from .evaluator import ResumeEvaluator
from .feedback import FEEDBACK_TEMPLATE_VERSION
from .mapping import ExtractionMapper, to_evaluate_response, to_score_response
from .policies import Clock, SystemClock

logger = logging.getLogger(__name__)


class ScoringService(scoring_pb2_grpc.ScoringServiceServicer):
    """Онлайн-оценка и извлечение резюме.

    `ScoreResume` и `ExtractResume` вызывают LLM. `EvaluateResumeMatch` и
    `EvaluateResumeMatches` работают только с уже извлечённым резюме.
    """

    def __init__(
        self,
        extractor: ResumeExtractor,
        evaluator: ResumeEvaluator,
        mapper: ExtractionMapper,
        clock: Clock | None = None,
    ) -> None:
        self._extractor = extractor
        self._evaluator = evaluator
        self._mapper = mapper
        self._clock = clock or SystemClock()

    def ScoreResume(
        self,
        request: messages_pb2.ScoreResumeRequest,
        context: grpc.ServicerContext,
    ) -> messages_pb2.ScoreResumeResponse:
        text = request.resume.text
        result = self._extract(text, context)
        parsed = self._mapper.to_parsed_resume(result.parsed, text)
        evaluation = self._evaluator.evaluate(
            parsed,
            text,
            request.vacancy,
            self._reference_date(request.reference_date),
        )

        logger.info(
            "score resume model=%s prompt=%s requirements=%d "
            "skills=%d education=%d experience=%d dropped=%d",
            self._extractor.model,
            self._extractor.prompt_version,
            len(request.vacancy.requirements),
            len(result.parsed.skills),
            len(result.parsed.education),
            len(result.parsed.experience),
            result.dropped,
        )
        return to_score_response(evaluation)

    def EvaluateResumeMatch(
        self,
        request: messages_pb2.EvaluateResumeMatchRequest,
        context: grpc.ServicerContext,
    ) -> messages_pb2.EvaluateResumeMatchResponse:
        text = request.unprocessed_resume.text
        self._require_text(text, context)
        evaluation = self._evaluator.evaluate(
            request.resume,
            text,
            request.vacancy,
            self._reference_date(request.reference_date),
        )

        logger.info(
            "evaluate resume match requirements=%d matches=%d gaps=%d "
            "strengths=%d risks=%d questions=%d feedback=%s",
            len(request.vacancy.requirements),
            len(evaluation.matches),
            len(evaluation.gaps),
            len(evaluation.strengths),
            len(evaluation.risks),
            len(evaluation.questions),
            FEEDBACK_TEMPLATE_VERSION,
        )
        return to_evaluate_response(evaluation)

    def EvaluateResumeMatches(
        self,
        request: messages_pb2.EvaluateResumeMatchesRequest,
        context: grpc.ServicerContext,
    ) -> messages_pb2.EvaluateResumeMatchesResponse:
        text = request.unprocessed_resume.text
        self._require_text(text, context)
        reference_date = self._reference_date(request.reference_date)
        results: dict[str, messages_pb2.EvaluateResumeMatchResult] = {}
        succeeded = 0
        failed = 0

        for key, vacancy in request.vacancies.items():
            item = messages_pb2.EvaluateResumeMatchResult()
            try:
                evaluation = self._evaluator.evaluate(
                    request.resume,
                    text,
                    vacancy,
                    reference_date,
                )
                item.match.CopyFrom(to_evaluate_response(evaluation))
                succeeded += 1
            except ScoringError as exc:
                item.error.CopyFrom(messages_pb2.BatchItemError(code=exc.code, message=exc.message))
                failed += 1
                logger.warning(
                    "evaluate resume matches item failed vacancy=%s code=%s error=%s",
                    key,
                    exc.code,
                    type(exc).__name__,
                )
            except Exception as exc:
                item.error.CopyFrom(
                    messages_pb2.BatchItemError(
                        code="INTERNAL",
                        message="vacancy evaluation failed",
                    )
                )
                failed += 1
                logger.warning(
                    "evaluate resume matches item failed vacancy=%s code=INTERNAL error=%s",
                    key,
                    type(exc).__name__,
                )
            results[key] = item

        logger.info(
            "evaluate resume matches vacancies=%d ok=%d failed=%d",
            len(request.vacancies),
            succeeded,
            failed,
        )
        return messages_pb2.EvaluateResumeMatchesResponse(results=results)

    def ExtractResume(
        self,
        request: messages_pb2.ExtractResumeRequest,
        context: grpc.ServicerContext,
    ) -> messages_pb2.ExtractResumeResponse:
        text = request.resume.text
        result = self._extract(text, context)
        reference_date = self._reference_date(request.reference_date)
        parsed = self._mapper.to_parsed_resume(result.parsed, text)
        warnings = self._mapper.warnings(parsed, reference_date)

        logger.info(
            "extract resume model=%s prompt=%s schema=%d "
            "skills=%d education=%d experience=%d dropped=%d warnings=%d",
            self._extractor.model,
            self._extractor.prompt_version,
            result.parsed.schema_version,
            len(result.parsed.skills),
            len(result.parsed.education),
            len(result.parsed.experience),
            result.dropped,
            len(warnings),
        )
        return messages_pb2.ExtractResumeResponse(
            parsed=parsed,
            dropped=result.dropped,
            warnings=warnings,
        )

    def _extract(self, text: str, context: grpc.ServicerContext) -> ExtractionResult:
        self._require_text(text, context)
        try:
            return self._extractor.extract(text)
        except LlmUnavailable:
            context.abort(grpc.StatusCode.UNAVAILABLE, "LLM is unavailable")
        except ExtractionError:
            context.abort(grpc.StatusCode.INTERNAL, "LLM response failed validation")
        raise AssertionError("context.abort must not return")

    @staticmethod
    def _require_text(text: str, context: grpc.ServicerContext) -> None:
        if not text.strip():
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "resume text is empty")

    def _reference_date(self, value: date_pb2.Date | None) -> date:
        return resolve_reference_date(value, self._clock.today())
