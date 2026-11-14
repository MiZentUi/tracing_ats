#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROTO_DIR="$SCRIPT_DIR/../contracts/proto"
OUT="$SCRIPT_DIR/src/"

uv run --project "$SCRIPT_DIR" python -m grpc_tools.protoc -I"$PROTO_DIR" \
    --python_out="$OUT" \
    --pyi_out="$OUT" \
    --grpc_python_out="$OUT" \
    sanitize_service/v1/sanitize.proto
