from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class JobPosition(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    JOB_POSITION_UNSPECIFIED: _ClassVar[JobPosition]
    JOB_POSITION_INTERN: _ClassVar[JobPosition]
    JOB_POSITION_JUNIOR: _ClassVar[JobPosition]
    JOB_POSITION_MIDDLE: _ClassVar[JobPosition]
    JOB_POSITION_SENIOR: _ClassVar[JobPosition]

class FeatureCriticality(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    FEATURE_CRITICALITY_UNSPECIFIED: _ClassVar[FeatureCriticality]
    FEATURE_CRITICALITY_OPTIONAL: _ClassVar[FeatureCriticality]
    FEATURE_CRITICALITY_NICE: _ClassVar[FeatureCriticality]
    FEATURE_CRITICALITY_REQUIRED: _ClassVar[FeatureCriticality]
    FEATURE_CRITICALITY_CRITICAL: _ClassVar[FeatureCriticality]
JOB_POSITION_UNSPECIFIED: JobPosition
JOB_POSITION_INTERN: JobPosition
JOB_POSITION_JUNIOR: JobPosition
JOB_POSITION_MIDDLE: JobPosition
JOB_POSITION_SENIOR: JobPosition
FEATURE_CRITICALITY_UNSPECIFIED: FeatureCriticality
FEATURE_CRITICALITY_OPTIONAL: FeatureCriticality
FEATURE_CRITICALITY_NICE: FeatureCriticality
FEATURE_CRITICALITY_REQUIRED: FeatureCriticality
FEATURE_CRITICALITY_CRITICAL: FeatureCriticality

class Vacancy(_message.Message):
    __slots__ = ("tenure", "text_description", "skills")
    TENURE_FIELD_NUMBER: _ClassVar[int]
    TEXT_DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    SKILLS_FIELD_NUMBER: _ClassVar[int]
    tenure: int
    text_description: str
    skills: _containers.RepeatedCompositeFieldContainer[RequiredSkill]
    def __init__(self, tenure: _Optional[int] = ..., text_description: _Optional[str] = ..., skills: _Optional[_Iterable[_Union[RequiredSkill, _Mapping]]] = ...) -> None: ...

class RequiredSkill(_message.Message):
    __slots__ = ("name", "normalize_name", "description", "criticality", "tenure")
    NAME_FIELD_NUMBER: _ClassVar[int]
    NORMALIZE_NAME_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    CRITICALITY_FIELD_NUMBER: _ClassVar[int]
    TENURE_FIELD_NUMBER: _ClassVar[int]
    name: str
    normalize_name: str
    description: str
    criticality: FeatureCriticality
    tenure: int
    def __init__(self, name: _Optional[str] = ..., normalize_name: _Optional[str] = ..., description: _Optional[str] = ..., criticality: _Optional[_Union[FeatureCriticality, str]] = ..., tenure: _Optional[int] = ...) -> None: ...

class UnprocessedResume(_message.Message):
    __slots__ = ("text",)
    TEXT_FIELD_NUMBER: _ClassVar[int]
    text: str
    def __init__(self, text: _Optional[str] = ...) -> None: ...

class ScoreResumeRequest(_message.Message):
    __slots__ = ("vacancy", "resume")
    VACANCY_FIELD_NUMBER: _ClassVar[int]
    RESUME_FIELD_NUMBER: _ClassVar[int]
    vacancy: Vacancy
    resume: UnprocessedResume
    def __init__(self, vacancy: _Optional[_Union[Vacancy, _Mapping]] = ..., resume: _Optional[_Union[UnprocessedResume, _Mapping]] = ...) -> None: ...

class FeatureEvidence(_message.Message):
    __slots__ = ("start_index", "end_index", "text")
    START_INDEX_FIELD_NUMBER: _ClassVar[int]
    END_INDEX_FIELD_NUMBER: _ClassVar[int]
    TEXT_FIELD_NUMBER: _ClassVar[int]
    start_index: int
    end_index: int
    text: str
    def __init__(self, start_index: _Optional[int] = ..., end_index: _Optional[int] = ..., text: _Optional[str] = ...) -> None: ...

class SkillAssessment(_message.Message):
    __slots__ = ("coverage", "recency_unknown", "tenure", "evidence")
    COVERAGE_FIELD_NUMBER: _ClassVar[int]
    RECENCY_UNKNOWN_FIELD_NUMBER: _ClassVar[int]
    TENURE_FIELD_NUMBER: _ClassVar[int]
    EVIDENCE_FIELD_NUMBER: _ClassVar[int]
    coverage: float
    recency_unknown: bool
    tenure: int
    evidence: FeatureEvidence
    def __init__(self, coverage: _Optional[float] = ..., recency_unknown: _Optional[bool] = ..., tenure: _Optional[int] = ..., evidence: _Optional[_Union[FeatureEvidence, _Mapping]] = ...) -> None: ...

class ScoreResumeResponse(_message.Message):
    __slots__ = ("relevance", "score_low", "score_high", "score", "tenure", "refine", "skills")
    RELEVANCE_FIELD_NUMBER: _ClassVar[int]
    SCORE_LOW_FIELD_NUMBER: _ClassVar[int]
    SCORE_HIGH_FIELD_NUMBER: _ClassVar[int]
    SCORE_FIELD_NUMBER: _ClassVar[int]
    TENURE_FIELD_NUMBER: _ClassVar[int]
    REFINE_FIELD_NUMBER: _ClassVar[int]
    SKILLS_FIELD_NUMBER: _ClassVar[int]
    relevance: float
    score_low: float
    score_high: float
    score: float
    tenure: int
    refine: bool
    skills: _containers.RepeatedCompositeFieldContainer[SkillAssessment]
    def __init__(self, relevance: _Optional[float] = ..., score_low: _Optional[float] = ..., score_high: _Optional[float] = ..., score: _Optional[float] = ..., tenure: _Optional[int] = ..., refine: _Optional[bool] = ..., skills: _Optional[_Iterable[_Union[SkillAssessment, _Mapping]]] = ...) -> None: ...
