import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 可點選的膠囊：報修類別、公共區域切換、完修結果、待認領的篩選磚都用這一個。
///
/// 選中時換成品牌色實心底配白字，沒選中時是淺灰底配深字。只有一處決定選中
/// 長什麼樣，四個畫面的選中態才會一致；畫面各寫一份的結果是同一個互動在不同
/// 畫面有不同的反應，員工得逐頁重學「這顆按下去了沒」。
///
/// [trailing] 給磚上要再掛一段內容的呼叫端用，例如待認領分類磚右側的筆數；
/// 不給值時磚上只有文字。整顆膠囊的最小高度是 iOS 的最小觸控目標，戴手套
/// 單手點得到。
class TmsSelectionChip extends StatelessWidget {
  const TmsSelectionChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
    this.trailing,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final foreground =
        selected ? TmsColors.brandOnPrimary : TmsColors.labelSoft;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        constraints: const BoxConstraints(minHeight: TmsSize.minTouchTarget),
        padding: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.md,
          vertical: TmsSpacing.sm,
        ),
        decoration: BoxDecoration(
          color: selected
              ? TmsColors.brandPrimary
              : TmsColors.neutralContainer,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              label,
              style: TmsText.subheadlineEmphasized.copyWith(color: foreground),
            ),
            if (trailing != null) ...[
              const SizedBox(width: TmsSpacing.xs2),
              trailing!,
            ],
          ],
        ),
      ),
    );
  }
}
