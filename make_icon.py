from PIL import Image, ImageDraw
import math

SIZE = 512
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

def s(v): return v * SIZE / 108

def bezier3(p0, p1, p2, p3, steps=60):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        x = (1-t)**3*p0[0] + 3*(1-t)**2*t*p1[0] + 3*(1-t)*t**2*p2[0] + t**3*p3[0]
        y = (1-t)**3*p0[1] + 3*(1-t)**2*t*p1[1] + 3*(1-t)*t**2*p2[1] + t**3*p3[1]
        pts.append((x, y))
    return pts

def bezier2(p0, p1, p2, steps=40):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        x = (1-t)**2*p0[0] + 2*(1-t)*t*p1[0] + t**2*p2[0]
        y = (1-t)**2*p0[1] + 2*(1-t)*t*p1[1] + t**2*p2[1]
        pts.append((x, y))
    return pts

# ── 배경 원 ──
draw.ellipse([0, 0, SIZE-1, SIZE-1], fill=(26, 35, 126, 255))

# ── 사운드 웨이브 (벨 뒤에 그리기) ──
draw.arc([s(6),  s(31), s(28), s(77)], start=300, end=60,  fill=(255,255,255,80),  width=int(s(2.5)))
draw.arc([s(16), s(38), s(32), s(70)], start=300, end=60,  fill=(255,255,255,160), width=int(s(2.8)))
draw.arc([s(76), s(38), s(92), s(70)], start=120, end=240, fill=(255,255,255,160), width=int(s(2.8)))
draw.arc([s(80), s(31), s(102),s(77)], start=120, end=240, fill=(255,255,255,80),  width=int(s(2.5)))

# ── 벨 몸통 ──
bell = []
# 왼쪽 곡선: 상단(54,31) → 컨트롤(36,31),(32,42) → (32,54)
bell += bezier3((s(54),s(31)), (s(38),s(31)), (s(32),s(42)), (s(32),s(55)))
# 하단 왼쪽
bell += [(s(32), s(63))]
bell += bezier2((s(32),s(68)), (s(32),s(68)), (s(38),s(68)))
# 하단 가로선
bell += [(s(70), s(68))]
# 하단 오른쪽 모서리
bell += bezier2((s(76),s(68)), (s(76),s(68)), (s(76),s(63)))
# 오른쪽 직선
bell += [(s(76), s(55))]
# 오른쪽 곡선: (76,55) → (76,42),(70,31) → (54,31)
bell += bezier3((s(76),s(55)), (s(76),s(42)), (s(70),s(31)), (s(54),s(31)))

draw.polygon(bell, fill=(255, 255, 255, 255))

# 하단 림 음영
rim = []
rim += bezier2((s(32),s(68)), (s(32),s(68)), (s(38),s(68)))
rim += [(s(70),s(68))]
rim += bezier2((s(76),s(68)), (s(76),s(68)), (s(76),s(63)))
rim += [(s(76),s(61)), (s(32),s(61))]
draw.polygon(rim, fill=(0, 0, 0, 30))

# ── 손잡이 ──
handle = bezier3((s(51),s(23)), (s(51),s(17)), (s(57),s(17)), (s(57),s(23)))
handle += [(s(57),s(31)), (s(51),s(31))]
draw.polygon(handle, fill=(255, 255, 255, 255))

# ── 추 ──
draw.ellipse([s(49.5), s(67.5), s(58.5), s(76.5)], fill=(255, 255, 255, 255))

# ── 오렌지 배지 ──
draw.ellipse([s(58), s(17), s(80), s(39)], fill=(255, 109, 0, 255))

# K 글자
lw = max(3, int(s(2.3)))
cx = s(69)
draw.line([(cx, s(23)), (cx, s(33))], fill=(255,255,255,255), width=lw)
draw.line([(cx, s(28)), (s(74), s(23))], fill=(255,255,255,255), width=lw)
draw.line([(cx, s(28)), (s(74), s(33))], fill=(255,255,255,255), width=lw)

img.save("app/src/main/res/drawable/ic_store_icon", "PNG")
print("완료: 512x512 ic_store_icon")
