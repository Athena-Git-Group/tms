import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_category_tile.dart';
import 'tms_glass.dart';

/// 分頁列上一格要畫的圖示。
///
/// 四個圖示都是同一套 1.9pt 線稿，所以不混用系統字形：字形的筆畫粗細由字型
/// 決定，和旁邊三個自繪圖示對不起來，四格並排時看得出其中一格比較重。
enum TmsTabGlyph { home, pool, notification, me }

/// 分頁列上的一格。
class TmsTabItem {
  const TmsTabItem({
    required this.label,
    required this.glyph,
    this.badgeCount = 0,
  });

  /// 圖示底下那行字。
  final String label;

  final TmsTabGlyph glyph;

  /// 右上角紅色數量徽章上的數字。0 表示整顆徽章不畫，理由見 [TmsCountBadge]
  /// 的類別註解。
  final int badgeCount;
}

/// 浮動的毛玻璃分頁列。
///
/// 它是一塊浮在內容上方的玻璃板，不是把內容往上推的實心列：左右各離畫面邊
/// 20，下緣離畫面底 [TmsSpacing.floatingTabBarBottom]，高 62、圓角 31。內容
/// 從它底下捲過去，所以捲動容器得自己讓開 [TmsSpacing.scrollPadBottom] 那麼
/// 多底部留白，否則最後一列會永遠被這塊玻璃蓋住。
///
/// 定位由呼叫端負責（外殼用 [Stack] 擺它），這個元件只畫自己。
class TmsTabBar extends StatelessWidget {
  const TmsTabBar({
    super.key,
    required this.items,
    required this.currentIndex,
    required this.onSelect,
  });

  final List<TmsTabItem> items;

  final int currentIndex;

  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    final cells = <Widget>[];
    for (var i = 0; i < items.length; i++) {
      if (i > 0) {
        cells.add(const SizedBox(width: TmsSize.floatingTabBarGap));
      }
      cells.add(
        Expanded(
          child: _TabCell(
            item: items[i],
            selected: i == currentIndex,
            onTap: () => onSelect(i),
          ),
        ),
      );
    }

    return TmsGlass(
      blur: _glassBlur,
      saturation: _glassSaturation,
      color: TmsColors.glassSurface,
      borderRadius: BorderRadius.circular(TmsSize.floatingTabBarRadius),
      border: Border.all(color: TmsColors.glassBorder, width: TmsSize.hairline),
      boxShadow: const <BoxShadow>[
        BoxShadow(
          color: TmsColors.glassShadow,
          offset: Offset(0, 4),
          blurRadius: 16,
        ),
      ],
      child: SizedBox(
        height: TmsSize.floatingTabBarHeight,
        child: Stack(
          children: [
            Positioned.fill(
              child: Padding(
                padding: const EdgeInsets.all(TmsSize.floatingTabBarPadding),
                child: Row(children: cells),
              ),
            ),
            // 上緣那條高光。原型寫成 box-shadow 的 inset 段，BoxDecoration 畫
            // 不出 inset 陰影，所以改成自己疊一條線；玻璃底板的圓角裁切會把
            // 它的兩端收成跟邊框一樣的弧度。
            const Positioned(
              top: 0,
              left: 0,
              right: 0,
              height: _highlightThickness,
              child: ColoredBox(color: TmsColors.glassHighlight),
            ),
          ],
        ),
      ),
    );
  }
}

/// 分頁列底下那層 soft scroll edge。
///
/// 它把分頁列佔到的那一段畫面壓淡並模糊，捲動內容從它底下透出來，員工因此
/// 看得出清單還沒到底。上緣用漸層淡出，不是一條硬邊。
class TmsBottomScrollEdge extends StatelessWidget {
  const TmsBottomScrollEdge({super.key, required this.height});

