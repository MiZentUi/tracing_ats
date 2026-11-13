#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(dirname "$0")
cd "$SCRIPT_DIR/../contracts/proto"

python -m grpc_tools.protoc -I. \
    --python_out=../../dictionary-service/src \
    --pyi_out=../../dictionary-service/src \
    --grpc_python_out=../../dictionary-service/src \
    dictionary_service/v1/dictionary.proto
