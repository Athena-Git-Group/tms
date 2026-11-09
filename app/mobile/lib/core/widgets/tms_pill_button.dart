import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 膠囊按鈕的外觀。一個畫面上同時最多出現一主一次，不要兩顆都用 [filled]：
/// 兩顆長得一樣重，員工就得逐字讀才知道哪一顆是「往下走」的那一顆。
enum TmsPillButtonStyle {
  /// 主要動作：品牌色底、白字。停用時底色換成不透明灰，字維持白色。
  filled,

  /// 次要動作：白底、品牌色 1.5 的外框與文字。
  outlined,

  /// 破壞性的次要動作（撤回工務單）：白底、系統紅 1.5 的外框與文字。
  /// 紅色只給「做了就收不回來」的動作，平常的次要動作用 [outlined]。
  dangerOutlined,

  /// 深底上的主要動作（進行中那張卡）：白底、品牌色字。
  inverse,
}

/// 實心主按鈕在 iOS 26 外觀下的光暈。
///
/// 網頁原型把外光暈與內緣高光寫在同一條 CSS `box-shadow` 裡，用 `inset`
/// 區分。Flutter 的 [BoxDecoration] 只畫得出外光暈，`inset` 那兩段得自己在
/// 按鈕上下緣疊一條 1pt 的線，所以一條 CSS 規則在這裡拆成三個欄位。
///
/// 原型有兩組不同的值，而且不是同一個情境用同一組，所以兩組都照抄，不在這裡
/// 收斂成一組。要加第三個呼叫處時，先去原型查它用的是哪一個 `btnShadow`。
@immutable
class TmsPillGlow {
  const TmsPillGlow({
    required this.outer,
    required this.innerTop,
    this.innerBottom,
  });

  /// 按鈕外面的品牌色光暈。CSS `box-shadow` 的模糊半徑與
  /// [BoxShadow.blurRadius] 是同一個量，照抄不換算。
  final List<BoxShadow> outer;

  /// 上緣內側 1pt 的白色高光。
  final Color innerTop;

  /// 下緣內側 1pt 的暗邊；沒有就不畫。
  final Color? innerBottom;

  /// 原型共用的 `ui.btnShadow`（Main.dc.html:4460）。首頁的下一間、房務待認領
  /// 的認領鈕、任務頁與房務視角工務單詳情的置底主要鈕都是這一組。
  static const TmsPillGlow standard = TmsPillGlow(
    outer: <BoxShadow>[
      BoxShadow(color: Color(0x470C7680), offset: Offset(0, 6), blurRadius: 18),
    ],
    innerTop: Color(0x59FFFFFF),
    innerBottom: Color(0x14000000),
  );

  /// 原型的另一組（Main.dc.html:4049、4532、4655、4727、4942）。工務視角的畫面、
  /// 底部面板的送出鈕、登入與變更密碼的置底送出鈕是這一組：外光暈的模糊半徑少
  /// 2、顏色濃一點，而且沒有下緣暗邊。
  static const TmsPillGlow alt = TmsPillGlow(
    outer: <BoxShadow>[
      BoxShadow(color: Color(0x4D0C7680), offset: Offset(0, 6), blurRadius: 16),
    ],
    innerTop: Color(0x59FFFFFF),
  );
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
    this.glow = TmsPillGlow.standard,
    this.child,
  });

  /// 按鈕文字。[child] 有值時只當成語意上的說明，畫面上顯示的是 [child]。
  final String label;

  /// 點下去要做的事；null 代表停用。
  final VoidCallback? onPressed;

  final TmsPillButtonStyle style;

  /// 按鈕高度，單位是 logical pixel。
  final double height;

  /// 這一顆要畫哪一組光暈。預設 [TmsPillGlow.standard]，工務視角與表單置底列
  /// 的按鈕要改傳 [TmsPillGlow.alt]，給 null 則一條都不畫。
  ///
  /// 只有可按的 [TmsPillButtonStyle.filled] 畫得出來。停用時底色是灰的，罩上
  /// 品牌色光暈會讓員工以為還點得下去。
  final TmsPillGlow? glow;

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
      case TmsPillButtonStyle.dangerOutlined:
        background = TmsColors.cardBackground;
        disabledBackground = TmsColors.cardBackground;
        foreground = enabled ? TmsColors.systemRed : TmsColors.opaqueSeparator;
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
    switch (style) {
      case TmsPillButtonStyle.outlined:
      case TmsPillButtonStyle.dangerOutlined:
        // 外框畫在按鈕外面一層：CupertinoButton 自己沒有邊框參數，而按下時的
        // 透明度動畫只作用在它的內容上，外框因此不會跟著閃。
        return DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(TmsRadius.pill),
            border: Border.all(color: foreground, width: 1.5),
          ),
          child: button,
        );
      case TmsPillButtonStyle.inverse:
        return button;
      case TmsPillButtonStyle.filled:
        final glow = this.glow;
        if (glow == null || !enabled) {
          return button;
        }
        return _glow(glow, button);
    }
  }

  Widget _glow(TmsPillGlow glow, Widget button) {
    final radius = BorderRadius.circular(TmsRadius.pill);
    final innerBottom = glow.innerBottom;
    return DecoratedBox(
      // 光暈畫在裁切之外，畫在裡面會被膠囊自己的圓角切掉。
      decoration: BoxDecoration(borderRadius: radius, boxShadow: glow.outer),
      child: ClipRRect(
        borderRadius: radius,
        clipBehavior: Clip.antiAlias,
        child: Stack(
          children: <Widget>[
            button,
            // 內緣高光是兩條 1pt 的橫線，交給膠囊的圓角裁成弧形。
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              height: 1,
              child: ColoredBox(color: glow.innerTop),
            ),
            if (innerBottom != null)
              Positioned(
                bottom: 0,
                left: 0,
                right: 0,
                height: 1,
                child: ColoredBox(color: innerBottom),
              ),
          ],
        ),
      ),
    );
  }
}
