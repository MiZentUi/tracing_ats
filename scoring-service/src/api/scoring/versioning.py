"""Сопоставление навыков с учётом версии в имени.

Версия — это хвост после базового имени навыка: ``Java 11/17`` → ``Java``,
``C++17`` → ``C++``, ``C# 12`` → ``C#``, ``.NET 8`` → ``.NET``. Хвост
отрезается только тогда, когда он действительно состоит из версий; ``Java EE``,
``AngularJS``, ``Spring Boot`` и ``Java developer`` остаются разными именами.

Правила версий живут здесь, а не в промпте: LLM не должен решать, что считать
версией, а что — частью названия продукта.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from ..extraction.normalize import fold_homoglyphs, normalize_key

VERSION_MATCHER_VERSION = "skill-versioned-v1"

_VERSION = r"[vV]?\d+(?:\.\d+)*"
_SEPARATOR = r"(?:/|,|;|–|—|-|\+|and|и|or|или)"

# Хвост начинается после пробела/разделителя или сразу после ``+``/``#``:
# так ``C++17`` и ``C#12`` разбираются, а ``JavaScript`` — нет.
_VERSION_TAIL = re.compile(
    rf"(?:(?<=[+#])|[\s/,;–—\-]+)"
    rf"(?P<tail>{_VERSION}(?:\s*{_SEPARATOR}\s*{_VERSION})*(?:\s*\+)?)"
    rf"\s*$",
    re.IGNORECASE,
)
_VERSION_SPLIT = re.compile(rf"\s*{_SEPARATOR}\s*", re.IGNORECASE)
_NUMERIC_VERSION = re.compile(r"\d+(?:\.\d+)*")


@dataclass(frozen=True, slots=True)
class ParsedSkillName:
    """Нормализованное имя и отделённый от него набор версий."""

    raw: str
    base: str
    versions: tuple[str, ...] = ()


@dataclass(frozen=True, slots=True)
class VersionMatch:
    """Результат сравнения базового имени и версий."""

    base: str
    required_versions: tuple[str, ...]
    found_versions: tuple[str, ...]
    versions_compatible: bool
    mismatch: bool
    score: float


@dataclass(frozen=True, slots=True)
class VersionMatchPolicy:
    """Политика для несовпадающих версий.

    По умолчанию несовпадение версий не штрафует кандидата: требование ``Java``
    и резюме ``Java 11`` должны давать полное покрытие. Значение можно
    переопределить в тестах или будущей конфигурации.
    """

    mismatch_score: float = 1.0

    def score(self) -> float:
        return min(1.0, max(0.0, self.mismatch_score))


def parse_skill_name(raw: str) -> ParsedSkillName:
    """Отделить version tail от имени навыка.

    Сравнение идёт по свёрнутым гомоглифам, чтобы ``1С 8.3`` и ``1C``
    сходились, а ``С++`` и ``C++`` не разъезжались.
    """

    normalized = normalize_key(raw)
    folded = fold_homoglyphs(normalized)
    match = _VERSION_TAIL.search(folded)
    if match is None:
        return ParsedSkillName(raw=raw, base=folded)

    base = folded[: match.start()].strip()
    versions = _extract_versions(match.group("tail"))
    if not base or not versions:
        return ParsedSkillName(raw=raw, base=folded)

    return ParsedSkillName(raw=raw, base=base, versions=versions)


def match_skill_names(
    required: str,
    found: str,
    policy: VersionMatchPolicy | None = None,
) -> VersionMatch | None:
    """Сравнить два имени навыка по базе и версиям.

    ``None`` означает, что базовые имена различаются. Если база совпала,
    возвращается ``VersionMatch``; несовпадение версий разрешает политика.
    """

    left = parse_skill_name(required)
    right = parse_skill_name(found)
    if not left.base or left.base != right.base:
        return None

    effective = policy or VersionMatchPolicy()
    if not left.versions or not right.versions:
        return VersionMatch(
            base=left.base,
            required_versions=left.versions,
            found_versions=right.versions,
            versions_compatible=True,
            mismatch=False,
            score=1.0,
        )

    compatible = bool(set(left.versions) & set(right.versions))
    return VersionMatch(
        base=left.base,
        required_versions=left.versions,
        found_versions=right.versions,
        versions_compatible=compatible,
        mismatch=not compatible,
        score=1.0 if compatible else effective.score(),
    )


def _extract_versions(tail: str) -> tuple[str, ...]:
    value = tail.strip()
    if value.endswith("+"):
        value = value[:-1].strip()

    versions: list[str] = []
    for part in _VERSION_SPLIT.split(value):
        token = part.strip().lstrip("vV")
        if token and _NUMERIC_VERSION.fullmatch(token):
            versions.append(token)

    return tuple(dict.fromkeys(versions))