  final double height;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: ClipRect(
        child: BackdropFilter(
          filter: TmsGlass.filterFor(
            blur: _edgeBlur,
            saturation: _edgeSaturation,
          ),
          child: SizedBox(
            height: height,
            width: double.infinity,
            child: const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.bottomCenter,
                  end: Alignment.topCenter,
                  colors: <Color>[
                    TmsColors.bottomScrollEdge,
                    TmsColors.bottomScrollEdge,
                    _edgeFadeOut,
                  ],
                  stops: <double>[0, 0.4, 1],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 分頁列的一格：膠囊底、圖示、文字，右上角可掛一顆徽章。
class _TabCell extends StatelessWidget {
  const _TabCell({
    required this.item,
    required this.selected,
    required this.onTap,
  });

  final TmsTabItem item;

  final bool selected;

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = selected ? TmsColors.brandPrimary : TmsColors.labelSoft;

    // 整格併成一個無障礙節點。驗收工具是靠「標籤含某幾個字的那個元素」找分頁
    // 再點下去的，一格拆成圖示、文字、徽章三個節點的話，它點到的可能是文字那
    // 個子節點，點擊會落在整格可點範圍之外。
    return MergeSemantics(
      child: Semantics(
        button: true,
        selected: selected,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onTap,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: selected
                  ? TmsColors.tabBarSelectedPill
                  : const Color(0x00000000),
              borderRadius: BorderRadius.circular(
                TmsSize.floatingTabBarPillRadius,
              ),
            ),
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                Positioned.fill(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      _TabGlyphIcon(glyph: item.glyph, color: color),
                      const SizedBox(height: _iconLabelGap),
                      Text(
                        item.label,
                        style: TmsText.caption3.copyWith(color: color),
                      ),
                    ],
                  ),
                ),
                if (item.badgeCount > 0)
                  Positioned.fill(child: _TabBadgeSlot(count: item.badgeCount)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 把徽章擺在整格寬度 56% 的位置上。
///
/// 百分比的基準是整顆分頁按鈕的寬度，不是圖示的寬度，所以要量到這一格實際
/// 分到多寬才算得出來。
class _TabBadgeSlot extends StatelessWidget {
  const _TabBadgeSlot({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        return Stack(
          clipBehavior: Clip.none,
          children: [
            Positioned(
              top: _badgeTop,
              left: constraints.maxWidth * _badgeLeftRatio,
              child: TmsCountBadge.tabBar(count: count),
            ),
          ],
        );
      },
    );
  }
}

/// 一格分頁的圖示。
class _TabGlyphIcon extends StatelessWidget {
  const _TabGlyphIcon({required this.glyph, required this.color});

  final TmsTabGlyph glyph;

  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: TmsSize.floatingTabBarIcon,
      height: TmsSize.floatingTabBarIcon,
      child: CustomPaint(
        painter: _TabGlyphPainter(glyph: glyph, color: color),
      ),
    );
  }
}

/// 照原型的 SVG 線稿畫四個分頁圖示。
///
/// 路徑座標直接取原型 `viewBox="0 0 24 24"` 的數字，畫之前把畫布縮放到實際
/// 邊長，所以同一組座標在任何尺寸下都對得上。筆畫粗細跟著一起縮放。
class _TabGlyphPainter extends CustomPainter {
  const _TabGlyphPainter({required this.glyph, required this.color});

  final TmsTabGlyph glyph;

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = _glyphStroke
      ..strokeCap = StrokeCap.round
      // 房子那一個原型只給了 stroke-linecap，沒給 stroke-linejoin，轉角是
      // 預設的尖角；另外三個都指定了圓角轉角。
      ..strokeJoin = glyph == TmsTabGlyph.home
          ? StrokeJoin.miter
          : StrokeJoin.round
      ..color = color;

    canvas.save();
    canvas.scale(size.width / _glyphViewBox, size.height / _glyphViewBox);
    canvas.drawPath(_pathFor(glyph), paint);
    canvas.restore();
  }

  @override
  bool shouldRepaint(_TabGlyphPainter oldDelegate) {
    return oldDelegate.glyph != glyph || oldDelegate.color != color;
  }

  static Path _pathFor(TmsTabGlyph glyph) {
    switch (glyph) {
      // Main.dc.html:1129 — M4 11l8-6.5 8 6.5V20H4z M10 20v-5h4v5
      case TmsTabGlyph.home:
        return Path()
          ..moveTo(4, 11)
          ..lineTo(12, 4.5)
          ..lineTo(20, 11)
          ..lineTo(20, 20)
          ..lineTo(4, 20)
          ..close()
          ..moveTo(10, 20)
          ..lineTo(10, 15)
          ..lineTo(14, 15)
          ..lineTo(14, 20);
      // Main.dc.html:1130 — M4 13h4l1.5 3h5L16 13h4M4 13l2.5-7h11L20 13v5H4z
      case TmsTabGlyph.pool:
        return Path()
          ..moveTo(4, 13)
          ..lineTo(8, 13)
          ..lineTo(9.5, 16)
          ..lineTo(14.5, 16)
          ..lineTo(16, 13)
          ..lineTo(20, 13)
          ..moveTo(4, 13)
          ..lineTo(6.5, 6)
          ..lineTo(17.5, 6)
          ..lineTo(20, 13)
          ..lineTo(20, 18)
          ..lineTo(4, 18)
          ..close();
      // Main.dc.html:1131 — M12 3.5a5.8 5.8 0 0 0-5.8 5.8v3.6L4.5 16h15l-1.7
      // -3.1V9.3A5.8 5.8 0 0 0 12 3.5z M9.8 19a2.3 2.3 0 0 0 4.4 0
      case TmsTabGlyph.notification:
        return Path()
          ..moveTo(12, 3.5)
          ..arcToPoint(
            const Offset(6.2, 9.3),
            radius: const Radius.circular(5.8),
            clockwise: false,
          )
          ..lineTo(6.2, 12.9)
          ..lineTo(4.5, 16)
          ..lineTo(19.5, 16)
          ..lineTo(17.8, 12.9)
          ..lineTo(17.8, 9.3)
          ..arcToPoint(
            const Offset(12, 3.5),
            radius: const Radius.circular(5.8),
            clockwise: false,
          )
          ..close()
          ..moveTo(9.8, 19)
          ..arcToPoint(
            const Offset(14.2, 19),
            radius: const Radius.circular(2.3),
            clockwise: false,
          );
      // Main.dc.html:1132 — circle r=9、circle r=3.2、
      // M6.3 18.6c1.3-2.1 3.3-3.3 5.7-3.3s4.4 1.2 5.7 3.3
      case TmsTabGlyph.me:
        return Path()
          ..addOval(Rect.fromCircle(center: const Offset(12, 12), radius: 9))
          ..addOval(Rect.fromCircle(center: const Offset(12, 10), radius: 3.2))
          ..moveTo(6.3, 18.6)
          ..cubicTo(7.6, 16.5, 9.6, 15.3, 12, 15.3)
          ..cubicTo(14.4, 15.3, 16.4, 16.5, 17.7, 18.6);
    }
  }
}

/// 圖示線稿的座標系邊長。Main.dc.html:1129 — viewBox="0 0 24 24"
const double _glyphViewBox = 24;

/// 圖示線稿的筆畫粗細。Main.dc.html:1129 — stroke-width: 1.9
const double _glyphStroke = 1.9;

/// 分頁列玻璃的模糊標準差。Main.dc.html:1126 — blur(24px)
const double _glassBlur = 24;

/// 分頁列玻璃的飽和度。Main.dc.html:1126 — saturate(180%)
const double _glassSaturation = 180;

/// 上緣高光那條線的粗細。Main.dc.html:1126 — inset 0 1px 0
const double _highlightThickness = 1;

/// soft scroll edge 的模糊標準差。Main.dc.html:1122 — blur(12px)
const double _edgeBlur = 12;

/// soft scroll edge 的飽和度。Main.dc.html:1122 — saturate(160%)
const double _edgeSaturation = 160;

/// soft scroll edge 上緣淡出後的顏色。原型用 mask 把整層的透明度收掉，這裡
/// 改成同一個色相收到全透明，兩種寫法在畫面上等價。
const Color _edgeFadeOut = Color(0x00F2F2F7);

/// 圖示與文字之間。Main.dc.html:1127 — gap: 1
const double _iconLabelGap = 1;

/// 徽章上緣離整格上緣的距離。Main.dc.html:1136 — top: 3px
const double _badgeTop = 3;

/// 徽章左緣落在整格寬度的哪一個比例上。Main.dc.html:1136 — left: 56%
const double _badgeLeftRatio = 0.56;
