"""gRPC-фасад dictionary-service."""

from __future__ import annotations

import logging

import grpc
from google.protobuf import empty_pb2

from dictionary_service.v1 import dictionary_pb2 as pb
from dictionary_service.v1 import dictionary_pb2_grpc as pb_grpc

from .config import Settings
from .domain import AliasConflict, CanonicalRequired, NotFound, ReviewItem, Skill
from .service import (
    ApplyAliasInput,
    ApplySkillInput,
    DictionaryService,
    ResolveInput,
    ResolveQualificationInput,
)

logger = logging.getLogger(__name__)

_SKILL_TYPE_TO_PROTO = {
    "TECH": pb.SKILL_TYPE_TECH,
    "TOOL": pb.SKILL_TYPE_TOOL,
    "DOMAIN": pb.SKILL_TYPE_DOMAIN,
    "SOFT": pb.SKILL_TYPE_SOFT,
    "OTHER": pb.SKILL_TYPE_OTHER,
}
_SKILL_TYPE_FROM_PROTO = {value: key for key, value in _SKILL_TYPE_TO_PROTO.items()}
_STATUS_TO_PROTO = {
    "ACTIVE": pb.SKILL_STATUS_ACTIVE,
    "DEPRECATED": pb.SKILL_STATUS_DEPRECATED,
}
_SOURCE_FROM_PROTO = {
    pb.RESOLVE_SOURCE_VACANCY: "VACANCY",
    pb.RESOLVE_SOURCE_RESUME: "RESUME",
    pb.RESOLVE_SOURCE_ADMIN: "ADMIN",
}
_METHOD_TO_PROTO = {
    "ALIAS_EXACT": pb.RESOLVE_METHOD_ALIAS_EXACT,
    "ALIAS_FOLDED": pb.RESOLVE_METHOD_ALIAS_FOLDED,
    "ALIAS_LEMMA": pb.RESOLVE_METHOD_ALIAS_LEMMA,
    "ALIAS_LEMMA_FOLDED": pb.RESOLVE_METHOD_ALIAS_LEMMA_FOLDED,
    "NEW": pb.RESOLVE_METHOD_NEW,
    "EMBEDDING": pb.RESOLVE_METHOD_EMBEDDING,
}
_ALIAS_SOURCE_TO_PROTO = {
    "SEED": pb.ALIAS_SOURCE_SEED,
    "HR": pb.ALIAS_SOURCE_HR,
    "LLM": pb.ALIAS_SOURCE_LLM,
    "EMBEDDING_CONFIRMED": pb.ALIAS_SOURCE_EMBEDDING_CONFIRMED,
}
_LEMMA_MODE_TO_PROTO = {"AUTO": pb.LEMMA_MODE_AUTO, "RAW_ONLY": pb.LEMMA_MODE_RAW_ONLY}
_RELATION_TO_PROTO = {
    "SAME_GROUP": pb.RELATION_KIND_SAME_GROUP,
    "PARENT": pb.RELATION_KIND_PARENT,
    "CHILD": pb.RELATION_KIND_CHILD,
    "RELATED": pb.RELATION_KIND_RELATED,
}
_REVIEW_STATUS_TO_PROTO = {
    "PENDING": pb.REVIEW_STATUS_PENDING,
    "RESOLVED_ALIAS": pb.REVIEW_STATUS_RESOLVED_ALIAS,
    "RESOLVED_NEW_SKILL": pb.REVIEW_STATUS_RESOLVED_NEW_SKILL,
    "REJECTED": pb.REVIEW_STATUS_REJECTED,
}
_REVIEW_STATUS_FROM_PROTO = {value: key for key, value in _REVIEW_STATUS_TO_PROTO.items()}
_REVIEW_ACTION_FROM_PROTO = {
    pb.REVIEW_ACTION_ALIAS: "ALIAS",
    pb.REVIEW_ACTION_NEW_SKILL: "NEW_SKILL",
    pb.REVIEW_ACTION_REJECT: "REJECT",
}


