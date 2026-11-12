"""Детерминированное извлечение признаков из обезличенного текста резюме.

Заглушка не вызывает LLM: признаки ищутся по фиксированному словарю навыков и
простым шаблонам дат. Каждая цитата — точный срез исходного текста, поэтому
инвариант «цитата является подстрокой» выполняется по построению.
"""

from __future__ import annotations

import datetime as dt
import re
from dataclasses import dataclass, field

from .dictionary import SKILL_PATTERN, canonical_skill

_YEAR = r"(?:19|20)\d{2}"
_RANGE_RE = re.compile(rf"({_YEAR})\s*[-–—]\s*({_YEAR}|н\.?\s?в\.?)", re.IGNORECASE)
_TENURE_RE = re.compile(r"(\d{1,2})\s*(?:\+)?\s*(?:год|года|лет)", re.IGNORECASE)
_SINCE_RE = re.compile(rf"\bс\s+({_YEAR})", re.IGNORECASE)
_YEAR_RE = re.compile(_YEAR)

_EDUCATION_KEYWORDS: tuple[tuple[int, tuple[str, ...]], ...] = (
    # Порядок задаёт приоритет: первое совпадение выигрывает.
    (5, ("аспирант", "докторант", "кандидат наук", "phd")),  # EDUCATION_LEVEL_DOCTOR
    (4, ("магистр", "магистратура", "master")),  # EDUCATION_LEVEL_MASTER
    (3, ("бакалавр", "бакалавриат", "bachelor", "специалист", "специалитет")),
    (2, ("колледж", "техникум", "среднее специальное")),
    (1, ("школа", "среднее образование", "аттестат")),
    (6, ("курсы", "сертификат", "course", "training")),  # EDUCATION_LEVEL_COURSES
)


@dataclass(frozen=True, slots=True)
class Evidence:
    """Цитата и её абсолютные смещения в исходном тексте."""

    start: int
    end: int
    text: str


@dataclass(frozen=True, slots=True)
class ParsedSkill:
    key: str
    name: str
    normalized_name: str
    evidence: Evidence
    tenure: int | None = None


@dataclass(frozen=True, slots=True)
class ParsedEducation:
    key: str
    level: int
    evidence: Evidence
    graduation_year: int | None = None


@dataclass(frozen=True, slots=True)
class ParsedExperience:
    key: str
    evidence: Evidence
    begin_year: int | None = None
    end_year: int | None = None


@dataclass(frozen=True, slots=True)
class ParsedResume:
    skills: tuple[ParsedSkill, ...] = ()
    education: tuple[ParsedEducation, ...] = ()
    experience: tuple[ParsedExperience, ...] = ()
    warnings: tuple[str, ...] = field(default_factory=tuple)


def reference_year(value: object | None) -> int:
    """Год из `google.type.Date`, а если он не задан — текущий год."""
    year = int(getattr(value, "year", 0) or 0)
    return year if year > 0 else dt.date.today().year


def _line_spans(text: str) -> list[tuple[int, str]]:
    spans: list[tuple[int, str]] = []
    offset = 0
    for raw in text.splitlines():
        spans.append((offset, raw))
        offset += len(raw) + 1
    return spans


def _line_tenure(line: str, year: int) -> int | None:
    match = _TENURE_RE.search(line)
    if match:
        return int(match.group(1))
    since = _SINCE_RE.search(line)
    if since:
        return max(0, year - int(since.group(1)))
    return None


def extract(text: str, year: int) -> ParsedResume:
    """Извлекает навыки, образование и опыт. Детерминированно для одного входа."""
    skills: list[ParsedSkill] = []
    education: list[ParsedEducation] = []
    experience: list[ParsedExperience] = []
    seen_skills: set[str] = set()

    for start, line in _line_spans(text):
        if not line.strip():
            continue

        matches = list(SKILL_PATTERN.finditer(line))
        # Стаж по строке относится к навыку только если он там один: иначе
        # непонятно, к какому навыку относится число лет.
        line_tenure = _line_tenure(line, year) if len(matches) == 1 else None
        for match in matches:
            canonical = canonical_skill(match.group(1))
            if canonical is None or canonical in seen_skills:
                continue
            seen_skills.add(canonical)
            evidence = Evidence(
                start=start + match.start(1),
                end=start + match.end(1),
                text=text[start + match.start(1) : start + match.end(1)],
            )
            skills.append(
                ParsedSkill(
                    key=f"skill:{len(skills)}",
                    name=evidence.text,
                    normalized_name=canonical,
                    evidence=evidence,
                    tenure=line_tenure,
                )
            )

        level = _education_level(line)
        if level is not None:
            years = [int(y) for y in _YEAR_RE.findall(line)]
            evidence = Evidence(start=start, end=start + len(line), text=line)
            education.append(
                ParsedEducation(
                    key=f"education:{len(education)}",
                    level=level,
                    evidence=evidence,
                    graduation_year=max(years) if years else None,
                )
            )

        for range_match in _RANGE_RE.finditer(line):
            begin = int(range_match.group(1))
            raw_end = range_match.group(2)
            end = int(raw_end) if raw_end.isdigit() else None
            evidence = Evidence(start=start, end=start + len(line), text=line)
            experience.append(
                ParsedExperience(
                    key=f"experience:{len(experience)}",
                    evidence=evidence,
                    begin_year=begin,
                    end_year=end,
                )
            )

    warnings: list[str] = []
    if not skills and not education and not experience:
        warnings.append(
            "Признаки не найдены: текст не содержит известных навыков, опыта или образования"
        )

    return ParsedResume(
        skills=tuple(skills),
        education=tuple(education),
        experience=tuple(experience),
        warnings=tuple(warnings),
    )


def _education_level(line: str) -> int | None:
    lower = line.lower()
    for level, keywords in _EDUCATION_KEYWORDS:
        if any(keyword in lower for keyword in keywords):
            return level
    return None


def total_experience_years(parsed: ParsedResume, year: int) -> float | None:
    """Суммарный стаж по диапазонам дат. `None`, если опыта не найдено."""
    if not parsed.experience:
        return None
    total = 0
    for item in parsed.experience:
        if item.begin_year is None:
            continue
        end = item.end_year if item.end_year is not None else year
        total += max(0, end - item.begin_year)
    return float(total)
