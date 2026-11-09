import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_glass.dart';

/// Alert 上一顆按鈕的輕重。一個 Alert 最多一顆 [primary] 或 [destructive]，
/// 其餘都是 [normal]：兩顆都畫成重的，員工就分不出哪一顆是會真的動到資料的。
enum TmsAlertActionStyle {
  /// 次要動作（取消、知道了）。半透明灰底、品牌色字、字重 400。
  normal,

  /// 會往下走的那一顆。品牌色實心底、白字、字重 600。只有一顆按鈕的 Alert
  /// 用這一種：那顆按鈕就是員工唯一能做的事，畫成次要的會讓人以為還有別條路。
  primary,

  /// 會造成損失的那一顆（登出、撤回工務單）。半透明灰底、紅字、字重 600。
  destructive,
}

/// [TmsAlert] 上的一顆按鈕。
class TmsAlertAction {
  const TmsAlertAction({
    required this.label,
    required this.onPressed,
    this.style = TmsAlertActionStyle.normal,
    this.id,
  });

  final String label;

  final VoidCallback onPressed;

  final TmsAlertActionStyle style;

  /// 自動化測試用來點到這顆按鈕的 accessibility id。同一句「知道了」會出現在
  /// 好幾個 Alert 上，id 必須分得出是哪一個，所以由呼叫端決定。不填時這顆按鈕
  /// 在 accessibility tree 上沒有自己的識別字，測試只能靠文字找它。
  final String? id;
}

/// iOS 26 版的 Alert 對話框。
///
/// ## 為什麼不用 `CupertinoAlertDialog`
///
/// Flutter 的 `CupertinoAlertDialog` 畫的是 iOS 18 版型：寬 270、圓角 14、
/// 文字置中、按鈕是以細線分隔的純文字列。設計原型走 iOS 26：寬 300、圓角 34、
/// 文字靠左、按鈕是沒有分隔線的膠囊。這兩套版型沒有任何參數可以互相轉換，
/// 底板本身也從不透明換成毛玻璃，所以整個容器自繪。
///
/// ## 節點結構與 accessibility id
///
/// 這個元件刻意不替整個對話框掛 id：有些呼叫端要把 id 掛在對話框本身，有些
/// 要掛在內文那一行（例如驗收要一字不差讀到後端回的拒絕原因時，掛在對話框層
/// 會被 alert 語意多併進一段文字）。對話框層的 id 由呼叫端自己在外面包一層
/// [TmsSemantics]，內文那一行的 id 走 [messageId]，按鈕走
/// [TmsAlertAction.id]。
///
/// 根節點宣告 `explicitChildNodes`，標題、內文與每顆按鈕因此各自是獨立節點，
/// 不會被併成外層節點的一段 label。少了這一條，按鈕就定位不到。
class TmsAlert extends StatelessWidget {
  const TmsAlert({
    super.key,
    this.title,
    required this.message,
    required this.actions,
    this.messageId,
  });

  /// 標題；沒有標題的 Alert 傳 null，版面不會留白。
  final String? title;

  final String message;

  /// 自動化測試用來讀到內文那一段文字的 accessibility id。後端回的拒絕原因要
  /// 一字不差被驗收時填它，定位與讀值才會落在同一個節點上。不填時內文只是
  /// 一般文字，測試得從對話框層的 label 去讀。
  final String? messageId;

  final List<TmsAlertAction> actions;

  /// 對話框寬度。`Main.dc.html:4860` 的 `al.w`。
  static const double width = 300;

  /// 毛玻璃底板的模糊標準差與飽和度。`Main.dc.html:2399`。
  static const double _blur = 30;
  static const double _saturation = 180;

  /// 底色 `rgba(255,255,255,.82)`。`Main.dc.html:4860` 的 `al.bg`。
  static const Color _background = Color(0xD1FFFFFF);

  /// 次要按鈕底色 `rgba(120,120,128,.16)`。`Main.dc.html:4867`。
  static const Color _secondaryActionBackground = Color(0x29787880);

  /// 按鈕高度。`Main.dc.html:4862` 的 `al.btnH`。
  static const double _actionHeight = 48;

  /// 按鈕之間的間距。`Main.dc.html:4862` 的 `al.btnGap`。
  static const double _actionGap = 10;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      scopesRoute: true,
      explicitChildNodes: true,
      child: Center(
        child: SizedBox(
          width: width,
          child: TmsGlass(
            blur: _blur,
            saturation: _saturation,
            color: _background,
            borderRadius: BorderRadius.circular(TmsRadius.alert),
            boxShadow: const [
              // `0 20px 50px rgba(0,0,0,.25)`，`Main.dc.html:4861`。
              BoxShadow(
                color: Color(0x40000000),
                offset: Offset(0, 20),
                blurRadius: 50,
              ),
            ],
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // 後端回的訊息長度不受 App 控制，長到撐破畫面時讓它在對話框
                // 內捲動，而不是把按鈕擠出螢幕。
                Flexible(child: SingleChildScrollView(child: _textBlock())),
                _actionRow(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 標題與內文。`padding: 22px 22px 6px`、`gap: 4`、靠左，見
  /// `Main.dc.html:2400` 與 `:4861`。
  Widget _textBlock() {
    Widget messageText = Text(
      message,
      textAlign: TextAlign.left,
      style: TmsText.subheadline.copyWith(height: 1.4),
    );
    final id = messageId;
    if (id != null) {
      messageText = TmsSemantics(
        id: id,
        label: message,
        child: ExcludeSemantics(child: messageText),
      );
    }
    final titleText = title;
    return Padding(
      padding: const EdgeInsets.fromLTRB(22, 22, 22, 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (titleText != null) ...[
            Text(titleText, textAlign: TextAlign.left, style: TmsText.headline),
            const SizedBox(height: 4),
          ],
          messageText,
        ],
      ),
    );
  }

  /// 按鈕列。`padding: 14px 16px 16px`、無分隔線、每顆等寬，見
  /// `Main.dc.html:2403` 與 `:4862`。
  Widget _actionRow() {
    final buttons = <Widget>[];
    for (final action in actions) {
      if (buttons.isNotEmpty) {
        buttons.add(const SizedBox(width: _actionGap));
      }
      buttons.add(Expanded(child: _actionButton(action)));
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
      child: Row(children: buttons),
    );
  }

  Widget _actionButton(TmsAlertAction action) {
    final Color background;
    final Color foreground;
    final FontWeight weight;
    switch (action.style) {
      case TmsAlertActionStyle.normal:
        background = _secondaryActionBackground;
        foreground = TmsColors.brandPrimary;
        weight = FontWeight.w400;
      case TmsAlertActionStyle.primary:
        background = TmsColors.brandPrimary;
        foreground = TmsColors.brandOnPrimary;
        weight = FontWeight.w600;
      case TmsAlertActionStyle.destructive:
        background = _secondaryActionBackground;
        foreground = TmsColors.systemRed;
        weight = FontWeight.w600;
    }
    final Widget button = CupertinoButton(
      padding: EdgeInsets.zero,
      borderRadius: BorderRadius.circular(TmsRadius.pill),
      color: background,
      minimumSize: const Size(double.infinity, _actionHeight),
      onPressed: action.onPressed,
      child: Text(
        action.label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TmsText.body.copyWith(color: foreground, fontWeight: weight),
      ),
    );
    final id = action.id;
    if (id == null) {
      return button;
    }
    return TmsSemantics(id: id, child: button);
  }
}
