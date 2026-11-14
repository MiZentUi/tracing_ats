"""Политики модели: веса, время, убедительность и домен.

Все значения, которые в RFC ещё открыты, вынесены в заменяемые политики.
Это позволяет менять их без правок сопоставления и арифметики.
"""

from __future__ import annotations

import math
import re
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from datetime import date
from typing import Protocol

from scoring_service.v1.models import messages_pb2
from scoring_service.v1.models.features import common_pb2


class WeightPolicy(Protocol):
    """Вес требования `Wv^i`.

    Политика получает распределение требований по критичности, чтобы
    учитывать размер групп. `counts` может быть `None`, если вызывающий
    код хочет получить базовый вес без нормализации.
    """

    def weight(
        self,
        criticality: int,
        counts: Mapping[int, int] | None = None,
    ) -> float: ...


@dataclass(frozen=True, slots=True)
class CriticalityWeightPolicy:
    """Маппинг criticality → Wv с учётом размера групп.

    Базовые веса задают относительную важность группы. Если
    `normalize_by_counts=True`, вес каждого требования делится на количество
    требований этой критичности: группа с десятью MUST не получает в десять
    раз больший суммарный вес, чем группа с одним MUST.

    Свой подбор весов можно передать через `weight_fn` или унаследоваться от
    политики и переопределить `weight`. Callback получает criticality и
    распределение `counts`.
    """

    optional: float = 0.25
    nice: float = 0.5
    required: float = 0.75
    critical: float = 1.0
    unspecified: float = 0.5
    normalize_by_counts: bool = True
    weight_fn: Callable[[int, Mapping[int, int]], float] | None = None

    def weight(
        self,
        criticality: int,
        counts: Mapping[int, int] | None = None,
    ) -> float:
        if self.weight_fn is not None:
            return self.weight_fn(criticality, counts or {})
        base = {
            common_pb2.FEATURE_CRITICALITY_OPTIONAL: self.optional,
            common_pb2.FEATURE_CRITICALITY_NICE: self.nice,
            common_pb2.FEATURE_CRITICALITY_REQUIRED: self.required,
            common_pb2.FEATURE_CRITICALITY_CRITICAL: self.critical,
        }.get(criticality, self.unspecified)

        if not self.normalize_by_counts or counts is None:
            return base
        count = counts.get(criticality, 0)
        if count <= 0:
            return base
        return base / count

    @staticmethod
    def my_weight(c: int, map: Mapping[int, int]) -> float:
        if c in (
            common_pb2.FEATURE_CRITICALITY_CRITICAL,
            common_pb2.FEATURE_CRITICALITY_REQUIRED,
        ):
            return 0.7 / map[c]
        elif c == common_pb2.FEATURE_CRITICALITY_NICE:
            return 0.2 / map[c]
        else:
            return 0.1 / (
                map[common_pb2.FEATURE_CRITICALITY_OPTIONAL]
                + map[common_pb2.FEATURE_CRITICALITY_UNSPECIFIED]
            )
        pass


@dataclass(frozen=True, slots=True)
class RelationPolicy:
    """Маппинг класса связи словаря в `S^{ij}`.

    Значения — начальная политика v1; окончательные числа фиксируются
    в `rfc-scoring-model.md`. Словарь не возвращает числа и не знает
    про формулу.
    """

    same_group: float = 0.7
    related: float = 0.7
    parent: float = 0.6
    child: float = 0.5

    def score(self, kind: str | None) -> float:
        return {
            "SAME_GROUP": self.same_group,
            "RELATED": self.related,
            "PARENT": self.parent,
            "CHILD": self.child,
        }.get(kind or "", 0.0)


class TemporalPolicyProtocol(Protocol):
    """Контракт `τ_i`."""

    def tau(
        self,
        years: float | None,
        required: float,
        recency: float | None,
    ) -> float: ...

    def tau_bounds(
        self,
        years: float | None,
        required: float,
        recency: float | None,
    ) -> tuple[float, float, float]: ...


@dataclass(frozen=True, slots=True)
class TemporalConfig:
    a: float = 1.0
    b: float = 1.0
    # Поля recency пока не используются: в v1 recency всегда равен 1.
    # Оставлены для будущей версии, когда свежесть снова войдёт в модель.
    half_life_years: float = 2.0
    max_recency_years: float = 10.0


class TemporalPolicy:
    """Темпоральный коэффициент из `rfc-scoring-model.md`.

    Неизвестный стаж расширяет вилку вниз (0 лет) и вверх (требуемый стаж).
    Давность пока не учитывается: recency всегда равен 1.
    """

    def __init__(self, config: TemporalConfig | None = None) -> None:
        self._config = config or TemporalConfig()

    def tau(
        self,
        years: float | None,
        required: float,
        recency: float | None,
    ) -> float:
        point, _, _ = self.tau_bounds(years, required, recency)
        return point

    def tau_bounds(
        self,
        years: float | None,
        required: float,
        recency: float | None,
    ) -> tuple[float, float, float]:
        tenure_point, tenure_low, tenure_high = self._tenure_bounds(years, required)
        recency_point, recency_low, recency_high = self._recency_bounds(recency)
        return (
            _clamp01(tenure_point * recency_point),
            _clamp01(tenure_low * recency_low),
            _clamp01(tenure_high * recency_high),
        )

    def _tenure_bounds(
        self,
        years: float | None,
        required: float,
    ) -> tuple[float, float, float]:
        if required <= 0:
            return 1.0, 1.0, 1.0
        if years is None:
            return (
                self._tenure_factor(required, required),
                self._tenure_factor(0.0, required),
                self._tenure_factor(required, required),
            )
        value = self._tenure_factor(years, required)
        return value, value, value

    def _tenure_factor(self, years: float, required: float) -> float:
        ratio = max(0.0, years) / required - 1.0
        return 0.5 * (1.0 + math.tanh(self._config.a * ratio + self._config.b))

    def _recency_bounds(
        self,
        recency: float | None,
    ) -> tuple[float, float, float]:
        # v1: свежесть отключена, поэтому точка и границы всегда нейтральны.
        return 1.0, 1.0, 1.0


