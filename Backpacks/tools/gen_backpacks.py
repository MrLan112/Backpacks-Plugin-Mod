"""Generate detailed backpack item models + textures and the worn entity texture.

Every tier gets a 64x64 texture atlas and a 22-44 element item model; the worn
model (BackpackModel.java) uses the neutral entity texture.

Requirements: Python 3 and Pillow.

Usage from the Backpacks directory:
    python tools/gen_backpacks.py mod/src/main/resources
"""

import json
import os
import random
import sys
from PIL import Image, ImageDraw

TIERS = [
    (0xB08A5A, 0xE8D3A9),  # 1 linen
    (0x8B5A2B, 0xD9A066),  # 2 leather
    (0xC77B4E, 0xF0B27A),  # 3 copper
    (0xB8BCC4, 0xE8ECF2),  # 4 iron
    (0xE0B23C, 0xFFE38A),  # 5 gold
    (0xB83232, 0xFF6B6B),  # 6 redstone
    (0x2D5BD1, 0x7FA7FF),  # 7 lapis
    (0x8E5BC9, 0xD7B3FF),  # 8 amethyst
    (0x2AA65C, 0x7BEEA9),  # 9 emerald
    (0xD9D2C4, 0xFFF8EC),  # 10 quartz
    (0x3B2A55, 0x8A6BC4),  # 11 obsidian
    (0x35C7C0, 0x9BFFF8),  # 12 diamond
    (0xD9731C, 0xFFC24A),  # 13 blaze
    (0x1F7A56, 0x5CF2B0),  # 14 ender
    (0x4A3F3A, 0xA89A90),  # 15 netherite
    (0x7B2FBE, 0xE58BFF),  # 16 dragon
    (0x1E1436, 0xF5C542),  # 17 ultimate
]

# Texture atlas regions (x, y, w, h) in the 64x64 item texture.
ATLAS = {
    "body": (0, 0, 16, 16),
    "flap": (16, 0, 16, 16),
    "pocket": (32, 0, 16, 16),
    "strap": (48, 0, 8, 16),
    "metal": (56, 0, 8, 16),
    "lining": (0, 16, 16, 16),
    "handle": (16, 16, 8, 8),
    "emblem": (32, 16, 8, 8),
    "pad": (48, 16, 8, 8),
}

TEXTURE_KEYS = list(ATLAS.keys())

# The PNG is 64x64, while model UVs always use the 0-16 texture unit space
# (16 units span the whole texture, no matter how many pixels it has).
ATLAS_SCALE = 4.0


def clamp(value):
    return max(0, min(255, int(round(value))))


def rgb(color):
    if isinstance(color, tuple):
        return color[:3]
    return (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF


def shade(color, factor):
    r, g, b = rgb(color)
    return clamp(r * factor), clamp(g * factor), clamp(b * factor)


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


def noise_layer(size, base, seed, amount=10, dark=1.0):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            delta = rnd.randint(-amount, amount)
            px[x, y] = (clamp(base[0] + delta * dark),
                        clamp(base[1] + delta * dark),
                        clamp(base[2] + delta * dark), 255)
    return img


def draw_body(draw, box, base, seed, stitch=True):
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    rnd = random.Random(seed)
    # Vertical leather grain: every other column slightly lighter/darker.
    for x in range(x0, x1):
        c = mix(base, (255, 255, 255), 0.06 if x % 4 == 0 else 0.0)
        if x % 5 == 2:
            c = mix(c, (0, 0, 0), 0.06)
        draw.line([(x, y0), (x, y1 - 1)], fill=c + (255,))
    # Random wear spots.
    for _ in range(max(3, w * h // 40)):
        sx = rnd.randint(x0 + 1, x1 - 2)
        sy = rnd.randint(y0 + 1, y1 - 2)
        tone = mix(base, (255, 255, 255) if rnd.random() < 0.5 else (0, 0, 0), 0.12)
        draw.point([(sx, sy)], fill=tone + (255,))
    # Bevel: top/left dark, bottom/right light.
    edge_dark = mix(base, (0, 0, 0), 0.32) + (255,)
    edge_light = mix(base, (255, 255, 255), 0.28) + (255,)
    draw.line([(x0, y0), (x1 - 1, y0)], fill=edge_dark)
    draw.line([(x0, y0), (x0, y1 - 1)], fill=edge_dark)
    draw.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=edge_light)
    draw.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], fill=edge_light)
    if stitch:
        thread = mix(base, (255, 255, 255), 0.55) + (255,)
        for x in range(x0 + 2, x1 - 2, 2):
            draw.point([(x, y0 + 2)], fill=thread)
            draw.point([(x, y1 - 3)], fill=thread)


