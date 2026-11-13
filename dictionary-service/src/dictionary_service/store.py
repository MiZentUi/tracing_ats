"""Хранилище словаря: протокол и in-memory реализация для тестов."""

from __future__ import annotations

import difflib
import json
import uuid
from dataclasses import asdict, replace
from datetime import UTC, datetime
from typing import Protocol

from .domain import (
    Alias,
    AliasConflict,
    AliasRef,
    Candidate,
    CanonicalRequired,
    Meta,
    NotFound,
    Qualification,
    QualificationAlias,
    QualificationAliasRef,
    ReviewItem,
    Skill,
    SkillRelation,
)
from .normalization import alias_keys, keys_for_raw, normalize_base
from .seed import SeedQualification, SeedRelation

_KIND_ORDER = ("RAW", "FOLDED", "LEMMA", "LEMMA_FOLDED")
_NAMESPACE = uuid.UUID("7b3d5b2e-6f4a-5c1d-9e8f-0a1b2c3d4e5f")


def now_iso() -> str:
    return datetime.now(UTC).replace(microsecond=0).isoformat()


def stable_id(name: str) -> str:
    return str(uuid.uuid5(_NAMESPACE, name))


class Store(Protocol):
    def meta(self) -> Meta: ...
    def bump_version(self) -> Meta: ...
    def bump_qualification_version(self) -> Meta: ...
    def bump_relation_version(self) -> Meta: ...
    def save_snapshot(self, resolver_version: str, payload: dict) -> None: ...
    def get_snapshot(self, resolver_version: str) -> dict | None: ...

    def seed(self, seed: tuple) -> None: ...
    def lookup(self, key: str) -> AliasRef | None: ...
    def get_skill(self, skill_id: str) -> Skill | None: ...
    def get_skills(self, skill_ids: list[str]) -> dict[str, Skill]: ...
    def list_skills(
        self,
        query: str,
        skill_type: str,
        status: str,
        limit: int,
        offset: int,
    ) -> tuple[list[Skill], int]: ...
    def create_skill(
        self,
        canonical_name: str,
        skill_type: str,
        actor: str,
        source: str,
        skill_id: str | None = None,
    ) -> Skill: ...
    def add_alias(
        self,
        skill_id: str,
        raw: str,
        lang: str,
        lemma_mode: str,
        source: str,
        actor: str,
    ) -> Alias: ...
    def set_canonical(self, skill_id: str, alias_id: str, actor: str) -> Skill: ...
    def delete_alias(self, alias_id: str, actor: str) -> None: ...
    def deprecate_skill(self, skill_id: str, actor: str) -> Skill: ...
    def delete_skill(self, skill_id: str, actor: str) -> None: ...

    def qualifications_empty(self) -> bool: ...
    def relations_empty(self) -> bool: ...
    def seed_qualifications(self, seed: tuple[SeedQualification, ...]) -> None: ...
    def seed_relations(self, seed: tuple[SeedRelation, ...]) -> None: ...
    def lookup_qualification(self, key: str) -> QualificationAliasRef | None: ...
    def get_relations(self, pairs: list[tuple[str, str]]) -> tuple[SkillRelation, ...]: ...
    def list_qualifications(
        self,
        query: str,
        status: str,
        limit: int,
        offset: int,
    ) -> tuple[list[Qualification], int]: ...
    def create_qualification(
        self,
        canonical_name: str,
        qualification_type: str,
        actor: str,
        source: str,
    ) -> Qualification: ...
    def add_qualification_alias(
        self,
        qualification_id: str,
        raw: str,
        lang: str,
        lemma_mode: str,
        source: str,
        actor: str,
    ) -> QualificationAlias: ...
    def set_canonical_qualification_alias(
        self, qualification_id: str, alias_id: str, actor: str
    ) -> Qualification: ...
    def delete_qualification_alias(self, alias_id: str, actor: str) -> None: ...
    def list_relations(self, limit: int, offset: int) -> tuple[list[SkillRelation], int]: ...
    def create_relation(self, left: str, right: str, kind: str, actor: str) -> SkillRelation: ...
    def delete_relation(self, left: str, right: str, actor: str) -> None: ...

    def enqueue_review(self, key: str, raw: str, source: str) -> ReviewItem: ...
    def list_review(self, status: str, limit: int, offset: int) -> tuple[list[ReviewItem], int]: ...
    def get_review(self, review_id: str) -> ReviewItem | None: ...
    def resolve_review(
        self,
        review_id: str,
        action: str,
        actor: str,
        skill_id: str | None,
        canonical_name: str | None,
        skill_type: str | None,
        additional_aliases: list[str],
    ) -> tuple[ReviewItem, Skill | None]: ...


