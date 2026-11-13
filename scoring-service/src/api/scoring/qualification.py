"""Детерминированная нормализация открытых квалификаций.

Словарь не задаёт закрытый перечень: он только сводит разные формулировки
одной квалификации к общему каноническому ключу. В v1 квалификации
разрешаются через `dictionary-service`, а снимок `QualificationCatalog`
из запроса остаётся fallback-механизмом. Сервис остаётся stateless.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field

from scoring_service.v1.models.features import qualification_pb2

from ..dictionary.client import DictionaryClient, DictionaryUnavailable
from ..extraction.normalize import candidate_keys, normalize_key

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class QualificationIdentity:
    raw_label: str
    qualification_id: str | None
    keys: tuple[str, ...] = field(default_factory=tuple)
    method: str = "NEW"


@dataclass(frozen=True, slots=True)
class BatchQualificationResolution:
    resolver_version: str
    mode: str
    identities: dict[str, QualificationIdentity]

    def identity(self, raw_label: str) -> QualificationIdentity | None:
        return self.identities.get(raw_label)

    @staticmethod
    def matches(left: QualificationIdentity | None, right: QualificationIdentity | None) -> bool:
        if left is None or right is None:
            return False
        if left.qualification_id and right.qualification_id:
            return left.qualification_id == right.qualification_id
        return bool(set(left.keys) & set(right.keys))


class QualificationResolver:
    """Разрешает квалификации через dictionary-service или catalog fallback."""

    def __init__(
        self,
        client: DictionaryClient | None = None,
        *,
        enabled: bool = True,
    ) -> None:
        self._client = client
        self._enabled = enabled and client is not None

    def resolve(
        self,
        labels: list[tuple[str, str]],
        catalog: qualification_pb2.QualificationCatalog | None = None,
    ) -> BatchQualificationResolution:
        if not labels:
            return BatchQualificationResolution("", "EMPTY", {})
        unique = {raw for raw, _ in labels if raw}
        if self._enabled:
            try:
                resolution = self._client.resolve_qualifications(labels)  # type: ignore[union-attr]
                normalizer = QualificationNormalizer(catalog)
                identities = {}
                for raw, item in resolution.items.items():
                    if raw not in unique:
                        continue
                    keys = [
                        item.normalized_key,
                        item.folded_key,
                        *candidate_keys(item.raw_label),
                    ]
                    if item.qualification_id is None and catalog is not None:
                        fallback_key = normalizer.canonical_key(item.raw_label)
                        if fallback_key:
                            keys.append(fallback_key)
                    identities[raw] = QualificationIdentity(
                        raw_label=item.raw_label,
                        qualification_id=item.qualification_id,
                        keys=tuple(dict.fromkeys(key for key in keys if key)),
                        method=item.method,
                    )
                return BatchQualificationResolution(
                    resolution.resolver_version,
                    resolution.mode,
                    identities,
                )
            except DictionaryUnavailable as error:
                logger.warning(
                    "dictionary_unavailable fallback=catalog component=qualifications error=%s",
                    type(error).__name__,
                )
        normalizer = QualificationNormalizer(catalog)
        identities = {}
        for raw in unique:
            key = normalizer.canonical_key(raw)
            identities[raw] = QualificationIdentity(
                raw_label=raw,
                qualification_id=None,
                keys=(key,) if key else (),
                method="EXACT",
            )
        return BatchQualificationResolution(
            normalizer.version or "local-exact-v1",
            "EXACT_FALLBACK",
            identities,
        )


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
