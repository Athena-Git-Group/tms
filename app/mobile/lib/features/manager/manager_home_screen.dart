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

/// 主管首頁（ui-plan 畫面 1）：上方是待辦格子，下方「我名下的單」。
///
/// 格子依主管種類：房務主管兩格並排（待查房、待驗收），工務主管只有一條待
/// 驗收寬磚。種類由主管外殼傳進來，這裡不再看登入身分。
///
/// 格子數字來自一次 `GET /me/manager-queues`，直接畫回應的 `counts`，App 不
/// 自己數。回應存在 [ManagerQueuesStore]，點磚進去的清單讀同一份，兩邊才對得
/// 起來；切回首頁分頁、從清單或名下的單返回時都重取一次。名下的單讀外殼上
/// [MyTodayScope] 那一份 `GET /me/today`：房務主管是 todayTasks、用房務首頁
/// 的 [TmsTaskRow]，工務主管是 engineeringSummary.tickets、用工務首頁的
/// [TmsRepairTicketRow]。員工首頁的問候列、進度環、「下一間」卡與急件保養區
/// 這裡都不放：主管每天先看的是有多少單在等他，不是自己的進度。
class ManagerHomeScreen extends StatefulWidget {
  const ManagerHomeScreen({super.key, required this.kind});

  final ManagerKind kind;

  /// 每一類待辦格子的標題與副文；queue 與主管清單共用同一組鍵。
  static const Map<String, ({String title, String hint})> _tileCopy = {
    'inspection': (title: '待查房', hint: '別人清好的房'),
    'verify': (title: '待驗收', hint: '已完修的工務單'),
  };

  /// 各種主管首頁的格子與順序，格子集合照 [ManagerKind.queues]。只有一格時
  /// 是寬磚，旁邊沒有別格要對齊，不放副文。
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
  /// 首頁與清單共用的主管待辦。
  final ManagerQueuesStore _store = ManagerQueuesStore();

  /// 上一次看到的分頁；從別的分頁切回首頁時重取一次待辦。
  int? _lastTab;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // 分頁外殼把建過的分頁留在樹上，切回首頁不會重新進場；格子數字要跟著
    // 別人剛處理掉、或剛完修的單更新，所以每次回到首頁分頁就重取一次。
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

  /// 從「我名下的單」點進任務頁或工務單詳情，回來時格子與名下的單一起重取：
  /// 主管在裡面完修或做完一張單，待驗收、待查房的數字可能已經變了。
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

/// 「我名下的單」：區段標題加卡片；名下沒有單時是一句說明。
///
/// 房務主管的單是 `todayTasks`，卡片用房務首頁的任務卡、點開任務頁；工務主
/// 管的單是 `engineeringSummary.tickets`，卡片用工務首頁的工務單卡、點開工務
/// 單詳情。詳情用主管視角：處理人動作與工務員工看自己名下的單相同，已完修
/// 時另有通過與不通過。
class _MineSection extends StatelessWidget {
  const _MineSection({required this.kind, required this.onOpen});

  final ManagerKind kind;

  /// 推一個畫面上去，回來時由首頁負責重取。
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
