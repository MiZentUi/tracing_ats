import grpc

from api.config import Settings
from api.server import create_server
from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import common_pb2, skill_pb2


def test_score_resume_returns_dummy_result() -> None:
    handle = create_server(Settings(grpc_host="127.0.0.1", grpc_port=0, _env_file=None))
    handle.server.start()

    request = messages_pb2.ScoreResumeRequest(
        vacancy=messages_pb2.Vacancy(
            tenure=5,
            text_description="Python backend",
            requirements=[
                features_pb2.FeatureRequirement(
                    id="python",
                    criticality=common_pb2.FEATURE_CRITICALITY_REQUIRED,
                    skill=skill_pb2.SkillRequirement(name="Python", tenure=3),
                )
            ],
        ),
        resume=messages_pb2.UnprocessedResume(text="Python developer"),
    )

    try:
        with grpc.insecure_channel(f"127.0.0.1:{handle.port}") as channel:
            stub = scoring_pb2_grpc.ScoringServiceStub(channel)
            response = stub.ScoreResume(request, timeout=2)
    finally:
        handle.server.stop(grace=None)

    assert response.score == 0.5
    assert response.score_low == 0.4
    assert response.score_high == 0.6
    assert response.tenure == 5
    assert response.features[0].id == "python"
    assert response.features[0].coverage == 0.5
