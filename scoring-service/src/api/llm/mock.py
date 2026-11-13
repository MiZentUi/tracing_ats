"""Детерминированная LLM-заглушка для локального запуска и CI.

Возвращает JSON по схеме `ParsedResume`, вытаскивая известные навыки, строки
образования и опыта из текста резюме. Это не модель и не оценка качества:
заглушка нужна, чтобы пайплайн и тесты работали без сети и ключей.
"""

from __future__ import annotations

import re

from ..extraction.schema import (
    ITEM_QUOTE_MAX_CHARS,
    SKILL_QUOTE_MAX_CHARS,
    EducationLevel,
    ExtractedEducation,
    ExtractedExperience,
    ExtractedSkill,
    ParsedResume,
)

_RESUME_OPEN = "<resume_text>"
_RESUME_CLOSE = "</resume_text>"
_YEARS = re.compile(r"(\d+)\s*(?:год|года|лет|year|years)", re.IGNORECASE)
_YEAR = re.compile(r"\b(19|20)\d{2}\b")

SKILLS = (
    "JavaScript",
    "TypeScript",
    "PostgreSQL",
    "Kubernetes",
    "Spring Boot",
    "React Native",
    "Node.js",
    "Objective-C",
    "C++",
    "C#",
    ".NET",
    "Python",
    "Kotlin",
    "Java",
    "Docker",
    "Kafka",
    "Redis",
    "MongoDB",
    "SQL",
    "React",
    "Vue",
    "Go",
    "Git",
    "Linux",
    "JS",
    "Tauri",
)
_SKILLS_SORTED = sorted(SKILLS, key=len, reverse=True)

_EDUCATION_MARKERS = ("университет", "институт", "академия", "university", "college")
_EXPERIENCE_MARKERS = (
    "разработчик",
    "developer",
    "engineer",
    "инженер",
    "ооо",
    "llc",
    "company",
)


def _resume_text(user: str) -> str:
    start = user.find(_RESUME_OPEN)
    end = user.rfind(_RESUME_CLOSE)
    if start < 0 or end < 0 or end <= start:
        return user
    return user[start + len(_RESUME_OPEN) : end].strip("\n")


def _years_of(line: str) -> int | None:
    match = _YEARS.search(line)
    return int(match.group(1)) if match else None


def _first_skill(line: str) -> str | None:
    lowered = line.lower()
    for skill in _SKILLS_SORTED:
        if skill.lower() in lowered:
            return skill
    return None


def _short_quote(line: str, limit: int, anchor: str | None = None) -> str:
    """Вернуть короткую подстроку, чтобы mock не нарушал лимиты схемы."""
    if len(line) <= limit:
        return line

    lowered = line.lower()
    position = lowered.find(anchor.lower()) if anchor else 0
    if position < 0:
        position = 0

    start = max(0, position - limit // 3)
    end = min(len(line), start + limit)
    start = max(0, end - limit)
    return line[start:end].strip()


def _parse(text: str) -> ParsedResume:
    skills: list[ExtractedSkill] = []
    education: list[ExtractedEducation] = []
    experience: list[ExtractedExperience] = []
    seen: set[str] = set()

    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue

        lowered = line.lower()
        skill = _first_skill(line)
        if skill is not None and skill.lower() not in seen:
            seen.add(skill.lower())
            skills.append(
                ExtractedSkill(
                    name=skill,
                    years=_years_of(line),
                    quote=_short_quote(line, SKILL_QUOTE_MAX_CHARS, anchor=skill),
                )
            )

        if any(marker in lowered for marker in _EDUCATION_MARKERS):
            year = _YEAR.search(line)
            education.append(
                ExtractedEducation(
                    institution=_short_quote(line, 256),
                    level=EducationLevel.UNKNOWN,
                    graduation_year=int(year.group(0)) if year else None,
                    quote=_short_quote(line, ITEM_QUOTE_MAX_CHARS),
                )
            )
        elif any(marker in lowered for marker in _EXPERIENCE_MARKERS):
            experience.append(
                ExtractedExperience(
                    company=_short_quote(line, 256),
                    quote=_short_quote(line, ITEM_QUOTE_MAX_CHARS),
                )
            )

    return ParsedResume(skills=skills, education=education, experience=experience)


class MockLlmClient:
    """Детерминированный клиент без сети. Модель указана явно как заглушка."""

    model = "mock/deterministic"

    def complete(self, system: str, user: str) -> str:
        return _parse(_resume_text(user)).model_dump_json()
