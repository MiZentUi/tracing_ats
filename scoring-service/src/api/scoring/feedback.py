"""Детерминированная генерация объяснений по результатам скоринга.

LLM здесь не вызывается. Модуль выбирает факты из уже посчитанного
сопоставления и подставляет их в шаблоны. Формулы скоринга не меняются:
`strengths`, `risks`, `questions` и `recruiter_note` — слой объяснений.

Каталог шаблонов и правила выбора зафиксированы в
`docs/rfc/rfc-feedback-generation.md`.
"""

from __future__ import annotations

import hashlib
import re
from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from datetime import date
from typing import Literal

from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import common_pb2

from .dates import end_date, start_date, total_experience_years
from .model import GeneralScore, RequirementScore

FEEDBACK_TEMPLATE_VERSION = "feedback-templates.v3"

MAX_STRENGTHS = 5
MAX_RISKS = 5
MAX_QUESTIONS = 5
MAX_LABEL_CHARS = 120
MAX_QUOTE_CHARS = 160
MAX_TEXT_CHARS = 400
MAX_NOTE_CHARS = 500

FeedbackKind = Literal["strength", "risk", "question", "note", "fallback"]

_WHITESPACE = re.compile(r"\s+")


@dataclass(frozen=True, slots=True)
class FeedbackTemplate:
    """Один шаблон формулировки. Слоты подставляются детерминированно."""

    id: str
    kind: FeedbackKind
    condition: str
    text: str


@dataclass(frozen=True, slots=True)
class FeedbackFact:
    """Факт, выбранный кодом. Шаблон только формулирует его."""

    fact_id: str
    kind: FeedbackKind
    condition: str
    priority: int
    slots: Mapping[str, str]


@dataclass(frozen=True, slots=True)
class Feedback:
    """Готовые объяснения для ответа gRPC."""

    strengths: list[str]
    risks: list[str]
    questions: list[str]
    recruiter_note: str


