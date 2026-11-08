import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 膠囊按鈕的外觀。一個畫面上同時最多出現一主一次，不要兩顆都用 [filled]：
/// 兩顆長得一樣重，員工就得逐字讀才知道哪一顆是「往下走」的那一顆。
enum TmsPillButtonStyle {
  /// 主要動作：品牌色底、白字。停用時底色換成不透明灰，字維持白色。
  filled,

  /// 次要動作：白底、品牌色 1.5 的外框與文字。
  outlined,

  /// 深底上的主要動作（進行中那張卡）：白底、品牌色字。
  inverse,
}

/// 全 App 的膠囊按鈕：首頁主卡的「繼續處理」、任務頁的底部動作列、登入頁的
/// 置底送出鈕都用這一個。
///
/// 高度由呼叫端給，因為原型在不同位置用的是不同高度（任務頁與登入 54、面板
/// 置底 52、下一間卡 50、深底上的繼續處理 48），但圓角、字級與配色三件事一律
/// 由這裡決定——這三件事各畫面自己寫就會各差一點。
///
/// 這個元件刻意不掛 accessibility id：同一顆按鈕在不同畫面上的 id 不同，掛死
/// 會撞。呼叫端自己包一層 `TmsSemantics`。也刻意不自建 semantics 節點，按鈕
/// 文字因此會併進外層節點的 label，定位與讀值落在同一個節點上。
class TmsPillButton extends StatelessWidget {
  const TmsPillButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.style = TmsPillButtonStyle.filled,
    this.height = TmsSize.primaryButtonHeight,
    this.child,
  });

  /// 按鈕文字。[child] 有值時只當成語意上的說明，畫面上顯示的是 [child]。
  final String label;

  /// 點下去要做的事；null 代表停用。
  final VoidCallback? onPressed;

  final TmsPillButtonStyle style;

  /// 按鈕高度，單位是 logical pixel。
  final double height;

  /// 改放別的東西當按鈕內容（例如送出中的轉圈）。
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null;
    final Color background;
    final Color disabledBackground;
    final Color foreground;
    switch (style) {
      case TmsPillButtonStyle.filled:
        background = TmsColors.brandPrimary;
        disabledBackground = TmsColors.opaqueSeparator;
        foreground = TmsColors.brandOnPrimary;
      case TmsPillButtonStyle.outlined:
        background = TmsColors.cardBackground;
        disabledBackground = TmsColors.cardBackground;
        foreground = enabled
            ? TmsColors.brandPrimary
            : TmsColors.opaqueSeparator;
      case TmsPillButtonStyle.inverse:
        background = TmsColors.brandOnPrimary;
        disabledBackground = TmsColors.brandOnPrimary;
        foreground = TmsColors.brandPrimary;
    }
    final Widget button = CupertinoButton(
      padding: EdgeInsets.zero,
      borderRadius: BorderRadius.circular(TmsRadius.pill),
      color: background,
      disabledColor: disabledBackground,
      minimumSize: Size(double.infinity, height),
      onPressed: onPressed,
      child:
          child ??
          Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TmsText.headline.copyWith(color: foreground),
          ),
    );
    if (style != TmsPillButtonStyle.outlined) {
      return button;
    }
    // 外框畫在按鈕外面一層：CupertinoButton 自己沒有邊框參數，而按下時的
    // 透明度動畫只作用在它的內容上，外框因此不會跟著閃。
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(TmsRadius.pill),
        border: Border.all(color: foreground, width: 1.5),
      ),
      child: button,
    );
  }
}
