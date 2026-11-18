import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../theme.dart';

enum TmsTimelineStatus {
  done,

  current,

  failed,

  pending,
}

class TmsTimelineStep {
  const TmsTimelineStep({
    required this.label,
    required this.status,
    this.note,
    this.time,
    this.wrap,
  });

  final String label;
  final TmsTimelineStatus status;

  final String? note;

  final String? time;

  final Widget Function(Widget node)? wrap;
}

class TmsTimeline extends StatelessWidget {
  const TmsTimeline({super.key, required this.steps});

  final List<TmsTimelineStep> steps;

  static const double _nodeSize = 24;

  static const double _lineLeft = 27;

  static const double _lineWidth = 2;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
      child: ColoredBox(
        color: TmsColors.cardBackground,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < steps.length; i++) _row(i),
          ],
        ),
      ),
    );
  }

  Color _segmentColor(int index) {
    final reachedNext = steps[index + 1].status != TmsTimelineStatus.pending;
    return steps[index].status == TmsTimelineStatus.done && reachedNext
        ? TmsColors.systemGreen
        : TmsColors.separator;
  }

  Color _labelColor(TmsTimelineStatus status) {
    return status == TmsTimelineStatus.pending
        ? TmsColors.quaternaryLabel
        : TmsColors.label;
  }

  Color _noteColor(TmsTimelineStatus status) {
    return status == TmsTimelineStatus.failed
        ? TmsColors.onErrorContainer
        : TmsColors.secondaryLabel;
  }

  Widget _row(int index) {
    final step = steps[index];
    final isLast = index == steps.length - 1;
    final node = Stack(
      children: [
        Positioned(
          left: _lineLeft,
          width: _lineWidth,
          top: 0,
          bottom: 0,
          child: CustomPaint(
            painter: _TmsTimelineLinePainter(
              top: index == 0 ? null : _segmentColor(index - 1),
              bottom: isLast ? null : _segmentColor(index),
              gap: _nodeSize,
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.only(left: TmsSpacing.screenEdge),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
            child: Row(
              children: [
                CustomPaint(
                  size: const Size.square(_nodeSize),
                  painter: _TmsTimelineNodePainter(status: step.status),
                ),
                const SizedBox(width: TmsSpacing.md2),
                Expanded(child: _content(step, isLast: isLast)),
              ],
            ),
          ),
        ),
      ],
    );
    final wrap = step.wrap;
    return wrap == null ? node : wrap(node);
  }

  Widget _content(TmsTimelineStep step, {required bool isLast}) {
    final time = step.status == TmsTimelineStatus.pending ? null : step.time;
    final note = step.note;
    return Container(
      padding: const EdgeInsets.fromLTRB(0, 10, TmsSpacing.screenEdge, 10),
      decoration: isLast
          ? null
          : const BoxDecoration(
              border: Border(
                bottom: BorderSide(
                  color: TmsColors.separator,
                  width: TmsSize.hairline,
                ),
              ),
            ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  step.label,
                  style: TmsText.body.copyWith(color: _labelColor(step.status)),
                ),
                if (note != null && note.isNotEmpty) ...[
                  const SizedBox(height: TmsSpacing.xxs),
                  Text(
                    note,
                    style: TmsText.subheadline.copyWith(
                      height: 1.35,
                      color: _noteColor(step.status),
                    ),
                  ),
                ],
              ],
            ),
          ),
          if (time != null && time.isNotEmpty) ...[
            const SizedBox(width: TmsSpacing.sm),
            Text(
              time,
              style: TmsText.body.copyWith(
                color: TmsColors.quaternaryLabel,
                fontFeatures: TmsText.tabularNumbers,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _TmsTimelineLinePainter extends CustomPainter {
  const _TmsTimelineLinePainter({
    required this.top,
    required this.bottom,
    required this.gap,
  });

  final Color? top;
  final Color? bottom;
  final double gap;

  @override
  void paint(Canvas canvas, Size size) {
    final middle = size.height / 2;
    final half = gap / 2;
    final topColor = top;
    if (topColor != null && middle - half > 0) {
      canvas.drawRect(
        Rect.fromLTRB(0, 0, size.width, middle - half),
        Paint()..color = topColor,
      );
    }
    final bottomColor = bottom;
    if (bottomColor != null && middle + half < size.height) {
      canvas.drawRect(
        Rect.fromLTRB(0, middle + half, size.width, size.height),
        Paint()..color = bottomColor,
      );
    }
  }

  @override
  bool shouldRepaint(_TmsTimelineLinePainter oldDelegate) {
    return oldDelegate.top != top ||
        oldDelegate.bottom != bottom ||
        oldDelegate.gap != gap;
  }
}

class _TmsTimelineNodePainter extends CustomPainter {
  const _TmsTimelineNodePainter({required this.status});

  final TmsTimelineStatus status;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    switch (status) {
      case TmsTimelineStatus.done:
        canvas.drawCircle(
          center,
          10.5,
          Paint()..color = TmsColors.systemGreen,
        );
        final check = Path()
          ..moveTo(7.5, 12.4)
          ..lineTo(10.5, 15.4)
          ..lineTo(16.5, 8.8);
        canvas.drawPath(
          check,
          Paint()
            ..color = TmsColors.cardBackground
            ..style = PaintingStyle.stroke
            ..strokeWidth = 2.3
            ..strokeCap = StrokeCap.round
            ..strokeJoin = StrokeJoin.round,
        );
      case TmsTimelineStatus.current:
        canvas.drawCircle(
          center,
          9.5,
          Paint()
            ..color = TmsColors.brandPrimary
            ..style = PaintingStyle.stroke
            ..strokeWidth = 2,
        );
        canvas.drawCircle(center, 5.5, Paint()..color = TmsColors.brandPrimary);
      case TmsTimelineStatus.failed:
        canvas.drawCircle(center, 10.5, Paint()..color = TmsColors.systemRed);
        final bang = Paint()
          ..color = TmsColors.cardBackground
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2.4
          ..strokeCap = StrokeCap.round;
        canvas.drawLine(const Offset(12, 6.8), const Offset(12, 13.2), bang);
        canvas.drawLine(const Offset(12, 17.2), const Offset(12, 17.2), bang);
      case TmsTimelineStatus.pending:
        _drawDashedCircle(canvas, center, 9.5);
    }
  }

  void _drawDashedCircle(Canvas canvas, Offset center, double radius) {
    const dashLength = 3.2;
    const gapLength = 2.6;
    final circumference = 2 * math.pi * radius;
    final segments =
        math.max(1, (circumference / (dashLength + gapLength)).round());
    final sweepPerSegment = 2 * math.pi / segments;
    final dashSweep = sweepPerSegment * dashLength / (dashLength + gapLength);
    final paint = Paint()
      ..color = TmsColors.systemGray3
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.8;
    final rect = Rect.fromCircle(center: center, radius: radius);
    for (var i = 0; i < segments; i++) {
      canvas.drawArc(rect, i * sweepPerSegment, dashSweep, false, paint);
    }
  }

  @override
  bool shouldRepaint(_TmsTimelineNodePainter oldDelegate) {
    return oldDelegate.status != status;
  }
}
