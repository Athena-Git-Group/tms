import 'dart:math' as math;

import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_card.dart';
import 'tms_motion.dart';
import 'tms_progress_ring.dart';

class TmsProgressRingCard extends StatelessWidget {
  const TmsProgressRingCard({
    super.key,
    required this.progress,
    required this.ringCenter,
    required this.details,
    this.footer,
  });

  final double progress;

  final Widget ringCenter;

  final Widget details;

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

class TmsStatBlock extends StatelessWidget {
  const TmsStatBlock({super.key, required this.value, required this.caption});

  final Widget value;

  final String caption;

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

class TmsHeroCard extends StatelessWidget {
  const TmsHeroCard({
    super.key,
    this.onDark = false,
    required this.eyebrow,
    required this.children,
    this.ornamentIcon,
    this.onTap,
  });

  final bool onDark;

  final String eyebrow;

  final List<Widget> children;

  final IconData? ornamentIcon;

  final VoidCallback? onTap;

  static const Color _darkOrnament = Color(0x14FFFFFF);

  static const Color _pulseDot = Color(0xFFFFB783);

  static const double _squareSize = 64;
  static const double _squareRadius = 20;
  static const double _squareRight = 26;
  static const double _squareTop = 20;
  static const double _squareIconSize = 34;

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

int? tmsOvertimeMinutes({
  required int? estMin,
  required int workedMinutes,
  int? workedSeconds,
}) {
  if (estMin == null || estMin <= 0) {
    return null;
  }
  final seconds = workedSeconds ?? workedMinutes * 60;
  if (seconds <= estMin * 60) {
    return null;
  }
  return math.max(1, seconds ~/ 60 - estMin);
}

class TmsHeroTimer extends StatelessWidget {
  const TmsHeroTimer({
    super.key,
    required this.workedMinutes,
    this.workedSeconds,
    this.estMin,
  });

  final int workedMinutes;

  final int? workedSeconds;

  final int? estMin;

  static const EdgeInsets _capsulePadding = EdgeInsets.symmetric(
    horizontal: TmsSpacing.sm,
    vertical: TmsSpacing.xxs,
  );

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
    final overtime = tmsOvertimeMinutes(
      estMin: estMin,
      workedMinutes: workedMinutes,
      workedSeconds: workedSeconds,
    );
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
