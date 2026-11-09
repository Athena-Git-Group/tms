import 'dart:math' as math;

import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_card.dart';
import 'tms_motion.dart';
import 'tms_progress_ring.dart';

/// 首頁最上面那張進度卡：左邊一個完成度的環，右邊一欄數字，底下可以再接一條
/// 橫跨整張卡的列。
///
/// 房務與工務兩個首頁共用這一張。兩邊環裡的數字與右欄的內容不同（房務數的是
/// 房間、工務數的是工單），但「環 + 右欄 + 底下一條」這個版型是同一個，各寫
/// 一份的話兩個首頁的環大小與留白一定會慢慢分岔。
class TmsProgressRingCard extends StatelessWidget {
  const TmsProgressRingCard({
    super.key,
    required this.progress,
    required this.ringCenter,
    required this.details,
    this.footer,
  });

  /// 完成度，0 到 1。
  final double progress;

  /// 環正中央放的東西，通常是「已完成／總數」加一行說明。
  final Widget ringCenter;

  /// 環右邊那一欄。
  final Widget details;

  /// 卡片最下面橫跨整張卡的一列；不給就不畫。
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    return TmsCard(
      style: TmsCardStyle.hero,
      padding: const EdgeInsets.all(TmsSpacing.lg2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              TmsProgressRing(progress: progress, child: ringCenter),
              const SizedBox(width: TmsSpacing.lg2),
              Expanded(child: details),
            ],
          ),
          if (footer != null) ...[
            const SizedBox(height: TmsSpacing.lg),
            footer!,
          ],
        ],
      ),
    );
  }
}

/// 進度卡右欄的一格：數字在上、說明在下。
///
/// 說明文字放在呼叫端給的數字節點外面，讀這個數字的一方拿到的才是數字本身，
/// 不含說明。數字本身由呼叫端給，因為它要掛自己的 accessibility id。
class TmsStatBlock extends StatelessWidget {
  const TmsStatBlock({super.key, required this.value, required this.caption});

  /// 這一格的數字。
  final Widget value;

  /// 數字下面那一行說明。
  final String caption;

  /// 說明與數字之間 1pt，比 [TmsSpacing] 最小的一級還窄。原型
  /// `Main.dc.html:147` 與 `150` 的 `gap: 1px`。只有這一處用得上，所以不進
  /// token 層。
  static const double _captionGap = 1;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        value,
        const SizedBox(height: _captionGap),
        Text(
          caption,
          style: TmsText.footnote.copyWith(color: TmsColors.secondaryLabel),
        ),
      ],
    );
  }
}

/// 首頁的主卡：手上那一張（深色、品牌色底）或下一張（白底）。
///
/// 兩種狀態共用同一個版型，只有配色與角落的裝飾不同。裝飾被裁在卡的圓角內，
/// 不會溢出去，作用是讓這張卡在一列白卡之中先被看到。
///
/// 房務與工務兩個首頁共用。深色那張的開頭有一顆橘色圓點，意思是「這一張正在
/// 進行」。
class TmsHeroCard extends StatelessWidget {
  const TmsHeroCard({
    super.key,
    this.onDark = false,
    required this.eyebrow,
    required this.children,
    this.ornamentIcon,
    this.onTap,
  });

  /// 是不是深底（手上正在做的那一張）。
  final bool onDark;

  /// 卡片最上面那一行小字（「進行中」「下一間」）。
  final String eyebrow;

  /// 小字底下的內容，由上到下。每一段之間的間距由這張卡自己補，呼叫端不要
  /// 再夾 [SizedBox]。
  final List<Widget> children;

  /// 右上角那塊旋轉方塊裡的圖示。房務的「下一間」放床、工務的「下一張」放
  /// 扳手；不給值就不畫這塊方塊（深底那張沒有）。
  final IconData? ornamentIcon;

  /// 點整張卡要做的事；不給值時整張卡不可點。
  final VoidCallback? onTap;

  /// 深底那張右上角那顆裝飾圓的底色：白色壓到 8% 的透明度。
  static const Color _darkOrnament = Color(0x14FFFFFF);

  /// 進行中那一行小字前面那顆圓點。
  static const Color _pulseDot = Color(0xFFFFB783);

  /// 右上角那塊方塊的邊長、圓角、離卡右緣與上緣的距離，以及裡面圖示的大小。
  /// 原型 `Main.dc.html:187-189`（房務）與 `916-917`（工務）。
  static const double _squareSize = 64;
  static const double _squareRadius = 20;
  static const double _squareRight = 26;
  static const double _squareTop = 20;
  static const double _squareIconSize = 34;

  /// 方塊的傾斜角。原型寫的是 `rotate(10deg)`，CSS 與 [Transform.rotate] 的
  /// 正角都是順時針，所以只需要換成弧度。
  static const double _squareTilt = 10 * math.pi / 180;

