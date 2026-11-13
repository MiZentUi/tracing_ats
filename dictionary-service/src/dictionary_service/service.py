"""Бизнес-логика словаря: Resolve, версия, очередь ревью и админские операции."""

from __future__ import annotations

from dataclasses import dataclass

from .domain import (
    Alias,
    AliasConflict,
    Candidate,
    Meta,
    NotFound,
    Resolved,
    ResolvedQualification,
    ReviewItem,
    Skill,
    SkillRelation,
)
from .normalization import alias_keys, normalize_base, pymorphy_version
from .seed import SEED, SEED_QUALIFICATIONS, SEED_RELATIONS
from .store import InMemoryStore, Store, raw_keys

_METHOD_BY_KIND = {
    "RAW": "ALIAS_EXACT",
    "FOLDED": "ALIAS_FOLDED",
    "LEMMA": "ALIAS_LEMMA",
    "LEMMA_FOLDED": "ALIAS_LEMMA_FOLDED",
}


@dataclass(frozen=True, slots=True)
class ResolveInput:
    key: str
    raw_name: str
    lang_hint: str
    source: str


@dataclass(frozen=True, slots=True)
class ResolveQualificationInput:
    key: str
    raw_label: str
    source: str


@dataclass(frozen=True, slots=True)
class ApplyAliasInput:
    text: str
    lang: str = "mixed"


@dataclass(frozen=True, slots=True)
class ApplySkillInput:
    canonical: str
    skill_type: str = "OTHER"
    target_skill_id: str | None = None
    aliases: tuple[ApplyAliasInput, ...] = ()


