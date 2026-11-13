"""Детерминированная нормализация открытых квалификаций.

Словарь не задаёт закрытый перечень: он только сводит разные формулировки
одной квалификации к общему каноническому ключу. Снимок словаря приходит
в gRPC-запросе, поэтому сервис остаётся stateless.
"""

from __future__ import annotations

from scoring_service.v1.models.features import qualification_pb2

from ..extraction.normalize import normalize_key


class QualificationNormalizer:
    """Сопоставляет label требования и резюме через алиасы словаря."""

    def __init__(
        self,
        catalog: qualification_pb2.QualificationCatalog | None = None,
    ) -> None:
        aliases: dict[str, str] = {}
        if catalog is not None:
            for alias in catalog.aliases:
                alias_key = normalize_key(alias.alias_normalized)
                if not alias_key:
                    continue
                canonical = (
                    normalize_key(alias.canonical_key)
                    or normalize_key(alias.canonical_label)
                    or alias_key
                )
                aliases[alias_key] = canonical
        self._aliases = aliases
        self._version = catalog.version if catalog is not None else ""

    @property
    def version(self) -> str:
        return self._version

    def canonical_key(self, label: str) -> str:
        """Канонический ключ label; без алиаса — нормализованный label."""
        normalized = normalize_key(label)
        if not normalized:
            return ""
        return self._aliases.get(normalized, normalized)

    def matches(self, requirement_label: str, resume_label: str) -> bool:
        """Точное совпадение канонических ключей. Частичных совпадений нет."""
        required = self.canonical_key(requirement_label)
        if not required:
            return False
        return required == self.canonical_key(resume_label)
