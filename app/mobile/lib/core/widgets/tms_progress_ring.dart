import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../theme.dart';

/// 首頁進度環：一圈灰色軌道，上面疊一段品牌色的弧表示已完成的比例。
///
/// 環的中央留給呼叫端放數字與說明（[child]），環本身不畫任何文字。數字是
/// 要被讀值的節點，留在外面才能由畫面決定掛哪一個 accessibility id。
///
/// [progress] 是 0 到 1 之間的比例，超出範圍的值會被夾回範圍內；分母為 0
/// 時傳 0 進來，環會只剩軌道。弧從正上方開始順時針長，和員工對「進度」的
/// 直覺一致。
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
      child: CustomPaint(
        painter: _RingPainter(
          progress: progress.clamp(0.0, 1.0),
          strokeWidth: strokeWidth,
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
