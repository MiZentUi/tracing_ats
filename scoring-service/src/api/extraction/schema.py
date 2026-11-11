"""Строгая схема ответа LLM для извлечения признаков из резюме.

Модель возвращает только текст, цитаты и категории. Никаких баллов, процентов
и производных оценок здесь нет: числа считает детерминированный код.
"""

from __future__ import annotations

from enum import StrEnum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

SCHEMA_VERSION = 1


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
    name: str = Field(min_length=1, max_length=128)
    # Извлечённый факт, не оценка: диапазон ограничен, баллы считает код.
    years: int | None = Field(default=None, ge=0, le=50)
    quote: str = Field(min_length=1, max_length=600)


class ExtractedEducation(_StrictModel):
    institution: str | None = Field(default=None, max_length=256)
    field: str | None = Field(default=None, max_length=256)
    level: EducationLevel = EducationLevel.UNKNOWN
    graduation_year: int | None = Field(default=None, ge=1950, le=2100)
    quote: str = Field(min_length=1, max_length=600)


class ExtractedExperience(_StrictModel):
    company: str | None = Field(default=None, max_length=256)
    position: str | None = Field(default=None, max_length=256)
    category: str | None = Field(default=None, max_length=256)
    begin_year: int | None = Field(default=None, ge=1950, le=2100)
    end_year: int | None = Field(default=None, ge=1950, le=2100)
    quote: str = Field(min_length=1, max_length=600)
    metrics: list[str] = Field(default_factory=list)


class ParsedResume(_StrictModel):
    """Результат извлечения. Версия схемы фиксируется в каждом ответе."""

    schema_version: Literal[1] = SCHEMA_VERSION
    skills: list[ExtractedSkill] = Field(default_factory=list)
    education: list[ExtractedEducation] = Field(default_factory=list)
    experience: list[ExtractedExperience] = Field(default_factory=list)
