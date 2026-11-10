import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../theme.dart';

/// 步驟條上一步的狀態。
///
/// 三態是刻意分開的：只分「走到了／還沒走到」的話，員工看不出「現在站在哪一
/// 步」——那正是步驟條唯一要回答的問題。
enum TmsStepStatus {
  /// 已經走完的步驟。實心圓加一個勾。
  done,

  /// 現在停在這一步。空心圓加內點，標籤加粗。
  current,

  /// 還沒走到的步驟。虛線圓。
  pending,
}

/// 步驟條上的一步。
///
/// [wrap] 給呼叫端把自己的 accessibility 節點包在這一步外面用。步驟條不自己
/// 掛 id：房務任務頁與工務單詳情用的是兩套不同的 id 命名
/// （`task.step-track.{步驟名}` 與 `ticket.stepper.{序號}`），在元件裡掛死會
/// 讓其中一邊定位不到。
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

/// 橫向步驟條：房務任務頁與工務單詳情共用的同一套畫法。
///
/// 頭尾兩步貼齊左右邊、中間的步驟置中，步驟之間是會自動拉長的連接線，橫向空
/// 間全部用上。連接線的顏色跟著它右邊那一步走：走到了就是品牌色，沒走到就是
/// 淺灰——所以一條線從頭亮到目前這一步，員工掃一眼就知道進度推到哪。
///
/// [onDark] 給深底（任務頁進行中時的青綠標題區）用：整組顏色換成白色系，否則
/// 品牌色疊在品牌色上會整個看不見。
class TmsStepTrack extends StatelessWidget {
  const TmsStepTrack({super.key, required this.steps, this.onDark = false});

  final List<TmsStepTrackStep> steps;
  final bool onDark;

  /// 步驟圖示的邊長。連接線的上緣留白要對齊它的圓心，見 [_connector]。
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

  /// 已完成那顆實心圓裡面那個勾的顏色。勾是挖在圖示色上的，所以要用對比色。
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

  /// 連接線的顏色。[reached] 是線右邊那一步走到了沒。
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

  /// 兩步之間那條線。上緣留 13 讓 3pt 的線剛好落在 28pt 圖示的圓心高度。
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

  /// 一步：圖示在上、標籤在下。
  ///
  /// 標籤通常比圖示寬，所以圖示在這一欄裡要靠哪邊對齊得看位置：第一步靠左、
  /// 最後一步靠右、中間置中。不這樣做的話頭尾兩步的圖示會被標籤推離畫面邊
  /// 緣，線的長度就對不上。
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

/// 畫步驟圖示的三種形態。
///
/// 三種形態都佔 28 × 28，圓心與直徑一致，換形態時圖示不會左右跳動。
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

  /// 虛線圓。Flutter 的畫筆沒有虛線，所以自己把圓切成等長的弧段來畫。
  ///
  /// 一段實線 4、一段空白 3.2 是原型的值，但圓周未必整除這個週期，直接照抄會
  /// 在起點收尾處留下一段長短不一的接縫。這裡先算出最接近的整數段數，再把週
  /// 期等比縮放成整除——實線與空白的比例維持原樣，接縫不見了。
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
