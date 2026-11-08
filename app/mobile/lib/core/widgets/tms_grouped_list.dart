import 'package:flutter/cupertino.dart';

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
    required this.children,
  });

  /// 群組上方的小標題（「通知與顯示」「今天」）。不給就不畫這一行。
  final String? header;

  /// 群組白卡的圓角級別。一般畫面用預設的分組級；底部面板裡的群組用
  /// [TmsCardStyle.inSheet]，面板本身的圓角比畫面小，群組跟著收一級才不會比
  /// 裝它的面板還圓。
  final TmsCardStyle style;

  /// 群組裡的列，由上到下。分隔線由這裡插，呼叫端不用自己畫。
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i > 0) {
        rows.add(
          const SizedBox(
            height: TmsSize.hairline,
            child: ColoredBox(color: TmsColors.separator),
          ),
        );
      }
      rows.add(children[i]);
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
            const Icon(
              CupertinoIcons.chevron_forward,
              size: TmsSize.chevron,
              color: TmsColors.chevron,
            ),
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
