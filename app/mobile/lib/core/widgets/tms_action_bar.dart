import 'dart:math' as math;

import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 置底操作列共用的外框：白底、上緣一條 0.5pt 的分隔線、左右各留 16。
///
/// 底部留白由這一層自己算，不靠外面包 `SafeArea`。原型的操作列是直接貼在螢幕
/// 底緣、把安全區當成自己 padding 的一部分，外面再包一層 `SafeArea` 會變成留
/// 兩次白。用這兩個元件的畫面要把外層 `SafeArea` 的 `bottom` 關掉。
class _ActionBarChrome extends StatelessWidget {
  const _ActionBarChrome({
    required this.topPadding,
    required this.bottomPadding,
    required this.child,
  });

  final double topPadding;
  final double bottomPadding;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        topPadding,
        TmsSpacing.screenEdge,
        bottomPadding,
      ),
      decoration: const BoxDecoration(
        color: TmsColors.cardBackground,
        border: Border(
          top: BorderSide(
            color: TmsColors.opaqueSeparator,
            width: TmsSize.hairline,
          ),
        ),
      ),
      child: child,
    );
  }
}

/// 詳情頁的置底操作列：任務頁與工務單詳情。
///
/// 一列最多兩顆，左邊次要、右邊主要，寬度 1 比 2，中間隔 12。這個比例讓主要
/// 動作明顯比較大，員工用拇指點的時候不會誤觸旁邊那顆；12 的間距也是為了同一
/// 件事。只有一顆時那一顆撐滿整列。
///
/// 兩顆都沒有的狀態整列不畫，呼叫端不必自己判斷。
class TmsActionBar extends StatelessWidget {
  const TmsActionBar({super.key, this.hint, this.secondary, this.primary});

  /// 按鈕上方的說明句，例如「還有 3 項沒勾」。
  final Widget? hint;

  /// 左邊那顆，佔 1 份寬。
  final Widget? secondary;

  /// 右邊那顆，佔 2 份寬。
  final Widget? primary;

  /// 操作列自己要吃掉的底部安全區，對應原型的 `dv.actionBottom`
  /// （Main.dc.html:4425）。沒有安全區的機型（例如有實體 Home 鍵的）留 16，
  /// 不然按鈕會貼著螢幕邊緣。
  static double bottomInset(BuildContext context) {
    final inset = MediaQuery.viewPaddingOf(context).bottom;
    return inset > 0 ? inset : TmsSpacing.lg;
  }

  @override
  Widget build(BuildContext context) {
    final hint = this.hint;
    final secondary = this.secondary;
    final primary = this.primary;
    if (secondary == null && primary == null) {
      return const SizedBox.shrink();
    }
    final row = Row(
      children: <Widget>[
        if (secondary != null) Expanded(child: secondary),
        if (secondary != null && primary != null)
          const SizedBox(width: TmsSpacing.md),
        if (primary != null) Expanded(flex: 2, child: primary),
      ],
    );
    return _ActionBarChrome(
      topPadding: TmsSpacing.md,
      bottomPadding: bottomInset(context),
      child: hint == null
          ? row
          : Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                hint,
                const SizedBox(height: TmsSpacing.sm),
                row,
              ],
            ),
    );
  }
}

/// 表單頁的置底操作列：登入、變更密碼、拾獲登記。
///
/// 送出鈕固定在畫面底部，不跟著表單捲走：員工填完最後一格時，手指已經在螢幕
/// 下半部，送出鈕要在原地等他。送出鈕底下通常再掛一顆純文字鈕（「忘記密碼？」），
/// 所以這裡排的是直的一行。
class TmsFormActionBar extends StatelessWidget {
  const TmsFormActionBar({super.key, required this.children});

  final List<Widget> children;

  /// 表單頁留的底部空間比詳情頁窄，對應原型的 `lg.barBottom` 與 `pw.barBottom`
  /// （Main.dc.html:4533、4653）。表單頁的捲動區已經很擠，操作列再占掉整個安全
  /// 區就只剩一兩格輸入框看得到。
  static double bottomInset(BuildContext context) {
    final actionBottom = TmsActionBar.bottomInset(context);
    return actionBottom > TmsSpacing.lg
        ? math.max(actionBottom - 14, TmsSpacing.sm)
        : TmsSpacing.sm;
  }

  @override
  Widget build(BuildContext context) {
    final spaced = <Widget>[];
    for (final child in children) {
      if (spaced.isNotEmpty) {
        // 間距取 4：原型的容器間距是 8，底下那顆純文字鈕再往上拉 4
        // （Main.dc.html:1087、1090），兩個數字抵銷後看到的就是 4。
        spaced.add(const SizedBox(height: TmsSpacing.xs));
      }
      spaced.add(child);
    }
    return _ActionBarChrome(
      topPadding: TmsSpacing.sm2,
      bottomPadding: bottomInset(context),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: spaced,
      ),
    );
  }
}
