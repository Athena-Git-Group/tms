import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_glass.dart';

/// 浮動按鈕上畫哪一個圖示。
///
/// 兩個圖示都自己畫，不取 [CupertinoIcons] 的字形。字形的筆畫粗細由字型決定，
/// 而這兩顆按鈕的線稿粗細是設計定下來的可量測值（加號 2.6pt、問號泡泡 2pt），
/// 取字形就只能接受字型給的粗細，56pt 的圓鈕上差一截看得出來。
enum TmsFabGlyph {
  /// 加號。工務首頁的「開工單」用它。
  plus,

  /// 帶問號的對話泡泡。任務頁的「查一下」用它。
  ask,
}

/// 浮在內容右下角的圓形浮動按鈕。
///
/// 一顆 56 見方的毛玻璃圓鈕，裡面只有一個圖示，按鈕的用途靠無障礙標籤說明。
/// 工務首頁的「開工單」與任務頁的「查一下」共用這一顆，兩邊只有圖示、標籤與
/// 按下去做什麼不同。
///
/// ## 為什麼只畫圖示
///
/// 這顆按鈕壓在捲動內容上面，底下常常是單卡或清單文字。放文字就得把底板加寬，
/// 蓋掉的內容跟著變多；圖示配上固定的 56 見方，員工一眼認得出它是浮在上面的
/// 動作而不是版面的一部分。看不見的那層意思由 [semanticsLabel] 說完整，
/// VoiceOver 讀得到。
///
/// ## 定位由呼叫端負責
///
/// 這個元件只畫自己，不決定自己浮在哪裡。擺放的畫面用 [Stack] 配
/// [Positioned]，右邊距固定 [sideInset]，下緣位置依畫面底下壓著什麼而不同，
/// 兩種算法分別是 [bottomAboveTabBar] 與 [bottomAboveActionBar]。
class TmsFab extends StatelessWidget {
  const TmsFab({
    super.key,
    required this.glyph,
    required this.semanticsLabel,
    required this.onPressed,
  });

  final TmsFabGlyph glyph;

  /// 唸給 VoiceOver 聽的用途，例如「開工單」。按鈕上沒有文字，這一句是螢幕
  /// 報讀時唯一的說明。
  final String semanticsLabel;

  final VoidCallback onPressed;

  /// 按鈕直徑。`Main.dc.html:1030` — width/height 56px
  static const double diameter = 56;

  /// 按鈕離畫面右邊的距離。`Main.dc.html:1030` — right: 16px
  static const double sideInset = 16;

  /// 浮在分頁列上方時，按鈕下緣離畫面底的距離。
  ///
  /// `Main.dc.html:4051` 的 `max(safeBottom - 13, 10) + 62 + 16`，也就是浮動
  /// 分頁列整個佔掉的那一段再往上 16。[bottomSafeAreaInset] 取
  /// `MediaQuery.paddingOf(context).bottom`。
  static double bottomAboveTabBar(double bottomSafeAreaInset) {
    return TmsSpacing.bottomScrollEdgeHeight(bottomSafeAreaInset) + 16;
  }

  /// 浮在置底動作列上方時，按鈕下緣離可用區域底部的距離。
  ///
  /// `Main.dc.html:5027` 的 `hasPrimary ? 16 : safeBottom + 16`。有動作列時它
  /// 自己已經讓開了底部安全區，按鈕只要離動作列 16；沒有動作列時按鈕直接壓在
  /// 畫面最底下，要自己讓開安全區，否則會疊到 home indicator 上。
  static double bottomAboveActionBar({
    required bool hasActionBar,
    required double bottomSafeAreaInset,
  }) {
    return (hasActionBar ? 0 : bottomSafeAreaInset) + 16;
  }

