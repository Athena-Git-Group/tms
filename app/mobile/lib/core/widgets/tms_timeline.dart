import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../theme.dart';

/// 直列時間軸上一步的狀態。
///
/// 比橫向步驟條多一個「沒通過」：審核會被主管退回、單會被撤回，這兩種結果
/// 都是停在那一步不再往前，和「還沒走到」是兩回事。只分已達與未達的話，一
/// 張被退回的單看起來會跟一張還在排隊的單一模一樣。
enum TmsTimelineStatus {
  /// 已經走完的步驟。實心綠圓加白勾。
  done,

  /// 現在停在這一步。品牌色外環加實心內圓。
  current,

  /// 這一步沒有通過，流程停在這裡。實心紅圓加白驚嘆號。
  failed,

  /// 還沒走到的步驟。虛線灰圓，整列淡化。
  pending,
}

/// 直列時間軸上的一步。
///
/// [wrap] 給呼叫端把自己的 accessibility 節點包在這一步外面用。時間軸不自己
/// 掛 id，也不自己組讀值的句子：同一個元件會出現在工務單詳情與遺失物物品詳
/// 情上，兩邊的 id 命名與讀值文案都不一樣，在元件裡掛死會讓其中一邊定位不到。
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

  /// 寫在步驟名下方的例外註記，例如「系統判定急件（規則），略過審核直接送
  /// 工務，已同步通知主管」。沒有例外就不給。
  final String? note;

  /// 這一步發生的時刻，靠右顯示。
  ///
  /// 還沒走到的步驟不顯示時間，即使呼叫端給了值也一樣：未來的時刻沒有意義，
  /// 顯示出來會被讀成「排定在這個時間」。
  final String? time;

  final Widget Function(Widget node)? wrap;
}

/// 直列時間軸：一張 inset grouped 白卡，每一步一列。
///
/// 左側是狀態圖示，一條中線貫穿每個圖示的圓心把各列串起來，右側是時刻。中線
/// 上下兩段分開上色，走完的那一段是系統綠、其餘是分隔線灰，所以綠線從第一步
/// 一路亮到流程目前停住的位置，員工掃一眼就知道進度推到哪。
///
/// 和橫向的步驟條分成兩個元件，是因為兩者回答的問題不同。步驟條只回答「現在
/// 站在第幾步」，所以壓成一條；時間軸還要回答「每一步什麼時候發生、哪一步出
/// 了什麼事」，每一步都得有自己的一列放時刻與例外註記。
class TmsTimeline extends StatelessWidget {
  const TmsTimeline({super.key, required this.steps});

  final List<TmsTimelineStep> steps;

  /// 狀態圖示的邊長，同時也是中線要讓開的高度：中線在圓心上下各停 12，圖示
  /// 剛好嵌進那段空白裡。
  static const double _nodeSize = 24;

  /// 中線的左緣。圖示從左內距 16 起算、寬 24，圓心在 28，中線寬 2 要跨在
  /// 圓心上，左緣就是 27。
  static const double _lineLeft = 27;

  static const double _lineWidth = 2;

  @override
  Widget build(BuildContext context) {
    // 工務單詳情上的白卡一律是 14（`Main.dc.html:1791`），比一般分組清單的 16
    // 收一級。
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

  /// 第 [index] 步與第 [index] + 1 步之間那一段中線的顏色。
  ///
  /// 綠色只畫在「上一步已完成，而且下一步已經開始」的那一段。少了後半個條件
  /// 的話，最後一步完成之後綠線會一路衝到還沒發生的步驟上。
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

  /// 一列。
  ///
  /// 中線是整列的背景而不是左欄的一部分，因為它要從列的最上緣連到最下緣；長
  /// 在左欄裡的話，帶例外註記的那幾列一變高，線就會在列與列之間斷開。
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

  /// 圖示右邊那一塊：步驟名與例外註記在左、時刻在右。
  ///
  /// 分隔線掛在這一塊的下緣而不是整列的下緣，列與列之間的線才會從文字起點開
  /// 始，和 iOS 清單的縮排分隔線一致。
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

/// 一列裡的中線：圓心上下各讓開 [gap] / 2，兩段分開上色。
///
/// [top] 或 [bottom] 給 null 代表那一段不畫，用在第一列的上半段與最後一列的
/// 下半段——時間軸的頭尾不該有線伸出卡片。
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

/// 畫狀態圖示的四種形態。
///
/// 四種都佔 24 × 24，圓心一致，狀態改變時圖示不會在中線上左右偏移。改用系統
/// 字形的圖示就做不到這件事：`CupertinoIcons` 沒有虛線圓，而字形的字框留白與
/// 原型訂死的半徑（10.5 與 9.5）對不上，四顆裡混兩種畫法會讓它們大小不一。
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

  /// 虛線圓。Flutter 的畫筆沒有虛線，所以自己把圓切成等長的弧段來畫。
  ///
  /// 一段實線 3.2、一段空白 2.6 是原型的值，但圓周未必整除這個週期，直接照抄
  /// 會在起點收尾處留下一段長短不一的接縫。這裡先算出最接近的整數段數，再把
  /// 週期等比縮放成整除，實線與空白的比例維持原樣，接縫就不見了。
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
