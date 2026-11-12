import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_glass.dart';

enum TmsFabGlyph {
  plus,

  ask,
}

class TmsFab extends StatelessWidget {
  const TmsFab({
    super.key,
    required this.glyph,
    required this.semanticsLabel,
    required this.onPressed,
  });

  final TmsFabGlyph glyph;

  final String semanticsLabel;

  final VoidCallback onPressed;

  static const double diameter = 56;

  static const double sideInset = 16;

  static double bottomAboveTabBar(double bottomSafeAreaInset) {
    return TmsSpacing.bottomScrollEdgeHeight(bottomSafeAreaInset) + 16;
  }

  static double bottomAboveActionBar({
    required bool hasActionBar,
    required double bottomSafeAreaInset,
  }) {
    return (hasActionBar ? 0 : bottomSafeAreaInset) + 16;
  }

  static const double scrollPadClearance = 72;

  static double scrollPadBottom(double fabBottom) =>
      fabBottom + scrollPadClearance;

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
                // BoxDecoration 畫不出 inset 陰影，上緣的高光只能另外疊一條線。
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

const double _glassBlur = 16;

const double _glassSaturation = 180;

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

class TmsFabGlyphPainter extends CustomPainter {
  const TmsFabGlyphPainter(this.glyph);

  final TmsFabGlyph glyph;

  static const Color strokeColor = TmsColors.brandPrimary;

  static const double _viewBox = 24;

  static const double plusStrokeWidth = 2.4;

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

  Path _plusPath() {
    return Path()
      ..moveTo(12, 5)
      ..lineTo(12, 19)
      ..moveTo(5, 12)
      ..lineTo(19, 12);
  }

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
