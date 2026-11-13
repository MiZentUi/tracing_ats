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
