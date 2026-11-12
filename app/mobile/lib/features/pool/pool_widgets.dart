import 'package:flutter/cupertino.dart';

import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_category_tile.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_tag.dart';

const Map<String, String> housekeepingPoolSourceLabels = {
  'guest_scan': '住客掃碼',
  'desk_forward': '櫃檯轉單',
  'manager_requeue': '主管退回',
  'leave_return': '請假退回',
  'shift_end_return': '班末退池',
  'inspection_return': '查房退回',
  'staff_report': '房務報修',
  'system': '自動化規則',
};

const Map<String, String> engineeringPoolSourceLabels = {
  'staff_report': '房務報修',
  'desk_forward': '櫃檯',
};

const int poolWaitedMinutesAlarmThreshold = 20;

Widget? poolWaitedTag({int? waitedMinutes, String? reminderStage}) {
  if (waitedMinutes == null || reminderStage != null) {
    return null;
  }
  final label = waitedMinutes < 1 ? '剛進來' : '等 $waitedMinutes 分';
  return TmsTag(
    label: label,
    tone: waitedMinutes >= poolWaitedMinutesAlarmThreshold
        ? TmsTagTone.danger
        : TmsTagTone.neutral,
  );
}

Widget? poolSameFloorTag({
  required String id,
  required bool sameFloor,
  required bool priorityNotify,
}) {
  if (!sameFloor) {
    return null;
  }
  return PoolCardField(
    id: id,
    text: priorityNotify ? '優先通知 · 同樓層' : '同樓層 · 離你最近',
    tone: priorityNotify ? TmsTagTone.brand : TmsTagTone.info,
  );
}

class PoolCard extends StatelessWidget {
  const PoolCard({
    super.key,
    required this.id,
    required this.children,
    this.semanticsLabel,
    this.highlighted = false,
    this.onTap,
  });

  final String id;
  final List<Widget> children;
  final String? semanticsLabel;
  final bool highlighted;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final spaced = <Widget>[];
    for (final child in children) {
      if (spaced.isNotEmpty) {
        spaced.add(const SizedBox(height: TmsSpacing.md));
      }
      spaced.add(child);
    }
    return TmsSemantics(
      id: id,
      label: semanticsLabel,
      child: TmsCard(
        style: TmsCardStyle.hero,
        margin: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.screenEdge,
          vertical: TmsSpacing.sm,
        ),
        highlighted: highlighted,
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: spaced,
        ),
      ),
    );
  }
}

/// 容器型 semantics 節點本身沒有 label，Appium 以 id 定位後讀到的會是空字串。
/// 文字因此同時掛在這個節點的 accessibility label 上。
class PoolCardField extends StatelessWidget {
  const PoolCardField({
    super.key,
    required this.id,
    required this.text,
    this.tone = TmsTagTone.neutralStrong,
  });

  final String id;
  final String text;
  final TmsTagTone tone;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      label: text,
      child: TmsTag(label: text, tone: tone),
    );
  }
}

class PoolCardSubtitle extends StatelessWidget {
  const PoolCardSubtitle({super.key, required this.id, required this.text});

  final String id;
  final String text;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      label: text,
      child: Text(
        text,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TmsText.subheadline.copyWith(color: TmsColors.secondaryLabel),
      ),
    );
  }
}

class PoolCardNote extends StatelessWidget {
  const PoolCardNote({super.key, required this.id, required this.text});

  final String id;
  final String text;

  static const String prefix = '做到哪：';

  @override
  Widget build(BuildContext context) {
    final full = '$prefix$text';
    return TmsSemantics(
      id: id,
      label: full,
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.md,
          vertical: TmsSpacing.sm2,
        ),
        decoration: BoxDecoration(
          color: TmsColors.groupedBackground,
          borderRadius: BorderRadius.circular(TmsRadius.md),
        ),
        child: Text(
          full,
          style: TmsText.subheadline.copyWith(color: TmsColors.labelSoft),
        ),
      ),
    );
  }
}

