from __future__ import annotations

import grpc

from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import features_pb2, messages_pb2


class ScoringService(scoring_pb2_grpc.ScoringServiceServicer):
    """Deterministic placeholder until the scoring model is wired in."""

    def ScoreResume(
        self,
        request: messages_pb2.ScoreResumeRequest,
        context: grpc.ServicerContext,
    ) -> messages_pb2.ScoreResumeResponse:
        features = [
            features_pb2.FeatureAssessment(
                id=requirement.id or f"feature-{index}",
                relevance=0.5,
                coverage=0.5,
            )
            for index, requirement in enumerate(request.vacancy.requirements)
        ]

        return messages_pb2.ScoreResumeResponse(
            relevance=0.5,
            score_low=0.4,
            score_high=0.6,
            score=0.5,
            tenure=request.vacancy.tenure or None,
            refine=False,
            features=features,
        )
