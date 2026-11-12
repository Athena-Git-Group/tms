import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/push_gateway.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_notification_kind_icon.dart';

class NotificationScreen extends StatefulWidget {
  const NotificationScreen({super.key});

  @override
  State<NotificationScreen> createState() => _NotificationScreenState();
}

class _NotificationScreenState extends State<NotificationScreen> {
  List<NotificationItem>? _items;
  bool _loadFailed = false;

  int _stateVersion = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final version = ++_stateVersion;
    try {
      final items = await ApiClient.instance.listNotifications();
      if (!mounted || version != _stateVersion) {
        return;
      }
      setState(() {
        _items = items;
        _loadFailed = false;
      });
    } catch (_) {
      if (!mounted || version != _stateVersion) {
        return;
      }
      setState(() {
        _loadFailed = true;
      });
    }
  }

  Future<void> _markAllRead() async {
    final version = ++_stateVersion;
    final items = await ApiClient.instance.markAllNotificationsRead();
    if (!mounted || version != _stateVersion) {
      return;
    }
    setState(() {
      _items = items;
    });
  }

  Future<void> _open(NotificationItem item) async {
    final version = ++_stateVersion;
    final updated = await ApiClient.instance.markNotificationRead(item.id);
    if (!mounted) {
      return;
    }
    if (version == _stateVersion) {
      setState(() {
        _items = _items
            ?.map((existing) => existing.id == updated.id ? updated : existing)
            .toList();
      });
    }
    await PushGateway.instance.openTarget(item.target);
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'notification.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        child: SafeArea(
          bottom: false,
          child: ListView(
            padding: EdgeInsets.fromLTRB(
              TmsSpacing.screenEdge,
              TmsSpacing.md,
              TmsSpacing.screenEdge,
              TmsSpacing.scrollPadBottom(MediaQuery.paddingOf(context).bottom),
            ),
            children: _buildContent(),
          ),
        ),
      ),
    );
  }

  List<Widget> _buildContent() {
    const statusBar = SystemStatusBar();
    final header = _Header(
      onMarkAllRead: _markAllRead,
      showMarkAllRead: _items?.any((item) => item.isUnread) ?? false,
    );
    if (_loadFailed) {
      return [
        statusBar,
        header,
        const SizedBox(height: TmsSpacing.lg),
        const Text('載入失敗，請稍後再試', style: TmsText.subheadline),
      ];
    }
    final items = _items;
    if (items == null) {
      return [
        statusBar,
        header,
        const SizedBox(height: TmsSpacing.xxl),
        const Center(child: CupertinoActivityIndicator()),
      ];
    }
    if (items.isEmpty) {
      return [
        statusBar,
        header,
        const SizedBox(height: TmsSpacing.lg),
        const TmsContentUnavailable(
          id: 'notification.empty-state',
          copy: TmsEmptyStates.notifications,
          minHeight: TmsContentUnavailable.notificationMinHeight,
        ),
      ];
    }

    final today = <NotificationItem>[];
    final earlier = <NotificationItem>[];
    final now = DateTime.now();
    for (final item in items) {
      final local = item.createdAt.toLocal();
      final isToday =
          local.year == now.year &&
          local.month == now.month &&
          local.day == now.day;
      (isToday ? today : earlier).add(item);
    }

    return [
      statusBar,
      header,
      const SizedBox(height: TmsSpacing.lg),
      if (today.isNotEmpty) ...[
        _NotificationGroup(
          groupKey: 'today',
          title: '今天',
          items: today,
          onTap: _open,
          now: now,
        ),
        const SizedBox(height: TmsSpacing.lg),
      ],
      if (earlier.isNotEmpty) ...[
        _NotificationGroup(
          groupKey: 'earlier',
          title: '更早',
          items: earlier,
          onTap: _open,
          now: now,
        ),
        const SizedBox(height: TmsSpacing.lg),
      ],
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
        child: Text(
          '有即時動態時，急件不另外推播，只更新即時動態並展開提醒；這裡會保留紀錄。',
          style: TmsText.footnote.copyWith(height: 1.5),
        ),
      ),
    ];
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.onMarkAllRead, required this.showMarkAllRead});

  final VoidCallback onMarkAllRead;

  final bool showMarkAllRead;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        const Expanded(child: Text('通知', style: TmsText.largeTitle)),
        if (showMarkAllRead)
          TmsSemantics(
            id: 'notification.mark-all-read-button',
            child: CupertinoButton(
              padding: EdgeInsets.zero,
              minimumSize: const Size(0, TmsSize.minTouchTarget),
              onPressed: onMarkAllRead,
              child: Text(
                '全部標為已讀',
                style: TmsText.body.copyWith(color: TmsColors.brandPrimary),
              ),
            ),
          ),
      ],
    );
  }
}

