import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../theme.dart';

enum TmsStepStatus {
  done,

  current,

  pending,
}

class TmsStepTrackStep {
  const TmsStepTrackStep({
    required this.label,
    required this.status,
    this.wrap,
  });

  final String label;
  final TmsStepStatus status;
  final Widget Function(Widget node)? wrap;
}

class TmsStepTrack extends StatelessWidget {
  const TmsStepTrack({super.key, required this.steps, this.onDark = false});

  final List<TmsStepTrackStep> steps;
  final bool onDark;

  static const double _nodeSize = 28;

  Color _iconColor(TmsStepStatus status) {
    final reached = status != TmsStepStatus.pending;
    if (onDark) {
      return reached
          ? TmsColors.brandOnPrimary
          : const Color(0x99FFFFFF);
    }
    return reached ? TmsColors.brandPrimary : TmsColors.systemGray2;
  }

  Color get _checkColor =>
      onDark ? TmsColors.brandPrimary : TmsColors.brandOnPrimary;

  Color _textColor(TmsStepStatus status) {
    final reached = status != TmsStepStatus.pending;
    if (onDark) {
      return reached
          ? TmsColors.brandOnPrimary
          : const Color(0xB8FFFFFF);
    }
    return reached ? TmsColors.label : TmsColors.tertiaryLabel;
  }

  Color _lineColor({required bool reached}) {
    if (onDark) {
      return reached
          ? TmsColors.brandOnPrimary
          : const Color(0x4DFFFFFF);
    }
    return reached ? TmsColors.brandPrimary : TmsColors.separator;
  }

  @override
  Widget build(BuildContext context) {
    final children = <Widget>[];
    for (var i = 0; i < steps.length; i++) {
      if (i > 0) {
        children.add(
          Expanded(
            child: _connector(
              reached: steps[i].status != TmsStepStatus.pending,
            ),
          ),
        );
      }
      children.add(_node(steps[i], index: i, total: steps.length));
    }
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.lg2),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: children),
    );
  }

  Widget _connector({required bool reached}) {
    return Container(
      height: 3,
      margin: const EdgeInsets.only(top: 13, left: 6, right: 6),
      decoration: BoxDecoration(
        color: _lineColor(reached: reached),
        borderRadius: BorderRadius.circular(TmsRadius.pill),
      ),
    );
  }

  Widget _node(TmsStepTrackStep step, {required int index, required int total}) {
    final CrossAxisAlignment align;
    if (index == 0) {
      align = CrossAxisAlignment.start;
    } else if (index == total - 1) {
      align = CrossAxisAlignment.end;
    } else {
      align = CrossAxisAlignment.center;
    }
    final node = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: align,
      children: [
        CustomPaint(
          size: const Size.square(_nodeSize),
          painter: _TmsStepNodePainter(
            status: step.status,
            color: _iconColor(step.status),
            checkColor: _checkColor,
          ),
        ),
        const SizedBox(height: TmsSpacing.xs2),
        Text(
          step.label,
          maxLines: 1,
          softWrap: false,
          style: TmsText.subheadline.copyWith(
            fontWeight: step.status == TmsStepStatus.current
                ? FontWeight.w700
                : FontWeight.w500,
            color: _textColor(step.status),
          ),
        ),
      ],
    );
    final wrap = step.wrap;
    return wrap == null ? node : wrap(node);
  }
}

class _TmsStepNodePainter extends CustomPainter {
  const _TmsStepNodePainter({
    required this.status,
    required this.color,
    required this.checkColor,
  });

  final TmsStepStatus status;
  final Color color;
  final Color checkColor;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    switch (status) {
      case TmsStepStatus.done:
        canvas.drawCircle(
          center,
          size.width / 2,
          Paint()..color = color,
        );
        final check = Path()
          ..moveTo(size.width * 8 / 28, size.height * 14.5 / 28)
          ..lineTo(size.width * 12 / 28, size.height * 18.5 / 28)
          ..lineTo(size.width * 20 / 28, size.height * 9.5 / 28);
        canvas.drawPath(
          check,
          Paint()
            ..color = checkColor
            ..style = PaintingStyle.stroke
            ..strokeWidth = 2.8
            ..strokeCap = StrokeCap.round
            ..strokeJoin = StrokeJoin.round,
        );
      case TmsStepStatus.current:
        canvas.drawCircle(
          center,
          12.5,
          Paint()
            ..color = color
            ..style = PaintingStyle.stroke
            ..strokeWidth = 3,
        );
        canvas.drawCircle(center, 6.5, Paint()..color = color);
      case TmsStepStatus.pending:
        _drawDashedCircle(canvas, center, 12.5);
    }
  }

  void _drawDashedCircle(Canvas canvas, Offset center, double radius) {
    const dashLength = 4.0;
    const gapLength = 3.2;
    final circumference = 2 * math.pi * radius;
    final segments = math.max(1, (circumference / (dashLength + gapLength)).round());
    final sweepPerSegment = 2 * math.pi / segments;
    final dashSweep = sweepPerSegment * dashLength / (dashLength + gapLength);
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2;
    final rect = Rect.fromCircle(center: center, radius: radius);
    for (var i = 0; i < segments; i++) {
      canvas.drawArc(rect, i * sweepPerSegment, dashSweep, false, paint);
    }
  }

  @override
  bool shouldRepaint(_TmsStepNodePainter oldDelegate) {
    return oldDelegate.status != status ||
        oldDelegate.color != color ||
        oldDelegate.checkColor != checkColor;
  }
}