FEEDBACK_TEMPLATES: tuple[FeedbackTemplate, ...] = (
    # Сильные стороны: 5 шаблонов.
    FeedbackTemplate(
        id="strength.critical_full",
        kind="strength",
        condition="critical_full",
        text="Критичное требование «{label}» полностью закрыто: {evidence}.{tenure_note}",
    ),
    FeedbackTemplate(
        id="strength.skill_full",
        kind="strength",
        condition="skill_full",
        text="Навык «{label}» подтверждён в резюме: {evidence}.{tenure_note}",
    ),
    FeedbackTemplate(
        id="strength.experience_full",
        kind="strength",
        condition="experience_full",
        text="Опыт в категории «{label}» подтверждён: {evidence}.{tenure_note}",
    ),
    FeedbackTemplate(
        id="strength.qualification_full",
        kind="strength",
        condition="qualification_full",
        text="Квалификация «{label}» подтверждена: {evidence}.",
    ),
    FeedbackTemplate(
        id="strength.education_full",
        kind="strength",
        condition="education_full",
        text="Образование по направлению «{label}» подтверждено: {evidence}.",
    ),
    FeedbackTemplate(
        id="strength.text_full",
        kind="strength",
        condition="text_full",
        text="Требование «{label}» подтверждено текстом резюме: {evidence}.",
    ),
    # Риски: 8 шаблонов, включая два варианта для обязательного пробела.
    FeedbackTemplate(
        id="risk.critical_gap",
        kind="risk",
        condition="critical_gap",
        text="Критичное требование «{label}» {status_phrase} — это ключевой риск для вакансии.",
    ),
    FeedbackTemplate(
        id="risk.required_gap.v1",
        kind="risk",
        condition="required_gap",
        text="Обязательное требование «{label}» не закрыто полностью: {status_note}.",
    ),
    FeedbackTemplate(
        id="risk.required_gap.v2",
        kind="risk",
        condition="required_gap",
        text="По обязательному требованию «{label}» {status_note}.",
    ),
    FeedbackTemplate(
        id="risk.insufficient_tenure",
        kind="risk",
        condition="insufficient_tenure",
        text=(
            "По требованию «{label}» подтверждён стаж {found_years}, а требуется {required_years}."
        ),
    ),
    FeedbackTemplate(
        id="risk.date_issue",
        kind="risk",
        condition="date_issue",
        text="В датах опыта есть пробел или расхождение: {detail}. Уточните фактические периоды.",
    ),
    FeedbackTemplate(
        id="risk.overclaim",
        kind="risk",
        condition="overclaim",
        text=(
            "Заявленный стаж по навыку «{label}» ({found_years}) превышает "
            "общий стаж по резюме ({total_years})."
        ),
    ),
    FeedbackTemplate(
        id="risk.wide_interval",
        kind="risk",
        condition="wide_interval",
        text=(
            "Доля требований без сведений в резюме — {not_stated_count} из {total_count}; "
            "вилка оценки широкая."
        ),
    ),
    FeedbackTemplate(
        id="risk.missing_data",
        kind="risk",
        condition="missing_data",
        text=(
            "В резюме нет раздела «{section}»; выводы по связанным требованиям "
            "опираются на неполные данные."
        ),
    ),
    # Вопросы: 9 шаблонов по интентам.
    FeedbackTemplate(
        id="question.skill_not_stated",
        kind="question",
        condition="skill_not_stated",
        text=(
            "В резюме не указан навык «{label}». Приведите пример задачи, "
            "где вы его применяли, и сколько времени это занимало?"
        ),
    ),
    FeedbackTemplate(
        id="question.experience_not_stated",
        kind="question",
        condition="experience_not_stated",
        text=(
            "В резюме не указан опыт в категории «{label}». "
            "Расскажите о релевантных проектах и вашей роли в них."
        ),
    ),
    FeedbackTemplate(
        id="question.qualification_not_stated",
        kind="question",
        condition="qualification_not_stated",
        text=(
            "В резюме не указана квалификация «{label}». "
            "Подтвердите, есть ли она у вас, и приложите документ."
        ),
    ),
    FeedbackTemplate(
        id="question.education_not_stated",
        kind="question",
        condition="education_not_stated",
        text=(
            "В резюме не указано образование по направлению «{label}». "
            "Уточните профиль обучения и пройденные курсы."
        ),
    ),
    FeedbackTemplate(
        id="question.text_not_stated",
        kind="question",
        condition="text_not_stated",
        text=(
            "В резюме не указано подтверждение требования «{label}». "
            "Приведите конкретный пример из опыта."
        ),
    ),
    FeedbackTemplate(
        id="question.skill_insufficient_tenure",
        kind="question",
        condition="skill_insufficient_tenure",
        text=(
            "По навыку «{label}» подтверждено {found_years}, требуется {required_years}. "
            "Уточните весь опыт: в каких проектах и сколько времени?"
        ),
    ),
    FeedbackTemplate(
        id="question.experience_insufficient_tenure",
        kind="question",
        condition="experience_insufficient_tenure",
        text=(
            "По опыту в категории «{label}» подтверждено {found_years}, "
            "требуется {required_years}. Уточните периоды и задачи."
        ),
    ),
    FeedbackTemplate(
        id="question.partial",
        kind="question",
        condition="partial",
        text=(
            "Требование «{label}» подтверждено частично. "
            "Какие задачи вы решали в этой области и насколько самостоятельно?"
        ),
    ),
    FeedbackTemplate(
        id="question.absent",
        kind="question",
        condition="absent",
        text=(
            "В резюме есть близкий опыт, но требование «{label}» не подтверждено. "
            "С какими похожими инструментами или задачами вы работали?"
        ),
    ),
    FeedbackTemplate(
        id="question.date_issue",
        kind="question",
        condition="date_issue",
        text=(
            "В датах опыта есть пробел или расхождение: {detail}. "
            "Уточните фактические периоды работы."
        ),
    ),
    # Рекомендация рекрутёру: 6 шаблонов.
    FeedbackTemplate(
        id="note.opener_high",
        kind="note",
        condition="opener_high",
        text=(
            "Соответствие высокое: полностью закрыто {full_count} из {total_count} "
            "требований, оценка {score} (вилка {score_low}–{score_high})."
        ),
    ),
    FeedbackTemplate(
        id="note.opener_mid",
        kind="note",
        condition="opener_mid",
        text=(
            "Соответствие частичное: полностью закрыто {full_count} из {total_count} "
            "требований, оценка {score} (вилка {score_low}–{score_high})."
        ),
    ),
    FeedbackTemplate(
        id="note.opener_low",
        kind="note",
        condition="opener_low",
        text=(
            "Соответствие низкое: полностью закрыто {full_count} из {total_count} "
            "требований, оценка {score} (вилка {score_low}–{score_high})."
        ),
    ),
    FeedbackTemplate(
        id="note.next_clarify",
        kind="note",
        condition="next_clarify",
        text="На скрининге уточните в первую очередь: {top_topics}.",
    ),
    FeedbackTemplate(
        id="note.next_interview",
        kind="note",
        condition="next_interview",
        text=(
            "Рекомендую техническое интервью: ключевые требования подтверждены, "
            "оставшиеся вопросы можно проверить на встрече."
        ),
    ),
    FeedbackTemplate(
        id="note.next_review",
        kind="note",
        condition="next_review",
        text="Рекомендую ручную проверку отчёта: автоматических решений система не принимает.",
    ),
    # Fallback: 2 шаблона.
    FeedbackTemplate(
        id="fallback.no_requirements",
        kind="fallback",
        condition="no_requirements",
        text="В вакансии нет требований для оценки.",
    ),
    FeedbackTemplate(
        id="fallback.no_data",
        kind="fallback",
        condition="no_data",
        text="Недостаточно данных резюме для содержательных выводов.",
    ),
    # v3: второй вариант для каждого условия, кроме required_gap и fallback.
    FeedbackTemplate(
        id="strength.critical_full.v2",
        kind="strength",
        condition="critical_full",
        text=(
            "Критичное требование «{label}» полностью закрыто: {evidence}.{tenure_note} "
            "Это ключевое подтверждение для вакансии."
        ),
    ),
    FeedbackTemplate(
        id="strength.skill_full.v2",
        kind="strength",
        condition="skill_full",
        text="Навык «{label}» подтверждён цитатой из резюме: {evidence}.{tenure_note}",
    ),
    FeedbackTemplate(
        id="strength.experience_full.v2",
        kind="strength",
        condition="experience_full",
        text=(
            "Опыт в категории «{label}» подтверждён: {evidence}.{tenure_note} "
            "Его можно учитывать при оценке глубины."
        ),
    ),
    FeedbackTemplate(
        id="strength.qualification_full.v2",
        kind="strength",
        condition="qualification_full",
        text=(
            "Квалификация «{label}» подтверждена: {evidence}. "
            "Документ или запись в резюме можно проверить на интервью."
        ),
    ),
    FeedbackTemplate(
        id="strength.education_full.v2",
        kind="strength",
        condition="education_full",
        text=(
            "Образование по направлению «{label}» подтверждено: {evidence}. "
            "Профиль подготовки соответствует требованию."
        ),
    ),
    FeedbackTemplate(
        id="strength.text_full.v2",
        kind="strength",
        condition="text_full",
        text=(
            "Требование «{label}» подтверждено текстом резюме: {evidence}. "
            "Формулировка требования и цитата согласованы."
        ),
    ),
    FeedbackTemplate(
        id="risk.critical_gap.v2",
        kind="risk",
        condition="critical_gap",
        text="Критичное требование «{label}» {status_phrase}. Это ключевой риск для вакансии.",
    ),
    FeedbackTemplate(
        id="risk.insufficient_tenure.v2",
        kind="risk",
        condition="insufficient_tenure",
        text=(
            "По требованию «{label}» подтверждён стаж {found_years}, "
            "а требуется {required_years}. Стоит уточнить периоды и глубину опыта."
        ),
    ),
    FeedbackTemplate(
        id="risk.date_issue.v2",
        kind="risk",
        condition="date_issue",
        text=(
            "В датах опыта есть пробел или расхождение: {detail}. "
            "Проверьте хронологию до интервью."
        ),
    ),
    FeedbackTemplate(
        id="risk.overclaim.v2",
        kind="risk",
        condition="overclaim",
        text=(
            "Заявленный стаж по навыку «{label}» ({found_years}) превышает "
            "общий стаж по резюме ({total_years}). Это стоит уточнить."
        ),
    ),
    FeedbackTemplate(
        id="risk.wide_interval.v2",
        kind="risk",
        condition="wide_interval",
        text=(
            "Доля требований без сведений в резюме — {not_stated_count} из {total_count}; "
            "вилка оценки широкая из-за неполных данных."
        ),
    ),
    FeedbackTemplate(
        id="risk.missing_data.v2",
        kind="risk",
        condition="missing_data",
        text="В резюме нет раздела «{section}»; связанные требования подтверждены неполно.",
    ),
    FeedbackTemplate(
        id="question.skill_not_stated.v2",
        kind="question",
        condition="skill_not_stated",
        text=(
            "В резюме не указан навык «{label}». "
            "В каких задачах или проектах вы его применяли?"
        ),
    ),
    FeedbackTemplate(
        id="question.experience_not_stated.v2",
        kind="question",
        condition="experience_not_stated",
        text=(
            "В резюме не указан опыт в категории «{label}». "
            "Опишите релевантные проекты, свою роль и результат."
        ),
    ),
    FeedbackTemplate(
        id="question.qualification_not_stated.v2",
        kind="question",
        condition="qualification_not_stated",
        text=(
            "В резюме не указана квалификация «{label}». "
            "Есть ли она у вас и можете ли вы подтвердить её документом?"
        ),
    ),
    FeedbackTemplate(
        id="question.education_not_stated.v2",
        kind="question",
        condition="education_not_stated",
        text=(
            "В резюме не указано образование по направлению «{label}». "
            "Уточните профиль обучения и релевантные курсы."
        ),
    ),
    FeedbackTemplate(
        id="question.text_not_stated.v2",
        kind="question",
        condition="text_not_stated",
        text=(
            "В резюме не указано подтверждение требования «{label}». "
            "Приведите пример задачи, где это проявлялось."
        ),
    ),
    FeedbackTemplate(
        id="question.skill_insufficient_tenure.v2",
        kind="question",
        condition="skill_insufficient_tenure",
        text=(
            "По навыку «{label}» подтверждено {found_years}, требуется {required_years}. "
            "Какие проекты дают недостающий опыт?"
        ),
    ),
    FeedbackTemplate(
        id="question.experience_insufficient_tenure.v2",
        kind="question",
        condition="experience_insufficient_tenure",
        text=(
            "По опыту в категории «{label}» подтверждено {found_years}, "
            "требуется {required_years}. Уточните периоды и сложность задач."
        ),
    ),
    FeedbackTemplate(
        id="question.partial.v2",
        kind="question",
        condition="partial",
        text=(
            "Требование «{label}» подтверждено частично. "
            "Что именно вы делали самостоятельно и какой был результат?"
        ),
    ),
    FeedbackTemplate(
        id="question.absent.v2",
        kind="question",
        condition="absent",
        text=(
            "В резюме есть близкий опыт, но требование «{label}» не подтверждено. "
            "Какие похожие инструменты вы использовали?"
        ),
    ),
    FeedbackTemplate(
        id="question.date_issue.v2",
        kind="question",
        condition="date_issue",
        text=(
            "В датах опыта есть пробел или расхождение: {detail}. "
            "Уточните фактические периоды работы."
        ),
    ),
    FeedbackTemplate(
        id="note.opener_high.v2",
        kind="note",
        condition="opener_high",
        text=(
            "Высокое соответствие: {full_count} из {total_count} требований закрыты "
            "полностью, оценка {score} (вилка {score_low}–{score_high})."
        ),
    ),
    FeedbackTemplate(
        id="note.opener_mid.v2",
        kind="note",
        condition="opener_mid",
        text=(
            "Соответствие частичное: {full_count} из {total_count} требований закрыты "
            "полностью, оценка {score} (вилка {score_low}–{score_high})."
        ),
    ),
    FeedbackTemplate(
        id="note.opener_low.v2",
        kind="note",
        condition="opener_low",
        text=(
            "Соответствие низкое: {full_count} из {total_count} требований закрыты "
            "полностью, оценка {score} (вилка {score_low}–{score_high})."
        ),
    ),
    FeedbackTemplate(
        id="note.next_clarify.v2",
        kind="note",
        condition="next_clarify",
        text=(
            "На скрининге уточните в первую очередь: {top_topics}. "
            "Это поможет закрыть оставшиеся пробелы."
        ),
    ),
    FeedbackTemplate(
        id="note.next_interview.v2",
        kind="note",
        condition="next_interview",
        text=(
            "Рекомендую техническое интервью: основные требования закрыты, "
            "детали и спорные данные можно проверить на встрече."
        ),
    ),
    FeedbackTemplate(
        id="note.next_review.v2",
        kind="note",
        condition="next_review",
        text=(
            "Рекомендую ручную проверку отчёта: финальное решение "
            "остаётся за рекрутёром."
        ),
    ),
)


