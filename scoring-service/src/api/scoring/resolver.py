"""Разрешение навыков в канон: dictionary-service или точный fallback."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field

from ..dictionary.client import DictionaryClient, DictionaryUnavailable
from ..extraction.normalize import candidate_keys

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class SkillIdentity:
    raw_name: str
    skill_id: str | None
    keys: tuple[str, ...] = field(default_factory=tuple)
    method: str = "NEW"


@dataclass(frozen=True, slots=True)
class BatchResolution:
    resolver_version: str
    mode: str
    identities: dict[str, SkillIdentity]

    def identity(self, raw_name: str) -> SkillIdentity | None:
        return self.identities.get(raw_name)


class SkillResolver:
    """Один batch-Resolve на запуск; сравнение только по skill_id или ключу."""

    def __init__(
        self,
        client: DictionaryClient | None = None,
        *,
        enabled: bool = True,
    ) -> None:
        self._client = client
        self._enabled = enabled and client is not None

    def resolve(self, names: list[tuple[str, str]]) -> BatchResolution:
        if not names:
            return BatchResolution("", "EMPTY", {})
        unique = {raw for raw, _ in names if raw}
        if self._enabled:
            try:
                resolution = self._client.resolve(names)  # type: ignore[union-attr]
                identities = {
                    raw: _from_dictionary(item)
                    for raw, item in resolution.items.items()
                    if raw in unique
                }
                return BatchResolution(resolution.resolver_version, resolution.mode, identities)
            except DictionaryUnavailable as error:
                logger.warning(
                    "dictionary_unavailable fallback=exact error=%s", type(error).__name__
                )
        return BatchResolution(
            "local-exact-v1",
            "EXACT_FALLBACK",
            {raw: _local(raw) for raw in unique},
        )

    @staticmethod
    def score(left: SkillIdentity | None, right: SkillIdentity | None) -> float:
        if left is None or right is None:
            return 0.0
        if left.skill_id and right.skill_id and left.skill_id == right.skill_id:
            return 1.0
        if set(left.keys) & set(right.keys):
            return 1.0
        return 0.0


def _from_dictionary(item) -> SkillIdentity:
    keys = [item.normalized_key, item.folded_key, *candidate_keys(item.raw_name)]
    return SkillIdentity(
        raw_name=item.raw_name,
        skill_id=item.skill_id,
        keys=tuple(dict.fromkeys(key for key in keys if key)),
        method=item.method,
    )


def _local(raw_name: str) -> SkillIdentity:
    return SkillIdentity(
        raw_name=raw_name,
        skill_id=None,
        keys=candidate_keys(raw_name),
        method="EXACT",
    )
