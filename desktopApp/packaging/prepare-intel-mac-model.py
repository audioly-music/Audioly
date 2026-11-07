"""Validate the current cross-platform beat model (no Intel conversion required)."""
from pathlib import Path
import onnx
root = Path(__file__).resolve().parents[2]
source = root / "app/src/main/assets/beat_this_final0_q8.onnx"
model = onnx.load(source)
assert not any(node.op_type == "ConvInteger" for node in model.graph.node), "Review Intel Mac support for quantized convolutions"
onnx.checker.check_model(model)
print("Current beat model uses floating-point convolutions; no Intel Mac conversion needed")
