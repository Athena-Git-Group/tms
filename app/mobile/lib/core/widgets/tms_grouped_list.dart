import 'package:flutter/widgets.dart';

import '../theme.dart';
import 'tms_card.dart';

/// iOS inset-grouped 清單的一「組」：上面一行小標題，下面一張白卡，卡裡的列
/// 之間用一條髮絲線隔開。
///
/// 全 App 只有這一個分組容器。各畫面自己兜「白底 + 圓角 + 分隔線」的話，分隔
/// 線會有的畫到底、有的縮排，圓角也會各差幾個 pixel——這種差異單看一支畫面
/// 看不出來，四支畫面擺在一起就很明顯。
///
/// 列不必是 [TmsGroupedRow]：通知中心的列有未讀點、分類圖示與兩段文字，和設定
/// 列長得完全不一樣，但它們共用同一個群組容器。
class TmsGroupedSection extends StatelessWidget {
  const TmsGroupedSection({
    super.key,
    this.header,
    this.style = TmsCardStyle.grouped,
    this.trailingDivider = false,
    required this.children,
  });

  /// 群組上方的小標題（「通知與顯示」「今天」）。不給就不畫這一行。
  final String? header;

  /// 群組白卡的圓角級別。一般畫面用預設的分組級；底部面板裡的群組用
  /// [TmsCardStyle.inSheet]，面板本身的圓角比畫面小，群組跟著收一級才不會比
  /// 裝它的面板還圓。
  final TmsCardStyle style;

  /// 最後一列底下要不要也畫一條線。
  ///
  /// 原型兩種都有。設定列、通知列這類一組就是一組的清單，最後一列不畫
  /// （`Main.dc.html:4572`）。面板裡的選項清單與清潔 SOP 清單每一列都畫，含
  /// 最後一列（`Main.dc.html:1450`、`1461`、`1601`、`1312`）。差別在於後者底
  /// 下還會接補充說明或下一組，那條底線是在說這一組到這裡為止。
  final bool trailingDivider;

  /// 群組裡的列，由上到下。分隔線由這裡插，呼叫端不用自己畫。
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    const divider = SizedBox(
      height: TmsSize.hairline,
      child: ColoredBox(color: TmsColors.separator),
    );
    final rows = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i > 0) {
        rows.add(divider);
      }
      rows.add(children[i]);
    }
    if (trailingDivider && children.isNotEmpty) {
      rows.add(divider);
    }
    final radius = style == TmsCardStyle.inSheet
        ? TmsRadius.cardInSheet
        : TmsRadius.card;
    final group = TmsCard(
      style: style,
      padding: EdgeInsets.zero,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: Column(mainAxisSize: MainAxisSize.min, children: rows),
      ),
    );
    if (header == null) {
      return group;
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.screenEdge,
            0,
            TmsSpacing.screenEdge,
            TmsSpacing.xs2,
          ),
          child: Text(header!, style: TmsText.footnote),
        ),
        group,
      ],
    );
  }
}

/// 分組清單裡的一般設定列：左邊標題、右邊目前的值、尾端一個 chevron。
///
/// 右邊的值可以不給（例如「變更密碼」平常沒有值可顯示）；值太長時從尾端截
/// 斷，不換行也不把左邊的標題擠掉——標題是員工用來找到這一列的東西，值只是
/// 補充。
class TmsGroupedRow extends StatelessWidget {
  const TmsGroupedRow({
    super.key,
    required this.label,
    this.value,
    this.showChevron = true,
    this.onTap,
  });

  final String label;

  /// 這一列目前的值，靠右顯示；不給就只有標題與 chevron。
  final String? value;

  /// 要不要在尾端畫 chevron。點了會換頁的列才畫。
  final bool showChevron;

  /// 點這一列要做的事；不給值時這一列不可點。
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final row = Container(
      constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.screenEdge,
        vertical: TmsSpacing.groupedRowVertical,
      ),
      color: TmsColors.cardBackground,
      child: Row(
        children: [
          Text(label, style: TmsText.body),
          const SizedBox(width: TmsSpacing.sm),
          Expanded(
            child: Text(
              value ?? '',
              textAlign: TextAlign.right,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TmsText.body.copyWith(color: TmsColors.quaternaryLabel),
            ),
          ),
          if (showChevron) ...[
            const SizedBox(width: TmsSpacing.sm),
            const TmsChevron(),
          ],
        ],
      ),
    );
    if (onTap == null) {
      return row;
    }
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: row,
    );
  }
}

/// 列尾那個「點了會換頁」的箭頭（`Main.dc.html:421`）。
///
/// 自己畫而不用 `CupertinoIcons.chevron_forward`。原型的箭頭是 8 × 13 的描邊
/// 圖形，高比寬多一半。系統圖示的字框是正方形，畫出來矮而胖，筆畫粗細也由字
/// 型決定，調不到原型的 2。
class TmsChevron extends StatelessWidget {
  const TmsChevron({super.key, this.color = TmsColors.chevron});

  /// 箭頭顏色。列尾那顆是淺灰，狀態列上「查看工務單」後面那顆跟著文字走品
  /// 牌色（`Main.dc.html:1241`）。
  final Color color;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      size: const Size(TmsSize.chevronWidth, TmsSize.chevronHeight),
      painter: _ChevronPainter(color: color),
    );
  }
}

class _ChevronPainter extends CustomPainter {
  const _ChevronPainter({required this.color});

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    // 原型的 path 畫在 8 × 13 的 viewBox 上：M1.5 1.5 l5 5 -5 5。
    final path = Path()
      ..moveTo(1.5, 1.5)
      ..lineTo(6.5, 6.5)
      ..lineTo(1.5, 11.5);
    canvas.drawPath(
      path,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = TmsSize.chevronStroke
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..color = color,
    );
  }

  @override
  bool shouldRepaint(_ChevronPainter oldDelegate) => oldDelegate.color != color;
}
