import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_glass.dart';

/// 凍結在內容之上的導覽列，給任務頁與工務單詳情這類「標題區很大、底下還有
/// 很長一段可捲內容」的整頁使用。
///
/// ## 這個元件要保證什麼
///
/// 導覽列永遠疊在捲動內容上面，而且只有兩種樣子。頁面還沒捲動時它與底下的
/// 標題區同一個底色、同一個文字色，看起來像標題區的一部分；標題區捲走之後
/// 它換成毛玻璃，並把標題區的主要資訊（房號、計時）接上來寫成一行小標題，
/// 讓員工捲到畫面下半部時仍然知道自己在哪一張單上。
///
/// ## 怎麼做到
///
/// 捲動偵測交給 [TmsScrollNavBarScroll]，它把「捲過 [scrollThreshold] 沒有」
/// 這一個布林值交給 [scrolled]。其餘每一項外觀都從這個布林值推出來，包含底
/// 色、文字色、要顯示 [restingTitle] 還是 [scrolledTitle]、左右兩顆圓鈕的底
/// 色與邊框。元件本身沒有狀態。
///
/// ## 不這樣做會壞在哪
///
/// 捲動狀態若由各畫面自己算，門檻值與「超過才換」的比較方式會各寫各的，同
/// 一個 App 的兩張詳情頁會在不同的捲動位置換樣子。標題文字也一樣：由元件決
/// 定「什麼時候換」、呼叫端只提供「換成什麼字」，兩者才不會互相猜測。
class TmsScrollNavBar extends StatelessWidget {
  const TmsScrollNavBar({
    super.key,
    required this.scrolled,
    required this.restingBackground,
    required this.restingForeground,
    required this.restingTitle,
    required this.scrolledTitle,
    this.titleSuffix,
    this.topInset = 0,
    this.glassFadeStop = 0.6,
    this.leading,
    this.trailing,
  });

  /// 捲動位置是否已經超過 [scrollThreshold]。
  final bool scrolled;

  /// 靜止時的底色，取呼叫端標題區的底色。
  final Color restingBackground;

  /// 靜止時的文字與圖示色，取呼叫端標題區的前景色。
  final Color restingForeground;

  final String restingTitle;

  final String scrolledTitle;

  /// 接在標題後面的那一小段紅字，例如超時提示。捲動後才顯示，不給就不畫。
  final String? titleSuffix;

  /// 頂部安全區高度。導覽列畫在安全區外面時由呼叫端把它傳進來，元件就會把
  /// 底色一路鋪到螢幕頂端；畫在 [SafeArea] 裡面時留 0，安全區已經被讓出去
  /// 了，再讓一次會多出一條空白。
  final double topInset;

  /// 毛玻璃底色從不透明轉到全透明的起點，0 是導覽列頂端、1 是底端。
  ///
  /// 頂著彩色標題區的頁面（任務頁、工務視角詳情）淡得早一點，玻璃與標題區的
  /// 交界才不明顯；房務視角詳情底下一路都是同一塊灰，晚一點淡掉看起來才像一
  /// 整片玻璃而不是一條橫帶（`Main.dc.html:4762`）。
  final double glassFadeStop;

  /// 左側項目，通常是 [TmsNavBarBackButton]。
  final Widget? leading;

  /// 右側項目，例如任務頁的報修鈕。要不要顯示由呼叫端自己決定，這裡只排版。
  final Widget? trailing;

  /// 捲過幾點之後換成毛玻璃。取 72，因為頁面最大的那個字（房號或計時）捲到
  /// 這個位置剛好整個鑽進導覽列底下。`Main.dc.html:5044`
  static const double scrollThreshold = 72;

  static const double glassBlur = 12;

  static const double glassSaturation = 160;

