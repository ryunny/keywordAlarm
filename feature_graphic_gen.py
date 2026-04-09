"""
Play Store 그래픽 이미지 생성 (1024x500px) - 모던 버전
- 그라디언트 배경
- 중앙 벨 아이콘 크게
- 키워드 버블 카드 플로팅
- 심플하고 임팩트 있는 슬로건
"""
from PIL import Image, ImageDraw, ImageFont
import math

SCALE = 3
W = 1024 * SCALE  # 3072
H = 500  * SCALE  # 1500

img  = Image.new("RGB", (W, H), "#0D1757")
draw = ImageDraw.Draw(img)

# ── 그라디언트 배경 (좌: 진남색 → 우: 중간 인디고) ────
for x in range(W):
    t = x / W
    r = int(13  + (30  - 13)  * t)
    g = int(23  + (35  - 23)  * t)
    b = int(87  + (120 - 87)  * t)
    draw.line([(x, 0), (x, H)], fill=(r, g, b))

# ── 배경 원형 글로우 (벨 뒤) ─────────────────────────
def draw_glow(cx, cy, max_r, color, steps=40):
    for i in range(steps, 0, -1):
        r = int(max_r * i / steps)
        a = int(40 * (1 - i/steps) ** 1.5)
        overlay = Image.new("RGBA", (W, H), (0,0,0,0))
        ov = ImageDraw.Draw(overlay)
        ov.ellipse([cx-r, cy-r, cx+r, cy+r], fill=(*color, a))
        base = img.convert("RGBA")
        img.paste(Image.alpha_composite(base, overlay).convert("RGB"))

draw_glow(W//2, H//2, 260*SCALE, (100, 120, 255))

# ── 폰트 ──────────────────────────────────────────────
def load_font(path, size):
    try:    return ImageFont.truetype(path, size)
    except: return ImageFont.load_default()

f_bold  = load_font("C:/Windows/Fonts/malgunbd.ttf", 50*SCALE)
f_med   = load_font("C:/Windows/Fonts/malgunbd.ttf", 28*SCALE)
f_small = load_font("C:/Windows/Fonts/malgun.ttf",   20*SCALE)
f_chip  = load_font("C:/Windows/Fonts/malgunbd.ttf", 20*SCALE)

draw = ImageDraw.Draw(img)

# ── 키워드 버블 카드 ──────────────────────────────────
def draw_chip(x, y, text, alpha=230):
    pad_x, pad_y = 28*SCALE, 14*SCALE
    bbox = draw.textbbox((0,0), text, font=f_chip)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    bx1, by1 = x, y
    bx2, by2 = x + tw + pad_x*2, y + th + pad_y*2

    # 카드 배경
    card = Image.new("RGBA", (W, H), (0,0,0,0))
    cd   = ImageDraw.Draw(card)
    cd.rounded_rectangle([bx1, by1, bx2, by2],
                         radius=int(0.5*(by2-by1)),
                         fill=(255,255,255, alpha))
    img.paste(Image.alpha_composite(img.convert("RGBA"), card).convert("RGB"))

    draw2 = ImageDraw.Draw(img)
    draw2.text((bx1 + pad_x, by1 + pad_y), text,
               fill="#1A237E", font=f_chip)
    return bx2 - bx1  # 칩 너비 반환

# 알림 카드 (왼쪽 상단)
def draw_notif_card(x, y, app, title, keyword, alpha=210):
    cw, ch = 270*SCALE, 90*SCALE
    card = Image.new("RGBA", (W, H), (0,0,0,0))
    cd   = ImageDraw.Draw(card)
    cd.rounded_rectangle([x, y, x+cw, y+ch],
                         radius=14*SCALE,
                         fill=(255,255,255, alpha))
    img.paste(Image.alpha_composite(img.convert("RGBA"), card).convert("RGB"))
    d2 = ImageDraw.Draw(img)
    # 앱 이름
    d2.text((x+18*SCALE, y+12*SCALE), app,   fill=(120,120,160), font=f_small)
    # 내용 (키워드 강조)
    d2.text((x+18*SCALE, y+38*SCALE), title, fill="#1A237E",     font=f_med)
    # 알림음 아이콘
    d2.ellipse([x+cw-42*SCALE, y+12*SCALE, x+cw-18*SCALE, y+36*SCALE],
               fill="#FF6D00")

# ── 알림 카드 배치 ────────────────────────────────────
draw_notif_card(38*SCALE,  40*SCALE,  "카카오톡",  "🔔 배달 도착했어요",    "배달", alpha=240)
draw_notif_card(38*SCALE,  155*SCALE, "문자메시지", "🔔 긴급 공지입니다",    "긴급", alpha=200)
draw_notif_card(38*SCALE,  270*SCALE, "라인",      "🔔 엄마: 전화해",       "엄마", alpha=180)

# ── 중앙 벨 아이콘 ────────────────────────────────────
icon_path = r"C:\Users\Admin\AndroidStudioProjects\keywordAlarm\app\src\main\res\drawable\ic_store_icon.png"
icon_size = int(260*SCALE)
icon = Image.open(icon_path).convert("RGBA").resize((icon_size, icon_size), Image.LANCZOS)
icon_x = (W - icon_size) // 2
icon_y = (H - icon_size) // 2
img.paste(icon, (icon_x, icon_y), icon)

# ── 오른쪽 텍스트 ─────────────────────────────────────
draw = ImageDraw.Draw(img)
tx = int(W * 0.60)
cy = H // 2

# 슬로건
draw.text((tx, cy - 90*SCALE), "중요한 알림만",       fill="white",        font=f_bold, anchor="lm")
draw.text((tx, cy - 20*SCALE), "소리로 받으세요",      fill="white",        font=f_bold, anchor="lm")

# 주황 밑줄
lx1, lx2 = tx, tx + 300*SCALE
draw.rectangle([lx1, cy+38*SCALE, lx2, cy+44*SCALE], fill="#FF6D00")

# 키워드 버블 (오른쪽 하단)
bx = tx
by = cy + 65*SCALE
chips = ["# 배달도착", "# 긴급", "# 엄마", "# 공지"]
gap = 14*SCALE
for chip in chips:
    w = draw_chip(bx, by, chip, alpha=200)
    bx += w + gap

# ── 1024×500 출력 ──────────────────────────────────────
out = img.resize((1024, 500), Image.LANCZOS)
out_path = r"C:\Users\Admin\Desktop\사업 구상품\feature_graphic.png"
out.save(out_path, "PNG")
print("saved:", out_path)
