"""Текстовые требования: детерминированный поиск подтверждения в резюме."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from .similarity import SimilarityScorer, tokenize

_MAX_EVIDENCE_CHARS = 240


@dataclass(frozen=True, slots=True)
class TextMatch:
    score: float
    evidence_text: str | None


class TextMatcher(Protocol):
    def match(self, requirement: str, text: str) -> TextMatch: ...


class DeterministicTextMatcher:
    """Ищет строку резюме с максимальным пересечением токенов требования."""

    def __init__(self, similarity: SimilarityScorer) -> None:
        self._similarity = similarity

    def match(self, requirement: str, text: str) -> TextMatch:
        if not requirement.strip() or not text.strip():
            return TextMatch(score=0.0, evidence_text=None)

        best_score = 0.0
        best_line: str | None = None
        for raw_line in text.splitlines():
            line = raw_line.strip()
            if not line:
                continue
            score = self._similarity.text_score(requirement, line)
            if score > best_score:
                best_score = score
                best_line = line

        if best_line is None or best_score <= 0.0:
            return TextMatch(score=0.0, evidence_text=None)

        return TextMatch(
            score=best_score,
            evidence_text=_short_evidence(best_line, requirement),
        )


def _short_evidence(line: str, requirement: str) -> str:
    """Ограничить доказательство окном вокруг первого совпавшего токена."""
    if len(line) <= _MAX_EVIDENCE_CHARS:
        return line

    lowered = line.lower()
    anchor = 0
    for token in tokenize(requirement):
        position = lowered.find(token)
        if position >= 0:
            anchor = position
            break

    start = max(0, anchor - _MAX_EVIDENCE_CHARS // 3)
    end = min(len(line), start + _MAX_EVIDENCE_CHARS)
    start = max(0, end - _MAX_EVIDENCE_CHARS)
    return line[start:end].strip()
