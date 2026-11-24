"""
writes brand/mogul-studio-logo.svg, the whole logo: the mark (brand/mogul-logo.svg), then "mogul"
in space grotesk bold and "STUDIO" in spaced ibm plex mono medium, both on one baseline. the
words are outlines of the studio's own fonts, so the logo looks the same wherever it's drawn,
with nothing left to a text layout's line heights. bin/brand.sh runs it with fonttools:

    uv run --with fonttools python brand/wordmark.py

the other mogul apps make theirs with it too, from their own mark and name, so the family's
logos are one design (mogul clip's bin/brand.sh, for one):

    uv run --with fonttools python brand/wordmark.py --mark <mark.svg> --product CLIP --out <logo.svg>
"""

import argparse
import re
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

root = Path(__file__).resolve().parent.parent
fonts = root / "shared/src/commonMain/composeResources/font"

arguments = argparse.ArgumentParser()
arguments.add_argument("--mark", type=Path, default=root / "brand/mogul-logo.svg", help="a 100 x 100 svg whose top-level shapes are the mark")
arguments.add_argument("--product", default="STUDIO", help="the word after mogul")
arguments.add_argument("--out", type=Path, default=root / "brand/mogul-studio-logo.svg")
options = arguments.parse_args()

# everything is in units of the "mogul"'s font size, 100
size = 100
mark_size = size * 1.25
mark_gap = size / 3
word_gap = size / 4
name_tracking = -0.05 * size
product_size = size * 3 / 8
product_tracking = 0.24 * product_size

ink = "#f2f6fc"
teal = "#609ea0"


def number(value):
    return f"{value:.2f}".rstrip("0").rstrip(".")


def word(font_file, text, font_size, tracking, x):
    """the word's outline as path data, starting at x on a baseline at y 0, its ink's bounds, and where it ends"""
    font = TTFont(fonts / font_file)
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    scale = font_size / font["head"].unitsPerEm
    path = SVGPathPen(glyphs, ntos=number)
    bounds = BoundsPen(glyphs)
    for i, char in enumerate(text):
        name = cmap[ord(char)]
        # font units are y up; the svg's are y down
        transform = (scale, 0, 0, -scale, x, 0)
        glyphs[name].draw(TransformPen(path, transform))
        glyphs[name].draw(TransformPen(bounds, transform))
        x += glyphs[name].width * scale + (tracking if i < len(text) - 1 else 0)
    return path.getCommands(), bounds.bounds, x


name, name_bounds, x = word("spacegrotesk_bold.ttf", "mogul", size, name_tracking, mark_size + mark_gap)
product, product_bounds, _ = word("ibmplexmono_medium.ttf", options.product, product_size, product_tracking, x + word_gap)

# the mark is centered on the words' ink, from the l's top to the g's tail
middle = (name_bounds[1] + name_bounds[3]) / 2
mark_top = middle - mark_size / 2
# the mark's svg, less its own svg element and comments: what's inside is drawn in 100 x 100
mark = re.sub(r"<!--.*?-->", "", options.mark.read_text(), flags=re.S)
mark = re.search(r"<svg[^>]*>(.*)</svg>", mark, re.S).group(1)
mark = "\n".join("      " + line.strip() for line in mark.splitlines() if line.strip())

top = min(mark_top, name_bounds[1], product_bounds[1])
bottom = max(mark_top + mark_size, name_bounds[3], product_bounds[3])
right = product_bounds[2]

# the size is spelled out as well as the viewBox, and the viewBox starts at 0 0: compose's svg
# loader takes the picture's shape from width and height, and draws a viewBox elsewhere squashed
width, height = number(right), number(bottom - top)
options.out.write_text(f"""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
  <!-- the whole mogul {options.product.lower()} logo, made by mogul studio's brand/wordmark.py from
       {options.mark.name} and the studio's fonts. the words are light, for the dark bars it sits on -->
  <g transform="translate(0 {number(-top)})">
    <g transform="translate(0 {number(mark_top)}) scale({number(mark_size / 100)})">
{mark}
    </g>
    <path id="name" d="{name}" fill="{ink}"/>
    <path id="product" d="{product}" fill="{teal}"/>
  </g>
</svg>
""")
print(f"{options.out.name} is {width} x {height}: its aspect ratio is {width}f / {height}f")
