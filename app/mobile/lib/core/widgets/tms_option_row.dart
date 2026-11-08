import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 這一列尾端要畫什麼。
enum TmsOptionAccessory {
  /// 選中時畫一個品牌色的勾；沒選中時尾端留白。用在「從幾個裡面挑一個」的
  /// 清單（延後原因、報修類別）。
  check,

  /// 永遠畫一個 chevron。用在「點了會往下走一步」的清單（查一下的建議問題）。
  chevron,

  /// 尾端什麼都不畫。
  none,
}

/// 分組清單裡可以點選的一列：左邊是選項文字（必要時下面再一行小字），右邊是
/// 勾或 chevron。
///
/// 面板裡所有「列出幾個選項讓員工挑」的地方都用這一個：延後／中斷原因、報修
/// 類別、查一下的建議問題。選中與否靠尾端那個勾來說，不靠字重——只有字重差別
/// 的話，員工在走廊的光線下根本看不出自己到底選了哪一列。
///
/// 一列最矮 52pt（原型規格，比一般設定列的 44 高一點：這些列是現場戴著手套
/// 單手點的）。
///
/// [title] 與 [subtitle] 收的是 widget 不是字串：有些列的文字本身要掛
/// accessibility id（查一下的建議問題與範圍各掛一個），呼叫端得有地方把那層
/// 包進來。字級與顏色由這一列統一指定，傳進來的 `Text` 不必自己帶 style。
class TmsOptionRow extends StatelessWidget {
  const TmsOptionRow({
    super.key,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.selected = false,
    this.accessory = TmsOptionAccessory.check,
  });

  final Widget title;

  /// 選項底下那一行補充說明（查一下的「範圍」）；不給就只有一行。
  final Widget? subtitle;

  final bool selected;

  final TmsOptionAccessory accessory;

  final VoidCallback? onTap;

  /// 原型的面板選項列高度。
  static const double minHeight = 52;

  @override
  Widget build(BuildContext context) {
    final sub = subtitle;
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        constraints: const BoxConstraints(minHeight: minHeight),
        padding: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.screenEdge,
          vertical: TmsSpacing.sm2,
        ),
        color: TmsColors.cardBackground,
        child: Row(
          children: [
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DefaultTextStyle(style: TmsText.body, child: title),
                  if (sub != null) ...[
                    const SizedBox(height: TmsSpacing.xxs),
                    DefaultTextStyle(style: TmsText.footnote, child: sub),
                  ],
                ],
              ),
            ),
            if (accessory == TmsOptionAccessory.check && selected) ...[
              const SizedBox(width: TmsSpacing.sm),
              const Icon(
                CupertinoIcons.check_mark,
                size: 20,
                color: TmsColors.brandPrimary,
              ),
            ],
            if (accessory == TmsOptionAccessory.chevron) ...[
              const SizedBox(width: TmsSpacing.sm),
              const Icon(
                CupertinoIcons.chevron_forward,
                size: TmsSize.chevron,
                color: TmsColors.chevron,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
