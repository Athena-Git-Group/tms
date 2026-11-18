import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../theme.dart';
import 'tms_motion.dart';

class TmsProgressRing extends StatelessWidget {
  const TmsProgressRing({
    super.key,
    required this.progress,
    required this.child,
    this.diameter = 112,
    this.strokeWidth = 12,
  });

  final double progress;
  final Widget child;
  final double diameter;
  final double strokeWidth;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: diameter,
      height: diameter,
      child: TweenAnimationBuilder<double>(
        tween: Tween<double>(end: progress.clamp(0.0, 1.0)),
        duration: TmsMotion.ringSweep.durationIn(context),
        curve: TmsMotion.ringSweep.curve,
        builder: (context, value, child) => CustomPaint(
          painter: _RingPainter(progress: value, strokeWidth: strokeWidth),
          child: child,
        ),
        child: Center(child: child),
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  _RingPainter({required this.progress, required this.strokeWidth});

  final double progress;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = (size.shortestSide - strokeWidth) / 2;
    final track = Paint()
      ..color = TmsColors.neutralContainer
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth;
    canvas.drawCircle(center, radius, track);

    if (progress <= 0) {
      return;
    }
    final arc = Paint()
      ..color = TmsColors.brandPrimary
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -math.pi / 2,
      2 * math.pi * progress,
      false,
      arc,
    );
  }

  @override
  bool shouldRepaint(_RingPainter oldDelegate) =>
      oldDelegate.progress != progress ||
      oldDelegate.strokeWidth != strokeWidth;
}
