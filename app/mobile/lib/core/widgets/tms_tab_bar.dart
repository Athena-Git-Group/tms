import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_category_tile.dart';
import 'tms_glass.dart';

enum TmsTabGlyph { home, pool, notification, me }

class TmsTabItem {
  const TmsTabItem({
    required this.label,
    required this.glyph,
    this.badgeCount = 0,
  });

  final String label;

  final TmsTabGlyph glyph;

  final int badgeCount;
}

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

const double _glyphViewBox = 24;

const double _glyphStroke = 1.9;

const double _glassBlur = 24;

const double _glassSaturation = 180;

const double _highlightThickness = 1;

const double _edgeBlur = 12;

const double _edgeSaturation = 160;

const Color _edgeFadeOut = Color(0x00F2F2F7);

const double _iconLabelGap = 1;

const double _badgeTop = 3;

const double _badgeLeftRatio = 0.56;