class PoolTagRow extends StatelessWidget {
  const PoolTagRow({super.key, required this.tags});

  final List<Widget> tags;

  @override
  Widget build(BuildContext context) {
    return Wrap(spacing: TmsTag.gap, runSpacing: TmsTag.gap, children: tags);
  }
}

class PoolClaimButton extends StatelessWidget {
  const PoolClaimButton({
    super.key,
    required this.id,
    required this.onPressed,
    this.glow = TmsPillGlow.standard,
  });

  final String id;
  final VoidCallback onPressed;

  final TmsPillGlow? glow;

  static const double _height = 46;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      child: TmsPillButton(
        label: '認領這單',
        height: _height,
        glow: glow,
        onPressed: onPressed,
      ),
    );
  }
}

class PoolFilterChip extends StatelessWidget {
  const PoolFilterChip({
    super.key,
    required this.id,
    required this.label,
    required this.iconBackground,
    required this.iconForeground,
    required this.selected,
    required this.onTap,
    this.icon,
    this.iconChild,
    this.countId,
    this.count,
    this.unselectedBackground,
  });

  final String id;
  final String label;

  final IconData? icon;
  final Widget? iconChild;

  final Color iconBackground;
  final Color iconForeground;
  final bool selected;
  final VoidCallback onTap;
  final String? countId;
  final int? count;
  final Color? unselectedBackground;

  @override
  Widget build(BuildContext context) {
    final countValue = count;
    Widget? badge;
    if (countValue != null && countValue > 0) {
      badge = TmsCountBadge(count: countValue);
      final id = countId;
      if (id != null) {
        badge = TmsSemantics(id: id, label: '$countValue', child: badge);
      }
    }
    return TmsSemantics(
      id: id,
      child: TmsCategoryTile(
        label: label,
        icon: icon,
        iconChild: iconChild,
        iconBackground: iconBackground,
        iconForeground: iconForeground,
        selected: selected,
        onTap: onTap,
        badge: badge,
        unselectedBackground: unselectedBackground,
      ),
    );
  }
}

class PoolFilterRow extends StatelessWidget {
  const PoolFilterRow({super.key, required this.tiles});

  final List<Widget> tiles;

  @override
  Widget build(BuildContext context) {
    final children = <Widget>[];
    for (final tile in tiles) {
      if (children.isNotEmpty) {
        children.add(const SizedBox(width: TmsCategoryTile.gap));
      }
      children.add(Expanded(child: tile));
    }
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: children,
      ),
    );
  }
}

class PoolScrollView extends StatelessWidget {
  const PoolScrollView({
    super.key,
    required this.children,
    this.aboveCategories,
  });

  final List<Widget> children;

  final Widget? aboveCategories;

  @override
  Widget build(BuildContext context) {
    final aboveCategories = this.aboveCategories;
    return SafeArea(
      bottom: false,
      child: ListView(
        padding: EdgeInsets.only(
          top: TmsSpacing.md,
          bottom: TmsSpacing.scrollPadBottom(
            MediaQuery.paddingOf(context).bottom,
          ),
        ),
        children: [
          const SystemStatusBar(
            padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
            child: Text('待認領', style: TmsText.largeTitle),
          ),
          if (aboveCategories != null) ...[
            const SizedBox(height: TmsSpacing.md),
            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: TmsSpacing.screenEdge,
              ),
              child: aboveCategories,
            ),
          ],
          const SizedBox(height: TmsSpacing.lg),
          ...children,
        ],
      ),
    );
  }
}

class PoolLoadError extends StatelessWidget {
  const PoolLoadError({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(
        left: TmsSpacing.screenEdge,
        right: TmsSpacing.screenEdge,
        top: TmsSpacing.lg,
      ),
      child: Text(message),
    );
  }
}

class PoolLoading extends StatelessWidget {
  const PoolLoading({super.key});

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.only(top: TmsSpacing.lg),
      child: Center(child: CupertinoActivityIndicator()),
    );
  }
}