def draw_strap(draw, box, base):
    x0, y0, x1, y1 = box
    dark = mix(base, (0, 0, 0), 0.5) + (255,)
    draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=dark)
    hi = mix(base, (255, 255, 255), 0.12) + (255,)
    for y in range(y0 + 1, y1 - 1, 3):
        draw.line([(x0 + 1, y), (x1 - 2, y)], fill=hi)


def draw_metal(draw, box, accent):
    accent = rgb(accent)
    x0, y0, x1, y1 = box
    bands = [0.55, 0.85, 1.2, 1.0, 0.7]
    for i, y in enumerate(range(y0, y1)):
        factor = bands[min(len(bands) - 1, i * len(bands) // max(1, y1 - y0))]
        draw.line([(x0, y), (x1 - 1, y)], fill=shade(accent, factor) + (255,))
    # Highlight edge and rivets.
    draw.line([(x0, y0), (x0, y1 - 1)], fill=mix(accent, (255, 255, 255), 0.5) + (255,))
    draw.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], fill=mix(accent, (0, 0, 0), 0.45) + (255,))
    for cx, cy in ((x0 + 1, y0 + 1), (x1 - 2, y0 + 1), (x0 + 1, y1 - 2), (x1 - 2, y1 - 2)):
        draw.point([(cx, cy)], fill=mix(accent, (0, 0, 0), 0.6) + (255,))


def draw_emblem(draw, box, accent, seed):
    accent = rgb(accent)
    x0, y0, x1, y1 = box
    draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=shade(accent, 0.35) + (255,))
    cx = (x0 + x1 - 1) / 2
    cy = (y0 + y1 - 1) / 2
    for y in range(y0, y1):
        for x in range(x0, x1):
            dist = abs(x - cx) + abs(y - cy)
            if dist < (x1 - x0) * 0.42:
                t = dist / max(1.0, (x1 - x0) * 0.42)
                c = mix(mix(accent, (255, 255, 255), 0.25), shade(accent, 0.55), t)
                draw.point([(x, y)], fill=c + (255,))


