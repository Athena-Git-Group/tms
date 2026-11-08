import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 紅色數量徽章：分類磚右上角那顆數字。
///
/// 數字永遠畫出來，就算是 0 也一樣。原型在 0 的時候把徽章藏起來，但那是給人
/// 看的原型；這個數字同時是驗收要讀的值（每一格分類磚都要對得上後端回的
/// counts），藏起來等於讀不到，所以這裡選擇顯示。
class TmsCountBadge extends StatelessWidget {
  const TmsCountBadge({super.key, required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minWidth: 20, minHeight: 20),
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.xs2),
      alignment: Alignment.center,
      decoration: const BoxDecoration(
        color: TmsColors.systemRed,
        shape: BoxShape.rectangle,
        borderRadius: BorderRadius.all(Radius.circular(TmsRadius.pill)),
      ),
      child: Text(
        '$count',
        maxLines: 1,
        softWrap: false,
        style: TmsText.caption1.copyWith(color: TmsColors.brandOnPrimary),
      ),
    );
  }
}

/// 待認領頂部那一排分類磚裡的一格。
///
/// 磚的主角是 44pt 的彩色圖示方塊：四格的顏色各不相同，員工是靠顏色而不是靠
/// 讀字找到要的那一格。選中的表現是「磚自己變白底再加一圈品牌色外環」，不是
/// 把圖示方塊換色——圖示方塊的顏色代表的是分類身分，選中與否不該改變它。
class TmsCategoryTile extends StatelessWidget {
  const TmsCategoryTile({
    super.key,
    required this.label,
    required this.icon,
    required this.iconBackground,
    required this.iconForeground,
    required this.selected,
    required this.onTap,
    this.badge,
  });

  final String label;
  final IconData icon;
  final Color iconBackground;
  final Color iconForeground;
  final bool selected;
  final VoidCallback onTap;

  /// 右上角的數量徽章；不給值時不畫。
  ///
  /// 收 Widget 而不是收數字，是因為徽章上的數字要掛自己的 accessibility id，
  /// 而 id 的命名跟著呼叫端的畫面走（房務是 `pool.category.{分類}.count`）。
  final Widget? badge;

  /// 分類磚之間的網格間距。
  static const double gap = TmsSpacing.sm2;

  @override
  Widget build(BuildContext context) {
    final tile = Container(
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.xs,
        TmsSpacing.sm2,
        TmsSpacing.xs,
        TmsSpacing.sm,
      ),
      decoration: BoxDecoration(
        color: selected ? TmsColors.cardBackground : null,
        borderRadius: BorderRadius.circular(TmsRadius.tile),
        // 沒選中時外環用透明色而不是不畫：外環有沒有佔位會差 2pt，不畫的話
        // 點下去整格內容會往內縮一下。
        border: Border.all(
          color: selected ? TmsColors.brandPrimary : const Color(0x00000000),
          width: 2,
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 44,
            height: 44,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: iconBackground,
              borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
            ),
            child: Icon(icon, size: 24, color: iconForeground),
          ),
          const SizedBox(height: TmsSpacing.xs2),
          Text(
            label,
            maxLines: 1,
            softWrap: false,
            style: TmsText.footnoteEmphasized.copyWith(color: TmsColors.labelSoft),
          ),
        ],
      ),
    );
    return GestureDetector(
      onTap: onTap,
      // 磚的留白本身也要可點：只有圖示方塊吃得到點擊的話，戴手套的手指很容易
      // 點在縫隙上而沒有反應。
      behavior: HitTestBehavior.opaque,
      child: badge == null
          ? tile
          : Stack(
              clipBehavior: Clip.none,
              children: [
                tile,
                Positioned(top: 0, right: 2, child: badge!),
              ],
            ),
    );
  }
}
