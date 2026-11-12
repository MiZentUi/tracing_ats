"""Ошибки доменного слоя скоринга."""

from __future__ import annotations


class ScoringError(Exception):
    """Ошибка, которую можно вернуть в одном элементе batch-ответа."""

    code: str
    message: str

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


class InvalidRequirement(ScoringError):
    """Требование вакансии не содержит поддерживаемой ветки `oneof`."""

    def __init__(self, requirement_id: str) -> None:
        super().__init__(
            "INVALID_ARGUMENT",
            f"requirement {requirement_id!r} has no supported feature",
        )
