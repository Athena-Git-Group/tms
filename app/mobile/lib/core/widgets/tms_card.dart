import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 卡片在版面裡的角色，決定圓角大小。
///
/// 設計上只有三級：主視覺卡最圓，分組列表次之，底部面板裡的白卡最小。
/// 讓呼叫端選角色而不是直接填圓角，是為了避免同一個畫面上出現兩張看起來
/// 一樣、圓角卻差幾個 pixel 的卡。
enum TmsCardStyle {
  /// 首頁與待認領的主視覺卡。
  hero,

  /// 分組列表群組、工作磚、狀態卡。
  grouped,

  /// 底部面板裡的白卡。
  inSheet,
}

/// 全 App 的白卡容器：房務與工務兩側的單卡、狀態卡、判定卡都用這一個。
///
/// 畫面只要說明「這是哪一級的卡、裡面放什麼」，底色、圓角與外環一律由這裡
/// 決定。畫面各自寫 `BoxDecoration` 的話，每多一個畫面就多一套對不上設計的
/// 數值，改設計時也沒有人找得齊。
///
/// 預設是白底、分組級圓角、四邊 16 的內距，沒有外框也不可點。
class TmsCard extends StatelessWidget {
  const TmsCard({
    super.key,
    required this.child,
    this.style = TmsCardStyle.grouped,
    this.padding = const EdgeInsets.all(TmsSpacing.lg),
    this.margin,
    this.highlighted = false,
    this.backgroundColor,
    this.onTap,
  });

  final Widget child;
  final TmsCardStyle style;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;

  /// 要不要在卡的四周加一圈品牌色外環。
  ///
  /// 推播指定某一間房時，待認領清單用這圈外環指出是哪一張單；預設不加。
  /// 這裡刻意不改底色：底色換掉會讓員工以為那張單變成另一種狀態，外環才
  /// 讀得出「要找的是這一張」。
  final bool highlighted;

  /// 卡的底色；不給值時是白色。
  ///
  /// 少數刻意不用白底的卡才傳值進來，例如慶祝頁上成對的統計磚。
  final Color? backgroundColor;

  /// 點整張卡時要做的事；不給值時這張卡不可點。
  final VoidCallback? onTap;

  double get _radius {
    switch (style) {
      case TmsCardStyle.hero:
        return TmsRadius.cardHero;
      case TmsCardStyle.grouped:
        return TmsRadius.card;
      case TmsCardStyle.inSheet:
        return TmsRadius.cardInSheet;
    }
  }

  @override
  Widget build(BuildContext context) {
    final card = Container(
      margin: margin,
      padding: padding,
      decoration: BoxDecoration(
        color: backgroundColor ?? TmsColors.cardBackground,
        borderRadius: BorderRadius.circular(_radius),
        // 標亮的外環長在卡的外面而不是內側，卡裡的內容因此不隨標亮移動
        // （`Main.dc.html:4255` 的 `box-shadow: 0 0 0 3px`）。畫成內縮邊框的話，
        // 推播把某一張卡標亮的瞬間，那張卡的整段文字會往內跳 3pt。
        boxShadow: highlighted
            ? const [BoxShadow(color: TmsColors.brandPrimary, spreadRadius: 3)]
            : null,
      ),
      child: child,
    );
    if (onTap == null) {
      return card;
    }
    return GestureDetector(onTap: onTap, child: card);
  }
}
