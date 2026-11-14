"""Внутренняя модель сущностей, независимая от protobuf.

Смещения — в Unicode code points, как в контракте. Python-строки индексируются
в code points, поэтому пересчёт не нужен; Java переводит их в UTF-16.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum


class EntityType(StrEnum):
    NAME = "NAME"
    EMAIL = "EMAIL"
    PHONE = "PHONE"
    URL = "URL"
    TELEGRAM = "TELEGRAM"
    LINKEDIN = "LINKEDIN"
    GITHUB = "GITHUB"
    PASSPORT = "PASSPORT"
    SNILS = "SNILS"
    INN = "INN"
    BIRTH_DATE = "BIRTH_DATE"
    AGE = "AGE"
    GENDER = "GENDER"
    LOCATION = "LOCATION"
    UNIVERSITY = "UNIVERSITY"
    GRADUATION_YEAR = "GRADUATION_YEAR"
    MARITAL_STATUS = "MARITAL_STATUS"
    PHOTO = "PHOTO"


class MaskLayer(StrEnum):
    SPACY = "SPACY"
    LLM = "LLM"
    REGEX = "REGEX"


@dataclass(frozen=True, slots=True)
class EntitySpan:
    """Найденная сущность. `end` исключительно."""

    type: EntityType
    start: int
    end: int
    confidence: float
    layer: MaskLayer

    def is_valid(self, text_length: int) -> bool:
        return 0 <= self.start < self.end <= text_length


class LayerUnavailableError(RuntimeError):
    """Слой зарезервирован контрактом, но ещё не реализован."""