  @override
  Widget build(BuildContext context) {
    final onDarkHero = !scrolled && _isDark(restingBackground);
    final foreground = scrolled ? TmsColors.label : restingForeground;
    final title = scrolled ? scrolledTitle : restingTitle;
    final suffix = scrolled ? titleSuffix : null;

    return SizedBox(
      height: topInset + TmsSize.frozenNavBarHeight,
      child: Stack(
        children: [
          Positioned.fill(child: _buildBackground()),
          Positioned(
            top: topInset,
            left: 0,
            right: 0,
            height: TmsSize.minTouchTarget,
            child: _TmsNavBarTint(
              color: foreground,
              onDarkHero: onDarkHero,
              child: Row(
                children: [
                  const SizedBox(width: TmsSpacing.md),
                  ?leading,
                  const Spacer(),
                  ?trailing,
                  const SizedBox(width: TmsSpacing.md),
                ],
              ),
            ),
          ),
          // 標題另外疊一層，不排進上面那一列：標題要對齊整個畫面的正中，排
          // 進 Row 的話左右只要一側有按鈕、另一側沒有，標題就會被推偏。
          Positioned(
            top: topInset,
            left: TmsSize.frozenNavTitleInset,
            right: TmsSize.frozenNavTitleInset,
            height: TmsSize.minTouchTarget,
            child: Center(child: _buildTitle(title, suffix, foreground)),
          ),
        ],
      ),
    );
  }

  /// 標題只有一段字時走一般的 [Text]，接了超時那一段才改用 [Text.rich]。
  /// 一律用 [Text.rich] 的話，測試與自動化驗收用整串文字找這個節點會找不到。
  Widget _buildTitle(String title, String? suffix, Color foreground) {
    final style = TmsText.headline.copyWith(
      color: foreground,
      fontFeatures: TmsText.tabularNumbers,
    );
    if (suffix == null) {
      return Text(
        title,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        textAlign: TextAlign.center,
        style: style,
      );
    }
    return Text.rich(
      TextSpan(
        text: title,
        children: <InlineSpan>[
          TextSpan(
            text: suffix,
            style: const TextStyle(color: TmsColors.overtimeTimerOnLight),
          ),
        ],
      ),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      textAlign: TextAlign.center,
      style: style,
    );
  }

  Widget _buildBackground() {
    if (!scrolled) {
      return ColoredBox(color: restingBackground);
    }
    // 玻璃的底色走由上而下的漸層，讓內容從玻璃裡慢慢浮出來，而不是被一條
    // 硬邊切斷。原型把這件事做在一張遮罩上，連模糊一起淡掉；Flutter 的
    // BackdropFilter 一旦包進 ShaderMask 就只讀得到遮罩自己那一層、模糊不
    // 到背後的頁面，所以這裡讓模糊維持整片均勻，只把疊上去的底色淡掉。模
    // 糊半徑與飽和度仍與原型相同。`Main.dc.html:5043`
    return TmsGlass(
      blur: glassBlur,
      saturation: glassSaturation,
      color: const Color(0x00000000),
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: const <Color>[
              TmsColors.frozenNavGlass,
              TmsColors.frozenNavGlass,
              Color(0x00F2F2F7),
            ],
            stops: <double>[0, glassFadeStop, 1],
          ),
        ),
        child: const SizedBox.expand(),
      ),
    );
  }

  /// 底色算不算深色。原型把這件事寫死成「標題區底色等於品牌色」，這裡改用
  /// 亮度判斷，之後多一種標題區底色就不必回來補一個條件。
  static bool _isDark(Color color) => color.computeLuminance() < 0.5;
}

/// 把導覽列目前的前景色與「現在是不是疊在深色標題區上」交給底下的按鈕。
///
/// 兩顆圓鈕的底色與邊框只分兩種：疊在深色標題區上時壓得很淡，其餘情況用接
/// 近不透明的白。放進 [InheritedWidget] 之後，呼叫端擺按鈕時不必再把這兩個
/// 值一路傳下去，也就不會有某一顆鈕忘了跟著標題區變色。
class _TmsNavBarTint extends InheritedWidget {
  const _TmsNavBarTint({
    required this.color,
    required this.onDarkHero,
    required super.child,
  });

  final Color color;
  final bool onDarkHero;

  static _TmsNavBarTint? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_TmsNavBarTint>();

  @override
  bool updateShouldNotify(_TmsNavBarTint oldWidget) =>
      color != oldWidget.color || onDarkHero != oldWidget.onDarkHero;
}

/// 導覽列左右兩側的 44×44 圓形玻璃鈕，給返回、報修這類單一動作用。
///
/// 導覽列疊在彩色標題區上，一般的文字鈕在深底上讀不出來，所以項目自己帶一
/// 塊半透明圓底把文字區隔開。底色、邊框與陰影全部由 [TmsScrollNavBar] 透過
/// [_TmsNavBarTint] 決定，呼叫端只給圖示、無障礙標籤與按下去要做什麼。
/// `Main.dc.html:4455`
/// 導覽列圓鈕的兩種長相。
///
/// 底下是彩色標題區的頁面用 [tinted]，鈕要跟著標題區一起變深變淺，否則捲到一
/// 半會出現一顆在深底上發亮的白鈕。底下一路都是同一塊灰的頁面用 [plain]：那裡
/// 沒有會變的底色可跟，固定的白玻璃鈕才讀得出來（`Main.dc.html:1759`）。
enum TmsNavBarButtonStyle {
  tinted,
  plain,
}

