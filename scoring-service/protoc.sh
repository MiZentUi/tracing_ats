#!sh
python -m grpc_tools.protoc -I.. \
    --python_out=src/ \
    --pyi_out=src/ \
    --grpc_python_out=src/ \
    contracts/scoring_service/v1/scoring.proto 