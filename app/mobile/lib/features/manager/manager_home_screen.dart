import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/landing.dart';
import '../../core/theme.dart';
import '../../core/today_refresh.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_count_tile.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_repair_ticket_row.dart';
import '../../core/widgets/tms_task_row.dart';
import '../shell/main_shell.dart';
import '../task/task_screen.dart';
import '../ticket/ticket_screen.dart';
import '../today/my_today_scope.dart';
import 'manager_ids.dart';
import 'manager_queue_screen.dart';
import 'manager_queues_store.dart';

class ManagerHomeScreen extends StatefulWidget {
  const ManagerHomeScreen({super.key, required this.kind});

  final ManagerKind kind;

  static const Map<String, ({String title, String hint})> _tileCopy = {
    'inspection': (title: '待查房', hint: '別人清好的房'),
    'verify': (title: '待驗收', hint: '已完修的工務單'),
  };

  static List<({String queue, String title, String? hint})> tilesFor(
    ManagerKind kind,
  ) => [
    for (final queue in kind.queues)
      (
        queue: queue,
        title: _tileCopy[queue]!.title,
        hint: kind.queues.length > 1 ? _tileCopy[queue]!.hint : null,
      ),
  ];

  @override
  State<ManagerHomeScreen> createState() => _ManagerHomeScreenState();
}

class _ManagerHomeScreenState extends State<ManagerHomeScreen> {
  final ManagerQueuesStore _store = ManagerQueuesStore();

  int? _lastTab;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final tab = MainShellTab.indexOf(context);
    if (tab == MainShellTab.homeIndex && _lastTab != MainShellTab.homeIndex) {
      _store.refresh();
    }
    _lastTab = tab;
  }

  @override
  void dispose() {
    _store.dispose();
    super.dispose();
  }

  Future<void> _openQueue(String queue) async {
    if (_store.value == null) {
      return;
    }
    await Navigator.of(context).push(
      CupertinoPageRoute<void>(
        builder: (_) =>
            ManagerQueueScreen(kind: widget.kind, queue: queue, store: _store),
      ),
    );
    _store.refresh();
  }

  Future<void> _openMine(Widget page) async {
    await Navigator.of(context)
        .push(CupertinoPageRoute<void>(builder: (_) => page));
    _store.refresh();
    TodayRefresh.instance.request();
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: ManagerIds.home,
      explicitChildNodes: true,
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        navigationBar: const CupertinoNavigationBar(middle: Text('主管首頁')),
        child: SafeArea(
          bottom: false,
          child: ListView(
            padding: EdgeInsets.fromLTRB(
              TmsSpacing.screenEdge,
              TmsSpacing.md,
              TmsSpacing.screenEdge,
              TmsSpacing.scrollPadBottom(MediaQuery.paddingOf(context).bottom),
            ),
            children: [
              ValueListenableBuilder<ManagerQueues?>(
                valueListenable: _store,
                builder: (context, _, _) {
                  final tiles = ManagerHomeScreen.tilesFor(widget.kind);
                  final wide = tiles.length == 1;
                  return Row(
                    children: [
                      for (final tile in tiles)
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: TmsSpacing.xs,
                            ),
                            child: TmsCountTile(
                              id: ManagerIds.homeTile(tile.queue),
                              countId: ManagerIds.homeTileCount(tile.queue),
                              title: tile.title,
                              hint: tile.hint,
                              wide: wide,
                              count: _store.countOf(tile.queue),
                              onTap: () => _openQueue(tile.queue),
                            ),
                          ),
                        ),
                    ],
                  );
                },
              ),
              const SizedBox(height: TmsSpacing.lg),
              TmsSemantics(
                id: ManagerIds.homeMine,
                explicitChildNodes: true,
                child: _MineSection(kind: widget.kind, onOpen: _openMine),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MineSection extends StatelessWidget {
  const _MineSection({required this.kind, required this.onOpen});

  final ManagerKind kind;

  final ValueChanged<Widget> onOpen;

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    final cards = switch (kind) {
      ManagerKind.housekeeping => _taskCards(today.todayTasks),
      ManagerKind.engineering => _ticketCards(
        (today.engineeringSummary ?? EngineeringSummary.none).tickets,
      ),
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.xs,
            0,
            TmsSpacing.xs,
            TmsSpacing.sm,
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('我名下的單', style: TmsText.title3),
              if (cards.isNotEmpty)
                Text(
                  '${cards.length} 張',
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.secondaryLabel,
                  ),
                ),
            ],
          ),
        ),
        if (cards.isEmpty)
          const TmsContentUnavailable.inCard(
            id: ManagerIds.homeMineEmpty,
            copy: TmsEmptyCopy(
              icon: CupertinoIcons.tray,
              title: '今天沒有名下的單',
              description: '有需要時可以到待認領看看兩個部門的單',
            ),
          )
        else if (kind == ManagerKind.engineering)
          TmsGroupedSection(children: cards)
        else
          TmsCard(
            padding: EdgeInsets.zero,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(TmsRadius.card),
              child: Column(children: cards),
            ),
          ),
      ],
    );
  }

  List<Widget> _taskCards(List<TodayTask> tasks) => [
    for (var i = 0; i < tasks.length; i++)
      TmsTaskRow(
        task: tasks[i],
        showSeparator: i < tasks.length - 1,
        onTap: () => onOpen(
          TaskScreen(roomNo: tasks[i].roomNo, taskId: tasks[i].taskId),
        ),
      ),
  ];

  List<Widget> _ticketCards(List<EngineeringTicket> tickets) => [
    for (final ticket in tickets)
      TmsRepairTicketRow(
        ticket: ticket,
        onTap: () => onOpen(
          TicketScreen(view: TicketView.manager, taskId: ticket.taskId),
        ),
      ),
  ];
}
