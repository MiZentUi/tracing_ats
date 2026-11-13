#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(dirname "$0")
cd "$SCRIPT_DIR/../../contracts/proto"

OUT=../../infra/mock-scoring-service/src/
PROTOBUFS="scoring_service/v1/models/features/common.proto \
    scoring_service/v1/models/features/education.proto \
    scoring_service/v1/models/features/experience.proto \
    scoring_service/v1/models/features.proto \
    scoring_service/v1/models/features/qualification.proto \
    scoring_service/v1/models/features/skill.proto \
    scoring_service/v1/models/messages.proto \
    scoring_service/v1/scoring.proto"

python -m grpc_tools.protoc -I. \
    --python_out=$OUT \
    --pyi_out=$OUT \
    --grpc_python_out=$OUT \
    $PROTOBUFS
