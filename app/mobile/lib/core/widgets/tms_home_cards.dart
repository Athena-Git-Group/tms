import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_card.dart';
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
              const SizedBox(width: TmsSpacing.lg),
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

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        value,
        const SizedBox(height: TmsSpacing.xxs),
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
/// 兩種狀態共用同一個版型，只有配色、角落那顆裝飾圓的位置與內容不同。角落的
/// 圓是整張卡唯一的裝飾，作用是讓這張卡在一列白卡之中先被看到——它被裁在卡
/// 的圓角內，不會溢出去。
///
/// 房務與工務兩個首頁共用。深色那張的開頭有一顆橘色圓點，意思是「這一張正在
/// 進行」。
class TmsHeroCard extends StatelessWidget {
  const TmsHeroCard({
    super.key,
    this.onDark = false,
    required this.eyebrow,
    required this.children,
    this.onTap,
  });

  /// 是不是深底（手上正在做的那一張）。
  final bool onDark;

  /// 卡片最上面那一行小字（「進行中」「下一間」）。
  final String eyebrow;

  /// 小字底下的內容，由上到下。
  final List<Widget> children;

  /// 點整張卡要做的事；不給值時整張卡不可點。
  final VoidCallback? onTap;

  /// 深底那張右上角那顆裝飾圓的底色：白色壓到 8% 的透明度。
  static const Color _darkOrnament = Color(0x14FFFFFF);

  /// 進行中那一行小字前面那顆圓點。
  static const Color _pulseDot = Color(0xFFFFB783);

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
          Padding(
            padding: const EdgeInsets.all(TmsSpacing.lg2),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    if (onDark) ...[
                      Container(
                        width: TmsSize.unreadDot,
                        height: TmsSize.unreadDot,
                        decoration: const BoxDecoration(
                          color: _pulseDot,
                          shape: BoxShape.circle,
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
                ...children,
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
