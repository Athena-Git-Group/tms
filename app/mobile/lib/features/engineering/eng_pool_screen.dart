import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/today_refresh.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_category_square.dart';
import '../../core/widgets/tms_category_tile.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_tag.dart';
import '../pool/pool_widgets.dart';
import '../ticket/repair_labels.dart';
import '../ticket/ticket_screen.dart';

class EngPoolScreen extends StatelessWidget {
  const EngPoolScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'engPool.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        child: EngineeringPoolList(dept: null, canClaim: true),
      ),
    );
  }
}

class EngineeringPoolList extends StatefulWidget {
  const EngineeringPoolList({
    super.key,
    required this.dept,
    required this.canClaim,
    this.highlightTaskId,
    this.aboveCategories,
  });

  static const String deptKey = 'engineering';

  final String? dept;

  final bool canClaim;

  final String? highlightTaskId;

  final Widget? aboveCategories;

  @override
  State<EngineeringPoolList> createState() => _EngineeringPoolListState();
}

class _EngineeringPoolListState extends State<EngineeringPoolList> {
  static const _categories = [
    (
      key: 'all',
      label: '全部',
      sourceParam: null,
      countKey: null,
      icon: CupertinoIcons.square_grid_2x2,
      iconBackground: TmsColors.brandContainer,
      iconForeground: TmsColors.brandPrimary,
    ),
    (
      key: 'urgent',
      label: '急件',
      sourceParam: null,
      countKey: 'urgent',
      icon: CupertinoIcons.bolt,
      iconBackground: TmsColors.errorContainer,
      iconForeground: TmsColors.onErrorContainer,
    ),
    (
      key: 'desk',
      label: '櫃檯',
      sourceParam: 'desk_forward',
      countKey: 'deskForward',
      icon: CupertinoIcons.person_2,
      iconBackground: TmsColors.otherContainer,
      iconForeground: TmsColors.onOtherContainer,
    ),
    (
      key: 'staff',
      label: '房務報修',
      sourceParam: 'staff_report',
      countKey: 'staffReport',
      icon: CupertinoIcons.wrench,
      iconBackground: TmsColors.brandAccentContainer,
      iconForeground: TmsColors.brandOnAccentContainer,
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
  void didUpdateWidget(EngineeringPoolList oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.dept != widget.dept) {
      _fetch();
    }
  }

  Future<void> _fetch() async {
    setState(() {
      _pool = null;
      _error = null;
    });
    try {
      final selected = _categories.firstWhere(
        (category) => category.key == _selected,
      );
      final pool = await ApiClient.instance.getPool(
        dept: widget.dept,
        urgent: selected.key == 'urgent' ? true : null,
        source: selected.sourceParam,
      );
      if (mounted) {
        setState(() {
          _pool = pool;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = '載入待認領失敗，請稍後再試。';
        });
      }
    }
  }

  Future<void> _claim(PoolItem item) async {
    try {
      await ApiClient.instance.claimTask(item.taskId);
      TodayRefresh.instance.request();
      await _fetch();
    } on ApiException catch (e) {
      final message = e.errorMessage ?? '這單已被其他同事認領';
      await _fetch();
      if (!mounted) {
        return;
      }
      await showCupertinoDialog<void>(
        context: context,
        builder: (dialogContext) => TmsAlert(
          message: message,
          messageId: 'engPool.claim-conflict-alert',
          actions: [
            TmsAlertAction(
              label: '知道了',
              style: TmsAlertActionStyle.primary,
              onPressed: () => Navigator.of(dialogContext).pop(),
            ),
          ],
        ),
      );
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = '認領失敗，請稍後再試。';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final pool = _pool;
    return PoolScrollView(
      aboveCategories: widget.aboveCategories,
      children: [
        PoolFilterRow(
          tiles: [
            for (final category in _categories)
              PoolFilterChip(
                id: 'engPool.category.${category.key}.chip',
                label: category.label,
                icon: category.icon,
                iconBackground: category.iconBackground,
                iconForeground: category.iconForeground,
                selected: _selected == category.key,
                count: _badgeCount(category.countKey),
                unselectedBackground: TmsCategoryTile.translucentBackground,
                onTap: () {
                  _selected = category.key;
                  _fetch();
                },
              ),
          ],
        ),
        if (_error != null) PoolLoadError(message: _error!),
        if (pool == null && _error == null) const PoolLoading(),
        if (pool != null && pool.items.isEmpty) ...[
          const SizedBox(height: TmsSpacing.lg),
          TmsContentUnavailable(
            id: 'engPool.empty-state',
            copy: TmsEmptyStates.engineeringPool(
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
          for (final item in pool.items) _engPoolCard(item),
        ],
      ],
    );
  }

  int? _badgeCount(String? countKey) {
    if (countKey == null) {
      return null;
    }
    final count = _pool?.counts[countKey];
    if (count == null || count == 0) {
      return null;
    }
    return count;
  }

  Widget _engPoolCard(PoolItem item) {
    final ticketNo = item.ticketNo;
    if (ticketNo == null) {
      return const SizedBox.shrink();
    }
    final itemId = 'engPool.item.$ticketNo';
    final sourceLabel = engineeringPoolSourceLabels[item.source];
    final waitedTag = poolWaitedTag(
      waitedMinutes: item.waitedMinutes,
      reminderStage: item.reminderStage,
    );
    final roomSquare = TmsRoomSquare(
      where: item.roomNo,
      label: repairCategoryShortText(item.category),
      palette: tmsRepairSquarePalette(isUrgent: item.urgent, isDone: false),
    );

    final isHighlighted = item.taskId == widget.highlightTaskId;
    return PoolCard(
      id: isHighlighted ? '$itemId.card--highlighted' : '$itemId.card',
      highlighted: isHighlighted,
      semanticsLabel: '$ticketNo ${item.roomNo} ${item.title}',
      onTap: () => Navigator.of(context).push(
        CupertinoPageRoute<void>(
          builder: (_) => TicketScreen(
            view: TicketView.engineering,
            taskId: item.taskId,
          ),
        ),
      ),
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (item.isPublicArea)
              TmsSemantics(
                id: '$itemId.common-area-label',
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
                    PoolCardSubtitle(
                      id: '$itemId.source-label',
                      text: sourceLabel,
                    ),
                  ],
                  if (item.verificationReturnReason case final reason?) ...[
                    const SizedBox(height: TmsSpacing.xs),
                    Text(
                      reason,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TmsText.footnote.copyWith(
                        color: TmsColors.secondaryLabel,
                      ),
                    ),
                  ],
                  const SizedBox(height: TmsSpacing.xs),
                  PoolTagRow(
                    tags: [
                      if (item.urgent)
                        const TmsTag(label: '急件', tone: TmsTagTone.danger),
                      if (item.verificationReturnReason case final reason?)
                        TmsSemantics(
                          id: '$itemId.verification-return-label',
                          label: '驗收退回：$reason',
                          child: const ExcludeSemantics(
                            child: TmsTag(
                              label: '驗收退回',
                              tone: TmsTagTone.warn,
                            ),
                          ),
                        ),
                      ?waitedTag,
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
        if (widget.canClaim)
          PoolClaimButton(
            id: '$itemId.claim-button',
            glow: TmsPillGlow.alt,
            onPressed: () => _claim(item),
          ),
      ],
    );
  }
}
