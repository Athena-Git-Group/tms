import 'dart:math' as math;

import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_motion.dart';

class TmsTipPopover extends StatelessWidget {
  const TmsTipPopover({
    super.key,
    required this.title,
    required this.message,
    required this.width,
    required this.onDismiss,
  });

  final String title;

  final String message;

  final double width;

  final VoidCallback onDismiss;

  static const double gapAboveAnchor = 12;

  static double bottomAbove(double anchorBottom, double anchorHeight) {
    return anchorBottom + anchorHeight + gapAboveAnchor;
  }

  @override
  Widget build(BuildContext context) {
    return TmsMotionView(
      spec: TmsMotion.drop,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          _card(),
          const Positioned(
            right: _arrowInsetRight,
            bottom: -_arrowOverhang,
            child: _TipArrow(),
          ),
        ],
      ),
    );
  }

  Widget _card() {
    return Container(
      width: width,
      decoration: BoxDecoration(
        color: TmsColors.cardBackground,
        borderRadius: BorderRadius.circular(TmsRadius.card),
        boxShadow: const <BoxShadow>[
          BoxShadow(
            color: TmsColors.tipPopoverShadow,
            offset: Offset(0, 10),
            blurRadius: 30,
          ),
        ],
      ),
      padding: const EdgeInsets.fromLTRB(14, 2, 2, 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: 12, bottom: 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: TmsText.headline),
                  const SizedBox(height: _titleGap),
                  Text(
                    message,
                    style: TmsText.subheadline.copyWith(
                      height: 1.4,
                      color: TmsColors.secondaryLabel,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: _textToCloseGap),
          _CloseButton(onPressed: onDismiss),
        ],
      ),
    );
  }
}

const double _titleGap = 2;

const double _textToCloseGap = 4;

const double _arrowInsetRight = 22;

const double _arrowOverhang = 6;

const double _arrowSize = 14;

class _TipArrow extends StatelessWidget {
  const _TipArrow();

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: _arrowSize,
      height: _arrowSize,
      child: Transform.rotate(
        angle: math.pi / 4,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: TmsColors.cardBackground,
            borderRadius: BorderRadius.circular(2),
          ),
        ),
      ),
    );
  }
}

class _CloseButton extends StatelessWidget {
  const _CloseButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: '關閉提示',
      button: true,
      child: CupertinoButton(
        padding: EdgeInsets.zero,
        minimumSize: const Size(TmsSize.minTouchTarget, TmsSize.minTouchTarget),
        onPressed: onPressed,
        child: const CustomPaint(
          size: Size(_closeGlyphSize, _closeGlyphSize),
          painter: TmsTipCloseGlyphPainter(),
        ),
      ),
    );
  }
}

const double _closeGlyphSize = 13;

class TmsTipCloseGlyphPainter extends CustomPainter {
  const TmsTipCloseGlyphPainter();

  static const Color strokeColor = TmsColors.quaternaryLabel;

  static const double _viewBox = 24;

  static const double strokeWidth = 3;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / _viewBox;
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..color = strokeColor
      ..strokeCap = StrokeCap.round
      ..strokeWidth = strokeWidth * scale;

    canvas.save();
    canvas.scale(scale);
    canvas.drawPath(
      Path()
        ..moveTo(5, 5)
        ..lineTo(19, 19)
        ..moveTo(19, 5)
        ..lineTo(5, 19),
      paint,
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(TmsTipCloseGlyphPainter oldDelegate) => false;
}
