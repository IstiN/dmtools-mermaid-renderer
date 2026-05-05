#!/usr/bin/env python3
"""
Creates side-by-side comparison: Playwright PNG | Our PNG | Our SVG (as PNG)
Each row = one diagram type. Output = comparison-sheet.png
"""

from PIL import Image, ImageDraw, ImageFont
import os
import subprocess
import sys
import tempfile

PLAYWRIGHT_DIR = sys.argv[1]
OUR_DIR = sys.argv[2]  # awt-metrics output (has PNG + SVG)
OUT_PATH = sys.argv[3]

THUMB_W = 480
THUMB_H = 360
PADDING = 8
HEADER_H = 24
LABEL_H = 20
COL_LABELS = ["Playwright (ground truth)", "Our PNG (GraalJS+AWT)", "Our SVG→PNG"]
BG = (245, 245, 245)
HEADER_BG = (30, 30, 30)
HEADER_FG = (255, 255, 255)
OK_BG = (220, 255, 220)
FAIL_BG = (255, 220, 220)

DIAGRAMS = [
    "flowchart", "class", "sequence", "entity-relationship", "state",
    "mindmap", "architecture", "block", "c4", "gantt", "git", "ishikawa",
    "kanban", "packet", "pie", "quadrant", "radar", "requirement", "sankey",
    "timeline", "treeview", "treemap", "user-journey", "venn", "wardley", "xy",
]

def load_image(path):
    if not os.path.exists(path):
        return None
    try:
        img = Image.open(path).convert("RGBA")
        bg = Image.new("RGBA", img.size, (255, 255, 255, 255))
        bg.paste(img, mask=img)
        return bg.convert("RGB")
    except Exception as e:
        print(f"  ⚠ Cannot open {path}: {e}", file=sys.stderr)
        return None

def svg_to_png(svg_path, out_png):
    """Convert SVG to PNG using rsvg-convert or Inkscape or ImageMagick."""
    for cmd in [
        ["rsvg-convert", "-w", str(THUMB_W * 2), svg_path, "-o", out_png],
        ["inkscape", svg_path, f"--export-filename={out_png}", f"--export-width={THUMB_W * 2}"],
        ["convert", "-background", "white", "-density", "96", svg_path, out_png],
    ]:
        try:
            result = subprocess.run(cmd, capture_output=True, timeout=10)
            if result.returncode == 0 and os.path.exists(out_png):
                return True
        except (FileNotFoundError, subprocess.TimeoutExpired):
            continue
    return False

def fit_image(img, w, h):
    """Fit image into (w, h) maintaining aspect ratio, white background."""
    canvas = Image.new("RGB", (w, h), (255, 255, 255))
    img.thumbnail((w, h), Image.LANCZOS)
    x = (w - img.width) // 2
    y = (h - img.height) // 2
    canvas.paste(img, (x, y))
    return canvas

def make_placeholder(w, h, text, color=(200, 200, 200)):
    img = Image.new("RGB", (w, h), color)
    d = ImageDraw.Draw(img)
    d.text((w // 2, h // 2), text, fill=(100, 100, 100), anchor="mm")
    return img

try:
    font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 13)
    font_bold = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 14)
    font_header = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 12)
except Exception:
    font = ImageFont.load_default()
    font_bold = font
    font_header = font

ncols = 3
col_w = THUMB_W + PADDING * 2
row_h = THUMB_H + HEADER_H + PADDING * 2
nrows = len(DIAGRAMS)

total_w = ncols * col_w + PADDING
total_h = LABEL_H + nrows * row_h + PADDING

sheet = Image.new("RGB", (total_w, total_h), BG)
draw = ImageDraw.Draw(sheet)

# Column headers
for ci, label in enumerate(COL_LABELS):
    x = ci * col_w + PADDING
    draw.rectangle([x, 0, x + col_w - PADDING, LABEL_H], fill=HEADER_BG)
    draw.text((x + col_w // 2 - PADDING // 2, LABEL_H // 2), label, fill=HEADER_FG, anchor="mm", font=font_bold)

with tempfile.TemporaryDirectory() as tmpdir:
    for ri, name in enumerate(DIAGRAMS):
        y_base = LABEL_H + ri * row_h

        pw_path = os.path.join(PLAYWRIGHT_DIR, f"{name}.png")
        our_png_path = os.path.join(OUR_DIR, f"{name}.png")
        our_svg_path = os.path.join(OUR_DIR, f"{name}.svg")

        pw_img = load_image(pw_path)
        our_png_img = load_image(our_png_path)

        svg_png_path = os.path.join(tmpdir, f"{name}-svg.png")
        svg_ok = svg_to_png(our_svg_path, svg_png_path) if os.path.exists(our_svg_path) else False
        our_svg_img = load_image(svg_png_path) if svg_ok else None

        images = [pw_img, our_png_img, our_svg_img]
        labels_ok = [pw_img is not None, our_png_img is not None, our_svg_img is not None]

        for ci, img in enumerate(images):
            x = ci * col_w + PADDING
            y = y_base

            # Row label (only on first column)
            if ci == 0:
                draw.rectangle([x, y, x + col_w - PADDING, y + HEADER_H], fill=(50, 50, 80))
                draw.text((x + 6, y + HEADER_H // 2), name, fill=(255, 255, 200), anchor="lm", font=font_bold)
            else:
                draw.rectangle([x, y, x + col_w - PADDING, y + HEADER_H], fill=(50, 50, 80))

            thumb_y = y + HEADER_H + PADDING
            if img is not None:
                thumb = fit_image(img, THUMB_W, THUMB_H)
                sheet.paste(thumb, (x, thumb_y))
            else:
                placeholder = make_placeholder(THUMB_W, THUMB_H, "N/A", (230, 210, 210))
                sheet.paste(placeholder, (x, thumb_y))

        print(f"  {'✓' if all(labels_ok) else '~'} {name}")

sheet.save(OUT_PATH, quality=90, optimize=True)
print(f"\nSaved: {OUT_PATH}  ({total_w}×{total_h}px)")
