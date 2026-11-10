import 'dart:math' as math;

import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_motion.dart';

/// 首次使用教學氣泡。
///
/// 一張白色小卡，右下角有一個指向按鈕的箭頭尖，右上角有一顆關掉它的 ×。它只
/// 告訴員工「這顆按鈕是做什麼的」，看過一次就不再出現。
///
/// ## 出現幾次由畫面決定，不由這個元件決定
///
/// 這個元件沒有記憶：給它就畫，不給就不畫。「只出現一次」是畫面自己的狀態，
/// 因為結束的條件不只一種，而且條件在各畫面不一樣：按 ×、按下它介紹的那顆
/// 按鈕、或是離開這個畫面，三者任一發生之後就不再顯示。元件記不到後兩種，
/// 做成元件自己管反而會出現「元件以為關了、畫面以為還開著」的兩份狀態。
///
/// 同一時間只讓一顆氣泡出現。畫面上已經有別的提示（頂部通知、狀態卡）時，
/// 這一顆延到下一次再說，兩張卡同時蓋住內容會讓員工不知道該先看哪一張。
///
/// ## 定位由呼叫端負責
///
/// 這個元件只畫自己。擺放的畫面用 [Stack] 配 [Positioned] 把它放在它介紹的
/// 那顆按鈕正上方，右邊距與按鈕同樣是 16，下緣是按鈕下緣再加按鈕高度與
/// [gapAboveAnchor]。箭頭尖從卡片下緣凸出去，所以外面那層 [Stack] 要
/// `clipBehavior: Clip.none`，預設的裁切會把箭頭削掉。
class TmsTipPopover extends StatelessWidget {
  const TmsTipPopover({
    super.key,
    required this.title,
    required this.message,
    required this.width,
    required this.onDismiss,
  });

  final String title;

  final String message;

  /// 卡片寬度。原型兩處的文案長短不同，寬度也不同（開工單 260、查一下 264），
  /// 所以由呼叫端照原型填。
  final double width;

  /// 按下 × 要做的事。畫面在這裡記下「看過了」。
  final VoidCallback onDismiss;

  /// 氣泡下緣與它介紹的那顆按鈕上緣之間的距離。
  /// `Main.dc.html:4052` — tipBottom = 按鈕下緣 + 56 + 12
  static const double gapAboveAnchor = 12;

  /// 氣泡下緣位置：介紹的按鈕下緣，加上按鈕高度與 [gapAboveAnchor]。
  static double bottomAbove(double anchorBottom, double anchorHeight) {
    return anchorBottom + anchorHeight + gapAboveAnchor;
  }

  @override
  Widget build(BuildContext context) {
    return TmsMotionView(
      spec: TmsMotion.drop,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          _card(),
          // 指向按鈕的箭頭尖：一個轉 45 度的小方塊，一半埋在卡片裡、一半露在
          // 卡片下緣外面。畫在卡片之後，才不會被卡片自己的陰影壓暗。
          const Positioned(
            right: _arrowInsetRight,
            bottom: -_arrowOverhang,
            child: _TipArrow(),
          ),
        ],
      ),
    );
  }

  Widget _card() {
    return Container(
      width: width,
      decoration: BoxDecoration(
        color: TmsColors.cardBackground,
        borderRadius: BorderRadius.circular(TmsRadius.card),
        boxShadow: const <BoxShadow>[
          BoxShadow(
            color: TmsColors.tipPopoverShadow,
            offset: Offset(0, 10),
            blurRadius: 30,
          ),
        ],
      ),
      // 卡片的內距看起來不對稱，是因為右上那顆 × 的 44 見方觸控範圍要往外吃進
      // 上方與右方的內距，× 本身才落在卡片的角上。原型用負 margin 做到同一件
      // 事。文字那一欄自己把被吃掉的上下內距補回來，所以文字離卡片上緣仍然是
      // 14、離下緣仍然是 12。
      padding: const EdgeInsets.fromLTRB(14, 2, 2, 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: 12, bottom: 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: TmsText.headline),
                  const SizedBox(height: _titleGap),
                  Text(
                    message,
                    style: TmsText.subheadline.copyWith(
                      height: 1.4,
                      color: TmsColors.secondaryLabel,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: _textToCloseGap),
          _CloseButton(onPressed: onDismiss),
        ],
      ),
    );
  }
}

const double _titleGap = 2;

const double _textToCloseGap = 4;

const double _arrowInsetRight = 22;

const double _arrowOverhang = 6;

const double _arrowSize = 14;

class _TipArrow extends StatelessWidget {
  const _TipArrow();

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: _arrowSize,
      height: _arrowSize,
      child: Transform.rotate(
        angle: math.pi / 4,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: TmsColors.cardBackground,
            borderRadius: BorderRadius.circular(2),
          ),
        ),
      ),
    );
  }
}

class _CloseButton extends StatelessWidget {
  const _CloseButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: '關閉提示',
      button: true,
      child: CupertinoButton(
        padding: EdgeInsets.zero,
        minimumSize: const Size(TmsSize.minTouchTarget, TmsSize.minTouchTarget),
        onPressed: onPressed,
        child: const CustomPaint(
          size: Size(_closeGlyphSize, _closeGlyphSize),
          painter: TmsTipCloseGlyphPainter(),
        ),
      ),
    );
  }
}

const double _closeGlyphSize = 13;

/// 教學氣泡右上角那個 ×。
///
/// 自己畫而不取 [CupertinoIcons] 的字形：這個 × 的筆畫比字形粗一截
/// （13 見方裡 1.625 的筆畫），取字形就只能接受字型給的粗細。
///
/// 公開是為了讓元件測試能單獨比對線稿，畫面不要直接用它，走 [TmsTipPopover]。
class TmsTipCloseGlyphPainter extends CustomPainter {
  const TmsTipCloseGlyphPainter();

  static const Color strokeColor = TmsColors.quaternaryLabel;

  /// 原型 SVG 的 viewBox 邊長。
  static const double _viewBox = 24;

  static const double strokeWidth = 3;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / _viewBox;
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..color = strokeColor
      ..strokeCap = StrokeCap.round
      ..strokeWidth = strokeWidth * scale;

    canvas.save();
    canvas.scale(scale);
    canvas.drawPath(
      Path()
        ..moveTo(5, 5)
        ..lineTo(19, 19)
        ..moveTo(19, 5)
        ..lineTo(5, 19),
      paint,
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(TmsTipCloseGlyphPainter oldDelegate) => false;
}
