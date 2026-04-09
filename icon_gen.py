"""
ic_store_icon.png 생성 스크립트
- 남색 원형 배경
- 흰색 벨 + K 뱃지
- 좌우 웨이브: 바깥쪽으로 뻗는 곡선 (흰색)
"""

import math

SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <!-- 배경 원 -->
  <circle cx="256" cy="256" r="256" fill="#1A237E"/>

  <!-- 왼쪽 바깥쪽 웨이브 (굵은, 반투명) -->
  <path d="M148,185 Q90,256 148,327"
        fill="none" stroke="white" stroke-width="14" stroke-linecap="round" opacity="0.45"/>

  <!-- 왼쪽 안쪽 웨이브 -->
  <path d="M168,205 Q122,256 168,307"
        fill="none" stroke="white" stroke-width="16" stroke-linecap="round" opacity="0.75"/>

  <!-- 오른쪽 안쪽 웨이브 -->
  <path d="M344,205 Q390,256 344,307"
        fill="none" stroke="white" stroke-width="16" stroke-linecap="round" opacity="0.75"/>

  <!-- 오른쪽 바깥쪽 웨이브 (굵은, 반투명) -->
  <path d="M364,185 Q422,256 364,327"
        fill="none" stroke="white" stroke-width="14" stroke-linecap="round" opacity="0.45"/>

  <!-- 벨 손잡이 -->
  <rect x="240" y="118" width="32" height="34" rx="16" fill="white"/>

  <!-- 벨 몸체 -->
  <path d="M256 148 C192 148 152 194 152 254 L152 314 L360 314 L360 254 C360 194 320 148 256 148 Z"
        fill="white"/>

  <!-- 벨 하단 그림자 -->
  <path d="M152 300 L152 314 L360 314 L360 300 Z"
        fill="black" opacity="0.08"/>

  <!-- 벨 클래퍼 -->
  <circle cx="256" cy="354" r="20" fill="white"/>

  <!-- 주황색 K 뱃지 -->
  <circle cx="334" cy="158" r="48" fill="#FF6D00"/>
  <text x="334" y="176"
        font-family="Arial Black, Arial" font-weight="900" font-size="52"
        fill="white" text-anchor="middle">K</text>
</svg>"""

# SVG 저장
svg_path = r"C:\Users\Admin\AndroidStudioProjects\keywordAlarm\app\src\main\res\drawable\ic_store_icon_new.svg"
with open(svg_path, "w", encoding="utf-8") as f:
    f.write(SVG)

# PNG 변환 시도
try:
    import cairosvg
    png_path = r"C:\Users\Admin\AndroidStudioProjects\keywordAlarm\app\src\main\res\drawable\ic_store_icon.png"
    cairosvg.svg2png(bytestring=SVG.encode(), write_to=png_path, output_width=512, output_height=512)
    print(f"✅ PNG 저장 완료: {png_path}")
except ImportError:
    # cairosvg 없으면 Pillow + svglib 시도
    try:
        from svglib.svglib import svg2rlg
        from reportlab.graphics import renderPM
        drawing = svg2rlg(svg_path)
        png_path = r"C:\Users\Admin\AndroidStudioProjects\keywordAlarm\app\src\main\res\drawable\ic_store_icon.png"
        renderPM.drawToFile(drawing, png_path, fmt="PNG")
        print(f"✅ PNG 저장 완료 (svglib): {png_path}")
    except Exception as e:
        print(f"⚠️ PNG 변환 실패: {e}")
        print(f"SVG 파일은 저장됨: {svg_path}")
        print("Canva나 온라인 SVG→PNG 변환기로 변환해주세요.")
