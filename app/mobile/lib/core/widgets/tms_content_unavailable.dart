import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_card.dart';

@immutable
class TmsEmptyCopy {
  const TmsEmptyCopy({
    required this.icon,
    required this.title,
    required this.description,
    this.isPositive = false,
    this.actionLabel,
  });

  final IconData icon;

  final String title;

  final String description;

  final bool isPositive;

  final String? actionLabel;
}

class TmsContentUnavailable extends StatelessWidget {
  const TmsContentUnavailable({
    super.key,
    required this.id,
    required this.copy,
    this.onAction,
    this.minHeight = poolMinHeight,
  }) : _inCard = false;

  const TmsContentUnavailable.inCard({
    super.key,
    required this.id,
    required this.copy,
    this.onAction,
  }) : _inCard = true,
       minHeight = 0;

  final String id;

  final TmsEmptyCopy copy;

  final VoidCallback? onAction;

  final double minHeight;

  final bool _inCard;

  static const double poolMinHeight = 380;

  static const double notificationMinHeight = 460;

  static const double lostAndFoundMinHeight = 520;

  static const double _horizontalPadding = 32;

  static const double _bottomPadding = 40;

  @override
  Widget build(BuildContext context) {
    final actionLabel = copy.actionLabel;
    final body = Column(
      mainAxisSize: _inCard ? MainAxisSize.min : MainAxisSize.max,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        TmsSemantics(
          id: id,
          label: '${copy.title}。${copy.description}',
          child: ExcludeSemantics(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  copy.icon,
                  size: TmsSize.emptyStateIcon,
                  color: copy.isPositive
                      ? TmsColors.systemGreen
                      : TmsColors.quaternaryLabel,
                ),
                SizedBox(height: _inCard ? TmsSpacing.md : TmsSpacing.md2),
                Text(
                  copy.title,
                  textAlign: TextAlign.center,
                  style: TmsText.title2,
                ),
                const SizedBox(height: TmsSpacing.xs2),
                Text(
                  copy.description,
                  textAlign: TextAlign.center,
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.secondaryLabel,
                  ),
                ),
              ],
            ),
          ),
        ),
        if (actionLabel != null && onAction != null) ...[
          const SizedBox(height: TmsSpacing.xs2),
          TmsSemantics(
            id: '$id.action',
            label: actionLabel,
            child: CupertinoButton(
              padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.md),
              minimumSize: const Size(0, TmsSize.minTouchTarget),
              onPressed: onAction,
              child: Text(
                actionLabel,
                style: TmsText.body.copyWith(color: TmsColors.brandPrimary),
              ),
            ),
          ),
        ],
      ],
    );

    if (_inCard) {
      return TmsCard(
        style: TmsCardStyle.hero,
        padding: const EdgeInsets.symmetric(
          vertical: TmsSpacing.xxl,
          horizontal: TmsSpacing.xl,
        ),
        child: body,
      );
    }
    return Container(
      constraints: BoxConstraints(minHeight: minHeight),
      padding: const EdgeInsets.fromLTRB(
        _horizontalPadding,
        TmsSpacing.xl2,
        _horizontalPadding,
        _bottomPadding,
      ),
      child: body,
    );
  }
}

abstract final class TmsEmptyStates {
  static const IconData _trayIcon = CupertinoIcons.tray;

  static const IconData _doneIcon = CupertinoIcons.check_mark_circled;

  static const String showAllCategoriesLabel = '顯示全部分類';

  static const TmsEmptyCopy notifications = TmsEmptyCopy(
    icon: CupertinoIcons.bell,
    title: '目前沒有通知',
    description: '派單、急件與退回都會出現在這裡。',
  );

  static const TmsEmptyCopy lostAndFound = TmsEmptyCopy(
    icon: CupertinoIcons.cube_box,
    title: '目前沒有拾獲的物品',
    description: '拾獲物品時，點右上角「＋」登記。',
  );

  static TmsEmptyCopy housekeepingPool({
    required bool filterApplied,
    required bool poolHasItems,
  }) {
    if (filterApplied && poolHasItems) {
      return const TmsEmptyCopy(
        icon: _trayIcon,
        title: '這個分類沒有待認領的單',
        description: '換個分類看看，或等新需求進來。',
        actionLabel: showAllCategoriesLabel,
      );
    }
    return const TmsEmptyCopy(
      icon: _trayIcon,
      title: '目前沒有待認領的單',
      description: '新需求進來時會推播通知你。',
    );
  }

  static TmsEmptyCopy engineeringPool({
    required bool filterApplied,
    required bool poolHasItems,
  }) {
    if (filterApplied && poolHasItems) {
      return const TmsEmptyCopy(
        icon: _trayIcon,
        title: '這個分類沒有待認領的單',
        description: '換個分類看看，或等新的工務單進來。',
        actionLabel: showAllCategoriesLabel,
      );
    }
    return const TmsEmptyCopy(
      icon: _trayIcon,
      title: '目前沒有待認領的單',
      description: '新的工務單進來會推播通知你。',
    );
  }

  static TmsEmptyCopy housekeepingHome({
    required int snoozedCount,
    required int doneCount,
    required bool poolHasItems,
  }) {
    if (snoozedCount > 0) {
      return const TmsEmptyCopy(
        icon: _trayIcon,
        title: '目前沒有可以開始的單',
        description: '延後的單在下方「我的任務」，準備好再開始。',
      );
    }
    final tail = poolHasItems ? '到「待認領」認領新的單。' : '新需求進來時會推播通知你。';
    if (doneCount > 0) {
      return TmsEmptyCopy(
        icon: _doneIcon,
        title: '今天的任務都完成了',
        description: '今天完成 $doneCount 單，$tail',
        isPositive: true,
      );
    }
    return TmsEmptyCopy(icon: _trayIcon, title: '手上沒有任務', description: tail);
  }

  static TmsEmptyCopy engineeringHome({
    required int doneCount,
    required bool poolHasItems,
  }) {
    final tail = poolHasItems ? '到「待認領」認領新的單。' : '新的工務單進來會推播通知你。';
    if (doneCount > 0) {
      return TmsEmptyCopy(
        icon: _doneIcon,
        title: '今天的工務單都完修了',
        description: '今天完修 $doneCount 張，$tail',
        isPositive: true,
      );
    }
    return TmsEmptyCopy(icon: _trayIcon, title: '手上沒有工務單', description: tail);
  }
}
