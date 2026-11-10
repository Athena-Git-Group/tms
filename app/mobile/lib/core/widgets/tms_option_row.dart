import 'package:flutter/widgets.dart';

import '../theme.dart';

/// 面板裡「列出幾個選項讓員工挑」的一列。
///
/// 設計原型有四種這樣的清單。每一種的列高、上下內距、副標字級都是一組綁在一
/// 起的值，不是可以任意組合的參數。所以這裡開四個具名建構子，一個對一種清單，
/// 而不是開一組參數讓呼叫端自己配。44 高的列掛一行副標、建議問題列尾端跑出一
/// 個勾，這些組合在原型裡不存在，也就不該在這個元件的介面裡存在。
///
/// 列高差三種是刻意的。原因列 52 是中斷流程時單手點的，類別列 44 是接在原因
/// 之後的第二層選擇、照 iOS 一般列高，建議問題列 56 要放兩行字。
///
/// [title] 與副標收 widget 不收字串。有些列的文字本身要掛 accessibility id，
/// 例如建議問題與它的範圍各掛一個，呼叫端得有地方把那層包進來。字級與顏色由
/// 這一列統一指定，傳進來的 `Text` 不必自己帶 style。
class TmsOptionRow extends StatelessWidget {
  /// 延後／中斷原因列（`Main.dc.html:1450`）。
  ///
  /// 點一下就直接送出，所以列尾什麼都不畫，也沒有選中狀態可畫——員工按下去
  /// 的下一刻面板就收起來了，中間沒有一個「選好了但還沒送」的時刻。
  const TmsOptionRow.reason({
    super.key,
    required this.title,
    required this.onTap,
  }) : subtitle = null,
       selected = false,
       _minHeight = 52,
       _verticalPadding = 0,
       _subtitleStyle = null,
       _subtitleGap = 0,
       _checkGap = TmsSpacing.sm;

  /// 報修問題類別列（`Main.dc.html:1461`）。
  const TmsOptionRow.category({
    super.key,
    required this.title,
    required this.selected,
    required this.onTap,
  }) : subtitle = null,
       _minHeight = 44,
       _verticalPadding = 0,
       _subtitleStyle = null,
       _subtitleGap = 0,
       _checkGap = TmsSpacing.sm;

  /// 「查一下」建議問題列（`Main.dc.html:1601`）。
  ///
  /// 點了會往下走一步，但列尾不畫 chevron，原型這裡刻意只有文字。一排問題各
  /// 自帶一個箭頭，箭頭就變成背景雜訊，讀的人反而要先過濾掉它才看得到問題。
  const TmsOptionRow.suggestion({
    super.key,
    required this.title,
    required Widget scope,
    required this.onTap,
  }) : subtitle = scope,
       selected = false,
       _minHeight = 56,
       _verticalPadding = TmsSpacing.sm2,
       _subtitleStyle = TmsText.caption1Regular,
       _subtitleGap = TmsSpacing.xxs,
       _checkGap = 0;

  /// 工務完修回報的處理結果列（`Main.dc.html:2179`）。
  const TmsOptionRow.result({
    super.key,
    required this.title,
    required Widget detail,
    required this.selected,
    required this.onTap,
  }) : subtitle = detail,
       _minHeight = 52,
       _verticalPadding = TmsSpacing.sm,
       _subtitleStyle = TmsText.footnote,
       _subtitleGap = 1,
       _checkGap = TmsSpacing.sm2;

  final Widget title;

  /// 選項底下那一行補充說明。只有建議問題列與處理結果列有。
  final Widget? subtitle;

  /// 選中時列尾畫一個品牌色的勾。選中與否只靠這個勾表示，不動字重。只有字重
  /// 差別的話，員工在走廊的光線下看不出自己選了哪一列。
  final bool selected;

  final VoidCallback? onTap;

  final double _minHeight;
  final double _verticalPadding;
  final TextStyle? _subtitleStyle;
  final double _subtitleGap;
  final double _checkGap;

  @override
  Widget build(BuildContext context) {
    final sub = subtitle;
    final subStyle = _subtitleStyle;
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        constraints: BoxConstraints(minHeight: _minHeight),
        padding: EdgeInsets.symmetric(
          horizontal: TmsSpacing.screenEdge,
          vertical: _verticalPadding,
        ),
        color: TmsColors.cardBackground,
        child: Row(
          children: [
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DefaultTextStyle(style: TmsText.body, child: title),
                  if (sub != null && subStyle != null) ...[
                    SizedBox(height: _subtitleGap),
                    DefaultTextStyle(style: subStyle, child: sub),
                  ],
                ],
              ),
            ),
            if (selected) ...[
              SizedBox(width: _checkGap),
              const TmsOptionCheck(),
            ],
          ],
        ),
      ),
    );
  }
}

/// 選項列尾端那個勾（`Main.dc.html:1463`）。
///
/// 自己畫而不用 `CupertinoIcons.check_mark`。原型的勾是 16 × 16 的描邊圖形，
/// 筆畫粗細與兩端的圓頭都寫死在 SVG 裡。字形的筆畫粗細由字型決定，調不到。
class TmsOptionCheck extends StatelessWidget {
  const TmsOptionCheck({super.key});

  @override
  Widget build(BuildContext context) {
    return const CustomPaint(
      size: Size(TmsSize.optionCheck, TmsSize.optionCheck),
      painter: _CheckPainter(),
    );
  }
}

class _CheckPainter extends CustomPainter {
  const _CheckPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / 24;
    final path = Path()
      ..moveTo(5 * scale, 12.5 * scale)
      ..lineTo(9.5 * scale, 17 * scale)
      ..lineTo(19 * scale, 7.5 * scale);
    canvas.drawPath(
      path,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = TmsSize.optionCheckStroke
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..color = TmsColors.brandPrimary,
    );
  }

  @override
  bool shouldRepaint(_CheckPainter oldDelegate) => false;
}
