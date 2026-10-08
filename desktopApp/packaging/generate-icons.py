"""Export desktop icons from the supplied transparent artwork (requires Pillow)."""
from pathlib import Path
from PIL import Image

desktop = Path(__file__).resolve().parents[1]
original = Image.open(desktop / "branding/audioly-mark.png").convert("RGBA")
bounds = original.getchannel("A").getbbox()
if bounds is None:
    raise ValueError("The logo is fully transparent")
mark = original.crop(bounds)
resources = desktop / "src/main/composeResources/drawable"
icons = desktop / "packaging/icons"

# Trim only empty padding for the in-app mark; keep the supplied artwork intact.
mark.save(resources / "logo_mark.png")
canvas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
fitted = mark.copy()
fitted = fitted.resize(
    (840, round(840 * mark.height / mark.width)), Image.Resampling.LANCZOS
)
canvas.alpha_composite(fitted, ((1024 - fitted.width) // 2, (1024 - fitted.height) // 2))
canvas.resize((512, 512), Image.Resampling.LANCZOS).save(resources / "logo.png")
canvas.resize((512, 512), Image.Resampling.LANCZOS).save(icons / "AppIcon.png")
canvas.save(icons / "AppIcon.icns")
canvas.save(icons / "AppIcon.ico", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("Exported transparent desktop icons and the full-color in-app mark.")