class InMemoryStore:
    """Детерминированное хранилище для unit-тестов и локальной отладки."""

    def __init__(self) -> None:
        self._meta = Meta(1, "", "", "none", "v1")
        self._skills: dict[str, Skill] = {}
        self._aliases: dict[str, Alias] = {}
        self._alias_keys: dict[str, tuple[str, str]] = {}
        self._review: dict[str, ReviewItem] = {}
        self._review_by_key: dict[str, str] = {}
        self._snapshots: dict[str, dict] = {}
        self._qualifications: dict[str, Qualification] = {}
        self._qualification_aliases: dict[str, QualificationAlias] = {}
        self._qualification_keys: dict[str, tuple[str, str]] = {}
        self._relations: dict[tuple[str, str], str] = {}

    # --- метаданные и версия -------------------------------------------------
    def meta(self) -> Meta:
        return self._meta

    def bump_version(self) -> Meta:
        self._meta = Meta(
            self._meta.dictionary_version + 1,
            self._meta.embedding_model_id,
            self._meta.config_hash,
            self._meta.pymorphy_version,
            self._meta.homoglyph_rules_version,
        )
        return self._meta

    def bump_qualification_version(self) -> Meta:
        self._meta = Meta(
            self._meta.dictionary_version,
            self._meta.embedding_model_id,
            self._meta.config_hash,
            self._meta.pymorphy_version,
            self._meta.homoglyph_rules_version,
            self._meta.qualification_version + 1,
            self._meta.relation_version,
        )
        return self._meta

    def bump_relation_version(self) -> Meta:
        self._meta = Meta(
            self._meta.dictionary_version,
            self._meta.embedding_model_id,
            self._meta.config_hash,
            self._meta.pymorphy_version,
            self._meta.homoglyph_rules_version,
            self._meta.qualification_version,
            self._meta.relation_version + 1,
        )
        return self._meta

    def save_snapshot(self, resolver_version: str, payload: dict) -> None:
        self._snapshots[resolver_version] = payload

    def get_snapshot(self, resolver_version: str) -> dict | None:
        return self._snapshots.get(resolver_version)

    # --- seed ---------------------------------------------------------------
    def seed(self, seed: tuple) -> None:
        if self._skills:
            return
        for item in seed:
            skill = self.create_skill(
                item.canonical,
                item.skill_type,
                actor="seed",
                source="SEED",
                skill_id=stable_id(item.canonical),
            )
            for alias_raw in item.aliases:
                self.add_alias(skill.id, alias_raw, "mixed", "AUTO", "SEED", "seed")
            for alias_raw in item.raw_only:
                self.add_alias(skill.id, alias_raw, "ru", "RAW_ONLY", "SEED", "seed")

    def qualifications_empty(self) -> bool:
        return not self._qualifications

    def relations_empty(self) -> bool:
        return not self._relations

    def seed_qualifications(self, seed: tuple[SeedQualification, ...]) -> None:
        if self._qualifications:
            return
        for item in seed:
            qualification_id = stable_id(item.canonical)
            aliases: list[QualificationAlias] = []
            for index, raw in enumerate((item.canonical, *item.aliases)):
                keys = alias_keys(raw)
                if not keys:
                    continue
                alias_id = str(uuid.uuid4())
                alias = QualificationAlias(
                    alias_id,
                    qualification_id,
                    raw,
                    index == 0,
                    "mixed",
                    "SEED",
                    "AUTO",
                    "seed",
                    now_iso(),
                )
                aliases.append(alias)
                self._qualification_aliases[alias_id] = alias
                for kind, key in keys.items():
                    self._qualification_keys[key] = (alias_id, kind)
            self._qualifications[qualification_id] = Qualification(
                qualification_id, item.canonical, "ACTIVE", item.qualification_type, tuple(aliases)
            )
        self._meta = Meta(
            self._meta.dictionary_version,
            self._meta.embedding_model_id,
            self._meta.config_hash,
            self._meta.pymorphy_version,
            self._meta.homoglyph_rules_version,
            self._meta.qualification_version + 1,
            self._meta.relation_version,
        )

    def seed_relations(self, seed: tuple[SeedRelation, ...]) -> None:
        if self._relations:
            return
        for item in seed:
            left = stable_id(item.left)
            right = stable_id(item.right)
            if left == right:
                continue
            key = (left, right) if left < right else (right, left)
            self._relations[key] = item.kind
        self._meta = Meta(
            self._meta.dictionary_version,
            self._meta.embedding_model_id,
            self._meta.config_hash,
            self._meta.pymorphy_version,
            self._meta.homoglyph_rules_version,
            self._meta.qualification_version,
            self._meta.relation_version + 1,
        )

    def lookup_qualification(self, key: str) -> QualificationAliasRef | None:
        found = self._qualification_keys.get(key)
        if found is None:
            return None
        alias_id, kind = found
        alias = self._qualification_aliases[alias_id]
        qualification = self._qualifications[alias.qualification_id]
        if qualification.status != "ACTIVE":
            return None
        return QualificationAliasRef(alias_id, qualification.id, qualification.canonical_name, kind)

    def get_relations(self, pairs: list[tuple[str, str]]) -> tuple[SkillRelation, ...]:
        result: list[SkillRelation] = []
        for left, right in pairs:
            key = (left, right) if left < right else (right, left)
            kind = self._relations.get(key)
            if kind is not None:
                result.append(SkillRelation(left, right, kind))
        return tuple(result)

    def _require_qualification(self, qualification_id: str) -> Qualification:
        qualification = self._qualifications.get(qualification_id)
        if qualification is None:
            raise NotFound(f"qualification {qualification_id} not found")
        return qualification

    def _qualification_aliases_for(self, qualification_id: str) -> tuple[QualificationAlias, ...]:
        return tuple(
            alias
            for alias in self._qualification_aliases.values()
            if alias.qualification_id == qualification_id
        )

    def _check_qualification_keys(self, qualification_id: str, keys: dict[str, str]) -> None:
        for key in keys.values():
            found = self._qualification_keys.get(key)
            if found is None:
                continue
            alias = self._qualification_aliases[found[0]]
            if alias.qualification_id == qualification_id:
                continue
            owner = self._qualifications[alias.qualification_id]
            raise AliasConflict(key, owner.id, owner.canonical_name)

    def list_qualifications(
        self, query: str, status: str, limit: int, offset: int
    ) -> tuple[list[Qualification], int]:
        items = list(self._qualifications.values())
        if status and status != "QUALIFICATION_STATUS_UNSPECIFIED":
            items = [item for item in items if item.status == status]
        if query:
            lowered = query.strip().lower()
            items = [
                item
                for item in items
                if lowered in item.canonical_name.lower()
                or any(lowered in alias.raw.lower() for alias in item.aliases)
            ]
        items.sort(key=lambda item: item.canonical_name.lower())
        return items[offset : offset + limit], len(items)

    def create_qualification(
        self, canonical_name: str, qualification_type: str, actor: str, source: str
    ) -> Qualification:
        key = normalize_base(canonical_name)
        if not key:
            raise NotFound("canonical name is empty")
        existing = self._qualification_keys.get(key)
        if existing is not None:
            return self._qualifications[self._qualification_aliases[existing[0]].qualification_id]
        qualification = Qualification(
            str(uuid.uuid4()), canonical_name.strip(), "ACTIVE", qualification_type
        )
        self._qualifications[qualification.id] = qualification
        self.add_qualification_alias(
            qualification.id, canonical_name, "mixed", "AUTO", source, actor
        )
        return self._qualifications[qualification.id]

    def add_qualification_alias(
        self,
        qualification_id: str,
        raw: str,
        lang: str,
        lemma_mode: str,
        source: str,
        actor: str,
    ) -> QualificationAlias:
        self._require_qualification(qualification_id)
        keys = alias_keys(raw, lemma_mode)
        if not keys:
            raise NotFound("alias is empty")
        self._check_qualification_keys(qualification_id, keys)
        existing = next(
            (
                alias
                for alias in self._qualification_aliases_for(qualification_id)
                if normalize_base(alias.raw) == normalize_base(raw)
            ),
            None,
        )
        if existing is not None:
            return existing
        alias = QualificationAlias(
            str(uuid.uuid4()),
            qualification_id,
            raw.strip(),
            False,
            lang,
            source,
            lemma_mode,
            actor,
            now_iso(),
        )
        self._qualification_aliases[alias.id] = alias
        for kind, key in keys.items():
            self._qualification_keys[key] = (alias.id, kind)
        qualification = self._qualifications[qualification_id]
        self._qualifications[qualification_id] = replace(
            qualification, aliases=(*qualification.aliases, alias)
        )
        return alias

    def set_canonical_qualification_alias(
        self, qualification_id: str, alias_id: str, actor: str
    ) -> Qualification:
        qualification = self._require_qualification(qualification_id)
        updated_aliases: list[QualificationAlias] = []
        selected: QualificationAlias | None = None
        for alias in self._qualification_aliases_for(qualification_id):
            if alias.id == alias_id:
                selected = replace(alias, is_canonical=True)
                updated_aliases.append(selected)
            else:
                updated_aliases.append(replace(alias, is_canonical=False))
            self._qualification_aliases[updated_aliases[-1].id] = updated_aliases[-1]
        if selected is None:
            raise NotFound(f"qualification alias {alias_id} not found")
        self._qualifications[qualification_id] = replace(
            qualification,
            canonical_name=selected.raw.strip() or qualification.canonical_name,
            aliases=tuple(updated_aliases),
        )
        return self._qualifications[qualification_id]

    def delete_qualification_alias(self, alias_id: str, actor: str) -> None:
        alias = self._qualification_aliases.get(alias_id)
        if alias is None:
            raise NotFound(f"qualification alias {alias_id} not found")
        if alias.is_canonical:
            raise CanonicalRequired("cannot delete canonical qualification alias")
        for key in alias_keys(alias.raw, alias.lemma_mode).values():
            self._qualification_keys.pop(key, None)
        self._qualification_aliases.pop(alias_id, None)
        qualification = self._qualifications[alias.qualification_id]
        self._qualifications[qualification.id] = replace(
            qualification,
            aliases=tuple(item for item in qualification.aliases if item.id != alias_id),
        )

    def list_relations(self, limit: int, offset: int) -> tuple[list[SkillRelation], int]:
        items = [
            SkillRelation(left, right, kind) for (left, right), kind in self._relations.items()
        ]
        items.sort(key=lambda item: (item.left_skill_id, item.right_skill_id))
        return items[offset : offset + limit], len(items)

    def create_relation(self, left: str, right: str, kind: str, actor: str) -> SkillRelation:
        if left == right:
            raise NotFound("relation endpoints must differ")
        if self._skills.get(left) is None or self._skills.get(right) is None:
            raise NotFound("relation skill not found")
        key = (left, right) if left < right else (right, left)
        self._relations[key] = kind
        return SkillRelation(left, right, kind)

    def delete_relation(self, left: str, right: str, actor: str) -> None:
        key = (left, right) if left < right else (right, left)
        if key not in self._relations:
            raise NotFound("relation not found")
        self._relations.pop(key, None)

    # --- чтение -------------------------------------------------------------
    def lookup(self, key: str) -> AliasRef | None:
        found = self._alias_keys.get(key)
        if found is None:
            return None
        alias_id, kind = found
        alias = self._aliases[alias_id]
        skill = self._skills.get(alias.skill_id)
        if skill is None or skill.status != "ACTIVE":
            return None
        return AliasRef(alias_id, skill.id, skill.canonical_name, kind)

    def get_skill(self, skill_id: str) -> Skill | None:
        return self._skills.get(skill_id)

    def get_skills(self, skill_ids: list[str]) -> dict[str, Skill]:
        return {
            skill_id: self._skills[skill_id] for skill_id in skill_ids if skill_id in self._skills
        }

    def list_skills(
        self,
        query: str,
        skill_type: str,
        status: str,
        limit: int,
        offset: int,
    ) -> tuple[list[Skill], int]:
        items = list(self._skills.values())
        if skill_type and skill_type != "SKILL_TYPE_UNSPECIFIED":
            items = [skill for skill in items if skill.skill_type == skill_type]
        if status and status != "SKILL_STATUS_UNSPECIFIED":
            items = [skill for skill in items if skill.status == status]
        if query:
            lowered = query.strip().lower()
            items = [
                skill
                for skill in items
                if lowered in skill.canonical_name.lower()
                or any(lowered in alias.raw.lower() for alias in skill.aliases)
            ]
        items.sort(key=lambda skill: skill.canonical_name.lower())
        return items[offset : offset + limit], len(items)

    # --- запись -------------------------------------------------------------
    def _require_skill(self, skill_id: str) -> Skill:
        skill = self._skills.get(skill_id)
        if skill is None:
            raise NotFound(f"skill {skill_id} not found")
        return skill

    def _check_keys(self, skill_id: str, keys: dict[str, str]) -> None:
        for key in keys.values():
            found = self._alias_keys.get(key)
            if found is None:
                continue
            alias = self._aliases[found[0]]
            if alias.skill_id == skill_id:
                continue
            owner = self._skills[alias.skill_id]
            raise AliasConflict(key, owner.id, owner.canonical_name)

    def create_skill(
        self,
        canonical_name: str,
        skill_type: str,
        actor: str,
        source: str,
        skill_id: str | None = None,
    ) -> Skill:
        key = normalize_base(canonical_name)
        if not key:
            raise NotFound("canonical name is empty")
        existing = self._alias_keys.get(key)
        if existing is not None:
            alias = self._aliases[existing[0]]
            return self._skills[alias.skill_id]
        new_id = skill_id or str(uuid.uuid4())
        self._skills[new_id] = Skill(new_id, canonical_name.strip(), skill_type, "ACTIVE", ())
        alias = self.add_alias(new_id, canonical_name, "mixed", "AUTO", source, actor)
        return self.set_canonical(new_id, alias.id, actor)

    def add_alias(
        self,
        skill_id: str,
        raw: str,
        lang: str,
        lemma_mode: str,
        source: str,
        actor: str,
    ) -> Alias:
        self._require_skill(skill_id)
        keys = alias_keys(raw, lemma_mode)
        if not keys:
            raise NotFound("alias is empty")
        self._check_keys(skill_id, keys)
        existing = next(
            (
                alias
                for alias in self._skills[skill_id].aliases
                if normalize_base(alias.raw) == normalize_base(raw)
            ),
            None,
        )
        if existing is not None:
            return existing
        alias = Alias(
            str(uuid.uuid4()),
            skill_id,
            raw.strip(),
            False,
            lang,
            source,
            lemma_mode,
            actor,
            now_iso(),
        )
        self._aliases[alias.id] = alias
        for kind, key in keys.items():
            self._alias_keys[key] = (alias.id, kind)
        skill = self._skills[skill_id]
        self._skills[skill_id] = Skill(
            skill.id,
            skill.canonical_name,
            skill.skill_type,
            skill.status,
            (*skill.aliases, alias),
        )
        return alias

    def set_canonical(self, skill_id: str, alias_id: str, actor: str) -> Skill:
        skill = self._require_skill(skill_id)
        aliases = []
        found_alias: Alias | None = None
        for alias in skill.aliases:
            if alias.id == alias_id:
                found_alias = alias
                aliases.append(replace(alias, is_canonical=True))
            else:
                aliases.append(replace(alias, is_canonical=False))
        if found_alias is None:
            raise NotFound(f"alias {alias_id} not found")
        canonical_name = found_alias.raw.strip() or skill.canonical_name
        self._skills[skill_id] = Skill(
            skill.id, canonical_name, skill.skill_type, skill.status, tuple(aliases)
        )
        for alias in aliases:
            self._aliases[alias.id] = alias
        return self._skills[skill_id]

    def delete_alias(self, alias_id: str, actor: str) -> None:
        alias = self._aliases.get(alias_id)
        if alias is None:
            raise NotFound(f"alias {alias_id} not found")
        if alias.is_canonical:
            raise CanonicalRequired("cannot delete canonical alias")
        skill = self._skills[alias.skill_id]
        remaining = []
        for item in skill.aliases:
            if item.id == alias_id:
                for key in alias_keys(item.raw, item.lemma_mode).values():
                    self._alias_keys.pop(key, None)
                continue
            remaining.append(item)
        self._aliases.pop(alias_id, None)
        self._skills[skill.id] = Skill(
            skill.id, skill.canonical_name, skill.skill_type, skill.status, tuple(remaining)
        )

    def deprecate_skill(self, skill_id: str, actor: str) -> Skill:
        skill = self._require_skill(skill_id)
        self._skills[skill_id] = Skill(
            skill.id, skill.canonical_name, skill.skill_type, "DEPRECATED", skill.aliases
        )
        return self._skills[skill_id]

    def delete_skill(self, skill_id: str, actor: str) -> None:
        skill = self._require_skill(skill_id)
        for alias in skill.aliases:
            for key in alias_keys(alias.raw, alias.lemma_mode).values():
                self._alias_keys.pop(key, None)
            self._aliases.pop(alias.id, None)
        self._skills.pop(skill_id, None)

    # --- очередь ревью ------------------------------------------------------
    def enqueue_review(self, key: str, raw: str, source: str) -> ReviewItem:
        existing_id = self._review_by_key.get(key)
        if existing_id is not None:
            item = self._review[existing_id]
            samples = tuple(dict.fromkeys((*item.sample_masked, raw)))[:3]
            updated = ReviewItem(
                item.id,
                item.key,
                samples,
                item.occurrences + 1,
                item.status,
                item.suggested_skill_id,
                item.suggested_confidence,
                item.candidates,
                item.resolved_skill_id,
                item.resolved_by,
                item.resolved_at,
                item.first_seen,
                now_iso(),
            )
            self._review[existing_id] = updated
            return updated
        candidates = self._suggest(key)
        item = ReviewItem(
            str(uuid.uuid4()),
            key,
            (raw,),
            1,
            "PENDING",
            candidates[0].skill_id if candidates else None,
            candidates[0].confidence if candidates else None,
            candidates,
            None,
            None,
            None,
            now_iso(),
            now_iso(),
        )
        self._review[item.id] = item
        self._review_by_key[key] = item.id
        return item

    def _suggest(self, key: str, limit: int = 3) -> tuple[Candidate, ...]:
        scored: list[Candidate] = []
        for skill in self._skills.values():
            if skill.status != "ACTIVE":
                continue
            names = [skill.canonical_name, *(alias.raw for alias in skill.aliases)]
            best = max(difflib.SequenceMatcher(None, key, name.lower()).ratio() for name in names)
            if best >= 0.6:
                scored.append(Candidate(skill.id, skill.canonical_name, round(best, 3)))
        scored.sort(key=lambda item: (-item.confidence, item.canonical_name))
        return tuple(scored[:limit])

    def list_review(self, status: str, limit: int, offset: int) -> tuple[list[ReviewItem], int]:
        items = list(self._review.values())
        if status and status != "REVIEW_STATUS_UNSPECIFIED":
            items = [item for item in items if item.status == status]
        items.sort(key=lambda item: item.first_seen)
        return items[offset : offset + limit], len(items)

    def get_review(self, review_id: str) -> ReviewItem | None:
        return self._review.get(review_id)

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
        item = self._review.get(review_id)
        if item is None:
            raise NotFound(f"review {review_id} not found")
        skill: Skill | None = None
        status = item.status
        if action == "ALIAS":
            if not skill_id:
                raise NotFound("skill_id is required for ALIAS")
            self.add_alias(skill_id, item.sample_masked[0], "mixed", "AUTO", "HR", actor)
            for alias in additional_aliases:
                self.add_alias(skill_id, alias, "mixed", "AUTO", "HR", actor)
            status = "RESOLVED_ALIAS"
            skill = self._skills[skill_id]
        elif action == "NEW_SKILL":
            skill = self.create_skill(
                canonical_name or item.sample_masked[0],
                skill_type or "OTHER",
                actor,
                "HR",
            )
            for alias in additional_aliases:
                self.add_alias(skill.id, alias, "mixed", "AUTO", "HR", actor)
            status = "RESOLVED_NEW_SKILL"
            skill = self._skills[skill.id]
        elif action == "REJECT":
            status = "REJECTED"
        updated = ReviewItem(
            item.id,
            item.key,
            item.sample_masked,
            item.occurrences,
            status,
            item.suggested_skill_id,
            item.suggested_confidence,
            item.candidates,
            skill.id if skill else None,
            actor,
            now_iso(),
            item.first_seen,
            item.last_seen,
        )
        self._review[review_id] = updated
        return updated, skill

    # --- снимки -------------------------------------------------------------
    def export_state(self) -> dict:
        return {
            "meta": {
                "dictionary_version": self._meta.dictionary_version,
                "embedding_model_id": self._meta.embedding_model_id,
                "config_hash": self._meta.config_hash,
                "pymorphy_version": self._meta.pymorphy_version,
                "homoglyph_rules_version": self._meta.homoglyph_rules_version,
                "qualification_version": self._meta.qualification_version,
                "relation_version": self._meta.relation_version,
            },
            "skills": [skill.to_dict() for skill in self._skills.values()],
            "alias_keys": {key: list(value) for key, value in self._alias_keys.items()},
            "qualifications": [
                {
                    "id": item.id,
                    "canonical_name": item.canonical_name,
                    "status": item.status,
                    "qualification_type": item.qualification_type,
                    "aliases": [asdict(alias) for alias in item.aliases],
                }
                for item in self._qualifications.values()
            ],
            "qualification_keys": {
                key: [value[0], value[1]] for key, value in self._qualification_keys.items()
            },
            "relations": [[left, right, kind] for (left, right), kind in self._relations.items()],
        }

    @classmethod
    def from_state(cls, payload: dict) -> InMemoryStore:
        store = cls()
        meta = payload["meta"]
        store._meta = Meta(
            meta["dictionary_version"],
            meta["embedding_model_id"],
            meta["config_hash"],
            meta["pymorphy_version"],
            meta["homoglyph_rules_version"],
            meta.get("qualification_version", 1),
            meta.get("relation_version", 1),
        )
        for skill_data in payload["skills"]:
            aliases = tuple(
                Alias(
                    alias["id"],
                    alias["skill_id"],
                    alias["raw"],
                    alias["is_canonical"],
                    alias["lang"],
                    alias["source"],
                    alias["lemma_mode"],
                    alias["created_by"],
                    alias["created_at"],
                )
                for alias in skill_data["aliases"]
            )
            skill = Skill(
                skill_data["id"],
                skill_data["canonical_name"],
                skill_data["skill_type"],
                skill_data["status"],
                aliases,
            )
            store._skills[skill.id] = skill
            for alias in aliases:
                store._aliases[alias.id] = alias
        for key, value in payload["alias_keys"].items():
            store._alias_keys[key] = (value[0], value[1])
        for item in payload.get("qualifications", []):
            aliases = tuple(
                QualificationAlias(
                    alias["id"],
                    alias["qualification_id"],
                    alias["raw"],
                    alias["is_canonical"],
                    alias["lang"],
                    alias["source"],
                    alias["lemma_mode"],
                    alias["created_by"],
                    alias["created_at"],
                )
                for alias in item.get("aliases", [])
            )
            store._qualifications[item["id"]] = Qualification(
                item["id"],
                item["canonical_name"],
                item.get("status", "ACTIVE"),
                item.get("qualification_type"),
                aliases,
            )
            for alias in aliases:
                store._qualification_aliases[alias.id] = alias
        for key, value in payload.get("qualification_keys", {}).items():
            store._qualification_keys[key] = (value[0], value[1])
        for left, right, kind in payload.get("relations", []):
            store._relations[(left, right)] = kind
        return store

    def dump(self) -> str:
        return json.dumps(self.export_state(), ensure_ascii=False, sort_keys=True)


def key_priority() -> tuple[str, ...]:
    return _KIND_ORDER


def raw_keys(raw: str) -> list[tuple[str, str]]:
    """Ключи запроса в порядке приоритета с именем способа."""
    raw_key, folded, lemma, lemma_folded = keys_for_raw(raw)
    result = [
        ("RAW", raw_key),
        ("FOLDED", folded),
        ("LEMMA", lemma),
        ("LEMMA_FOLDED", lemma_folded),
    ]
    seen: set[str] = set()
    ordered: list[tuple[str, str]] = []
    for kind, key in result:
        if key and key not in seen:
            seen.add(key)
            ordered.append((kind, key))
    return ordered