class _NotificationGroup extends StatelessWidget {
  const _NotificationGroup({
    required this.groupKey,
    required this.title,
    required this.items,
    required this.onTap,
    required this.now,
  });

  final String groupKey;
  final String title;
  final List<NotificationItem> items;
  final ValueChanged<NotificationItem> onTap;

  final DateTime now;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'notification.group.$groupKey.section',
      explicitChildNodes: true,
      child: TmsGroupedSection(
        header: title,
        children: [
          for (final item in items)
            _NotificationRow(item: item, now: now, onTap: () => onTap(item)),
        ],
      ),
    );
  }
}

class _NotificationRow extends StatelessWidget {
  const _NotificationRow({
    required this.item,
    required this.now,
    required this.onTap,
  });

  final NotificationItem item;
  final DateTime now;
  final VoidCallback onTap;

  static const double _dotTopInset = 14;

  static const double _titleLineHeight = 1.35;

  static const double _bodyLineHeight = 1.4;

  @override
  Widget build(BuildContext context) {
    final body = item.body;
    final titleStyle = (item.isUnread ? TmsText.headline : TmsText.body)
        .copyWith(height: _titleLineHeight);
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      // 分組只有一列時，Flutter 會把沒有自己 semantics 節點的列內容併進上層節點。
      child: Semantics(
        container: true,
        child: Container(
          color: TmsColors.cardBackground,
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.md,
            TmsSpacing.md,
            TmsSpacing.screenEdge,
            TmsSpacing.md,
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: TmsSize.unreadDot,
                child: item.isUnread
                    ? Padding(
                        padding: const EdgeInsets.only(top: _dotTopInset),
                        child: TmsSemantics(
                          id: 'notification.item.${item.id}.unread-dot',
                          label: '未讀',
                          child: Container(
                            width: TmsSize.unreadDot,
                            height: TmsSize.unreadDot,
                            decoration: const BoxDecoration(
                              color: TmsColors.brandPrimary,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ),
                      )
                    : null,
              ),
              const SizedBox(width: TmsSpacing.md),
              TmsNotificationKindIcon(kind: _kindOf(item)),
              const SizedBox(width: TmsSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.baseline,
                      textBaseline: TextBaseline.alphabetic,
                      children: [
                        Expanded(
                          child: TmsSemantics(
                            id: 'notification.item.${item.id}.title',
                            label: item.title,
                            child: ExcludeSemantics(
                              child: Text(item.title, style: titleStyle),
                            ),
                          ),
                        ),
                        const SizedBox(width: TmsSpacing.sm),
                        Text(
                          relativeTimeLabel(item.createdAt, now),
                          style: TmsText.footnote.copyWith(
                            color: TmsColors.quaternaryLabel,
                          ),
                        ),
                      ],
                    ),
                    if (body != null && body.isNotEmpty) ...[
                      const SizedBox(height: TmsSpacing.xxs),
                      Text(
                        body,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TmsText.subheadline.copyWith(
                          color: TmsColors.secondaryLabel,
                          height: _bodyLineHeight,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

TmsNotificationKind _kindOf(NotificationItem item) {
  switch (item.event) {
    case 'repair_urgent':
      return TmsNotificationKind.urgent;
    case 'unclaimed_reminder_staff':
    case 'unclaimed_reminder_manager':
    case 'shift_end_still_interrupted':
      return TmsNotificationKind.timeout;
    case 'inspection_passed':
    case 'repair_completed':
      return TmsNotificationKind.done;
    case 'pool_new_request':
    case 'reassigned_to_me':
    case 'leave_takeover':
    case 'inspection_failed':
    case 'spot_check_failed':
      return TmsNotificationKind.newRequest;
    case 'kb_reply':
    case 'kb_unhelpful':
      return TmsNotificationKind.memo;
    default:
      return TmsNotificationKind.info;
  }
}

String relativeTimeLabel(DateTime createdAt, DateTime now) {
  final local = createdAt.toLocal();
  final minutes = now.difference(local).inMinutes;
  if (minutes < 1) {
    return '剛剛';
  }
  if (minutes < 60) {
    return '$minutes 分鐘前';
  }
  if (minutes < 60 * 24) {
    return '${minutes ~/ 60} 小時前';
  }
  final hour = local.hour.toString().padLeft(2, '0');
  final minute = local.minute.toString().padLeft(2, '0');
  return '昨天 $hour:$minute';
}
