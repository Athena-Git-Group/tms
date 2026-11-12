import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_count_tile.dart';
import '../../core/widgets/tms_task_row.dart';
import '../task/task_screen.dart';
import '../today/my_today_scope.dart';
import 'manager_ids.dart';
import 'manager_queue_screen.dart';
import 'manager_queues_store.dart';

/// 主管首頁（ui-plan 畫面 1）：上方三格磚（待查房、待審核、待驗收），下方
/// 「我名下的單」。
///
/// 三格數字來自一次 `GET /me/manager-queues`，直接畫回應的 `counts`，App 不
/// 自己數。回應存在 [ManagerQueuesStore]，點磚進去的清單讀同一份，兩邊才對得
/// 起來。名下的單讀外殼上 [MyTodayScope] 那一份 `GET /me/today` 的
/// todayTasks，卡片用房務首頁同一個 [TmsTaskRow]。房務首頁的問候列、進度環與
/// 「下一間」卡這裡都不放：主管每天先看的是有多少單在等他，不是自己的進度。
class ManagerHomeScreen extends StatefulWidget {
  const ManagerHomeScreen({super.key});

  /// 三格磚的順序與標題；queue 是 inspection、review、verify，與主管清單共用同一組鍵。
  static const List<({String queue, String title, String hint})> tiles = [
    (queue: 'inspection', title: '待查房', hint: '別人清好的房'),
    (queue: 'review', title: '待審核', hint: '房務的報修'),
    (queue: 'verify', title: '待驗收', hint: '已完修的工務單'),
  ];

  @override
  State<ManagerHomeScreen> createState() => _ManagerHomeScreenState();
}

class _ManagerHomeScreenState extends State<ManagerHomeScreen> {
  /// 首頁與清單共用的主管待辦；首頁進場時取一次。
  final ManagerQueuesStore _store = ManagerQueuesStore();

  @override
  void initState() {
    super.initState();
    _store.refresh();
  }

  @override
  void dispose() {
    _store.dispose();
    super.dispose();
  }

  void _openQueue(String queue) {
    if (_store.value == null) {
      return;
    }
    Navigator.of(context).push(
      CupertinoPageRoute<void>(
        builder: (_) => ManagerQueueScreen(queue: queue, store: _store),
      ),
    );
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
                builder: (context, _, _) => Row(
                  children: [
                    for (final tile in ManagerHomeScreen.tiles)
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
                            count: _store.countOf(tile.queue),
                            onTap: () => _openQueue(tile.queue),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: TmsSpacing.lg),
              const TmsSemantics(
                id: ManagerIds.homeMine,
                explicitChildNodes: true,
                child: _MineSection(),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 「我名下的單」：區段標題加任務卡；名下沒有單時是一句說明。
class _MineSection extends StatelessWidget {
  const _MineSection();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    final tasks = today.todayTasks;
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
              if (tasks.isNotEmpty)
                Text(
                  '${tasks.length} 張',
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.secondaryLabel,
                  ),
                ),
            ],
          ),
        ),
        if (tasks.isEmpty)
          const TmsContentUnavailable.inCard(
            id: ManagerIds.homeMineEmpty,
            copy: TmsEmptyCopy(
              icon: CupertinoIcons.tray,
              title: '今天沒有名下的單',
              description: '有需要時可以到待認領看看兩個部門的單',
            ),
          )
        else
          TmsCard(
            padding: EdgeInsets.zero,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(TmsRadius.card),
              child: Column(
                children: [
                  for (var i = 0; i < tasks.length; i++)
                    TmsTaskRow(
                      task: tasks[i],
                      showSeparator: i < tasks.length - 1,
                      onTap: () => Navigator.of(context).push(
                        CupertinoPageRoute<void>(
                          builder: (_) => TaskScreen(
                            roomNo: tasks[i].roomNo,
                            taskId: tasks[i].taskId,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}
