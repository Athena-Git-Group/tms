import 'dart:math' as math;

import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_glass.dart';
import 'tms_motion.dart';

/// 左上角那一顆鈕要帶員工回到哪裡。
///
/// 設計稿把這顆鈕畫成圖示而不是文字（`Main.dc.html:1428`），所以它只有「收掉
/// 整個面板」和「退回上一步」兩種意思，由呼叫端挑一個，不由呼叫端給字。讓呼叫
/// 端傳字的話，四個面板會各自寫出「關閉」「取消」「返回」三種說法，而畫面上根
/// 本看不到字，誰寫錯都不會有人發現。
enum TmsSheetLeading {
  /// 收掉整個面板，畫面回到面板底下那一頁。
  close(CupertinoIcons.xmark, 15, '關閉'),

  /// 退回面板裡的上一步，面板本身留著。
  back(CupertinoIcons.chevron_back, 18, '返回');

  const TmsSheetLeading(this.icon, this.iconSize, this.label);

  final IconData icon;

  /// 圖示邊長，取設計稿兩顆 SVG 的尺寸：✕ 是 15、‹ 是 18。
  final double iconSize;

  /// 螢幕朗讀時念出來的名字。畫面上不顯示。
  final String label;
}

/// 把面板推上畫面的路由。
///
/// 面板升起時背後那一頁要壓暗，點暗掉的地方要能把面板收回去，升起的過程是
/// 0.26 秒的由下而上（`Main.dc.html:1414`、`Main.dc.html:29`）。這三件事屬於
/// 「怎麼把面板放上畫面」而不是「面板長什麼樣」，所以由路由負責，面板本身只管
/// 自己的長相。
///
/// 四個面板各自呼叫 `showCupertinoModalPopup` 的話，遮罩濃度會是框架預設值而不
/// 是設計稿的值，升起的秒數也會是框架的 0.335 秒，而且沒有一個地方可以一次對帳。
class TmsSheetRoute<T> extends PopupRoute<T> {
  TmsSheetRoute({required this.builder});

  final WidgetBuilder builder;

  /// 開面板的唯一入口。面板用 `Navigator.pop` 關閉，回傳值就是 pop 的值。
  static Future<T?> show<T>(BuildContext context, WidgetBuilder builder) {
    return Navigator.of(context, rootNavigator: true)
        .push(TmsSheetRoute<T>(builder: builder));
  }

  @override
  Color get barrierColor => TmsColors.scrimSheet;

  @override
  bool get barrierDismissible => true;

  @override
  String get barrierLabel => '關閉面板';

  @override
  Duration get transitionDuration => TmsMotion.up.duration;

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) {
    return Align(alignment: Alignment.bottomCenter, child: builder(context));
  }

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    return SlideTransition(
      position: Tween<Offset>(begin: const Offset(0, 1), end: Offset.zero)
          .animate(
        CurvedAnimation(parent: animation, curve: TmsMotion.up.curve),
      ),
      child: child,
    );
  }
}

/// 從畫面底部升起的面板外框：延後／中斷、報修、查一下、工務單四組畫面共用這
/// 一個。
///
/// 面板在 iOS 上是一種有固定長相的東西——上緣大圓角、頂上一條抓柄、底下是一列
/// 置中標題——員工看到這三個記號就知道「這是一層蓋上來的東西，按左上角或點旁
/// 邊就會回到原來的畫面」。各畫面自己用 `Container` 兜的話，有的有抓柄有的沒
/// 有、圓角各差幾個 pixel，同一個 App 裡就會出現好幾種不同的面板。
///
/// 外框只管四件事：上緣圓角、底色與陰影，抓柄，標題列，以及內容與置底按鈕的捲
/// 動與留白。面板裡要放什麼由呼叫端決定，[children] 之間的間距統一由外框插。
///
/// ## 底色是不透明的，不是毛玻璃
///
/// iOS 26 的面板看起來是一整塊玻璃，但設計稿把面板底色訂成不透明的系統分組灰
/// （`Main.dc.html:4933`）。半透明底疊上置底按鈕列那一塊漸層底時，兩層的透明度
/// 疊起來會在接縫處透出一條色差。整支外框只有左上角那顆鈕是玻璃。
///
/// ## 鍵盤升起時底部要讓位
///
/// 面板貼在畫面最下緣，鍵盤會整個蓋住輸入欄與確認鈕，員工既看不到自己打了什麼
/// 也按不到確認。底部讓出 `viewInsets` 的高度，整個面板就被頂到鍵盤上方。
class TmsSheet extends StatelessWidget {
  const TmsSheet({
    super.key,
    required this.title,
    required this.children,
    this.leading = TmsSheetLeading.close,
    this.onLeadingTap,
    this.backgroundColor = TmsColors.groupedBackground,
    this.bottomAction,
  });

  /// 標題列正中央那一行字。
  final String title;

  /// 左上角那顆鈕的意思。沒給 [onLeadingTap] 就不畫這顆鈕。
  final TmsSheetLeading leading;

  final VoidCallback? onLeadingTap;

  /// 面板底色。預設是系統分組灰，裡面的白卡才浮得出來；整面都是白卡的面板才
  /// 傳白色進來。
  final Color backgroundColor;

  /// 面板內容，由上而下排，之間自動留 [TmsSpacing.md]。
  final List<Widget> children;

