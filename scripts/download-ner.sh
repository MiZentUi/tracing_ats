#!/usr/bin/env bash
# Готовит локальную NER-модель для resume-service: скачивает tokenizer/config,
# экспортирует модель в ONNX и квантует веса в int8.
#
# Модель не коммитится: models/ в .gitignore. CI использует fake-NER.
#
# Примеры:
#   scripts/download-ner.sh
#   MODEL_ID=Gherman/bert-base-NER-Russian scripts/download-ner.sh
#   OUT_DIR=models/ner scripts/download-ner.sh
set -euo pipefail

MODEL_ID="${MODEL_ID:-yqelz/xml-roberta-large-ner-russian}"
OUT_DIR="${OUT_DIR:-models/ner}"
VENV_DIR="${VENV_DIR:-.ner-venv}"
TORCH_INDEX_URL="${TORCH_INDEX_URL:-https://download.pytorch.org/whl/cpu}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
case "$OUT_DIR" in
    /*) ;;
    *) OUT_DIR="$ROOT_DIR/$OUT_DIR" ;;
esac
case "$VENV_DIR" in
    /*) ;;
    *) VENV_DIR="$ROOT_DIR/$VENV_DIR" ;;
esac

if ! command -v python3 >/dev/null 2>&1; then
    echo "python3 не найден: нужен Python 3.10+ для экспорта ONNX" >&2
    exit 1
fi

mkdir -p "$OUT_DIR"
if [ ! -x "$VENV_DIR/bin/python" ]; then
    echo "==> Создаю виртуальное окружение $VENV_DIR"
    python3 -m venv "$VENV_DIR"
fi

PYTHON="$VENV_DIR/bin/python"
PIP="$VENV_DIR/bin/pip"

echo "==> Ставлю зависимости (один раз, может занять несколько минут)"
"$PIP" install --quiet --upgrade pip
"$PIP" install --quiet "optimum[onnxruntime]" "huggingface_hub" sentencepiece
if ! "$PYTHON" -c "import torch" >/dev/null 2>&1; then
    "$PIP" install --quiet torch --index-url "$TORCH_INDEX_URL"
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "==> Экспортирую $MODEL_ID в ONNX"
"$VENV_DIR/bin/optimum-cli" export onnx \
    --model "$MODEL_ID" \
    --task token-classification \
    "$TMP_DIR/onnx"

ONNX_FILE="$(find "$TMP_DIR/onnx" -maxdepth 1 -name '*.onnx' -print -quit)"
if [ -z "$ONNX_FILE" ]; then
    echo "ONNX-файл не найден после экспорта" >&2
    exit 1
fi

echo "==> Квантую веса в int8"
"$PYTHON" - "$ONNX_FILE" "$TMP_DIR/model-int8.onnx" <<'PY'
import sys
from onnxruntime.quantization import QuantType, quantize_dynamic

quantize_dynamic(sys.argv[1], sys.argv[2], weight_type=QuantType.QInt8)
PY

echo "==> Скачиваю config.json и tokenizer.json"
"$PYTHON" - "$MODEL_ID" "$OUT_DIR" <<'PY'
import sys
from huggingface_hub import hf_hub_download

model_id, out_dir = sys.argv[1], sys.argv[2]
for name in ("config.json", "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json"):
    try:
        hf_hub_download(model_id, name, local_dir=out_dir)
    except Exception as exc:  # файла может не быть в репозитории
        print(f"    пропускаю {name}: {exc}")
PY

cp "$TMP_DIR/model-int8.onnx" "$OUT_DIR/model.onnx"

echo
echo "Готово: $OUT_DIR/model.onnx"
echo "SHA-256: $(sha256sum "$OUT_DIR/model.onnx" | cut -d' ' -f1)"
echo
echo "Включите модель:"
echo "  ATS_PII_NER_PROVIDER=onnx"
echo "  ATS_PII_NER_MODEL_PATH=$OUT_DIR/model.onnx"
echo "  ATS_PII_NER_TOKENIZER_PATH=$OUT_DIR/tokenizer.json"
echo "  ATS_PII_NER_CONFIG_PATH=$OUT_DIR/config.json"
