# Laya model files

The backend's Laya classifier arm reads two directories from here (they are git-ignored):

- `checkpoint/`: `rl_agent_config.json`, `tokenizer/`, `encoder/config.json` (the Hugging Face checkpoint)
- `onnx/`: the exported graph (`laya.onnx`, or `encoder.onnx` + `head.onnx`)

Create them with `scripts/get-laya-model.sh` (the multilingual mmBERT-base checkpoint, ~1.3 GB on disk).
If they are absent, the backend starts normally and the arm is reported as unavailable.
