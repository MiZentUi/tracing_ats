import grpc

from scoring_service.v1.models.messages_pb2 import (
    
)
from contracts.scoring_service.v1.scoring_pb2_grpc import ScoringServiceServicer


class ScoringService(ScoringServiceServicer):
    def ScoreResume(
        self, request: , context: grpc.ServicerContext
    ) -> ScoreResumeResponse:
        return ScoreResumeResponse(
            relevance=0.0,
            score_low=0.0,
            score_high=1.0,
            score=0.5,
            tenure=12,
            refine=False,
            skills=[
                SkillAssessment(
                    coverage=1.0,
                    recency_unknown=True,
                    tenure=None,
                    evidence=FeatureEvidence(
                        start_index=0, end_index=8, text="abyrvalg"
                    ),
                )
            ],
        )