def build_texture(tier, body, accent, path):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Fill regions with a base + grain first.
    for name, (x, y, w, h) in ATLAS.items():
        if name == "body":
            base = rgb(body)
        elif name == "flap":
            base = mix(rgb(body), (255, 255, 255), 0.12)
        elif name == "pocket":
            base = mix(rgb(body), (0, 0, 0), 0.08)
        elif name == "strap":
            base = rgb(body)
        elif name == "metal":
            base = rgb(accent)
        elif name == "lining":
            base = mix(rgb(body), (0, 0, 0), 0.6)
        elif name == "handle":
            base = mix(rgb(body), (0, 0, 0), 0.25)
        elif name == "emblem":
            base = rgb(accent)
        else:  # pad
            base = mix(rgb(body), (0, 0, 0), 0.35)
        region = noise_layer(max(w, h), base, tier * 97 + len(name), amount=6 if name != "metal" else 2)
        img.paste(region.crop((0, 0, w, h)), (x, y))

    draw = ImageDraw.Draw(img)
    for name, (x, y, w, h) in ATLAS.items():
        box = (x, y, x + w, y + h)
        if name == "body":
            draw_body(draw, box, rgb(body), tier * 13 + 1)
        elif name == "flap":
            draw_body(draw, box, mix(rgb(body), (255, 255, 255), 0.12), tier * 13 + 2)
            # Double stitching around the flap.
            inset = mix(rgb(body), (255, 255, 255), 0.6) + (255,)
            for x2 in range(x + 2, x + w - 2, 2):
                draw.point([(x2, y + 4)], fill=inset)
                draw.point([(x2, y + h - 5)], fill=inset)
        elif name == "pocket":
            draw_body(draw, box, mix(rgb(body), (0, 0, 0), 0.08), tier * 13 + 3)
        elif name == "strap":
            draw_strap(draw, box, rgb(body))
        elif name == "metal":
            draw_metal(draw, box, accent)
        elif name == "lining":
            draw_body(draw, box, mix(rgb(body), (0, 0, 0), 0.6), tier * 13 + 4, stitch=False)
        elif name == "handle":
            draw_strap(draw, box, rgb(body))
            seam = mix(rgb(body), (255, 255, 255), 0.45) + (255,)
            draw.line([(x + 1, y + h // 2), (x + w - 2, y + h // 2)], fill=seam)
        elif name == "emblem":
            draw_emblem(draw, box, accent, tier)
        else:  # pad
            draw_body(draw, box, mix(rgb(body), (0, 0, 0), 0.35), tier * 13 + 5)

    img.save(path)


def faces_for(material, w, h, d):
    rx, ry, rw, rh = ATLAS[material]

    def rect(u0, v0, ww, hh):
        scale = min(1.0, (rw - 3.0) / max(ww, 1.0), (rh - 3.0) / max(hh, 1.0))
        # Pixel coordinates of the atlas region become 0-16 model texture units.
        return [
            round(min(16.0, max(0.0, u0 / ATLAS_SCALE)), 3),
            round(min(16.0, max(0.0, v0 / ATLAS_SCALE)), 3),
            round(min(16.0, max(0.0, (u0 + ww * scale) / ATLAS_SCALE)), 3),
            round(min(16.0, max(0.0, (v0 + hh * scale) / ATLAS_SCALE)), 3),
        ]

    return {
        "north": {"uv": rect(rx + 1, ry + 1, w, h), "texture": "#" + material},
        "south": {"uv": rect(rx + 2, ry + 2, w, h), "texture": "#" + material},
        "east": {"uv": rect(rx + 1, ry + 2, d, h), "texture": "#" + material},
        "west": {"uv": rect(rx + 2, ry + 1, d, h), "texture": "#" + material},
        "up": {"uv": rect(rx + 2, ry + 2, w, d), "texture": "#" + material},
        "down": {"uv": rect(rx + 1, ry + 1, w, d), "texture": "#" + material},
    }


def element(x1, y1, z1, x2, y2, z2, material):
    x1, y1, z1, x2, y2, z2 = (round(v, 2) for v in (x1, y1, z1, x2, y2, z2))
    w, h, d = x2 - x1, y2 - y1, z2 - z1
    faces = faces_for(material, w, h, d)
    return {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}


def tier_elements(tier):
    parts = []

    def add(x1, y1, z1, x2, y2, z2, material):
        parts.append(element(x1, y1, z1, x2, y2, z2, material))

    # Main body and its reinforced bottom.
    add(3, 4.2, 6, 13, 13, 10, "body")
    add(2.9, 3.1, 5.9, 13.1, 4.5, 10.1, "pad")
    # Rolled top.
    add(3.2, 13.0, 6.2, 12.8, 14.3, 9.8, "body")
    add(3.0, 13.1, 6.05, 3.6, 14.15, 9.95, "strap")
    add(12.4, 13.1, 6.05, 13.0, 14.15, 9.95, "strap")
    # Flap with a front lip and a centre strap.
    add(2.7, 10.3, 5.55, 13.3, 13.25, 10.3, "flap")
    add(2.7, 8.9, 5.45, 13.3, 10.7, 6.15, "flap")
    add(7.3, 9.55, 4.5, 8.7, 12.5, 4.85, "strap")
    add(7.05, 8.2, 4.45, 8.95, 9.75, 4.95, "metal")
    # Front pocket with its own flap.
    add(4.7, 4.6, 4.8, 11.3, 8.7, 6.0, "pocket")
    add(4.5, 8.5, 4.7, 11.5, 9.9, 6.1, "flap")
    # Shoulder straps and their buckles.
    add(4.2, 4.1, 9.95, 6.0, 13.2, 10.45, "strap")
    add(10.0, 4.1, 9.95, 11.8, 13.2, 10.45, "strap")
    add(4.25, 6.4, 9.9, 5.95, 7.4, 10.5, "metal")
    add(10.05, 6.4, 9.9, 11.75, 7.4, 10.5, "metal")
    # Top handle.
    add(6.5, 14.15, 7.1, 9.5, 15.15, 8.9, "handle")
    add(6.35, 13.7, 6.95, 6.95, 14.35, 9.05, "strap")
    add(9.05, 13.7, 6.95, 9.65, 14.35, 9.05, "strap")
    # Rivets on the flap corners.
    for x in (3.0, 12.5):
        for y in (9.2, 12.2):
            add(x, y, 5.45, x + 0.5, y + 0.5, 5.7, "metal")

    if tier >= 3:
        # Side pockets.
        add(2.15, 5.0, 6.5, 3.05, 9.3, 9.5, "pocket")
        add(12.95, 5.0, 6.5, 13.85, 9.3, 9.5, "pocket")
    if tier >= 4:
        # Metal corner caps.
        for x in (2.8, 12.4):
            for z in (5.8, 9.4):
                add(x, 3.0, z, x + 0.8, 4.7, z + 0.8, "metal")
    if tier >= 5:
        add(2.9, 13.5, 5.7, 13.1, 14.4, 6.15, "metal")
    if tier >= 6:
        # Compression strap across the front pocket.
        add(4.6, 6.4, 4.68, 11.4, 7.0, 4.79, "strap")
    if tier >= 8:
        # Emblem plate on the flap.
        add(6.7, 10.9, 5.28, 9.3, 12.9, 5.44, "emblem")
    if tier >= 10:
        # Vertical reinforcement bands beside the pocket.
        add(3.15, 4.6, 5.9, 3.75, 10.0, 6.02, "metal")
        add(12.25, 4.6, 5.9, 12.85, 10.0, 6.02, "metal")
    if tier >= 12:
        # Bedroll on top with metal end caps.
        add(3.5, 14.25, 6.3, 12.5, 15.5, 9.7, "strap")
        add(3.3, 14.35, 6.15, 3.85, 15.4, 9.85, "metal")
        add(12.15, 14.35, 6.15, 12.7, 15.4, 9.85, "metal")
    if tier >= 14:
        # Shoulder strap pads.
        add(4.0, 10.8, 10.4, 6.1, 12.4, 10.8, "pad")
        add(9.9, 10.8, 10.4, 12.0, 12.4, 10.8, "pad")
    if tier >= 15:
        # Lock plate around the buckle.
        add(6.9, 7.9, 4.4, 9.1, 8.45, 4.97, "metal")
    if tier >= 17:
        # Star emblem above the buckle.
        add(7.6, 11.4, 5.2, 8.4, 12.2, 5.3, "emblem")
        add(7.85, 12.1, 5.2, 8.15, 12.6, 5.3, "emblem")
        add(7.85, 11.0, 5.2, 8.15, 11.5, 5.3, "emblem")
        add(7.2, 11.65, 5.2, 7.7, 11.95, 5.3, "emblem")
        add(8.3, 11.65, 5.2, 8.8, 11.95, 5.3, "emblem")

    return parts


def build_model(tier, body, accent, path):
    texture = "backpacks:item/backpack_%d" % tier
    model = {
        "parent": "minecraft:block/block",
        "textures": {"particle": texture},
        "elements": tier_elements(tier),
    }
    for key in TEXTURE_KEYS:
        model["textures"][key] = texture
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(model, handle, indent=2)
        handle.write("\n")


def build_item_definition(tier, path):
    definition = {
        "model": {
            "type": "minecraft:model",
            "model": "backpacks:item/backpack_%d" % tier,
        }
    }
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(definition, handle, indent=2)
        handle.write("\n")


def build_entity_texture(path):
    """Neutral leather grain tinted by the tier colours at render time."""
    size = 64
    rnd = random.Random(4242)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            v = 176 + rnd.randint(-14, 14)
            # Subtle panel seams every 16 pixels.
            if x % 16 == 3 or y % 16 == 3:
                v -= 18
            if x % 16 == 4 or y % 16 == 4:
                v += 10
            px[x, y] = (clamp(v), clamp(v), clamp(v), 255)
    ImageDraw.Draw(img).rectangle([0, 0, size - 1, size - 1], outline=(150, 150, 150, 255))
    img.save(path)


def main():
    if len(sys.argv) < 2:
        print("usage: gen_backpacks.py <mod resources dir>")
        return 1
    root = sys.argv[1]
    items = os.path.join(root, "assets", "backpacks", "items")
    models = os.path.join(root, "assets", "backpacks", "models", "item")
    textures = os.path.join(root, "assets", "backpacks", "textures", "item")
    entity = os.path.join(root, "assets", "backpacks", "textures", "entity")
    for path in (items, models, textures, entity):
        os.makedirs(path, exist_ok=True)

    for index, (body, accent) in enumerate(TIERS, start=1):
        build_model(index, body, accent, os.path.join(models, "backpack_%d.json" % index))
        build_item_definition(index, os.path.join(items, "backpack_%d.json" % index))
        build_texture(index, body, accent, os.path.join(textures, "backpack_%d.png" % index))
        print("tier", index, "elements", len(tier_elements(index)))
    build_entity_texture(os.path.join(entity, "backpack.png"))
    print("done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
