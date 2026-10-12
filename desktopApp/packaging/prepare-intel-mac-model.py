"""Derive the Intel macOS beat model from the Android quantized model.

ORT 1.23's CPU ConvInteger kernel accepts unsigned weights. Shift the stored
signed weights and their zero point by 128; (w - zero_point) is unchanged.
No retraining, dequantization, or change to the model's arithmetic is involved.
Requires the onnx and numpy Python packages.
"""
from pathlib import Path
import numpy as np
import onnx
from onnx import numpy_helper

root = Path(__file__).resolve().parents[2]
source = root / 'app/src/main/assets/beat_this_int8.onnx'
target = root / 'desktopApp/packaging/intel-mac-models/beat_this_int8.onnx'
model = onnx.load(source)
initializers = {item.name: item for item in model.graph.initializer}
converted = 0
replaced = set()
for node in model.graph.node:
    if node.op_type != 'ConvInteger':
        continue
    weight = numpy_helper.to_array(initializers[node.input[1]])
    zero = numpy_helper.to_array(initializers[node.input[3]])
    if weight.dtype != np.int8:
        continue
    assert zero.dtype == np.int8
    for index, values in ((1, weight), (3, zero)):
        replaced.add(node.input[index])
        name = node.input[index] + '_intel_unsigned'
        shifted = (values.astype(np.int16) + 128).astype(np.uint8)
        model.graph.initializer.append(numpy_helper.from_array(shifted, name))
        node.input[index] = name
    converted += 1
assert converted == 4, f'Review the new source model: expected 4 ConvInteger nodes, got {converted}'
used = {name for node in model.graph.node for name in node.input}
retained = [item for item in model.graph.initializer if item.name not in replaced or item.name in used]
del model.graph.initializer[:]
model.graph.initializer.extend(retained)
onnx.checker.check_model(model)
target.parent.mkdir(parents=True, exist_ok=True)
onnx.save(model, target)
print(f'Prepared Intel Mac model: {target.name}; {converted} equivalent weight conversions')