  @override
  Widget build(BuildContext context) {
    final foreground = onDark
        ? TmsColors.brandOnPrimary
        : TmsColors.brandPrimary;
    final card = Container(
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: onDark ? TmsColors.brandPrimary : TmsColors.cardBackground,
        borderRadius: BorderRadius.circular(TmsRadius.cardHero),
      ),
      child: Stack(
        children: [
          Positioned(
            right: onDark ? -30 : -24,
            top: onDark ? -30 : null,
            bottom: onDark ? null : -36,
            child: Container(
              width: onDark ? 140 : 150,
              height: onDark ? 140 : 150,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: onDark ? _darkOrnament : TmsColors.brandContainer,
              ),
            ),
          ),
          if (ornamentIcon != null)
            Positioned(
              right: _squareRight,
              top: _squareTop,
              child: Transform.rotate(
                angle: _squareTilt,
                child: Container(
                  width: _squareSize,
                  height: _squareSize,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: TmsColors.brandPrimary,
                    borderRadius: BorderRadius.circular(_squareRadius),
                  ),
                  child: Icon(
                    ornamentIcon,
                    size: _squareIconSize,
                    color: TmsColors.brandOnPrimary,
                  ),
                ),
              ),
            ),
          Padding(
            padding: const EdgeInsets.all(TmsSpacing.lg2),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    if (onDark) ...[
                      const TmsMotionView(
                        spec: TmsMotion.pulse,
                        child: SizedBox(
                          width: TmsSize.unreadDot,
                          height: TmsSize.unreadDot,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: _pulseDot,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: TmsSpacing.xs2),
                    ],
                    Flexible(
                      child: Text(
                        eyebrow,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TmsText.footnoteEmphasized.copyWith(
                          color: foreground,
                        ),
                      ),
                    ),
                  ],
                ),
                for (final child in children) ...[
                  const SizedBox(height: TmsSpacing.md2),
                  child,
                ],
              ],
            ),
          ),
        ],
      ),
    );
    if (onTap == null) {
      return card;
    }
    return GestureDetector(onTap: onTap, child: card);
  }
}

/// 進行中那張主卡右側的計時欄：計時數字，超時時底下再掛一顆膠囊。
///
/// 房務與工務共用。原型 `Main.dc.html:174-182` 把它寫成一個靠右對齊、間距 4
/// 的直欄，和房號、任務名同一列。
///
/// 超時只是提醒，不擋任何操作，所以深底上用黃字而不是錯誤紅——紅色在青綠底
/// 上會讀成「這張單出事了」。
class TmsHeroTimer extends StatelessWidget {
  const TmsHeroTimer({
    super.key,
    required this.workedMinutes,
    this.workedSeconds,
    this.estMin,
  });

  /// 後端給的已做分鐘。走不了秒時用它顯示。
  final int workedMinutes;

  /// 目前已做秒數。後端的員工工作台還沒有 `segmentStartedAt` 與
  /// `accumulatedSeconds` 這兩欄，首頁取不到它，取得到的呼叫端才傳。
  final int? workedSeconds;

  /// 預估分鐘；沒有預估值就無從判斷超時。
  final int? estMin;

  /// 超時膠囊的內距。原型 `Main.dc.html:180` 的 `padding: 2px 8px`。
  static const EdgeInsets _capsulePadding = EdgeInsets.symmetric(
    horizontal: TmsSpacing.sm,
    vertical: TmsSpacing.xxs,
  );

  /// 超時幾分鐘；沒超時回 null。
  ///
  /// 原型 `Main.dc.html:3153-3156`：秒數超過預估就算超時，分鐘數無條件進位並
  /// 且至少是 1，所以剛跨過預估的那一秒顯示的是「超時 1 分」而不是 0。走不了
  /// 秒時拿已做分鐘代入同一條式子，結果就是分鐘相減。
  int? get _overtimeMinutes {
    final est = estMin;
    if (est == null || est <= 0) {
      return null;
    }
    final seconds = workedSeconds ?? workedMinutes * 60;
    final over = seconds - est * 60;
    if (over <= 0) {
      return null;
    }
    return math.max(1, (over / 60).ceil());
  }

  /// 計時那一串字。
  ///
  /// 走得了秒時寫成「分:秒」，超過一小時才補上小時那一段，與原型
  /// `Main.dc.html:3477` 的 `fmt` 同一套；走不了秒時只講已做分鐘，不把分鐘補
  /// 成「35:00」——那會讓員工以為秒數剛好歸零。
  String get timerText {
    final total = workedSeconds;
    if (total == null) {
      return '$workedMinutes 分';
    }
    final hours = total ~/ 3600;
    final minutes = (total % 3600) ~/ 60;
    final seconds = total % 60;
    final mm = minutes.toString().padLeft(2, '0');
    final ss = seconds.toString().padLeft(2, '0');
    return hours > 0 ? '$hours:$mm:$ss' : '$mm:$ss';
  }

  @override
  Widget build(BuildContext context) {
    final overtime = _overtimeMinutes;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          timerText,
          style: TmsText.displayTimer.copyWith(
            color: overtime == null
                ? TmsColors.brandOnPrimary
                : TmsColors.overtimeTimerOnDark,
          ),
        ),
        if (overtime != null) ...[
          const SizedBox(height: TmsSpacing.xs),
          Container(
            padding: _capsulePadding,
            decoration: BoxDecoration(
              color: TmsColors.overtimeCapsule,
              borderRadius: BorderRadius.circular(TmsRadius.pill),
            ),
            child: Text(
              '超時 $overtime 分',
              style: TmsText.caption1.copyWith(
                fontWeight: FontWeight.w800,
                color: TmsColors.onOvertimeCapsule,
              ),
            ),
          ),
        ],
      ],
    );
  }
}
