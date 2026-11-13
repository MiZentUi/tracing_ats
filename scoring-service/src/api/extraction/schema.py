"""Строгая схема ответа LLM для извлечения признаков из резюме.

Модель возвращает только текст, цитаты и категории. Никаких баллов, процентов
и производных оценок здесь нет: числа считает детерминированный код.

Лимиты цитат — часть схемы, а не только просьба в промпте. Это защищает от
раздувания ответа, когда модель возвращает целый абзац для каждого навыка.
"""

from __future__ import annotations

from enum import StrEnum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

SCHEMA_VERSION = 3

SKILL_QUOTE_MAX_CHARS = 160
ITEM_QUOTE_MAX_CHARS = 240
METRIC_QUOTE_MAX_CHARS = 160
MAX_METRICS = 8


class EducationLevel(StrEnum):
    UNKNOWN = "unknown"
    SECONDARY = "secondary"
    SECONDARY_SPECIALIZED = "secondary_specialized"
    BACHELOR = "bachelor"
    MASTER = "master"
    DOCTOR = "doctor"
    COURSES = "courses"


class _StrictModel(BaseModel):
    """Лишние поля отбрасываются, неизвестные значения enum — ошибка схемы."""

    model_config = ConfigDict(extra="ignore")


class ExtractedSkill(_StrictModel):
    name: str = Field(
        min_length=1,
        max_length=128,
        description="Название навыка как в резюме.",
    )
    # Извлечённый факт, не оценка: диапазон ограничен, баллы считает код.
    years: int | None = Field(
        default=None,
        ge=0,
        le=50,
        description="Стаж навыка в годах, если он явно указан.",
    )
    quote: str = Field(
        min_length=1,
        max_length=SKILL_QUOTE_MAX_CHARS,
        description=(
            "Минимальная дословная цитата только для этого навыка. "
            "Если навыки перечислены в абзаце, верни только элемент списка, "
            "а не весь абзац."
        ),
    )


class ExtractedQualification(_StrictModel):
    """Бинарная квалификация: группа допуска, категория прав, аттестат.

    Список квалификаций открытый. Модель возвращает `label` из текста, а не
    значение из закрытого перечня; канонический ключ вычисляет словарь при
    сопоставлении.
    """

    label: str = Field(
        min_length=1,
        max_length=128,
        description="Название квалификации дословно из цитаты.",
    )
    quote: str = Field(
        min_length=1,
        max_length=SKILL_QUOTE_MAX_CHARS,
        description=(
            "Минимальная дословная цитата, где указана квалификация. "
            "label должен быть её фрагментом."
        ),
    )


class ExtractedEducation(_StrictModel):
    institution: str | None = Field(
        default=None,
        max_length=256,
        description="Учебное заведение; PII уже замаскирован Java.",
    )
    field: str | None = Field(
        default=None,
        max_length=256,
        description="Направление или специальность.",
    )
    level: EducationLevel = EducationLevel.UNKNOWN
    graduation_year: int | None = Field(default=None, ge=1950, le=2100)
    quote: str = Field(
        min_length=1,
        max_length=ITEM_QUOTE_MAX_CHARS,
        description="Одна строка или одно предложение об образовании.",
    )


class ExtractedExperience(_StrictModel):
    company: str | None = Field(default=None, max_length=256)
    position: str | None = Field(default=None, max_length=256)
    category: str | None = Field(default=None, max_length=256)
    begin_year: int | None = Field(default=None, ge=1950, le=2100)
    end_year: int | None = Field(default=None, ge=1950, le=2100)
    quote: str = Field(
        min_length=1,
        max_length=ITEM_QUOTE_MAX_CHARS,
        description="Одна строка или одно предложение об опыте работы.",
    )
    metrics: list[str] = Field(
        default_factory=list,
        max_length=MAX_METRICS,
        description=(
            "Короткие дословные фрагменты с числами, результатами или "
            "метриками. Не более одного-двух предложений каждый."
        ),
    )

    @field_validator("metrics")
    @classmethod
    def _validate_metrics(cls, values: list[str]) -> list[str]:
        for value in values:
            if len(value) > METRIC_QUOTE_MAX_CHARS:
                raise ValueError(f"metric quote is longer than {METRIC_QUOTE_MAX_CHARS} characters")
        return values


class ParsedResume(_StrictModel):
    """Результат извлечения. Версия схемы фиксируется в каждом ответе."""

    schema_version: Literal[3] = SCHEMA_VERSION
    skills: list[ExtractedSkill] = Field(default_factory=list)
    qualifications: list[ExtractedQualification] = Field(default_factory=list)
    education: list[ExtractedEducation] = Field(default_factory=list)
    experience: list[ExtractedExperience] = Field(default_factory=list)