  /// 被這顆按鈕蓋住的捲動區，底部要留的空白。
  ///
  /// `Main.dc.html:5028` — 按鈕下緣位置再加 72。按鈕本身 56，上下各留一點，
  /// 捲到底時最後一列才整列看得到，不會有一半藏在按鈕底下。
  static double scrollPadBottom(double fabBottom) => fabBottom + 72;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: semanticsLabel,
      button: true,
      child: CupertinoButton(
        padding: EdgeInsets.zero,
        minimumSize: const Size(diameter, diameter),
        onPressed: onPressed,
        child: TmsGlass(
          blur: _glassBlur,
          saturation: _glassSaturation,
          color: TmsColors.fabSurface,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
          // `Main.dc.html:4055` — 1px solid，不是分隔線那種半點細線。
          border: Border.all(color: TmsColors.fabBorder),
          boxShadow: const <BoxShadow>[
            BoxShadow(
              color: TmsColors.fabShadow,
              offset: Offset(0, 8),
              blurRadius: 24,
            ),
          ],
          child: SizedBox(
            width: diameter,
            height: diameter,
            child: Stack(
              children: [
                Center(child: _TmsFabGlyphIcon(glyph: glyph)),
                // 上緣那條高光。原型寫成 box-shadow 的 inset 段，
                // BoxDecoration 畫不出 inset 陰影，所以自己疊一條線；玻璃底板
                // 的圓角裁切會把它收成圓頂上的一小段。
                const Positioned(
                  top: 0,
                  left: 0,
                  right: 0,
                  height: _highlightThickness,
                  child: ColoredBox(color: TmsColors.fabHighlight),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// `Main.dc.html:4056` — blur(16px)
const double _glassBlur = 16;

/// `Main.dc.html:4056` — saturate(180%)
const double _glassSaturation = 180;

/// `Main.dc.html:4056` — inset 0 1px 0
const double _highlightThickness = 1;

class _TmsFabGlyphIcon extends StatelessWidget {
  const _TmsFabGlyphIcon({required this.glyph});

  final TmsFabGlyph glyph;

  @override
  Widget build(BuildContext context) {
    switch (glyph) {
      case TmsFabGlyph.plus:
        return const CustomPaint(
          size: Size(26, 26),
          painter: TmsFabGlyphPainter(TmsFabGlyph.plus),
        );
      case TmsFabGlyph.ask:
        return const CustomPaint(
          size: Size(24, 24),
          painter: TmsFabGlyphPainter(TmsFabGlyph.ask),
        );
    }
  }
}

/// 兩個浮動按鈕圖示的線稿。
///
/// 原型用 24 單位見方的 SVG viewBox 寫座標，兩個圖示各自放到自己的實際尺寸
/// （加號 26、問號泡泡 24）。這裡照原尺寸算一個縮放倍率，座標與筆畫粗細一起
/// 乘上去，圖示放大時筆畫才跟著變粗，不會變成細線。
///
/// 公開是為了讓元件測試能單獨比對線稿，畫面不要直接用它，走 [TmsFab]。
class TmsFabGlyphPainter extends CustomPainter {
  const TmsFabGlyphPainter(this.glyph);

  final TmsFabGlyph glyph;

  /// `Main.dc.html:5029` — iOS 26 的前景色 #0C7680
  static const Color strokeColor = TmsColors.brandPrimary;

  /// 原型 SVG 的 viewBox 邊長。
  static const double _viewBox = 24;

  /// 加號的筆畫。`Main.dc.html:1031` — stroke-width 2.4
  static const double plusStrokeWidth = 2.4;

  /// 問號泡泡的筆畫。`Main.dc.html:1346` — stroke-width 2
  static const double askStrokeWidth = 2;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / _viewBox;
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..color = strokeColor
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..strokeWidth =
          (glyph == TmsFabGlyph.plus ? plusStrokeWidth : askStrokeWidth) *
          scale;

    canvas.save();
    canvas.scale(scale);
    canvas.drawPath(
      glyph == TmsFabGlyph.plus ? _plusPath() : _askPath(),
      paint,
    );
    canvas.restore();
  }

  /// `Main.dc.html:1031` — M12 5v14 M5 12h14
  Path _plusPath() {
    return Path()
      ..moveTo(12, 5)
      ..lineTo(12, 19)
      ..moveTo(5, 12)
      ..lineTo(19, 12);
  }

  /// `Main.dc.html:1346` — 對話泡泡的外框、問號的鉤，與下方那一點。
  Path _askPath() {
    const bubbleRadius = Radius.circular(8);
    return Path()
      ..moveTo(20, 12)
      ..arcToPoint(
        const Offset(8.4, 19.1),
        radius: bubbleRadius,
        clockwise: true,
      )
      ..lineTo(4, 20)
      ..lineTo(5, 15.9)
      ..arcToPoint(
        const Offset(20, 12),
        radius: bubbleRadius,
        largeArc: true,
        clockwise: true,
      )
      ..moveTo(9.8, 9.6)
      ..arcToPoint(
        const Offset(14.2, 10.5),
        radius: const Radius.circular(2.3),
        clockwise: true,
      )
      ..cubicTo(14.2, 12, 12, 12.4, 12, 13.6)
      ..moveTo(12, 16.4)
      ..lineTo(12.01, 16.4);
  }

  @override
  bool shouldRepaint(TmsFabGlyphPainter oldDelegate) =>
      oldDelegate.glyph != glyph;
}
