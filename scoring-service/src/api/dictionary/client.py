"""Клиент dictionary-service для scoring-service."""

from __future__ import annotations

import logging
import time
import uuid
from dataclasses import dataclass, field

import grpc

from dictionary_service.v1 import dictionary_pb2 as pb
from dictionary_service.v1 import dictionary_pb2_grpc as pb_grpc

logger = logging.getLogger(__name__)


class DictionaryUnavailable(Exception):
    """Словарь недоступен или вернул ошибку; вызывающая сторона уходит в fallback."""


@dataclass(frozen=True, slots=True)
class ResolvedName:
    raw_name: str
    skill_id: str | None
    canonical_name: str
    normalized_key: str
    folded_key: str
    method: str
    candidates: tuple[str, ...] = field(default_factory=tuple)


@dataclass(frozen=True, slots=True)
class Resolution:
    resolver_version: str
    mode: str
    items: dict[str, ResolvedName]


@dataclass(frozen=True, slots=True)
class ResolvedQualificationName:
    raw_label: str
    qualification_id: str | None
    canonical_name: str
    normalized_key: str
    folded_key: str
    method: str


@dataclass(frozen=True, slots=True)
class QualificationResolution:
    resolver_version: str
    mode: str
    items: dict[str, ResolvedQualificationName]


class DictionaryClient:
    def __init__(self, target: str, deadline_seconds: float = 2.0, as_of: str = "") -> None:
        self._channel = grpc.insecure_channel(target)
        self._stub = pb_grpc.DictionaryServiceStub(self._channel)
        self._deadline = deadline_seconds
        self._as_of = as_of

    def resolve(self, names: list[tuple[str, str]]) -> Resolution:
        """names — пары (raw_name, source), source: RESUME|VACANCY."""
        unique: dict[str, str] = {}
        for raw, source in names:
            if raw and raw not in unique:
                unique[raw] = source
        if not unique:
            return Resolution("", "EMPTY", {})

        request = pb.ResolveBatchRequest(as_of_resolver_version=self._as_of)
        for index, (raw, source) in enumerate(unique.items()):
            request.items.add(
                key=str(index),
                raw_name=raw,
                source=(
                    pb.RESOLVE_SOURCE_RESUME if source == "RESUME" else pb.RESOLVE_SOURCE_VACANCY
                ),
            )

        request_id = uuid.uuid4().hex[:16]
        started = time.monotonic()
        logger.info(
            "outbound request sent target=dictionary-service method=Resolve request_id=%s batch=%d",
            request_id,
            len(unique),
        )
        try:
            response = self._stub.Resolve(request, timeout=self._deadline)
        except grpc.RpcError as error:
            logger.warning(
                "outbound request failed target=dictionary-service method=Resolve "
                "request_id=%s error=%s elapsed_ms=%.1f",
                request_id,
                type(error).__name__,
                (time.monotonic() - started) * 1000,
            )
            raise DictionaryUnavailable(str(error)) from error
        elapsed = (time.monotonic() - started) * 1000
        logger.info(
            "outbound response received target=dictionary-service method=Resolve "
            "request_id=%s status=OK elapsed_ms=%.1f",
            request_id,
            elapsed,
        )
        items: dict[str, ResolvedName] = {}
        for item in response.items:
            items[item.raw_name] = ResolvedName(
                raw_name=item.raw_name,
                skill_id=item.skill_id or None,
                canonical_name=item.canonical_name,
                normalized_key=item.normalized_key,
                folded_key=item.folded_key,
                method=pb.ResolveMethod.Name(item.method).replace("RESOLVE_METHOD_", ""),
                candidates=tuple(candidate.canonical_name for candidate in item.candidates),
            )
        return Resolution(response.resolver_version, "DICTIONARY", items)

    def resolve_qualifications(
        self,
        labels: list[tuple[str, str]],
    ) -> QualificationResolution:
        unique: dict[str, str] = {}
        for raw, source in labels:
            if raw and raw not in unique:
                unique[raw] = source
        if not unique:
            return QualificationResolution("", "EMPTY", {})

        request = pb.ResolveQualificationsRequest(as_of_resolver_version=self._as_of)
        for index, (raw, source) in enumerate(unique.items()):
            request.items.add(
                key=str(index),
                raw_label=raw,
                source=(
                    pb.RESOLVE_SOURCE_RESUME if source == "RESUME" else pb.RESOLVE_SOURCE_VACANCY
                ),
            )

        request_id = uuid.uuid4().hex[:16]
        started = time.monotonic()
        logger.info(
            "outbound request sent target=dictionary-service "
            "method=ResolveQualifications request_id=%s batch=%d",
            request_id,
            len(unique),
        )
        try:
            response = self._stub.ResolveQualifications(request, timeout=self._deadline)
        except grpc.RpcError as error:
            logger.warning(
                "outbound request failed target=dictionary-service "
                "method=ResolveQualifications request_id=%s error=%s elapsed_ms=%.1f",
                request_id,
                type(error).__name__,
                (time.monotonic() - started) * 1000,
            )
            raise DictionaryUnavailable(str(error)) from error
        logger.info(
            "outbound response received target=dictionary-service "
            "method=ResolveQualifications request_id=%s status=OK elapsed_ms=%.1f",
            request_id,
            (time.monotonic() - started) * 1000,
        )
        items: dict[str, ResolvedQualificationName] = {}
        for item in response.items:
            items[item.raw_label] = ResolvedQualificationName(
                raw_label=item.raw_label,
                qualification_id=item.qualification_id or None,
                canonical_name=item.canonical_name,
                normalized_key=item.normalized_key,
                folded_key=item.folded_key,
                method=pb.ResolveMethod.Name(item.method).replace("RESOLVE_METHOD_", ""),
            )
        return QualificationResolution(response.resolver_version, "DICTIONARY", items)

    def get_relations(self, pairs: list[tuple[str, str]]) -> dict[tuple[str, str], str]:
        unique = {
            (left, right) if left < right else (right, left)
            for left, right in pairs
            if left and right and left != right
        }
        if not unique:
            return {}

        request = pb.GetRelationsRequest(as_of_resolver_version=self._as_of)
        for left, right in unique:
            request.pairs.add(left_skill_id=left, right_skill_id=right)

        request_id = uuid.uuid4().hex[:16]
        started = time.monotonic()
        logger.info(
            "outbound request sent target=dictionary-service method=GetRelations "
            "request_id=%s pairs=%d",
            request_id,
            len(unique),
        )
        try:
            response = self._stub.GetRelations(request, timeout=self._deadline)
        except grpc.RpcError as error:
            logger.warning(
                "outbound request failed target=dictionary-service method=GetRelations "
                "request_id=%s error=%s elapsed_ms=%.1f",
                request_id,
                type(error).__name__,
                (time.monotonic() - started) * 1000,
            )
            raise DictionaryUnavailable(str(error)) from error
        logger.info(
            "outbound response received target=dictionary-service method=GetRelations "
            "request_id=%s status=OK elapsed_ms=%.1f",
            request_id,
            (time.monotonic() - started) * 1000,
        )
        return {
            (
                item.left_skill_id
                if item.left_skill_id < item.right_skill_id
                else item.right_skill_id,
                item.right_skill_id
                if item.left_skill_id < item.right_skill_id
                else item.left_skill_id,
            ): pb.RelationKind.Name(item.kind).replace("RELATION_KIND_", "")
            for item in response.relations
        }
