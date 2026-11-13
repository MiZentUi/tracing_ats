from scoring_service.v1.models.features import common_pb2 as _common_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class QualificationRequirement(_message.Message):
    __slots__ = ("label",)
    LABEL_FIELD_NUMBER: _ClassVar[int]
    label: str
    def __init__(self, label: _Optional[str] = ...) -> None: ...

class QualificationPerResume(_message.Message):
    __slots__ = ("label", "evidence")
    LABEL_FIELD_NUMBER: _ClassVar[int]
    EVIDENCE_FIELD_NUMBER: _ClassVar[int]
    label: str
    evidence: _common_pb2.FeatureEvidence
    def __init__(self, label: _Optional[str] = ..., evidence: _Optional[_Union[_common_pb2.FeatureEvidence, _Mapping]] = ...) -> None: ...

class QualificationAlias(_message.Message):
    __slots__ = ("alias_normalized", "canonical_key", "canonical_label")
    ALIAS_NORMALIZED_FIELD_NUMBER: _ClassVar[int]
    CANONICAL_KEY_FIELD_NUMBER: _ClassVar[int]
    CANONICAL_LABEL_FIELD_NUMBER: _ClassVar[int]
    alias_normalized: str
    canonical_key: str
    canonical_label: str
    def __init__(self, alias_normalized: _Optional[str] = ..., canonical_key: _Optional[str] = ..., canonical_label: _Optional[str] = ...) -> None: ...

class QualificationCatalog(_message.Message):
    __slots__ = ("version", "aliases")
    VERSION_FIELD_NUMBER: _ClassVar[int]
    ALIASES_FIELD_NUMBER: _ClassVar[int]
    version: str
    aliases: _containers.RepeatedCompositeFieldContainer[QualificationAlias]
    def __init__(self, version: _Optional[str] = ..., aliases: _Optional[_Iterable[_Union[QualificationAlias, _Mapping]]] = ...) -> None: ...
