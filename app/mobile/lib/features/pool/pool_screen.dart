import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/today_refresh.dart';
import '../../core/widgets/tms_category_square.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_tag.dart';
import '../shell/main_shell.dart';
import '../task/task_screen.dart';
import 'pool_widgets.dart';

class PoolScreen extends StatefulWidget {
  const PoolScreen({super.key, this.highlightRoomNo});

  final String? highlightRoomNo;

  @override
  State<PoolScreen> createState() => _PoolScreenState();
}

class _PoolScreenState extends State<PoolScreen> {
  String? get _deptQuery => ApiClient.instance.session?.dept == null
      ? HousekeepingPoolList.deptKey
      : null;

  String? _highlightRoomNo;

  @override
  void initState() {
    super.initState();
    _highlightRoomNo =
        widget.highlightRoomNo ?? MainShellTabRequest.poolHighlightRoomNo.value;
    MainShellTabRequest.poolHighlightRoomNo.addListener(_applyHighlightRequest);
  }

  @override
  void dispose() {
    MainShellTabRequest.poolHighlightRoomNo.removeListener(
      _applyHighlightRequest,
    );
    super.dispose();
  }

  void _applyHighlightRequest() {
    setState(() {
      _highlightRoomNo = MainShellTabRequest.poolHighlightRoomNo.value;
    });
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'pool.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        child: HousekeepingPoolList(
          dept: _deptQuery,
          canClaim: true,
          highlightRoomNo: _highlightRoomNo,
        ),
      ),
    );
  }
}

class HousekeepingPoolList extends StatefulWidget {
  const HousekeepingPoolList({
    super.key,
    required this.dept,
    required this.canClaim,
    this.highlightRoomNo,
    this.aboveCategories,
  });

  static const String deptKey = 'housekeeping';

  final String? dept;

  final bool canClaim;

  final String? highlightRoomNo;

  final Widget? aboveCategories;

  @override
  State<HousekeepingPoolList> createState() => _HousekeepingPoolListState();
}

class _HousekeepingPoolListState extends State<HousekeepingPoolList> {
  static const _categories = [
    (
      key: 'all',
      label: '全部',
      icon: CupertinoIcons.square_grid_2x2_fill,
      iconBackground: TmsColors.brandPrimary,
      iconForeground: TmsColors.brandOnPrimary,
    ),
    (
      key: 'amenity',
      label: '備品',
      icon: CupertinoIcons.cube,
      iconBackground: TmsColors.brandAccentVivid,
      iconForeground: TmsColors.brandOnPrimary,
    ),
    (
      key: 'cleaning',
      label: '打掃',
      icon: CupertinoIcons.sparkles,
      iconBackground: TmsColors.brandContainer,
      iconForeground: TmsColors.brandPrimary,
    ),
    (
      key: 'other',
      label: '其他',
      icon: CupertinoIcons.bubble_left,
      iconBackground: TmsColors.otherContainer,
      iconForeground: TmsColors.onOtherContainer,
    ),
  ];

  String _selected = 'all';
  DeptPool? _pool;
  String? _error;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  @override
  void didUpdateWidget(HousekeepingPoolList oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.highlightRoomNo != widget.highlightRoomNo ||
        oldWidget.dept != widget.dept) {
      _fetch();
    }
  }

  Future<void> _fetch() async {
    setState(() {
      _pool = null;
      _error = null;
    });
    try {
      final pool = await ApiClient.instance.getPool(
        dept: widget.dept,
        category: _selected == 'all' ? null : _selected,
      );
      if (mounted) {
        setState(() => _pool = pool);
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error = '載入待認領失敗，請稍後再試。');
      }
    }
  }

  Future<void> _claim(PoolItem item) async {
    if (ConnectionStatus.instance.isOffline) {
      await showOfflineBlockedAlert(context);
      return;
    }
    await ApiClient.instance.claimTask(item.taskId);
    TodayRefresh.instance.request();
    await _fetch();
  }

  void _openTask(PoolItem item) {
    Navigator.of(context).push(
      CupertinoPageRoute<void>(
        builder: (_) => TaskScreen(roomNo: item.roomNo, taskId: item.taskId),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pool = _pool;
    return PoolScrollView(
      aboveCategories: widget.aboveCategories,
      children: [
        _categoryRow(pool?.counts ?? const <String, int>{}),
        if (_error != null) PoolLoadError(message: _error!),
        if (pool == null && _error == null) const PoolLoading(),
        if (pool != null && pool.activeStaffCount == 0) ...[
          const SizedBox(height: TmsSpacing.lg),
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: TmsSpacing.screenEdge,
            ),
            child: TmsSemantics(
              id: 'pool.empty-state--no-active-staff',
              label: _noActiveStaffMessage,
              child: const Text(_noActiveStaffMessage),
            ),
          ),
        ] else if (pool != null && pool.items.isEmpty) ...[
          const SizedBox(height: TmsSpacing.lg),
          TmsContentUnavailable(
            id: 'pool.empty-state',
            copy: TmsEmptyStates.housekeepingPool(
              filterApplied: _selected != 'all',
              poolHasItems: pool.counts.values.any((count) => count > 0),
            ),
            onAction: () {
              _selected = 'all';
              _fetch();
            },
          ),
        ] else if (pool != null) ...[
          const SizedBox(height: TmsSpacing.sm),
          for (final item in pool.items)
            _HousekeepingPoolCard(
              item: item,
              isHighlighted: item.roomNo == widget.highlightRoomNo,
              onTap: () => _openTask(item),
              onClaim: widget.canClaim ? () => _claim(item) : null,
            ),
        ],
      ],
    );
  }

  static const String _noActiveStaffMessage = '這個部門目前沒有在職員工可以搶單';

  Widget _categoryRow(Map<String, int> counts) {
    return PoolFilterRow(
      tiles: [
        for (final category in _categories)
          PoolFilterChip(
            id: 'pool.category.${category.key}.chip',
            label: category.label,
            icon: category.icon,
            iconBackground: category.iconBackground,
            iconForeground: category.iconForeground,
            selected: _selected == category.key,
            countId: 'pool.category.${category.key}.count',
            count: counts[category.key] ?? 0,
            onTap: () {
              _selected = category.key;
              _fetch();
            },
          ),
      ],
    );
  }
}

