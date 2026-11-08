import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 從畫面底部升起的面板外框：延後／中斷、報修、查一下三個面板共用這一個。
///
/// 面板在 iOS 上是一種有固定長相的東西——上緣圓角、頂上一條抓柄、底下是一列
/// 置中標題——員工看到這三個記號就知道「這是一層蓋上來的東西，往下滑或按左上
/// 角就會回到原來的畫面」。三個面板各自用 `Container` 兜的話，有的有抓柄有的
/// 沒有、圓角各差幾個 pixel，同一個 App 裡就會出現三種不同的面板。
///
/// 外框只管四件事：上緣圓角與底色、抓柄、標題列、內容與置底按鈕的捲動與留白。
/// 面板裡要放什麼由呼叫端決定，[children] 之間的間距統一由外框插。
///
/// 鍵盤升起時底部要讓出 `viewInsets` 的高度：面板貼在畫面最下緣，鍵盤會整個
/// 蓋住輸入欄與確認鈕，員工既看不到自己打了什麼，也按不到確認。
class TmsSheet extends StatelessWidget {
  const TmsSheet({
    super.key,
    required this.title,
    required this.children,
    this.leadingLabel,
    this.onLeadingTap,
    this.backgroundColor = TmsColors.groupedBackground,
    this.bottomAction,
  });

  /// 標題列正中央那一行字。
  final String title;

  /// 標題列左欄的文字鈕（「關閉」「返回」）；不給就只有標題。
  final String? leadingLabel;

  final VoidCallback? onLeadingTap;

  /// 面板底色。預設是系統分組灰，裡面的白卡才浮得出來；整面都是白卡的面板才
  /// 傳白色進來。
  final Color backgroundColor;

  /// 面板內容，由上而下排，之間自動留 [TmsSpacing.md]。
  final List<Widget> children;

  /// 置底的主要動作。它不跟著內容捲走——員工捲到一半也要按得到確認。
  final Widget? bottomAction;

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final keyboard = media.viewInsets.bottom;
    final content = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i > 0) {
        content.add(const SizedBox(height: TmsSpacing.md));
      }
      content.add(children[i]);
    }
    return Container(
      width: double.infinity,
      // 面板再高也要露出底下那一頁的一角：完全蓋滿的話，它看起來就是換了一頁，
      // 員工不會想到可以往下滑把它收起來。
      constraints: BoxConstraints(maxHeight: media.size.height - 40),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(TmsRadius.sm),
        ),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: EdgeInsets.only(bottom: keyboard),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(height: TmsSpacing.xs2),
              Container(
                width: 36,
                height: 5,
                decoration: BoxDecoration(
                  color: TmsColors.opaqueSeparator,
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
              _titleRow(context),
              Flexible(
                child: SingleChildScrollView(
                  padding: EdgeInsets.fromLTRB(
                    TmsSpacing.screenEdge,
                    0,
                    TmsSpacing.screenEdge,
                    bottomAction == null ? TmsSpacing.lg : 0,
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: content,
                  ),
                ),
              ),
              if (bottomAction != null)
                Padding(
                  padding: const EdgeInsets.fromLTRB(
                    TmsSpacing.screenEdge,
                    TmsSpacing.lg,
                    TmsSpacing.screenEdge,
                    TmsSpacing.lg,
                  ),
                  child: bottomAction,
                ),
            ],
          ),
        ),
      ),
    );
  }

  /// 標題列：左欄文字鈕、中欄標題、右欄留白。左右兩欄等寬，標題才會真的在畫面
  /// 正中央，而不是被左邊的按鈕推開。
  Widget _titleRow(BuildContext context) {
    final leading = leadingLabel;
    return Container(
      constraints: const BoxConstraints(minHeight: 52),
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.sm),
      child: Row(
        children: [
          Expanded(
            child: Align(
              alignment: Alignment.centerLeft,
              child: leading == null
                  ? const SizedBox.shrink()
                  : CupertinoButton(
                      padding: const EdgeInsets.symmetric(
                        horizontal: TmsSpacing.sm,
                      ),
                      onPressed: onLeadingTap,
                      child: Text(
                        leading,
                        style: TmsText.body.copyWith(
                          color: TmsColors.brandPrimary,
                        ),
                      ),
                    ),
            ),
          ),
          Text(title, style: TmsText.headline),
          const Expanded(child: SizedBox.shrink()),
        ],
      ),
    );
  }
}

/// 面板置底那一顆滿寬膠囊鈕，以及面板裡少數要跟它長一樣的次要動作。
///
/// 高度與圓角跟著設計走（52pt 滿寬膠囊），呼叫端只決定文字、主次與可不可按。
/// 內層仍然是 `CupertinoButton`：它會自己把不可按的狀態畫成淡色，點擊與無障礙
/// 的行為也跟 App 其他按鈕一致。
class TmsSheetButton extends StatelessWidget {
  const TmsSheetButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.filled = true,
    this.child,
  });

  final String label;

  /// 不給值代表現在不能按。
  final VoidCallback? onPressed;

  /// 實心主按鈕（預設）或白底描邊的次要按鈕。
  final bool filled;

  /// 要放別的東西（例如送出中的轉圈）時用這個取代文字。
  final Widget? child;

  /// 置底按鈕的高度。
  static const double height = 52;

  @override
  Widget build(BuildContext context) {
    final foreground =
        filled ? TmsColors.brandOnPrimary : TmsColors.brandPrimary;
    final content = child ??
        Text(label, style: TmsText.headline.copyWith(color: foreground));
    if (filled) {
      return SizedBox(
        width: double.infinity,
        height: height,
        child: CupertinoButton.filled(
          padding: EdgeInsets.zero,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
          onPressed: onPressed,
          child: content,
        ),
      );
    }
    return SizedBox(
      width: double.infinity,
      height: height,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: TmsColors.cardBackground,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
          border: Border.all(color: TmsColors.brandPrimary, width: 1.5),
        ),
        child: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: onPressed,
          child: content,
        ),
      ),
    );
  }
}