class DictionaryService:
    def __init__(
        self,
        store: Store,
        *,
        config_hash: str,
        embedding_model_id: str = "",
        homoglyph_rules_version: str = "v1",
        seed_on_start: bool = True,
    ) -> None:
        self._store = store
        self._config_hash = config_hash
        self._embedding_model_id = embedding_model_id
        self._homoglyph_rules_version = homoglyph_rules_version
        self._seed_on_start = seed_on_start

    # --- инициализация ------------------------------------------------------
    def seed_if_empty(self) -> None:
        if self._seed_on_start:
            if self._store.list_skills("", "", "", 1, 0)[1] == 0:
                self._store.seed(SEED)
            if self._store.qualifications_empty():
                self._store.seed_qualifications(SEED_QUALIFICATIONS)
            if self._store.relations_empty():
                self._store.seed_relations(SEED_RELATIONS)
        meta = self.version()
        if self._store.get_snapshot(meta.resolver_version) is not None:
            return
        self._save_snapshot(meta)

    # --- версия -------------------------------------------------------------
    def version(self) -> Meta:
        meta = self._store.meta()
        return Meta(
            meta.dictionary_version,
            self._embedding_model_id,
            self._config_hash,
            pymorphy_version(),
            self._homoglyph_rules_version,
            meta.qualification_version,
            meta.relation_version,
        )

    def _save_snapshot(self, meta: Meta) -> None:
        payload = self._store.export_state()
        payload["meta"] = {
            "dictionary_version": meta.dictionary_version,
            "embedding_model_id": meta.embedding_model_id,
            "config_hash": meta.config_hash,
            "pymorphy_version": meta.pymorphy_version,
            "homoglyph_rules_version": meta.homoglyph_rules_version,
            "qualification_version": meta.qualification_version,
            "relation_version": meta.relation_version,
        }
        self._store.save_snapshot(meta.resolver_version, payload)

    def _commit(self) -> Meta:
        self._store.bump_version()
        meta = self.version()
        self._save_snapshot(meta)
        return meta

    # --- Resolve ------------------------------------------------------------
    def resolve(
        self, items: list[ResolveInput], as_of: str = "", skip_review: bool = False
    ) -> tuple[str, list[Resolved]]:
        meta = self.version()
        resolver_version = meta.resolver_version
        if not as_of and self._store.get_snapshot(resolver_version) is None:
            self._save_snapshot(meta)
        store: Store = self._store
        read_only = False
        if as_of:
            snapshot = self._store.get_snapshot(as_of)
            if snapshot is None:
                raise NotFound(f"resolver version {as_of} not found")
            store = InMemoryStore.from_state(snapshot)
            resolver_version = as_of
            read_only = True

        result: list[Resolved] = []
        for item in items:
            normalized_key, folded_key = _base_keys(item.raw_name)
            hit = None
            hit_kind = ""
            for kind, key in raw_keys(item.raw_name):
                found = store.lookup(key)
                if found is not None:
                    hit = found
                    hit_kind = kind
                    break
            if hit is not None:
                result.append(
                    Resolved(
                        raw_name=item.raw_name,
                        normalized_key=normalized_key,
                        folded_key=folded_key,
                        skill_id=hit.skill_id,
                        canonical_name=hit.canonical_name,
                        method=_METHOD_BY_KIND.get(hit_kind, "ALIAS_EXACT"),
                        confidence=1.0,
                    )
                )
                continue

            review: ReviewItem | None = None
            if not read_only and not skip_review and normalized_key:
                review = self._store.enqueue_review(normalized_key, item.raw_name, item.source)
            result.append(
                Resolved(
                    raw_name=item.raw_name,
                    normalized_key=normalized_key,
                    folded_key=folded_key,
                    skill_id=None,
                    canonical_name="",
                    method="NEW",
                    confidence=0.0,
                    candidates=review.candidates if review else (),
                )
            )
        return resolver_version, result

    def resolve_qualifications(
        self,
        items: list[ResolveQualificationInput],
        as_of: str = "",
    ) -> tuple[str, list[ResolvedQualification]]:
        meta = self.version()
        resolver_version = meta.resolver_version
        if not as_of and self._store.get_snapshot(resolver_version) is None:
            self._save_snapshot(meta)
        store: Store = self._store
        if as_of:
            snapshot = self._store.get_snapshot(as_of)
            if snapshot is None:
                raise NotFound(f"resolver version {as_of} not found")
            store = InMemoryStore.from_state(snapshot)
            resolver_version = as_of

        result: list[ResolvedQualification] = []
        for item in items:
            normalized_key, folded_key = _base_keys(item.raw_label)
            hit = None
            hit_kind = ""
            for kind, key in raw_keys(item.raw_label):
                found = store.lookup_qualification(key)
                if found is not None:
                    hit = found
                    hit_kind = kind
                    break
            if hit is None:
                result.append(
                    ResolvedQualification(
                        raw_label=item.raw_label,
                        normalized_key=normalized_key,
                        folded_key=folded_key,
                        qualification_id=None,
                        canonical_name="",
                        method="NEW",
                        confidence=0.0,
                    )
                )
                continue
            result.append(
                ResolvedQualification(
                    raw_label=item.raw_label,
                    normalized_key=normalized_key,
                    folded_key=folded_key,
                    qualification_id=hit.qualification_id,
                    canonical_name=hit.canonical_name,
                    method=_METHOD_BY_KIND.get(hit_kind, "ALIAS_EXACT"),
                    confidence=1.0,
                )
            )
        return resolver_version, result

    def get_relations(
        self,
        pairs: list[tuple[str, str]],
        as_of: str = "",
    ) -> tuple[str, tuple[SkillRelation, ...]]:
        meta = self.version()
        resolver_version = meta.resolver_version
        store: Store = self._store
        if as_of:
            snapshot = self._store.get_snapshot(as_of)
            if snapshot is None:
                raise NotFound(f"resolver version {as_of} not found")
            store = InMemoryStore.from_state(snapshot)
            resolver_version = as_of
        return resolver_version, store.get_relations(pairs)

    # --- чтение -------------------------------------------------------------
    def get_skills(self, skill_ids: list[str]) -> dict[str, Skill]:
        return self._store.get_skills(skill_ids)

    def list_skills(
        self,
        query: str,
        skill_type: str,
        status: str,
        limit: int,
        offset: int,
    ) -> tuple[list[Skill], int]:
        return self._store.list_skills(query, skill_type, status, limit, offset)

    def list_review(
        self,
        status: str,
        limit: int,
        offset: int,
    ) -> tuple[list[ReviewItem], int]:
        return self._store.list_review(status, limit, offset)

    # --- применение черновиков ---------------------------------------------
    def apply_skills(self, inputs: list[ApplySkillInput], actor: str) -> tuple[list[Skill], str]:
        targets: list[Skill | None] = []
        for item in inputs:
            target = self._store.get_skill(item.target_skill_id) if item.target_skill_id else None
            if target is None:
                for key in alias_keys(item.canonical).values():
                    hit = self._store.lookup(key)
                    if hit is not None:
                        target = self._store.get_skill(hit.skill_id)
                        break
            targets.append(target)

        # Сначала проверяем все конфликты, чтобы не записать половину пачки.
        for item, target in zip(inputs, targets, strict=True):
            for alias in item.aliases:
                for key in alias_keys(alias.text).values():
                    hit = self._store.lookup(key)
                    if hit is not None and (target is None or hit.skill_id != target.id):
                        raise AliasConflict(key, hit.skill_id, hit.canonical_name)

        applied: list[Skill] = []
        for item, target in zip(inputs, targets, strict=True):
            if target is None:
                target = self._store.create_skill(
                    item.canonical, item.skill_type or "OTHER", actor, "LLM"
                )
            result = target
            for alias in item.aliases:
                self._store.add_alias(
                    target.id, alias.text, alias.lang or "mixed", "AUTO", "LLM", actor
                )
                result = self._store.get_skill(target.id) or result
            applied.append(result)
        meta = self._commit()
        return applied, meta.resolver_version

    # --- админские операции -------------------------------------------------
    def create_skill(self, canonical_name: str, skill_type: str, actor: str) -> Skill:
        skill = self._store.create_skill(canonical_name, skill_type, actor, "HR")
        self._commit()
        return skill

    def add_alias(
        self,
        skill_id: str,
        raw: str,
        lang: str,
        lemma_mode: str,
        actor: str,
    ) -> Alias:
        alias = self._store.add_alias(skill_id, raw, lang, lemma_mode, "HR", actor)
        self._commit()
        return alias

    def set_canonical(self, skill_id: str, alias_id: str, actor: str) -> Skill:
        skill = self._store.set_canonical(skill_id, alias_id, actor)
        self._commit()
        return skill

    def delete_alias(self, alias_id: str, actor: str) -> None:
        self._store.delete_alias(alias_id, actor)
        self._commit()

    def deprecate_skill(self, skill_id: str, actor: str) -> Skill:
        skill = self._store.deprecate_skill(skill_id, actor)
        self._commit()
        return skill

    def delete_skill(self, skill_id: str, actor: str) -> None:
        self._store.delete_skill(skill_id, actor)
        self._commit()

    def resolve_review(
        self,
        review_id: str,
        action: str,
        actor: str,
        skill_id: str | None,
        canonical_name: str | None,
        skill_type: str | None,
        additional_aliases: list[str],
    ) -> tuple[ReviewItem, Skill | None]:
        item, skill = self._store.resolve_review(
            review_id,
            action,
            actor,
            skill_id,
            canonical_name,
            skill_type,
            additional_aliases,
        )
        self._commit()
        return item, skill


def _base_keys(raw: str) -> tuple[str, str]:
    raw_key = normalize_base(raw)
    if not raw_key:
        return "", ""
    folded = ""
    for kind, key in raw_keys(raw):
        if kind == "FOLDED":
            folded = key
            break
    return raw_key, folded


def candidates_for_new(store: Store, key: str) -> tuple[Candidate, ...]:
    """Подсказки только для очереди ревью; на матчинг не влияют."""
    item = store.enqueue_review(key, key, "ADMIN")
    return item.candidates


__all__ = [
    "AliasConflict",
    "DictionaryService",
    "ResolveInput",
    "ResolveQualificationInput",
    "candidates_for_new",
]