  /// 置底的主要動作，高度請維持 [TmsSheetButton.height]。
  ///
  /// 它不跟著內容捲走，員工捲到一半也要按得到確認。外框依這個高度算出內容要預
  /// 留多少捲動空間，傳更高的東西進來只會讓最後一點內容捲不乾淨，不會畫壞。
  final Widget? bottomAction;

  /// 面板再高也要露出底下那一頁的一角。完全蓋滿的話它看起來就是換了一頁，員工
  /// 不會想到旁邊還點得到。
  static const double _gapToScreenTop = 40;

  /// 標題列高度。
  static const double _titleRowHeight = 52;

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    // 沒有瀏海的機型安全區是 0，底部留白仍然要有 16，否則按鈕會貼到螢幕邊緣。
    final bottomInset = math.max(media.padding.bottom, TmsSpacing.lg);
    final action = bottomAction;
    final barHeight = TmsSpacing.lg + TmsSheetButton.height + bottomInset;

    final content = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i > 0) {
        content.add(const SizedBox(height: TmsSpacing.md));
      }
      content.add(children[i]);
    }

    return Container(
      width: double.infinity,
      constraints: BoxConstraints(
        maxHeight: media.size.height - _gapToScreenTop,
      ),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(TmsRadius.sheet),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x2E000000),
            offset: Offset(0, -4),
            blurRadius: 30,
          ),
        ],
      ),
      child: Padding(
        padding: EdgeInsets.only(bottom: media.viewInsets.bottom),
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
            const SizedBox(height: TmsSpacing.md),
            _titleRow(),
            Flexible(
              child: Stack(
                children: [
                  SingleChildScrollView(
                    padding: EdgeInsets.fromLTRB(
                      TmsSpacing.screenEdge,
                      TmsSpacing.md,
                      TmsSpacing.screenEdge,
                      action == null
                          ? bottomInset
                          : TmsSpacing.md + barHeight,
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: content,
                    ),
                  ),
                  if (action != null)
                    Positioned(
                      left: 0,
                      right: 0,
                      bottom: 0,
                      child: _bottomBar(action, bottomInset, barHeight),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 置底按鈕列。
  ///
  /// 它蓋在捲動內容上面，底是一塊上緣 16pt 由透明轉到面板底色的漸層，捲到按鈕
  /// 後面的內容會淡掉而不是被硬切。直接給它一塊實色的話，員工看不出下面還有東
  /// 西；什麼都不給的話，內容會直接貼在按鈕上。
  Widget _bottomBar(Widget action, double bottomInset, double height) {
    return Container(
      constraints: BoxConstraints(minHeight: height),
      padding: EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        TmsSpacing.lg,
        TmsSpacing.screenEdge,
        bottomInset,
      ),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [backgroundColor.withAlpha(0), backgroundColor],
          stops: [0, TmsSpacing.lg / height],
        ),
      ),
      child: action,
    );
  }

  /// 標題列：左欄按鈕、中欄標題、右欄留白。左右兩欄等寬，標題才會真的在畫面正
  /// 中央，而不是被左邊的按鈕推開。
  Widget _titleRow() {
    final onTap = onLeadingTap;
    return Container(
      constraints: const BoxConstraints(minHeight: _titleRowHeight),
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      child: Row(
        children: [
          Expanded(
            child: Align(
              alignment: Alignment.centerLeft,
              child: onTap == null
                  ? const SizedBox.shrink()
                  : _leadingButton(onTap),
            ),
          ),
          Text(title, style: TmsText.headline),
          const Expanded(child: SizedBox.shrink()),
        ],
      ),
    );
  }

  /// 左上角那顆 44pt 圓形玻璃鈕。
  ///
  /// 它是整支外框唯一的玻璃，疊在面板自己的底色上，所以模糊只有 12 而且不調飽
  /// 和度（`Main.dc.html:1428`）。
  Widget _leadingButton(VoidCallback onTap) {
    return SizedBox(
      width: TmsSize.minTouchTarget,
      height: TmsSize.minTouchTarget,
      child: TmsGlass(
        blur: 12,
        color: const Color(0xB3FFFFFF),
        borderRadius: BorderRadius.circular(TmsRadius.pill),
        border: Border.all(color: const Color(0xCCFFFFFF)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x14000000),
            offset: Offset(0, 2),
            blurRadius: 10,
          ),
        ],
        child: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: onTap,
          child: Icon(
            leading.icon,
            size: leading.iconSize,
            color: TmsColors.labelSoft,
            semanticLabel: leading.label,
          ),
        ),
      ),
    );
  }
}

/// 面板置底那一顆滿寬膠囊鈕，以及面板裡少數要跟它長一樣的次要動作。
///
/// 高度與圓角跟著設計走（52pt 滿寬膠囊），呼叫端只決定文字、主次與可不可按。
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
    // 不能按的時候底色是固定的中灰，白字留著（`Main.dc.html:4941`）。
    // `CupertinoButton.filled` 改用框架自己的淡色，那個值跟設計稿對不上。
    final background = filled
        ? (onPressed == null
            ? TmsColors.opaqueSeparator
            : TmsColors.brandPrimary)
        : TmsColors.cardBackground;
    return SizedBox(
      width: double.infinity,
      height: height,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
          border: filled
              ? null
              : Border.all(color: TmsColors.brandPrimary, width: 1.5),
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