class _HousekeepingPoolCard extends StatelessWidget {
  const _HousekeepingPoolCard({
    required this.item,
    required this.isHighlighted,
    required this.onTap,
    required this.onClaim,
  });

  static const Map<String, ({String idSuffix, String text, TmsTagTone tone})>
  _reminderTags = {
    'staff_reminded': (
      idSuffix: 'unaccepted-notified-peer',
      text: '未接 · 已提醒同仁',
      tone: TmsTagTone.warn,
    ),
    'manager_notified': (
      idSuffix: 'unaccepted-notified-manager',
      text: '未接 · 已通知主管',
      tone: TmsTagTone.danger,
    ),
  };

  final PoolItem item;
  final bool isHighlighted;
  final VoidCallback onTap;

  final VoidCallback? onClaim;

  @override
  Widget build(BuildContext context) {
    final cardId = 'pool.card.${item.roomNo}';
    final sourceLabel = housekeepingPoolSourceLabels[item.source];
    final reasonFieldId = item.source == 'system'
        ? '$cardId.rule-hit-tag'
        : '$cardId.return-reason';
    final reminderTag = _reminderTags[item.reminderStage];
    final sameFloorTag = poolSameFloorTag(
      id: '$cardId.same-floor-tag',
      sameFloor: item.sameFloor,
      priorityNotify: item.priorityNotify,
    );
    final waitedTag = poolWaitedTag(
      waitedMinutes: item.waitedMinutes,
      reminderStage: item.reminderStage,
    );

    final categoryAppearance = tmsCategoryAppearance(item.category);
    final roomSquare = TmsRoomSquare(
      where: item.roomNo,
      label: categoryAppearance.label,
      palette: categoryAppearance.palette,
    );
    final onClaim = this.onClaim;

    return PoolCard(
      id: isHighlighted ? '$cardId--highlighted' : cardId,
      highlighted: isHighlighted,
      onTap: onTap,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (item.isPublicArea)
              TmsSemantics(
                id: 'pool.card.common-area-label',
                label: PoolItem.publicAreaRoomNo,
                child: roomSquare,
              )
            else
              roomSquare,
            const SizedBox(width: TmsSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TmsText.headline,
                  ),
                  if (sourceLabel != null) ...[
                    const SizedBox(height: TmsSpacing.xs),
                    PoolCardSubtitle(id: '$cardId.subtitle', text: sourceLabel),
                  ],
                  const SizedBox(height: TmsSpacing.xs),
                  PoolTagRow(
                    tags: [
                      ?sameFloorTag,
                      if (item.returnReason != null)
                        PoolCardField(
                          id: reasonFieldId,
                          text: item.returnReason!,
                        ),
                      if (reminderTag != null)
                        PoolCardField(
                          id: '$cardId.status-tag--${reminderTag.idSuffix}',
                          text: reminderTag.text,
                          tone: reminderTag.tone,
                        ),
                      ?waitedTag,
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
        if (item.handoverNote != null)
          PoolCardNote(
            id: '$cardId.handoff-progress',
            text: item.handoverNote!,
          ),
        if (onClaim != null)
          PoolClaimButton(id: '$cardId.claim-button', onPressed: onClaim),
      ],
    );
  }
}
