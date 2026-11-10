import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 紅色數量徽章：分類磚右上角、分頁列圖示右上角那顆數字。
///
/// 兩處的規格差在一號尺寸，分類磚是 20pt 高配 12pt 數字
/// （`Main.dc.html:264`），分頁列是 18pt 高配 11pt 數字
/// （`Main.dc.html:1136`）。差別小到不值得拆成兩個元件，但也不該讓呼叫端各自
/// 傳一組尺寸數字，所以用兩個具名建構子把規格收在這裡。
///
/// 計數是 0 的時候呼叫端不要畫這顆徽章，由呼叫端自己判斷：一顆寫著 0 的紅點
/// 會讓員工以為有東西等著處理。
class TmsCountBadge extends StatelessWidget {
  const TmsCountBadge({super.key, required this.count}) : _onTabBar = false;

  const TmsCountBadge.tabBar({super.key, required this.count})
    : _onTabBar = true;

  final int count;

  final bool _onTabBar;

  @override
  Widget build(BuildContext context) {
    final diameter = _onTabBar ? 18.0 : 20.0;
    return Container(
      constraints: BoxConstraints(minWidth: diameter),
      height: diameter,
      padding: EdgeInsets.symmetric(horizontal: _onTabBar ? 5 : TmsSpacing.xs2),
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
        style: (_onTabBar ? TmsText.caption2 : TmsText.caption1).copyWith(
          color: TmsColors.brandOnPrimary,
        ),
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
    required this.iconBackground,
    required this.iconForeground,
    required this.selected,
    required this.onTap,
    this.icon,
    this.iconChild,
    this.badge,
    this.unselectedBackground,
  }) : assert(
         (icon == null) != (iconChild == null),
         'icon 與 iconChild 只能給一個',
       );

  final String label;

  /// 圖示方塊裡的字形。自繪的圖示走 [iconChild]，兩者只給一個。
  final IconData? icon;

  /// 自繪的圖示，畫在 24×24 的範圍內。
  ///
  /// `CupertinoIcons` 找不到形狀對得上的字形時走這裡（裁決 R4）。顏色不由這個
  /// 元件套上去——自繪的圖形各有幾條線要各自上色，呼叫端拿自己傳的
  /// [iconForeground] 畫，比這裡包一層 `IconTheme` 讓呼叫端去繼承好追。
  final Widget? iconChild;

  final Color iconBackground;
  final Color iconForeground;
  final bool selected;
  final VoidCallback onTap;

  /// 沒選中時磚的底色；不給值就是全透明。
  ///
  /// 房務的磚疊在白底上，不給底色；工務的磚疊在淺灰底上，要靠一層半透明白
  /// 才看得出磚的範圍，那一側傳 [translucentBackground]。
  final Color? unselectedBackground;

  /// 工務分流磚沒選中時的底色（`Main.dc.html:4091` 的 `rgba(255,255,255,.55)`）。
  static const Color translucentBackground = Color(0x8CFFFFFF);

  /// 右上角的數量徽章；不給值時不畫。
  ///
  /// 收 Widget 而不是收數字，是因為徽章上的數字要掛自己的 accessibility id，
  /// 而 id 的命名跟著呼叫端的畫面走（房務是 `pool.category.{分類}.count`）。
  final Widget? badge;

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
        color: selected ? TmsColors.cardBackground : unselectedBackground,
        borderRadius: BorderRadius.circular(TmsRadius.tile),
        // 外環長在磚的外面而不是內側，磚的內容框因此不隨選中與否移動
        // （`Main.dc.html:4232` 的 `box-shadow: 0 0 0 2px`）。畫成內縮邊框的話，
        // 點一下整格圖示與標籤會往內跳 2pt。
        boxShadow: selected
            ? const [BoxShadow(color: TmsColors.brandPrimary, spreadRadius: 2)]
            : null,
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
            child:
                iconChild ?? Icon(icon, size: 24, color: iconForeground),
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
              // 磚要吃滿分類列分給它的那一格。Stack 預設只給子元件寬鬆約束，
              // 磚會縮成圖示那麼寬，四格就擠在各自格子的左邊、徽章也跟著
              // 偏位；把外層的約束原樣傳下去，有沒有徽章磚都一樣寬。
              fit: StackFit.passthrough,
              clipBehavior: Clip.none,
              children: [
                tile,
                // 徽章上緣凸出磚外 4pt（`Main.dc.html:264`）。
                Positioned(top: -4, right: 2, child: badge!),
              ],
            ),
    );
  }
}