class DictionaryServicer(pb_grpc.DictionaryServiceServicer):
    def __init__(self, service: DictionaryService, settings: Settings) -> None:
        self._service = service
        self._settings = settings

    # --- Resolve ------------------------------------------------------------
    def Resolve(self, request: pb.ResolveBatchRequest, context: grpc.ServicerContext):
        if len(request.items) > self._settings.max_batch:
            context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                f"TOO_MANY_ITEMS|max={self._settings.max_batch}",
            )
        raw_items = []
        for index, item in enumerate(request.items):
            raw_name = item.raw_name.strip()
            if not raw_name:
                context.abort(grpc.StatusCode.INVALID_ARGUMENT, "EMPTY_NAME")
            if len(raw_name) > 256:
                context.abort(grpc.StatusCode.INVALID_ARGUMENT, "NAME_TOO_LONG")
            raw_items.append(
                ResolveInput(
                    key=item.key or str(index),
                    raw_name=raw_name,
                    lang_hint=item.lang_hint,
                    source=_SOURCE_FROM_PROTO.get(item.source, "ADMIN"),
                )
            )
        try:
            version, resolved_items = self._service.resolve(
                raw_items, request.as_of_resolver_version, request.skip_review
            )
        except NotFound as error:
            context.abort(grpc.StatusCode.NOT_FOUND, f"VERSION_NOT_FOUND|{error}")
        response = pb.ResolveBatchResponse(resolver_version=version)
        for item, resolved in zip(raw_items, resolved_items, strict=True):
            message = response.items.add(
                key=item.key,
                raw_name=resolved.raw_name,
                normalized_key=resolved.normalized_key,
                folded_key=resolved.folded_key,
                canonical_name=resolved.canonical_name,
                method=_METHOD_TO_PROTO.get(resolved.method, pb.RESOLVE_METHOD_NEW),
                confidence=resolved.confidence,
            )
            if resolved.skill_id:
                message.skill_id = resolved.skill_id
            for candidate in resolved.candidates:
                message.candidates.add(
                    skill_id=candidate.skill_id,
                    canonical_name=candidate.canonical_name,
                    confidence=candidate.confidence,
                )
        return response

    def ResolveQualifications(
        self,
        request: pb.ResolveQualificationsRequest,
        context: grpc.ServicerContext,
    ):
        if len(request.items) > self._settings.max_batch:
            context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                f"TOO_MANY_ITEMS|max={self._settings.max_batch}",
            )
        raw_items = []
        for index, item in enumerate(request.items):
            raw_label = item.raw_label.strip()
            if not raw_label:
                context.abort(grpc.StatusCode.INVALID_ARGUMENT, "EMPTY_NAME")
            if len(raw_label) > 256:
                context.abort(grpc.StatusCode.INVALID_ARGUMENT, "NAME_TOO_LONG")
            raw_items.append(
                ResolveQualificationInput(
                    key=item.key or str(index),
                    raw_label=raw_label,
                    source=_SOURCE_FROM_PROTO.get(item.source, "ADMIN"),
                )
            )
        try:
            version, resolved_items = self._service.resolve_qualifications(
                raw_items, request.as_of_resolver_version
            )
        except NotFound as error:
            context.abort(grpc.StatusCode.NOT_FOUND, f"VERSION_NOT_FOUND|{error}")
        response = pb.ResolveQualificationsResponse(resolver_version=version)
        for item, resolved in zip(raw_items, resolved_items, strict=True):
            message = response.items.add(
                key=item.key,
                raw_label=resolved.raw_label,
                normalized_key=resolved.normalized_key,
                folded_key=resolved.folded_key,
                canonical_name=resolved.canonical_name,
                method=_METHOD_TO_PROTO.get(resolved.method, pb.RESOLVE_METHOD_NEW),
                confidence=resolved.confidence,
            )
            if resolved.qualification_id:
                message.qualification_id = resolved.qualification_id
        return response

    def GetRelations(self, request: pb.GetRelationsRequest, context: grpc.ServicerContext):
        if len(request.pairs) > self._settings.max_batch:
            context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                f"TOO_MANY_PAIRS|max={self._settings.max_batch}",
            )
        pairs = []
        for pair in request.pairs:
            left = pair.left_skill_id.strip()
            right = pair.right_skill_id.strip()
            if not left or not right or left == right:
                continue
            pairs.append((left, right))
        try:
            version, relations = self._service.get_relations(pairs, request.as_of_resolver_version)
        except NotFound as error:
            context.abort(grpc.StatusCode.NOT_FOUND, f"VERSION_NOT_FOUND|{error}")
        return pb.GetRelationsResponse(
            resolver_version=version,
            relations=[
                pb.SkillRelation(
                    left_skill_id=item.left_skill_id,
                    right_skill_id=item.right_skill_id,
                    kind=_RELATION_TO_PROTO.get(item.kind, pb.RELATION_KIND_UNSPECIFIED),
                )
                for item in relations
            ],
        )

    # --- версия и справочники ----------------------------------------------
    def GetVersion(self, request: pb.GetVersionRequest, context: grpc.ServicerContext):
        meta = self._service.version()
        return pb.GetVersionResponse(
            resolver_version=meta.resolver_version,
            dictionary_version=meta.dictionary_version,
            embedding_model_id=meta.embedding_model_id,
            config_hash=meta.config_hash,
            pymorphy_version=meta.pymorphy_version,
            homoglyph_rules_version=meta.homoglyph_rules_version,
            qualification_version=meta.qualification_version,
            relation_version=meta.relation_version,
        )

    def GetSkills(self, request: pb.GetSkillsRequest, context: grpc.ServicerContext):
        skills = self._service.get_skills(list(request.skill_ids))
        return pb.GetSkillsResponse(skills={key: _skill(item) for key, item in skills.items()})

    def ListSkills(self, request: pb.ListSkillsRequest, context: grpc.ServicerContext):
        skill_type = _SKILL_TYPE_FROM_PROTO.get(request.skill_type, "")
        status = {pb.SKILL_STATUS_ACTIVE: "ACTIVE", pb.SKILL_STATUS_DEPRECATED: "DEPRECATED"}.get(
            request.status, ""
        )
        items, total = self._service.list_skills(
            request.query,
            skill_type,
            status,
            request.limit or 50,
            request.offset,
        )
        return pb.ListSkillsResponse(items=[_skill(item) for item in items], total=total)

    def ListReviewItems(self, request: pb.ListReviewItemsRequest, context: grpc.ServicerContext):
        status = _REVIEW_STATUS_FROM_PROTO.get(request.status, "")
        items, total = self._service.list_review(status, request.limit or 50, request.offset)
        return pb.ListReviewItemsResponse(
            items=[_review(item) for item in items],
            total=total,
        )

    def ResolveReviewItem(
        self, request: pb.ResolveReviewItemRequest, context: grpc.ServicerContext
    ):
        action = _REVIEW_ACTION_FROM_PROTO.get(request.action, "")
        if not action:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "REVIEW_ACTION_REQUIRED")
        try:
            item, skill = self._service.resolve_review(
                request.review_id,
                action,
                request.actor or "hr",
                request.skill_id or None,
                request.canonical_name or None,
                _SKILL_TYPE_FROM_PROTO.get(request.skill_type),
                list(request.additional_aliases),
            )
        except AliasConflict as error:
            context.abort(grpc.StatusCode.FAILED_PRECONDITION, _error(error))
        except NotFound as error:
            context.abort(grpc.StatusCode.NOT_FOUND, _error(error))
        response = pb.ResolveReviewItemResponse(item=_review(item))
        if skill is not None:
            response.skill.CopyFrom(_skill(skill))
        return response

    # --- админские операции -------------------------------------------------
    def CreateSkill(self, request: pb.CreateSkillRequest, context: grpc.ServicerContext):
        skill_type = _SKILL_TYPE_FROM_PROTO.get(request.skill_type, "OTHER")
        try:
            skill = self._service.create_skill(
                request.canonical_name, skill_type, request.actor or "hr"
            )
        except (AliasConflict, NotFound, ValueError) as error:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, _error(error))
        return _skill(skill)

    def AddAlias(self, request: pb.AddAliasRequest, context: grpc.ServicerContext):
        lemma_mode = "RAW_ONLY" if request.lemma_mode == pb.LEMMA_MODE_RAW_ONLY else "AUTO"
        try:
            alias = self._service.add_alias(
                request.skill_id,
                request.alias_raw,
                request.lang or "mixed",
                lemma_mode,
                request.actor or "hr",
            )
        except AliasConflict as error:
            context.abort(grpc.StatusCode.FAILED_PRECONDITION, _error(error))
        except (NotFound, ValueError) as error:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, _error(error))
        return pb.Alias(
            id=alias.id,
            skill_id=alias.skill_id,
            raw=alias.raw,
            is_canonical=alias.is_canonical,
            lang=alias.lang,
            source=_ALIAS_SOURCE_TO_PROTO.get(alias.source, pb.ALIAS_SOURCE_HR),
            lemma_mode=_LEMMA_MODE_TO_PROTO.get(alias.lemma_mode, pb.LEMMA_MODE_AUTO),
            created_by=alias.created_by,
            created_at=alias.created_at,
        )

    def SetCanonicalAlias(
        self, request: pb.SetCanonicalAliasRequest, context: grpc.ServicerContext
    ):
        try:
            skill = self._service.set_canonical(
                request.skill_id, request.alias_id, request.actor or "hr"
            )
        except NotFound as error:
            context.abort(grpc.StatusCode.NOT_FOUND, _error(error))
        return _skill(skill)

    def DeleteAlias(self, request: pb.DeleteAliasRequest, context: grpc.ServicerContext):
        try:
            self._service.delete_alias(request.alias_id, request.actor or "hr")
        except CanonicalRequired as error:
            context.abort(grpc.StatusCode.FAILED_PRECONDITION, _error(error))
        except NotFound as error:
            context.abort(grpc.StatusCode.NOT_FOUND, _error(error))
        return empty_pb2.Empty()

    def DeprecateSkill(self, request: pb.DeprecateSkillRequest, context: grpc.ServicerContext):
        try:
            skill = self._service.deprecate_skill(request.skill_id, request.actor or "hr")
            if request.hard_delete:
                self._service.delete_skill(request.skill_id, request.actor or "hr")
        except NotFound as error:
            context.abort(grpc.StatusCode.NOT_FOUND, _error(error))
        return _skill(skill)

    def DeleteSkill(self, request: pb.DeleteSkillRequest, context: grpc.ServicerContext):
        try:
            self._service.delete_skill(request.skill_id, request.actor or "hr")
        except NotFound as error:
            context.abort(grpc.StatusCode.NOT_FOUND, _error(error))
        return empty_pb2.Empty()

    def ApplySkills(self, request: pb.ApplySkillsRequest, context: grpc.ServicerContext):
        inputs = []
        for item in request.skills:
            aliases = tuple(
                ApplyAliasInput(text=alias.text, lang=alias.lang or "mixed")
                for alias in item.aliases
                if alias.text.strip()
            )
            inputs.append(
                ApplySkillInput(
                    canonical=item.canonical,
                    skill_type=_SKILL_TYPE_FROM_PROTO.get(item.skill_type, "OTHER"),
                    target_skill_id=item.target_skill_id or None,
                    aliases=aliases,
                )
            )
        try:
            skills, version = self._service.apply_skills(inputs, request.actor or "hr")
        except AliasConflict as error:
            context.abort(grpc.StatusCode.FAILED_PRECONDITION, _error(error))
        except NotFound as error:
            context.abort(grpc.StatusCode.NOT_FOUND, _error(error))
        return pb.ApplySkillsResponse(
            skills=[_skill(skill) for skill in skills],
            resolver_version=version,
        )


