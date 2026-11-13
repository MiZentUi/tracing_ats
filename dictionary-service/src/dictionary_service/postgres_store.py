"""PostgreSQL-хранилище словаря."""

from __future__ import annotations

import difflib
import json
import uuid
from dataclasses import asdict

from psycopg_pool import ConnectionPool

from .domain import (
    Alias,
    AliasConflict,
    AliasRef,
    Candidate,
    CanonicalRequired,
    Meta,
    NotFound,
    QualificationAliasRef,
    ReviewItem,
    Skill,
    SkillRelation,
)
from .normalization import alias_keys, normalize_base
from .seed import SeedQualification, SeedRelation
from .store import stable_id


class PostgresStore:
    def __init__(self, database_url: str) -> None:
        # Схему накатывает Liquibase (миграции 020–023).
        self._pool = ConnectionPool(
            database_url,
            min_size=1,
            max_size=8,
            kwargs={"options": "-c search_path=dictionary"},
            open=True,
        )

    # --- метаданные ---------------------------------------------------------
    def meta(self) -> Meta:
        with self._pool.connection() as conn:
            row = conn.execute(
                "SELECT dictionary_version, embedding_model_id, config_hash, "
                "pymorphy_version, homoglyph_rules_version, qualification_version, "
                "relation_version FROM dictionary_meta WHERE id = 1"
            ).fetchone()
        return Meta(*row)

    def bump_version(self) -> Meta:
        with self._pool.connection() as conn:
            conn.execute(
                "UPDATE dictionary_meta SET dictionary_version = dictionary_version + 1 "
                "WHERE id = 1"
            )
            conn.commit()
        return self.meta()

    def save_snapshot(self, resolver_version: str, payload: dict) -> None:
        with self._pool.connection() as conn:
            conn.execute(
                "INSERT INTO dictionary_snapshot (resolver_version, payload) VALUES (%s, %s) "
                "ON CONFLICT (resolver_version) DO UPDATE SET payload = EXCLUDED.payload",
                (resolver_version, json.dumps(payload, ensure_ascii=False)),
            )
            conn.commit()

    def get_snapshot(self, resolver_version: str) -> dict | None:
        with self._pool.connection() as conn:
            row = conn.execute(
                "SELECT payload FROM dictionary_snapshot WHERE resolver_version = %s",
                (resolver_version,),
            ).fetchone()
        return row[0] if row else None

    # --- чтение -------------------------------------------------------------
    def lookup(self, key: str) -> AliasRef | None:
        with self._pool.connection() as conn:
            row = conn.execute(
                """
                SELECT a.id, a.skill_id, s.id, s.status, s.skill_type, k.kind,
                       (SELECT alias_raw FROM skill_alias
                        WHERE skill_id = s.id AND is_canonical LIMIT 1) AS canonical_name
                FROM skill_alias_key k
                JOIN skill_alias a ON a.id = k.alias_id
                JOIN skill s ON s.id = a.skill_id
                WHERE k.key = %s
                """,
                (key,),
            ).fetchone()
        if row is None or row[3] != "ACTIVE":
            return None
        return AliasRef(str(row[0]), str(row[1]), row[6] or "", row[5])

    def _skill(self, conn, skill_id: str) -> Skill | None:
        row = conn.execute(
            "SELECT id, status, skill_type FROM skill WHERE id = %s", (skill_id,)
        ).fetchone()
        if row is None:
            return None
        aliases = conn.execute(
            "SELECT id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, "
            "created_by, created_at FROM skill_alias WHERE skill_id = %s ORDER BY created_at",
            (skill_id,),
        ).fetchall()
        alias_rows = tuple(_alias(item) for item in aliases)
        canonical = next((item.raw for item in alias_rows if item.is_canonical), "")
        return Skill(str(row[0]), canonical, row[2], row[1], alias_rows)

    def get_skill(self, skill_id: str) -> Skill | None:
        with self._pool.connection() as conn:
            return self._skill(conn, skill_id)

    def get_skills(self, skill_ids: list[str]) -> dict[str, Skill]:
        if not skill_ids:
            return {}
        with self._pool.connection() as conn:
            rows = conn.execute(
                "SELECT id FROM skill WHERE id = ANY(%s::uuid[])", (skill_ids,)
            ).fetchall()
            return {str(row[0]): self._skill(conn, str(row[0])) for row in rows}

    def list_skills(
        self,
        query: str,
        skill_type: str,
        status: str,
        limit: int,
        offset: int,
    ) -> tuple[list[Skill], int]:
        clauses = []
        params: list = []
        if skill_type and skill_type != "SKILL_TYPE_UNSPECIFIED":
            clauses.append("s.skill_type = %s")
            params.append(skill_type)
        if status and status != "SKILL_STATUS_UNSPECIFIED":
            clauses.append("s.status = %s")
            params.append(status)
        if query:
            clauses.append("(s.id IN (SELECT skill_id FROM skill_alias WHERE alias_raw ILIKE %s))")
            params.append(f"%{query}%")
        where = (" WHERE " + " AND ".join(clauses)) if clauses else ""
        with self._pool.connection() as conn:
            total = conn.execute(f"SELECT count(*) FROM skill s{where}", params).fetchone()[0]
            rows = conn.execute(
                f"SELECT s.id FROM skill s{where} ORDER BY s.id LIMIT %s OFFSET %s",
                [*params, limit, offset],
            ).fetchall()
            items = [self._skill(conn, str(row[0])) for row in rows]
        return [item for item in items if item is not None], total

    # --- запись -------------------------------------------------------------
    def _find_by_key(self, conn, key: str) -> Skill | None:
        row = conn.execute(
            "SELECT a.skill_id FROM skill_alias_key k "
            "JOIN skill_alias a ON a.id = k.alias_id WHERE k.key = %s",
            (key,),
        ).fetchone()
        if row is None:
            return None
        return self._skill(conn, str(row[0]))

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
        with self._pool.connection() as conn, conn.transaction():
            existing = None
            for key in alias_keys(canonical_name).values():
                existing = self._find_by_key(conn, key)
                if existing is not None:
                    return existing
            new_id = skill_id or str(uuid.uuid4())
            conn.execute("INSERT INTO skill (id, skill_type) VALUES (%s, %s)", (new_id, skill_type))
            self._insert_alias(
                conn, new_id, canonical_name, "mixed", "AUTO", source, actor, canonical=True
            )
            skill = self._skill(conn, new_id)
        if skill is None:  # pragma: no cover - защита от гонки
            raise NotFound("skill was not created")
        return skill

    def add_alias(
        self,
        skill_id: str,
        raw: str,
        lang: str,
        lemma_mode: str,
        source: str,
        actor: str,
    ) -> Alias:
        with self._pool.connection() as conn, conn.transaction():
            if self._skill(conn, skill_id) is None:
                raise NotFound(f"skill {skill_id} not found")
            existing = conn.execute(
                "SELECT id FROM skill_alias WHERE skill_id = %s AND alias_raw ILIKE %s",
                (skill_id, raw.strip()),
            ).fetchone()
            if existing is not None:
                return _alias_by_id(conn, str(existing[0]))
            keys = alias_keys(raw, lemma_mode)
            if not keys:
                raise NotFound("alias is empty")
            matched_alias: str | None = None
            for key in keys.values():
                row = conn.execute(
                    "SELECT a.skill_id, a.id FROM skill_alias_key k "
                    "JOIN skill_alias a ON a.id = k.alias_id WHERE k.key = %s",
                    (key,),
                ).fetchone()
                if row is None:
                    continue
                if str(row[0]) != skill_id:
                    owner = self._skill(conn, str(row[0]))
                    raise AliasConflict(
                        key,
                        str(row[0]),
                        owner.canonical_name if owner else str(row[0]),
                    )
                matched_alias = str(row[1])
            if matched_alias is not None:
                return _alias_by_id(conn, matched_alias)
            alias = self._insert_alias(
                conn, skill_id, raw, lang, lemma_mode, source, actor, canonical=False
            )
        return alias

    def _insert_alias(
        self,
        conn,
        skill_id: str,
        raw: str,
        lang: str,
        lemma_mode: str,
        source: str,
        actor: str,
        *,
        canonical: bool,
    ) -> Alias:
        alias_id = str(uuid.uuid4())
        for key in alias_keys(raw, lemma_mode).values():
            row = conn.execute(
                "SELECT a.skill_id FROM skill_alias_key k "
                "JOIN skill_alias a ON a.id = k.alias_id WHERE k.key = %s",
                (key,),
            ).fetchone()
            if row is not None and str(row[0]) != skill_id:
                owner = self._skill(conn, str(row[0]))
                raise AliasConflict(
                    key, str(row[0]), owner.canonical_name if owner else str(row[0])
                )
        row = conn.execute(
            "INSERT INTO skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, "
            "lemma_mode, created_by) VALUES (%s, %s, %s, %s, %s, %s, %s, %s) "
            "RETURNING created_at",
            (alias_id, skill_id, raw.strip(), canonical, lang, source, lemma_mode, actor),
        ).fetchone()
        for kind, key in alias_keys(raw, lemma_mode).items():
            conn.execute(
                "INSERT INTO skill_alias_key (key, alias_id, kind) VALUES (%s, %s, %s)",
                (key, alias_id, kind),
            )
        return Alias(
            alias_id,
            skill_id,
            raw.strip(),
            canonical,
            lang,
            source,
            lemma_mode,
            actor,
            row[0].isoformat(),
        )

    def set_canonical(self, skill_id: str, alias_id: str, actor: str) -> Skill:
        with self._pool.connection() as conn, conn.transaction():
            alias = conn.execute(
                "SELECT id, alias_raw, is_canonical FROM skill_alias "
                "WHERE id = %s AND skill_id = %s",
                (alias_id, skill_id),
            ).fetchone()
            if alias is None:
                raise NotFound(f"alias {alias_id} not found")
            if alias[2]:
                skill = self._skill(conn, skill_id)
            else:
                conn.execute(
                    "UPDATE skill_alias SET is_canonical = false WHERE skill_id = %s", (skill_id,)
                )
                conn.execute(
                    "UPDATE skill_alias SET is_canonical = true WHERE id = %s", (alias_id,)
                )
                skill = self._skill(conn, skill_id)
        if skill is None:  # pragma: no cover
            raise NotFound("skill not found")
        return skill

    def delete_alias(self, alias_id: str, actor: str) -> None:
        with self._pool.connection() as conn, conn.transaction():
            row = conn.execute(
                "SELECT is_canonical FROM skill_alias WHERE id = %s", (alias_id,)
            ).fetchone()
            if row is None:
                raise NotFound(f"alias {alias_id} not found")
            if row[0]:
                raise CanonicalRequired("cannot delete canonical alias")
            conn.execute("DELETE FROM skill_alias WHERE id = %s", (alias_id,))

    def deprecate_skill(self, skill_id: str, actor: str) -> Skill:
        with self._pool.connection() as conn, conn.transaction():
            conn.execute(
                "UPDATE skill SET status = 'DEPRECATED', updated_at = now() WHERE id = %s",
                (skill_id,),
            )
            skill = self._skill(conn, skill_id)
        if skill is None:
            raise NotFound(f"skill {skill_id} not found")
        return skill

    def delete_skill(self, skill_id: str, actor: str) -> None:
        with self._pool.connection() as conn, conn.transaction():
            conn.execute("DELETE FROM skill WHERE id = %s", (skill_id,))

    # --- квалификации и отношения ------------------------------------------
    def qualifications_empty(self) -> bool:
        with self._pool.connection() as conn:
            return conn.execute("SELECT count(*) FROM qualification").fetchone()[0] == 0

    def relations_empty(self) -> bool:
        with self._pool.connection() as conn:
            return conn.execute("SELECT count(*) FROM skill_relation").fetchone()[0] == 0

    def seed_qualifications(self, seed: tuple[SeedQualification, ...]) -> None:
        if not self.qualifications_empty():
            return
        with self._pool.connection() as conn, conn.transaction():
            for item in seed:
                qualification_id = stable_id(item.canonical)
                conn.execute(
                    "INSERT INTO qualification (id, canonical_name, qualification_type) "
                    "VALUES (%s, %s, %s) ON CONFLICT (id) DO NOTHING",
                    (qualification_id, item.canonical, item.qualification_type),
                )
                for index, raw in enumerate((item.canonical, *item.aliases)):
                    keys = alias_keys(raw)
                    if not keys:
                        continue
                    alias_id = str(uuid.uuid4())
                    conn.execute(
                        "INSERT INTO qualification_alias "
                        "(id, qualification_id, alias_raw, is_canonical, lang, source, "
                        "lemma_mode, created_by) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)",
                        (
                            alias_id,
                            qualification_id,
                            raw,
                            index == 0,
                            "mixed",
                            "SEED",
                            "AUTO",
                            "seed",
                        ),
                    )
                    for kind, key in keys.items():
                        conn.execute(
                            "INSERT INTO qualification_alias_key (key, alias_id, kind) "
                            "VALUES (%s, %s, %s) ON CONFLICT (key) DO NOTHING",
                            (key, alias_id, kind),
                        )
            conn.execute(
                "UPDATE dictionary_meta SET qualification_version = qualification_version + 1 "
                "WHERE id = 1"
            )

    def seed_relations(self, seed: tuple[SeedRelation, ...]) -> None:
        if not self.relations_empty():
            return
        with self._pool.connection() as conn, conn.transaction():
            for item in seed:
                left = stable_id(item.left)
                right = stable_id(item.right)
                if left == right:
                    continue
                if left > right:
                    left, right = right, left
                conn.execute(
                    "INSERT INTO skill_relation (left_skill_id, right_skill_id, relation_kind) "
                    "VALUES (%s, %s, %s) ON CONFLICT DO NOTHING",
                    (left, right, item.kind),
                )
            conn.execute(
                "UPDATE dictionary_meta SET relation_version = relation_version + 1 WHERE id = 1"
            )

    def lookup_qualification(self, key: str) -> QualificationAliasRef | None:
        with self._pool.connection() as conn:
            row = conn.execute(
                """
                SELECT a.id, a.qualification_id, q.canonical_name, q.status, k.kind
                FROM qualification_alias_key k
                JOIN qualification_alias a ON a.id = k.alias_id
                JOIN qualification q ON q.id = a.qualification_id
                WHERE k.key = %s
                """,
                (key,),
            ).fetchone()
        if row is None or row[3] != "ACTIVE":
            return None
        return QualificationAliasRef(str(row[0]), str(row[1]), row[2], row[4])

    def get_relations(self, pairs: list[tuple[str, str]]) -> tuple[SkillRelation, ...]:
        if not pairs:
            return ()
        clauses: list[str] = []
        params: list[str] = []
        for left, right in pairs:
            if left > right:
                left, right = right, left
            clauses.append("(left_skill_id = %s AND right_skill_id = %s)")
            params.extend([left, right])
        with self._pool.connection() as conn:
            rows = conn.execute(
                "SELECT left_skill_id, right_skill_id, relation_kind FROM skill_relation WHERE "
                + " OR ".join(clauses),
                params,
            ).fetchall()
        return tuple(SkillRelation(str(row[0]), str(row[1]), row[2]) for row in rows)

    # --- очередь ревью ------------------------------------------------------
    def enqueue_review(self, key: str, raw: str, source: str) -> ReviewItem:
        with self._pool.connection() as conn, conn.transaction():
            row = conn.execute(
                "SELECT id, occurrences, sample_masked FROM review_item WHERE key = %s", (key,)
            ).fetchone()
            if row is not None:
                samples = list(dict.fromkeys([*row[2], raw]))[:3]
                conn.execute(
                    "UPDATE review_item SET occurrences = occurrences + 1, sample_masked = %s, "
                    "last_seen = now() WHERE id = %s",
                    (samples, row[0]),
                )
                return self._review(conn, str(row[0]))
            candidates = self._suggest(conn, key)
            item_id = str(uuid.uuid4())
            conn.execute(
                "INSERT INTO review_item (id, key, sample_masked, candidates, suggested_skill_id, "
                "suggested_confidence) VALUES (%s, %s, %s, %s, %s, %s)",
                (
                    item_id,
                    key,
                    [raw],
                    json.dumps([asdict(candidate) for candidate in candidates], ensure_ascii=False),
                    candidates[0].skill_id if candidates else None,
                    candidates[0].confidence if candidates else None,
                ),
            )
            return self._review(conn, item_id)

    def _suggest(self, conn, key: str, limit: int = 3) -> tuple[Candidate, ...]:
        rows = conn.execute(
            "SELECT s.id, a.alias_raw, "
            "(SELECT alias_raw FROM skill_alias "
            " WHERE skill_id = s.id AND is_canonical LIMIT 1) AS canonical_name "
            "FROM skill s JOIN skill_alias a ON a.skill_id = s.id "
            "WHERE s.status = 'ACTIVE'"
        ).fetchall()
        scored: dict[str, tuple[float, str]] = {}
        for skill_id, raw, canonical in rows:
            ratio = difflib.SequenceMatcher(None, key, normalize_base(raw)).ratio()
            current = scored.get(str(skill_id))
            if current is None or ratio > current[0]:
                scored[str(skill_id)] = (ratio, canonical or raw)
        result = [
            Candidate(skill_id, name, round(ratio, 3))
            for skill_id, (ratio, name) in scored.items()
            if ratio >= 0.6
        ]
        result.sort(key=lambda item: (-item.confidence, item.canonical_name))
        return tuple(result[:limit])

    def _review(self, conn, review_id: str) -> ReviewItem:
        row = conn.execute(
            "SELECT id, key, sample_masked, occurrences, status, suggested_skill_id, "
            "suggested_confidence, candidates, resolved_skill_id, resolved_by, resolved_at, "
            "first_seen, last_seen FROM review_item WHERE id = %s",
            (review_id,),
        ).fetchone()
        candidates = tuple(Candidate(**item) for item in (row[7] or []))
        return ReviewItem(
            str(row[0]),
            row[1],
            tuple(row[2] or ()),
            row[3],
            row[4],
            str(row[5]) if row[5] else None,
            row[6],
            candidates,
            str(row[8]) if row[8] else None,
            row[9],
            row[10].isoformat() if row[10] else None,
            row[11].isoformat(),
            row[12].isoformat(),
        )

    def list_review(self, status: str, limit: int, offset: int) -> tuple[list[ReviewItem], int]:
        where = ""
        params: list = []
        if status and status != "REVIEW_STATUS_UNSPECIFIED":
            where = " WHERE status = %s"
            params.append(status)
        with self._pool.connection() as conn:
            total = conn.execute(f"SELECT count(*) FROM review_item{where}", params).fetchone()[0]
            rows = conn.execute(
                f"SELECT id FROM review_item{where} ORDER BY first_seen LIMIT %s OFFSET %s",
                [*params, limit, offset],
            ).fetchall()
            return [self._review(conn, str(row[0])) for row in rows], total

    def get_review(self, review_id: str) -> ReviewItem | None:
        with self._pool.connection() as conn:
            row = conn.execute("SELECT id FROM review_item WHERE id = %s", (review_id,)).fetchone()
            return self._review(conn, review_id) if row else None

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
        item = self.get_review(review_id)
        if item is None:
            raise NotFound(f"review {review_id} not found")
        skill: Skill | None = None
        status = item.status
        if action == "ALIAS":
            if not skill_id:
                raise NotFound("skill_id is required for ALIAS")
            self.add_alias(skill_id, item.sample_masked[0], "mixed", "AUTO", "HR", actor)
            for raw in additional_aliases:
                self.add_alias(skill_id, raw, "mixed", "AUTO", "HR", actor)
            status = "RESOLVED_ALIAS"
            skill = self.get_skill(skill_id)
        elif action == "NEW_SKILL":
            skill = self.create_skill(
                canonical_name or item.sample_masked[0], skill_type or "OTHER", actor, "HR"
            )
            for raw in additional_aliases:
                self.add_alias(skill.id, raw, "mixed", "AUTO", "HR", actor)
            status = "RESOLVED_NEW_SKILL"
            skill = self.get_skill(skill.id)
        elif action == "REJECT":
            status = "REJECTED"
        with self._pool.connection() as conn:
            conn.execute(
                "UPDATE review_item SET status = %s, resolved_skill_id = %s, resolved_by = %s, "
                "resolved_at = now() WHERE id = %s",
                (status, skill.id if skill else None, actor, review_id),
            )
            conn.commit()
            item = self._review(conn, review_id)
        return item, skill

    # --- снимок -------------------------------------------------------------
    def export_state(self) -> dict:
        with self._pool.connection() as conn:
            skills = conn.execute("SELECT id FROM skill").fetchall()
            items = [self._skill(conn, str(row[0])) for row in skills]
            keys = conn.execute("SELECT key, alias_id, kind FROM skill_alias_key").fetchall()
            qualifications = conn.execute(
                "SELECT id, canonical_name, status, qualification_type FROM qualification"
            ).fetchall()
            qualification_aliases = conn.execute(
                "SELECT id, qualification_id, alias_raw FROM qualification_alias"
            ).fetchall()
            qualification_keys = conn.execute(
                "SELECT key, alias_id, kind FROM qualification_alias_key"
            ).fetchall()
            relations = conn.execute(
                "SELECT left_skill_id, right_skill_id, relation_kind FROM skill_relation"
            ).fetchall()
        meta = self.meta()
        return {
            "meta": {
                "dictionary_version": meta.dictionary_version,
                "embedding_model_id": meta.embedding_model_id,
                "config_hash": meta.config_hash,
                "pymorphy_version": meta.pymorphy_version,
                "homoglyph_rules_version": meta.homoglyph_rules_version,
                "qualification_version": meta.qualification_version,
                "relation_version": meta.relation_version,
            },
            "skills": [item.to_dict() for item in items if item is not None],
            "alias_keys": {row[0]: [str(row[1]), row[2]] for row in keys},
            "qualifications": [
                {
                    "id": str(row[0]),
                    "canonical_name": row[1],
                    "status": row[2],
                    "qualification_type": row[3],
                }
                for row in qualifications
            ],
            "qualification_aliases": {
                str(row[0]): [str(row[1]), row[2]] for row in qualification_aliases
            },
            "qualification_keys": {row[0]: [str(row[1]), row[2]] for row in qualification_keys},
            "relations": [[str(row[0]), str(row[1]), row[2]] for row in relations],
        }


def _alias(row) -> Alias:
    return Alias(
        str(row[0]),
        str(row[1]),
        row[2],
        row[3],
        row[4],
        row[5],
        row[6],
        row[7],
        row[8].isoformat() if row[8] else "",
    )


def _alias_by_id(conn, alias_id: str) -> Alias:
    row = conn.execute(
        "SELECT id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, "
        "created_by, created_at FROM skill_alias WHERE id = %s",
        (alias_id,),
    ).fetchone()
    return _alias(row)
