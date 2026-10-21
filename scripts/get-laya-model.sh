#!/usr/bin/env bash
# Download the multilingual Laya checkpoint (mmBERT-base) and export its ONNX graph into ./laya,
# using laya's own prepare_checkpoint.py (pinned HF revision).
# PYTHON picks the interpreter (default python3); it needs torch, transformers, safetensors,
# huggingface_hub, onnx, onnxscript and onnxruntime.
# LAYA_CHECKPOINT_NAME=english for the English ModernBERT-large checkpoint.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/laya-common.sh
NAME=${LAYA_CHECKPOINT_NAME:-multilingual}

laya_checkout
(cd "$LAYA_SRC" && "${PYTHON:-python3}" laya-java/scripts/prepare_checkpoint.py --checkpoint "$NAME")
rm -rf laya/checkpoint laya/onnx
cp -R "$LAYA_SRC/laya-java/.work/checkpoints/$NAME" laya/checkpoint
rm -f laya/checkpoint/model.safetensors   # the torch weights; the ONNX graph carries them
cp -R "$LAYA_SRC/laya-java/.work/onnx/$NAME" laya/onnx
du -sh laya/checkpoint laya/onnx