def _error(error: Exception) -> str:
    code = getattr(error, "code", error.__class__.__name__)
    return f"{code}|{error}"


def _skill(skill: Skill) -> pb.Skill:
    return pb.Skill(
        id=skill.id,
        canonical_name=skill.canonical_name,
        skill_type=_SKILL_TYPE_TO_PROTO.get(skill.skill_type, pb.SKILL_TYPE_OTHER),
        status=_STATUS_TO_PROTO.get(skill.status, pb.SKILL_STATUS_ACTIVE),
        aliases=[
            pb.Alias(
                id=alias.id,
                skill_id=alias.skill_id,
                raw=alias.raw,
                is_canonical=alias.is_canonical,
                lang=alias.lang,
                source=_ALIAS_SOURCE_TO_PROTO.get(alias.source, pb.ALIAS_SOURCE_HR),
                lemma_mode=_LEMMA_MODE_TO_PROTO.get(alias.lemma_mode, pb.LEMMA_MODE_AUTO),
                created_by=alias.created_by,
                created_at=alias.created_at,
            )
            for alias in skill.aliases
        ],
    )


def _review(item: ReviewItem) -> pb.ReviewItem:
    return pb.ReviewItem(
        id=item.id,
        key=item.key,
        sample_masked=list(item.sample_masked),
        occurrences=item.occurrences,
        status=_REVIEW_STATUS_TO_PROTO.get(item.status, pb.REVIEW_STATUS_PENDING),
        suggested_skill_id=item.suggested_skill_id or "",
        suggested_confidence=item.suggested_confidence or 0.0,
        candidates=[
            pb.Candidate(
                skill_id=candidate.skill_id,
                canonical_name=candidate.canonical_name,
                confidence=candidate.confidence,
            )
            for candidate in item.candidates
        ],
        resolved_skill_id=item.resolved_skill_id or "",
        resolved_by=item.resolved_by or "",
        resolved_at=item.resolved_at or "",
    )