class FeedbackBuilder:
    """Собирает объяснения из фактов скоринга. Детерминирован и не вызывает LLM."""

    def __init__(self, templates: Iterable[FeedbackTemplate] = FEEDBACK_TEMPLATES) -> None:
        self._templates = tuple(templates)
        grouped: dict[tuple[FeedbackKind, str], list[FeedbackTemplate]] = {}
        for template in self._templates:
            grouped.setdefault((template.kind, template.condition), []).append(template)
        self._by_condition = {
            key: tuple(sorted(value, key=lambda item: item.id)) for key, value in grouped.items()
        }

    def build(
        self,
        *,
        resume: messages_pb2.ParsedResume,
        vacancy: messages_pb2.Vacancy,
        matches: Mapping[str, RequirementScore],
        general: GeneralScore,
        reference_date: date,
        total_years: float | None = None,
    ) -> Feedback:
        if not matches:
            return Feedback(
                strengths=[],
                risks=[],
                questions=[],
                recruiter_note=self._fallback("no_requirements"),
            )

        if total_years is None:
            total_years = total_experience_years(resume.experience.values(), reference_date)

        kinds = _vacancy_kinds(vacancy)
        strengths = self._render_facts(
            self._strength_facts(vacancy, matches),
            limit=MAX_STRENGTHS,
        )
        risks = self._render_facts(
            self._risk_facts(resume, vacancy, matches, kinds, total_years, reference_date),
            limit=MAX_RISKS,
        )
        questions = self._render_facts(
            self._question_facts(resume, vacancy, matches, reference_date),
            limit=MAX_QUESTIONS,
        )
        return Feedback(
            strengths=strengths,
            risks=risks,
            questions=questions,
            recruiter_note=self._build_note(resume, vacancy, matches, general),
        )

    def _strength_facts(
        self,
        vacancy: messages_pb2.Vacancy,
        matches: Mapping[str, RequirementScore],
    ) -> list[FeedbackFact]:
        facts: list[FeedbackFact] = []
        for requirement_id, match in sorted(matches.items()):
            if match.status != common_pb2.CORRESPONDENCE_FULL:
                continue
            evidence = _quote(match)
            if evidence is None:
                continue
            requirement = vacancy.requirements.get(requirement_id)
            kind = _kind_of(requirement, match)
            if match.criticality == common_pb2.FEATURE_CRITICALITY_CRITICAL:
                condition = "critical_full"
            elif kind == "skill":
                condition = "skill_full"
            elif kind == "qualification":
                condition = "qualification_full"
            elif kind == "experience":
                condition = "experience_full"
            elif kind == "education":
                condition = "education_full"
            else:
                condition = "text_full"
            facts.append(
                FeedbackFact(
                    fact_id=f"strength:{requirement_id}",
                    kind="strength",
                    condition=condition,
                    priority=_match_priority(match) + round(match.coverage * 10),
                    slots={
                        "label": _short_label(match.label),
                        "evidence": evidence,
                        "tenure_note": _tenure_note(requirement, match),
                    },
                )
            )
        return facts

    def _risk_facts(
        self,
        resume: messages_pb2.ParsedResume,
        vacancy: messages_pb2.Vacancy,
        matches: Mapping[str, RequirementScore],
        kinds: set[str],
        total_years: float | None,
        reference_date: date,
    ) -> list[FeedbackFact]:
        facts: list[FeedbackFact] = []
        for requirement_id, match in sorted(matches.items()):
            if match.status == common_pb2.CORRESPONDENCE_FULL:
                continue
            requirement = vacancy.requirements.get(requirement_id)
            required = _required_tenure(requirement)
            if match.gap_reason == common_pb2.GAP_REASON_INSUFFICIENT_TENURE:
                if match.years_found is not None and required is not None:
                    facts.append(
                        FeedbackFact(
                            fact_id=f"risk:insufficient_tenure:{requirement_id}",
                            kind="risk",
                            condition="insufficient_tenure",
                            priority=_match_priority(match) + 10,
                            slots={
                                "label": _short_label(match.label),
                                "found_years": _format_years(match.years_found),
                                "required_years": _format_years(required),
                            },
                        )
                    )
                continue
            if match.criticality == common_pb2.FEATURE_CRITICALITY_CRITICAL:
                facts.append(
                    FeedbackFact(
                        fact_id=f"risk:critical_gap:{requirement_id}",
                        kind="risk",
                        condition="critical_gap",
                        priority=100,
                        slots={
                            "label": _short_label(match.label),
                            "status_phrase": _status_phrase(match),
                        },
                    )
                )
            elif match.criticality == common_pb2.FEATURE_CRITICALITY_REQUIRED:
                facts.append(
                    FeedbackFact(
                        fact_id=f"risk:required_gap:{requirement_id}",
                        kind="risk",
                        condition="required_gap",
                        priority=80,
                        slots={
                            "label": _short_label(match.label),
                            "status_note": _status_note(match),
                        },
                    )
                )
        facts.extend(_date_risk_facts(resume, reference_date))
        facts.extend(_overclaim_risk_facts(resume, total_years))
        facts.extend(_wide_interval_risk_facts(matches))
        facts.extend(_missing_data_risk_facts(resume, kinds))
        return facts

    def _question_facts(
        self,
        resume: messages_pb2.ParsedResume,
        vacancy: messages_pb2.Vacancy,
        matches: Mapping[str, RequirementScore],
        reference_date: date,
    ) -> list[FeedbackFact]:
        facts: list[FeedbackFact] = []
        for requirement_id, match in sorted(matches.items()):
            if match.status == common_pb2.CORRESPONDENCE_FULL:
                continue
            requirement = vacancy.requirements.get(requirement_id)
            kind = _kind_of(requirement, match)
            required = _required_tenure(requirement)
            condition = _question_condition(kind, match)
            if condition is None:
                continue
            slots = {"label": _short_label(match.label)}
            if condition.endswith("insufficient_tenure"):
                if match.years_found is None or required is None:
                    continue
                slots["found_years"] = _format_years(match.years_found)
                slots["required_years"] = _format_years(required)
            facts.append(
                FeedbackFact(
                    fact_id=f"question:{requirement_id}",
                    kind="question",
                    condition=condition,
                    priority=_question_priority(match),
                    slots=slots,
                )
            )
        facts.extend(_date_question_facts(resume, reference_date))
        return facts

    def _build_note(
        self,
        resume: messages_pb2.ParsedResume,
        vacancy: messages_pb2.Vacancy,
        matches: Mapping[str, RequirementScore],
        general: GeneralScore,
    ) -> str:
        if (
            not resume.skills
            and not resume.qualifications
            and not resume.experience
            and not resume.education
            and all(
                match.status == common_pb2.CORRESPONDENCE_NOT_STATED for match in matches.values()
            )
        ):
            return self._fallback("no_data")

        full_count = sum(
            1 for match in matches.values() if match.status == common_pb2.CORRESPONDENCE_FULL
        )
        slots = {
            "full_count": str(full_count),
            "total_count": str(len(matches)),
            "score": _percent(general.score),
            "score_low": _percent(general.score_low),
            "score_high": _percent(general.score_high),
        }
        if general.score >= 0.75:
            opener_condition = "opener_high"
        elif general.score >= 0.45:
            opener_condition = "opener_mid"
        else:
            opener_condition = "opener_low"
        opener = self._render(FeedbackFact("note:opener", "note", opener_condition, 10, slots))

        gaps = sorted(
            (match for match in matches.values() if match.status != common_pb2.CORRESPONDENCE_FULL),
            key=lambda match: (-_match_priority(match), match.requirement_id),
        )
        important = [
            match
            for match in gaps
            if match.criticality
            in (common_pb2.FEATURE_CRITICALITY_CRITICAL, common_pb2.FEATURE_CRITICALITY_REQUIRED)
            or match.gap_reason == common_pb2.GAP_REASON_INSUFFICIENT_TENURE
        ]
        if important:
            topics = ", ".join(_topic(match, vacancy) for match in important[:3])
            next_fact = FeedbackFact(
                "note:next",
                "note",
                "next_clarify",
                20,
                {"top_topics": topics},
            )
        elif general.score >= 0.75:
            next_fact = FeedbackFact("note:next", "note", "next_interview", 20, {})
        else:
            next_fact = FeedbackFact("note:next", "note", "next_review", 20, {})

        next_step = self._render(next_fact)
        if opener and next_step:
            note = f"{opener} {next_step}"
            if len(note) <= MAX_NOTE_CHARS:
                return note
        if opener:
            return opener
        return self._fallback("no_data")

    def _render_facts(self, facts: Iterable[FeedbackFact], limit: int) -> list[str]:
        ordered = sorted(facts, key=lambda fact: (-fact.priority, fact.fact_id))
        result: list[str] = []
        seen: set[str] = set()
        for fact in ordered:
            text = self._render(fact)
            if text is None or text in seen:
                continue
            seen.add(text)
            result.append(text)
            if len(result) >= limit:
                break
        return result

    def _render(self, fact: FeedbackFact) -> str | None:
        candidates = self._by_condition.get((fact.kind, fact.condition))
        if not candidates:
            return None
        start = _stable_index(fact.fact_id, len(candidates))
        for offset in range(len(candidates)):
            template = candidates[(start + offset) % len(candidates)]
            try:
                text = template.text.format_map(_StrictSlots(fact.slots))
            except KeyError, ValueError:
                continue
            text = text.strip()
            if not text or "{" in text or "}" in text:
                continue
            if len(text) > MAX_TEXT_CHARS:
                continue
            return text
        return None

    def _fallback(self, condition: str) -> str:
        candidates = self._by_condition.get(("fallback", condition))
        if not candidates:
            return ""
        return candidates[0].text