@dataclass(frozen=True, slots=True)
class StatusPolicy:
    """Пороги статусов требований.

    `coverage` участвует в выборе `FULL`: наличие точного названия навыка
    само по себе не означает, что требование закрыто. Порог навыка ниже
    остальных, потому что навык может быть подтверждён только перечислением.
    """

    skill_full_coverage: float = 0.70
    experience_full_coverage: float = 0.85
    education_full_coverage: float = 0.85
    education_partial_coverage: float = 0.50
    text_full_coverage: float = 0.75
    text_partial_coverage: float = 0.35
    absent_relevance: float = 0.50

    def skill(
        self,
        relevance: float,
        coverage: float,
        years: float | None,
        required: float,
    ) -> tuple[int, int | None]:
        if relevance < self.absent_relevance:
            return (
                common_pb2.CORRESPONDENCE_ABSENT,
                common_pb2.GAP_REASON_ABSENT,
            )
        if required > 0 and years is None:
            return common_pb2.CORRESPONDENCE_PARTIAL, None
        if required > 0 and years is not None and years + 0.01 < required:
            return (
                common_pb2.CORRESPONDENCE_PARTIAL,
                common_pb2.GAP_REASON_INSUFFICIENT_TENURE,
            )
        if coverage + 1e-9 < self.skill_full_coverage:
            return (
                common_pb2.CORRESPONDENCE_PARTIAL,
                common_pb2.GAP_REASON_PARTIAL,
            )
        return common_pb2.CORRESPONDENCE_FULL, None

    def experience(
        self,
        relevance: float,
        coverage: float,
        years: float | None,
        required: float,
    ) -> tuple[int, int | None]:
        if relevance < self.absent_relevance:
            return (
                common_pb2.CORRESPONDENCE_ABSENT,
                common_pb2.GAP_REASON_ABSENT,
            )
        if required > 0 and years is None:
            return common_pb2.CORRESPONDENCE_PARTIAL, None
        if required > 0 and years is not None and years + 0.01 < required:
            return (
                common_pb2.CORRESPONDENCE_PARTIAL,
                common_pb2.GAP_REASON_INSUFFICIENT_TENURE,
            )
        if coverage + 1e-9 < self.experience_full_coverage:
            return (
                common_pb2.CORRESPONDENCE_PARTIAL,
                common_pb2.GAP_REASON_PARTIAL,
            )
        return common_pb2.CORRESPONDENCE_FULL, None

    def education(self, coverage: float) -> tuple[int, int | None]:
        if coverage >= self.education_full_coverage:
            return common_pb2.CORRESPONDENCE_FULL, None
        if coverage >= self.education_partial_coverage:
            return (
                common_pb2.CORRESPONDENCE_PARTIAL,
                common_pb2.GAP_REASON_PARTIAL,
            )
        return common_pb2.CORRESPONDENCE_ABSENT, common_pb2.GAP_REASON_ABSENT

    def text(self, coverage: float) -> tuple[int, int | None]:
        if coverage >= self.text_full_coverage:
            return common_pb2.CORRESPONDENCE_FULL, None
        if coverage >= self.text_partial_coverage:
            return (
                common_pb2.CORRESPONDENCE_PARTIAL,
                common_pb2.GAP_REASON_PARTIAL,
            )
        return common_pb2.CORRESPONDENCE_ABSENT, common_pb2.GAP_REASON_ABSENT


class ClaimConfidence(Protocol):
    """Убедительность заявления `t_i`."""

    def confidence(self, evidence_text: str) -> float: ...


class HeuristicClaimConfidence:
    """Секционные правила `t_i` из RFC: список / обязанность / результат."""

    _RESULT = re.compile(
        r"\d|%|\brps\b|\bms\b|\bтыс\b|\bмлн\b|\bраз\b|увелич|снизил|сократ|"
        r"ускор|оптимиз|повыс|boost|reduce|improve|optimi[sz]",
        re.IGNORECASE,
    )
    _DUTY = re.compile(
        r"разраб|реализ|настро|внедр|поддерж|писал|созда|спроект|"
        r"develop|implement|design|build|built|led|maintain",
        re.IGNORECASE,
    )

    def confidence(self, evidence_text: str) -> float:
        text = evidence_text.strip()
        if not text:
            return 0.0
        if self._RESULT.search(text):
            return 1.0
        if self._DUTY.search(text):
            return 0.75
        return 0.5


class DomainMatcher(Protocol):
    """Соответствие домена `F`."""

    def score(
        self,
        resume: messages_pb2.ParsedResume,
        vacancy: messages_pb2.Vacancy,
    ) -> float: ...


class NoDomainMatcher:
    """Fallback `F = 0`: формула домена ещё не зафиксирована в RFC."""

    def score(
        self,
        resume: messages_pb2.ParsedResume,
        vacancy: messages_pb2.Vacancy,
    ) -> float:
        return 0.0


class Clock(Protocol):
    def today(self) -> date: ...


class SystemClock:
    def today(self) -> date:
        return date.today()


def _clamp01(value: float) -> float:
    return min(1.0, max(0.0, value))
