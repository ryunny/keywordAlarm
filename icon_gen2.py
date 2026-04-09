"""
ic_store_icon.png 생성
- 108dp 벡터 원본(ic_launcher_foreground.xml)과 동일한 좌표계 사용
- 4x 오버샘플링 후 LANCZOS 축소 → 안티앨리어싱
"""
from PIL import Image, ImageDraw, ImageFont
import math

SCALE = 4          # 오버샘플 배수
SRC   = 108        # 원본 벡터 뷰포트 크기 (dp)
OUT   = 512        # 최종 출력 크기 (px)
W     = OUT * SCALE  # 작업 캔버스 = 2048

SF = W / SRC       # 좌표 변환 계수 (108→2048)

img  = Image.new("RGBA", (W, W), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# ── 헬퍼 ──────────────────────────────────────────────
def sc(v):
    """108dp 단일 값 → 캔버스 픽셀"""
    return v * SF

def pt(x, y):
    return (sc(x), sc(y))

def qbez_pts(x0, y0, cx, cy, x1, y1, steps=200):
    """이차 베지어 점 목록"""
    return [
        ((1-t)**2*x0 + 2*(1-t)*t*cx + t**2*x1,
         (1-t)**2*y0 + 2*(1-t)*t*cy + t**2*y1)
        for t in (i/steps for i in range(steps+1))
    ]

def cbez_pts(x0, y0, cx1, cy1, cx2, cy2, x1, y1, steps=200):
    """삼차 베지어 점 목록"""
    return [
        ((1-t)**3*x0 + 3*(1-t)**2*t*cx1 + 3*(1-t)*t**2*cx2 + t**3*x1,
         (1-t)**3*y0 + 3*(1-t)**2*t*cy1 + 3*(1-t)*t**2*cy2 + t**3*y1)
        for t in (i/steps for i in range(steps+1))
    ]

def draw_curve(pts, width_dp, alpha):
    """점 목록을 선으로 그리기 (108dp 좌표 → 캔버스)"""
    px = [(sc(x), sc(y)) for x, y in pts]
    w  = max(1, round(sc(width_dp)))
    a  = int(255 * alpha)
    for i in range(len(px) - 1):
        draw.line([px[i], px[i+1]], fill=(255, 255, 255, a), width=w)

def draw_poly(pts, fill):
    """108dp 좌표 폴리곤"""
    draw.polygon([(sc(x), sc(y)) for x, y in pts], fill=fill)

# ── 배경 원 ──────────────────────────────────────────
draw.ellipse([0, 0, W, W], fill="#1A237E")

# ── 사운드 웨이브 (원본 벡터 좌표 그대로) ─────────────
# 왼쪽 바깥: M24,36 Q10,54 24,72  stroke=2.5 alpha=0.45
draw_curve(qbez_pts(24,36, 10,54, 24,72), width_dp=2.5, alpha=0.45)
# 왼쪽 안쪽: M30,42 Q20,54 30,66  stroke=2.8 alpha=0.75
draw_curve(qbez_pts(30,42, 20,54, 30,66), width_dp=2.8, alpha=0.75)
# 오른쪽 안쪽: M78,42 Q88,54 78,66
draw_curve(qbez_pts(78,42, 88,54, 78,66), width_dp=2.8, alpha=0.75)
# 오른쪽 바깥: M84,36 Q98,54 84,72
draw_curve(qbez_pts(84,36, 98,54, 84,72), width_dp=2.5, alpha=0.45)

# ── 벨 손잡이: M51,22 Q54,18 57,22 L57,30 L51,30 Z ──
handle_pts  = qbez_pts(51,22, 54,18, 57,22, steps=60)
handle_pts += [(57,30), (51,30)]
draw_poly(handle_pts, fill="white")

# ── 벨 몸통 폴리곤 조립 ──────────────────────────────
# M54,30
# C40,30 32,42 32,54   (왼쪽 곡선)
# L32,62
# Q32,67 37,67         (왼쪽 아래 모서리)
# L71,67
# Q76,67 76,62         (오른쪽 아래 모서리)
# L76,54
# C76,42 68,30 54,30   (오른쪽 곡선)  Z

bell_pts  = cbez_pts(54,30, 40,30, 32,42, 32,54)    # 왼쪽 돔
bell_pts += [(32,62)]                                 # 왼쪽 직선
bell_pts += qbez_pts(32,62, 32,67, 37,67, steps=30)  # 왼쪽 모서리
bell_pts += [(71,67)]                                 # 바닥 림
bell_pts += qbez_pts(71,67, 76,67, 76,62, steps=30)  # 오른쪽 모서리
bell_pts += [(76,54)]                                 # 오른쪽 직선
bell_pts += cbez_pts(76,54, 76,42, 68,30, 54,30)     # 오른쪽 돔

draw_poly(bell_pts, fill="white")

# 림 하단 그림자 (약간 어두운 색)
shadow_pts  = [(32,62)]
shadow_pts += qbez_pts(32,62, 32,67, 37,67, steps=30)
shadow_pts += [(71,67)]
shadow_pts += qbez_pts(71,67, 76,67, 76,62, steps=30)
shadow_pts += [(76,58), (32,58)]
draw_poly(shadow_pts, fill=(0, 0, 0, 20))

# ── 클래퍼: M59,67 A5,5 0 1,1 49,67 ──────────────────
# 반원(아래쪽) 으로 그리기
clapper_cx, clapper_cy, clapper_r = 54, 67, 5
draw.ellipse([
    sc(clapper_cx - clapper_r), sc(clapper_cy - clapper_r),
    sc(clapper_cx + clapper_r), sc(clapper_cy + clapper_r)
], fill="white")

# ── 주황 K 뱃지 ──────────────────────────────────────
# 원본: M78,28 A10,10 = 중심(68,28) r=10
badge_cx, badge_cy, badge_r = 68, 28, 10
draw.ellipse([
    sc(badge_cx - badge_r), sc(badge_cy - badge_r),
    sc(badge_cx + badge_r), sc(badge_cy + badge_r)
], fill="#FF6D00")

# K 글자
try:
    font_size = round(sc(12.5))
    font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", font_size)
except:
    font = ImageFont.load_default()

draw.text((sc(badge_cx), sc(badge_cy + 0.5)), "K",
          fill="white", font=font, anchor="mm")

# ── 512×512 출력 ──────────────────────────────────────
out = img.resize((OUT, OUT), Image.LANCZOS)
out_path = r"C:\Users\Admin\AndroidStudioProjects\keywordAlarm\app\src\main\res\drawable\ic_store_icon.png"
out.save(out_path, "PNG")
print("saved:", out_path)