class _StrictSlots(dict[str, str]):
    def __missing__(self, key: str) -> str:
        raise KeyError(key)


@dataclass(frozen=True, slots=True)
class _DateIssue:
    code: str
    detail: str
    priority: int


def _stable_index(value: str, size: int) -> int:
    """Стабильный индекс варианта: один и тот же факт даёт один и тот же текст."""
    if size <= 1:
        return 0
    digest = hashlib.sha256(value.encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big") % size


def _kind_of(
    requirement: features_pb2.FeatureRequirement | None,
    match: RequirementScore | None,
) -> str:
    if requirement is not None:
        kind = requirement.WhichOneof("feature")
        if kind == "text_requirement":
            return "text"
        if kind is not None:
            return kind
    if match is not None and match.resume_kind:
        return match.resume_kind
    return "text"


def _required_tenure(requirement: features_pb2.FeatureRequirement | None) -> float | None:
    if requirement is None:
        return None
    kind = requirement.WhichOneof("feature")
    if kind == "skill":
        value = float(requirement.skill.tenure)
    elif kind == "experience":
        value = float(requirement.experience.tenure)
    else:
        return None
    return value if value > 0 else None


def _vacancy_kinds(vacancy: messages_pb2.Vacancy) -> set[str]:
    return {_kind_of(requirement, None) for requirement in vacancy.requirements.values()}


def _match_priority(match: RequirementScore) -> int:
    return {
        common_pb2.FEATURE_CRITICALITY_CRITICAL: 100,
        common_pb2.FEATURE_CRITICALITY_REQUIRED: 80,
        common_pb2.FEATURE_CRITICALITY_NICE: 50,
        common_pb2.FEATURE_CRITICALITY_OPTIONAL: 30,
    }.get(match.criticality, 40)


def _question_priority(match: RequirementScore) -> int:
    bonus = {
        common_pb2.GAP_REASON_INSUFFICIENT_TENURE: 10,
        common_pb2.GAP_REASON_ABSENT: 5,
    }.get(match.gap_reason, 0)
    return _match_priority(match) + bonus


def _question_condition(kind: str, match: RequirementScore) -> str | None:
    if match.gap_reason == common_pb2.GAP_REASON_INSUFFICIENT_TENURE:
        if kind == "skill":
            return "skill_insufficient_tenure"
        if kind == "experience":
            return "experience_insufficient_tenure"
        return "partial"
    if match.status == common_pb2.CORRESPONDENCE_NOT_STATED:
        return {
            "skill": "skill_not_stated",
            "qualification": "qualification_not_stated",
            "experience": "experience_not_stated",
            "education": "education_not_stated",
            "text": "text_not_stated",
        }.get(kind, "partial")
    if match.status == common_pb2.CORRESPONDENCE_ABSENT:
        return "absent"
    if match.status == common_pb2.CORRESPONDENCE_PARTIAL:
        return "partial"
    return None


def _status_note(match: RequirementScore) -> str:
    if match.status == common_pb2.CORRESPONDENCE_NOT_STATED:
        return "в резюме нет сведений"
    if match.status == common_pb2.CORRESPONDENCE_ABSENT:
        return "подтверждения в резюме нет"
    if match.status == common_pb2.CORRESPONDENCE_PARTIAL:
        return "подтверждение частичное"
    return "требование закрыто не полностью"


def _status_phrase(match: RequirementScore) -> str:
    if match.status == common_pb2.CORRESPONDENCE_PARTIAL:
        return "подтверждено частично"
    if match.status == common_pb2.CORRESPONDENCE_NOT_STATED:
        return "в резюме не подтверждено"
    return "не подтверждено"


def _tenure_note(
    requirement: features_pb2.FeatureRequirement | None,
    match: RequirementScore,
) -> str:
    required = _required_tenure(requirement)
    if required is None or match.years_found is None:
        return ""
    return (
        f" Найденный стаж — {_format_years(match.years_found)}, "
        f"требуется {_format_years(required)}."
    )


def _date_issues(
    resume: messages_pb2.ParsedResume,
    reference_date: date,
) -> list[tuple[str, _DateIssue]]:
    issues: list[tuple[str, _DateIssue]] = []
    for key, experience in sorted(resume.experience.items()):
        begin = start_date(experience.begin)
        end = end_date(experience.end, reference_date)
        if begin is None:
            issues.append((key, _DateIssue("missing_begin", "не указано начало периода", 55)))
            continue
        if begin > reference_date:
            issues.append((key, _DateIssue("future_begin", "начало периода позже даты оценки", 90)))
        if end is not None and end < begin:
            issues.append(
                (key, _DateIssue("end_before_begin", "окончание периода раньше начала", 90))
            )
    return issues


def _date_risk_facts(
    resume: messages_pb2.ParsedResume,
    reference_date: date,
) -> list[FeedbackFact]:
    return [
        FeedbackFact(
            fact_id=f"risk:date:{key}:{issue.code}",
            kind="risk",
            condition="date_issue",
            priority=issue.priority,
            slots={"detail": issue.detail},
        )
        for key, issue in _date_issues(resume, reference_date)
    ]


def _date_question_facts(
    resume: messages_pb2.ParsedResume,
    reference_date: date,
) -> list[FeedbackFact]:
    facts: list[FeedbackFact] = []
    for key, issue in _date_issues(resume, reference_date):
        priority = 50 if issue.code == "missing_begin" else 95
        facts.append(
            FeedbackFact(
                fact_id=f"question:date:{key}:{issue.code}",
                kind="question",
                condition="date_issue",
                priority=priority,
                slots={"detail": issue.detail},
            )
        )
    return facts


def _overclaim_risk_facts(
    resume: messages_pb2.ParsedResume,
    total_years: float | None,
) -> list[FeedbackFact]:
    if total_years is None:
        return []
    facts: list[FeedbackFact] = []
    for key, skill in sorted(resume.skills.items()):
        if not skill.HasField("tenure"):
            continue
        if float(skill.tenure) <= total_years + 1.0:
            continue
        label = skill.name.strip() or key
        facts.append(
            FeedbackFact(
                fact_id=f"risk:overclaim:{key}",
                kind="risk",
                condition="overclaim",
                priority=60,
                slots={
                    "label": _short_label(label),
                    "found_years": _format_years(float(skill.tenure)),
                    "total_years": _format_years(total_years),
                },
            )
        )
    return facts


def _wide_interval_risk_facts(
    matches: Mapping[str, RequirementScore],
) -> list[FeedbackFact]:
    total = len(matches)
    if total < 2:
        return []
    not_stated = sum(
        1 for match in matches.values() if match.status == common_pb2.CORRESPONDENCE_NOT_STATED
    )
    if not_stated / total < 0.4:
        return []
    return [
        FeedbackFact(
            fact_id="risk:wide_interval",
            kind="risk",
            condition="wide_interval",
            priority=30,
            slots={"not_stated_count": str(not_stated), "total_count": str(total)},
        )
    ]


def _missing_data_risk_facts(
    resume: messages_pb2.ParsedResume,
    kinds: set[str],
) -> list[FeedbackFact]:
    sections: list[tuple[str, str]] = []
    if "skill" in kinds and not resume.skills:
        sections.append(("skills", "навыки"))
    if "qualification" in kinds and not resume.qualifications:
        sections.append(("qualifications", "квалификации"))
    if "experience" in kinds and not resume.experience:
        sections.append(("experience", "опыт работы"))
    if "education" in kinds and not resume.education:
        sections.append(("education", "образование"))
    return [
        FeedbackFact(
            fact_id=f"risk:missing_data:{code}",
            kind="risk",
            condition="missing_data",
            priority=40,
            slots={"section": title},
        )
        for code, title in sections
    ]


def _topic(match: RequirementScore, vacancy: messages_pb2.Vacancy) -> str:
    requirement = vacancy.requirements.get(match.requirement_id)
    kind = _kind_of(requirement, match)
    label = _short_label(match.label)
    if kind == "skill":
        return f"навык «{label}»"
    if kind == "qualification":
        return f"квалификацию «{label}»"
    if kind == "experience":
        return f"опыт в категории «{label}»"
    if kind == "education":
        return f"образование по направлению «{label}»"
    return f"требование «{label}»"


def _quote(match: RequirementScore) -> str | None:
    if match.evidence is None:
        return None
    text = _WHITESPACE.sub(" ", match.evidence.text).strip()
    if not text:
        return None
    if len(text) > MAX_QUOTE_CHARS:
        # Не добавляем «…»: фрагмент должен оставаться подстрокой проверенной
        # цитаты, иначе инвариант «цитата — подстрока» нарушается.
        cut = text[:MAX_QUOTE_CHARS]
        boundary = cut.rfind(" ")
        if boundary >= MAX_QUOTE_CHARS // 2:
            cut = cut[:boundary]
        text = cut.rstrip(" ,;:.-")
    return text or None


def _short_label(value: str) -> str:
    text = _WHITESPACE.sub(" ", value).strip()
    if len(text) <= MAX_LABEL_CHARS:
        return text
    return text[: MAX_LABEL_CHARS - 3].rstrip() + "..."


def _percent(value: float) -> str:
    return f"{min(1.0, max(0.0, value)):.0%}"


def _format_years(value: float) -> str:
    if value < 1.0:
        return "менее года"
    rounded = round(value, 1)
    if abs(rounded - round(rounded)) < 0.05:
        number = round(rounded)
        return f"{number} {_years_word(number)}"
    text = f"{rounded:.1f}".replace(".", ",")
    return f"{text} года"


def _years_word(number: int) -> str:
    if number % 100 in (11, 12, 13, 14):
        return "лет"
    if number % 10 == 1:
        return "год"
    if number % 10 in (2, 3, 4):
        return "года"
    return "лет"
