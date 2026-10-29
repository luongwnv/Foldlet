#!/usr/bin/env python3
"""Rebuild the approved A mark and original, outlined geometric wordmark."""
from pathlib import Path

OUT = Path(__file__).resolve().parent / "final"
MARK = "M32 32H176L224 80V144H176V80H32Z M32 112H80V176H224V224H80L32 176Z"
TEAL, PALE_BLUE, CYAN = "#006F95", "#D8F2FC", "#7DDCEA"
# Original lettering, constructed here; no font file or live SVG text required.
GLYPHS = {
    "L": (40, "M0 0H10V54H40V64H0Z"),
    "I": (10, "M0 0H10V64H0Z"),
    "T": (44, "M0 0H44V10H27V64H17V10H0Z"),
    "S": (42, "M42 0H10Q0 0 0 10V26Q0 36 10 36H30Q32 36 32 38V52Q32 54 30 54H0V64H32Q42 64 42 54V36Q42 26 32 26H12Q10 26 10 24V12Q10 10 12 10H42Z"),
    "C": (42, "M42 0H10Q0 0 0 10V54Q0 64 10 64H42V54H13Q10 54 10 51V13Q10 10 13 10H42Z"),
    "R": (46, "M0 0H32Q44 0 44 12V24Q44 36 32 36H28L46 64H33L10 36V64H0Z M10 10V26H30Q34 26 34 22V14Q34 10 30 10Z"),
    "E": (38, "M0 0H38V10H10V27H34V37H10V54H38V64H0Z"),
    "N": (54, "M0 0H12L44 45V0H54V64H42L10 19V64H0Z"),
    "O": (44, "M10 0H34Q44 0 44 10V54Q44 64 34 64H10Q0 64 0 54V10Q0 0 10 0Z M13 10Q10 10 10 13V51Q10 54 13 54H31Q34 54 34 51V13Q34 10 31 10Z"),
}


def letters(text, gap):
    paths, x = [], 0
    for char in text:
        if char == " ":
            x += 24
            continue
        width, data = GLYPHS[char]
        paths.append(f'<path transform="translate({x} 0)" fill-rule="evenodd" d="{data}"/>')
        x += width + gap
    return "".join(paths), x - gap


def wordmark():
    top, _ = letters("LIT", 14)
    bottom, _ = letters("SCREEN OS", 9)
    return f'<g transform="scale(1.5)">{top}</g><g transform="translate(0 122) scale(0.6)">{bottom}</g>'


def svg(name, width, height, body, title, color=TEAL):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{name}.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}" role="img" aria-labelledby="title">\n'
        f'<title id="title">LIT Screen OS — {title}</title>\n<g fill="{color}">{body}</g>\n</svg>\n'
    )


def main():
    symbol = f'<path d="{MARK}"/>'
    horizontal = symbol + f'<g transform="translate(288 48)">{wordmark()}</g>'
    stacked = f'<g transform="translate(32 0)">{symbol}</g><g transform="translate(32 280)">{wordmark()}</g>'
    for suffix, color in (("brand", TEAL), ("black", "#000000"), ("white", "#FFFFFF"), ("cyan", CYAN)):
        svg(f"symbol-{suffix}", 256, 256, symbol, f"Nếp mở, {suffix}", color)
        svg(f"horizontal-{suffix}", 576, 256, horizontal, f"logo ngang, {suffix}", color)
        svg(f"stacked-{suffix}", 320, 472, stacked, f"logo dọc, {suffix}", color)
        svg(f"wordmark-{suffix}", 288, 192, f'<g transform="translate(16 16)">{wordmark()}</g>', f"tên ứng dụng, {suffix}", color)
    # The same grid remains crisp at 16px: bars = 3px, gaps = 2px.
    svg("symbol-small", 256, 256, symbol, "Nếp mở, dùng từ 16 px")
    svg("app-tile", 256, 256, f'<rect width="256" height="256" fill="{PALE_BLUE}"/><g transform="translate(12.8 12.8) scale(0.9)">{symbol}</g>', "ô biểu tượng ứng dụng nền sáng", TEAL)
    # Native adaptive foreground has 108dp canvas; masked viewport is 72dp.
    res = Path(__file__).resolve().parents[2] / "app/src/main/res/drawable"
    adaptive = (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="256" android:viewportHeight="256">\n'
        '    <group android:scaleX="0.6" android:scaleY="0.6" android:pivotX="128" android:pivotY="128">\n'
        f'        <path android:fillColor="{TEAL}" android:pathData="{MARK}"/>\n'
        '    </group>\n</vector>\n'
    )
    (res / "ic_launcher_foreground.xml").write_text(adaptive)
    (res / "ic_launcher_monochrome.xml").write_text(adaptive.replace(TEAL, "#FFFFFF"))
    (res / "ic_status.xml").write_text(
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="256" android:viewportHeight="256">\n'
        f'    <path android:fillColor="#FFFFFF" android:pathData="{MARK}"/>\n</vector>\n'
    )


if __name__ == "__main__":
    main()