class TmsNavBarButton extends StatelessWidget {
  const TmsNavBarButton({
    super.key,
    required this.semanticsLabel,
    required this.onPressed,
    required this.child,
    this.semanticsId,
    this.style = TmsNavBarButtonStyle.tinted,
  });

  /// 這顆鈕要不要跟著標題區變色；見 [TmsNavBarButtonStyle]。
  final TmsNavBarButtonStyle style;

  /// 讀螢幕時唸出來的名字。圓鈕裡面只有圖示，不給就沒有人知道它是什麼。
  final String semanticsLabel;

  /// 自動化測試定位這顆鈕用的 accessibility id。不給就只能靠名字找，名字
  /// 會跟著文案改，測試就會跟著一起紅。
  final String? semanticsId;

  final VoidCallback onPressed;

  final Widget child;

  static const double glassBlur = 12;

  static const double glassSaturation = 180;

  @override
  Widget build(BuildContext context) {
    final tint = _TmsNavBarTint.maybeOf(context);
    final plain = style == TmsNavBarButtonStyle.plain;
    final onDarkHero = !plain && (tint?.onDarkHero ?? false);
    // 把按鈕底下的節點併成一個：圓鈕裡面只有圖形，讀螢幕的人要在同一個節
    // 點上同時拿到名字與「可以按」。
    return MergeSemantics(
      child: Semantics(
        identifier: semanticsId,
        label: semanticsLabel,
        child: CupertinoButton(
          padding: EdgeInsets.zero,
          minimumSize: const Size(
            TmsSize.minTouchTarget,
            TmsSize.minTouchTarget,
          ),
          onPressed: onPressed,
          child: ExcludeSemantics(
            child: TmsGlass(
              blur: glassBlur,
              saturation: glassSaturation,
              color: onDarkHero
                  ? TmsColors.navButtonSurfaceOnDark
                  : TmsColors.navButtonSurface,
              borderRadius: BorderRadius.circular(TmsRadius.pill),
              border: Border.all(
                color: plain
                    ? TmsColors.plainNavButtonBorder
                    : (onDarkHero
                          ? TmsColors.navButtonBorderOnDark
                          : TmsColors.navButtonBorder),
                width: plain ? 1 : TmsSize.hairline,
              ),
              boxShadow: plain
                  ? const <BoxShadow>[
                      BoxShadow(
                        color: TmsColors.plainNavButtonShadow,
                        offset: Offset(0, 2),
                        blurRadius: 10,
                      ),
                    ]
                  : const <BoxShadow>[
                      BoxShadow(
                        color: TmsColors.glassShadow,
                        offset: Offset(0, 4),
                        blurRadius: 16,
                      ),
                    ],
              child: SizedBox(
                width: TmsSize.minTouchTarget,
                height: TmsSize.minTouchTarget,
                // 圖示跟著導覽列的前景色走，呼叫端給 Icon 時不必自己指定顏
                // 色，也就不會在標題區換底色時漏掉其中一顆鈕。
                child: IconTheme.merge(
                  data: IconThemeData(
                    color: plain
                        ? TmsColors.labelSoft
                        : (tint?.color ?? TmsColors.label),
                    size: TmsSize.navBarItemIcon,
                  ),
                  child: Center(child: child),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 詳情頁左上角回上一頁的鈕。
///
/// 箭頭旁邊不接上一頁的標題。同一張詳情頁可以從首頁、待認領池、通知中心與
/// 推播四個入口進來，寫出上一頁的名字會讓同一顆鈕在不同入口上長得不一樣，
/// 員工得先把那段字讀完才認得出它是返回。讀螢幕的人仍然聽得到「返回」，那
/// 段文字放在無障礙標籤上。
class TmsNavBarBackButton extends StatelessWidget {
  const TmsNavBarBackButton({
    super.key,
    required this.onPressed,
    this.semanticsLabel = '返回',
    this.semanticsId = defaultSemanticsId,
    this.style = TmsNavBarButtonStyle.tinted,
  });

  /// 這顆鈕要不要跟著標題區變色；見 [TmsNavBarButtonStyle]。
  final TmsNavBarButtonStyle style;

  /// 每一支詳情頁的返回鈕共用同一個 accessibility id，自動化測試要從任何
  /// 一頁退回首頁時才不必先判斷自己停在哪一頁。
  static const String defaultSemanticsId = 'nav.back-button';

  final VoidCallback onPressed;

  final String semanticsLabel;

  final String? semanticsId;

  @override
  Widget build(BuildContext context) {
    final tint = _TmsNavBarTint.maybeOf(context);
    final plain = style == TmsNavBarButtonStyle.plain;
    return TmsNavBarButton(
      semanticsId: semanticsId,
      semanticsLabel: semanticsLabel,
      onPressed: onPressed,
      style: style,
      child: CustomPaint(
        size: plain
            ? const Size(_plainChevronWidth, _plainChevronHeight)
            : const Size(TmsSize.navBarChevron, TmsSize.navBarChevron),
        painter: plain
            ? const _PlainChevronPainter()
            : _NavChevronPainter(color: tint?.color ?? TmsColors.label),
      ),
    );
  }

  static const double _plainChevronWidth = 11;
  static const double _plainChevronHeight = 18;
}

/// 畫 [TmsNavBarButtonStyle.plain] 那顆白鈕裡的箭頭。
///
/// 白玻璃底的對比低，筆畫細了在亮處就看不見，所以這個箭頭的粗細與長寬自成一
/// 組，不跟著彩色標題區那顆走（`Main.dc.html:1759`）。
class _PlainChevronPainter extends CustomPainter {
  const _PlainChevronPainter();

  @override
  void paint(Canvas canvas, Size size) {
    canvas.scale(size.width / 12, size.height / 20);
    canvas.drawPath(
      Path()
        ..moveTo(10, 2)
        ..lineTo(2, 10)
        ..lineTo(10, 18),
      Paint()
        ..color = TmsColors.labelSoft
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.6
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(_PlainChevronPainter oldDelegate) => false;
}

/// 畫導覽列返回鈕裡那個向左的箭頭。
///
/// 沒有拿 [CupertinoIcons] 的現成字形：那一組是填滿的字符，線條粗細跟著字級
/// 走，調不到這裡要的 2.2，在彩色標題區上會比旁邊的標題細一截。
/// `Main.dc.html:1151`
class _NavChevronPainter extends CustomPainter {
  const _NavChevronPainter({required this.color});

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / TmsSize.navBarChevron;
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = TmsSize.navBarChevronStroke * scale
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final path = Path()
      ..moveTo(15 * scale, 5 * scale)
      ..lineTo(8 * scale, 12 * scale)
      ..lineTo(15 * scale, 19 * scale);
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(_NavChevronPainter oldDelegate) =>
      oldDelegate.color != color;
}

/// 把頁面的捲動位置換算成「捲過門檻沒有」，交給 [builder] 去決定導覽列長怎
/// 樣，給每一支用 [TmsScrollNavBar] 的整頁包在最外層。
///
/// 門檻預設是 [TmsScrollNavBar.scrollThreshold]，比較方式與原型相同：超過才
/// 算捲過，剛好等於不算。只收垂直方向、而且是直接巢狀在底下那一層的捲動通
/// 知。同一頁上的照片橫向列也會送出捲動通知，不過濾的話員工左右滑照片時導
/// 覽列會跟著變樣子。
class TmsScrollNavBarScroll extends StatefulWidget {
  const TmsScrollNavBarScroll({
    super.key,
    required this.builder,
    this.threshold = TmsScrollNavBar.scrollThreshold,
  });

  final Widget Function(BuildContext context, bool scrolled) builder;

  final double threshold;

  @override
  State<TmsScrollNavBarScroll> createState() => _TmsScrollNavBarScrollState();
}

class _TmsScrollNavBarScrollState extends State<TmsScrollNavBarScroll> {
  bool _scrolled = false;

  bool _onNotification(ScrollNotification notification) {
    if (notification.depth != 0 || notification.metrics.axis != Axis.vertical) {
      return false;
    }
    final scrolled = notification.metrics.pixels > widget.threshold;
    if (scrolled != _scrolled) {
      setState(() => _scrolled = scrolled);
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    return NotificationListener<ScrollNotification>(
      onNotification: _onNotification,
      child: Builder(builder: (context) => widget.builder(context, _scrolled)),
    );
  }
}
