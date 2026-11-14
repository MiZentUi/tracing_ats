"""Доменные структуры словаря."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field


@dataclass(frozen=True, slots=True)
class Alias:
    id: str
    skill_id: str
    raw: str
    is_canonical: bool
    lang: str
    source: str
    lemma_mode: str
    created_by: str
    created_at: str


@dataclass(frozen=True, slots=True)
class Skill:
    id: str
    canonical_name: str
    skill_type: str
    status: str
    aliases: tuple[Alias, ...] = ()

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "canonical_name": self.canonical_name,
            "skill_type": self.skill_type,
            "status": self.status,
            "aliases": [asdict(alias) for alias in self.aliases],
        }


@dataclass(frozen=True, slots=True)
class AliasRef:
    """Результат поиска по ключу."""

    alias_id: str
    skill_id: str
    canonical_name: str
    kind: str


@dataclass(frozen=True, slots=True)
class Candidate:
    skill_id: str
    canonical_name: str
    confidence: float


@dataclass(frozen=True, slots=True)
class ReviewItem:
    id: str
    key: str
    sample_masked: tuple[str, ...]
    occurrences: int
    status: str
    suggested_skill_id: str | None
    suggested_confidence: float | None
    candidates: tuple[Candidate, ...]
    resolved_skill_id: str | None
    resolved_by: str | None
    resolved_at: str | None
    first_seen: str
    last_seen: str


@dataclass(frozen=True, slots=True)
class QualificationReviewItem:
    id: str
    key: str
    sample_masked: tuple[str, ...]
    occurrences: int
    status: str
    suggested_qualification_id: str | None
    suggested_confidence: float | None
    candidates: tuple[Candidate, ...]
    resolved_qualification_id: str | None
    resolved_by: str | None
    resolved_at: str | None
    first_seen: str
    last_seen: str


@dataclass(frozen=True, slots=True)
class Meta:
    dictionary_version: int
    embedding_model_id: str
    config_hash: str
    pymorphy_version: str
    homoglyph_rules_version: str
    qualification_version: int = 1
    relation_version: int = 1

    @property
    def resolver_version(self) -> str:
        return (
            f"d{self.dictionary_version}-q{self.qualification_version}"
            f"-r{self.relation_version}-e{self.embedding_model_id or 'none'}"
            f"-c{self.config_hash}-p{self.pymorphy_version}"
            f"-h{self.homoglyph_rules_version}"
        )


@dataclass(frozen=True, slots=True)
class Resolved:
    raw_name: str
    normalized_key: str
    folded_key: str
    skill_id: str | None
    canonical_name: str
    method: str
    confidence: float
    candidates: tuple[Candidate, ...] = field(default_factory=tuple)


@dataclass(frozen=True, slots=True)
class QualificationAlias:
    id: str
    qualification_id: str
    raw: str
    is_canonical: bool
    lang: str
    source: str
    lemma_mode: str
    created_by: str
    created_at: str


@dataclass(frozen=True, slots=True)
class Qualification:
    id: str
    canonical_name: str
    status: str = "ACTIVE"
    qualification_type: str | None = None
    aliases: tuple[QualificationAlias, ...] = ()


@dataclass(frozen=True, slots=True)
class QualificationAliasRef:
    alias_id: str
    qualification_id: str
    canonical_name: str
    kind: str


@dataclass(frozen=True, slots=True)
class ResolvedQualification:
    raw_label: str
    normalized_key: str
    folded_key: str
    qualification_id: str | None
    canonical_name: str
    method: str
    confidence: float


@dataclass(frozen=True, slots=True)
class SkillRelation:
    left_skill_id: str
    right_skill_id: str
    kind: str


class DictionaryError(Exception):
    """Базовая ошибка домена."""

    code = "DICTIONARY_ERROR"


class AliasConflict(DictionaryError):
    code = "ALIAS_OWNED_BY_OTHER"

    def __init__(self, key: str, owner_skill_id: str, owner_name: str) -> None:
        super().__init__(f"alias key {key!r} belongs to {owner_name}")
        self.key = key
        self.owner_skill_id = owner_skill_id
        self.owner_name = owner_name


class CanonicalRequired(DictionaryError):
    code = "CANONICAL_REQUIRED"


class NotFound(DictionaryError):
    code = "NOT_FOUND"
