import 'package:flutter/cupertino.dart';

import '../../core/system_status.dart';
import '../../core/tms_semantics.dart';
import '../engineering/eng_home_screen.dart';
import '../me/me_screen.dart';
import '../notification/notification_screen.dart';
import '../pool/pool_screen.dart';
import '../task/task_screen.dart';
import '../today/my_today_scope.dart';
import '../today/progress_value.dart';

/// 房務首頁：今日進度四格、連續零退回天數，以及待開始的任務列表。
///
/// 畫面上的每一個數字與任務卡的順序都直接取自 `GET /me/today`（由
/// [MyTodayScope] 取一次、整個畫面共用），App 不自己算也不自己排。
/// 算法的真相在後端，兩邊各算一次就會有兩種答案。
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'home.screen',
      child: MyTodayScope(
        child: CupertinoPageScaffold(
          navigationBar: const CupertinoNavigationBar(middle: Text('首頁')),
          child: SafeArea(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // 連線狀態列固定在內容最上方，員工在首頁一眼就知道現在是離線
                // 還是連線不穩定，不必等某個操作失敗才發現。
                const SystemStatusBar(),
                Expanded(
                  child: Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const _TodayProgress(),
                        CupertinoButton(
                          onPressed: () {
                            Navigator.of(context).push(
                              CupertinoPageRoute<void>(
                                builder: (_) => const PoolScreen(),
                              ),
                            );
                          },
                          child: const Text('待認領'),
                        ),
                        // 以下三個入口是骨架期的路由接線：正式的 Tab bar 與部門
                        // 切換是後續 feature，先讓新畫面可以被導到。
                        CupertinoButton(
                          onPressed: () {
                            Navigator.of(context).push(
                              CupertinoPageRoute<void>(
                                builder: (_) => const NotificationScreen(),
                              ),
                            );
                          },
                          child: const Text('通知'),
                        ),
                        CupertinoButton(
                          onPressed: () {
                            Navigator.of(context).push(
                              CupertinoPageRoute<void>(
                                builder: (_) => const MeScreen(),
                              ),
                            );
                          },
                          child: const Text('我的'),
                        ),
                        CupertinoButton(
                          onPressed: () {
                            Navigator.of(context).push(
                              CupertinoPageRoute<void>(
                                builder: (_) => const EngHomeScreen(),
                              ),
                            );
                          },
                          child: const Text('工務首頁'),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 今日進度四格、連續零退回天數，以及待開始的任務卡。
class _TodayProgress extends StatelessWidget {
  const _TodayProgress();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      // 載入中或載入失敗都先不佔版面。首頁的錯誤提示還沒有畫面規格，
      // 在拿到規格之前不自行發明一種說法。
      return const SizedBox.shrink();
    }
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        // 四格的值全部直接取自今日進度的回應。第四格是「查房通過」
        // （inspectedCount），不是今天完成的單數（completedCount，那個值
        // 留給完成慶祝頁）。
        _ProgressCell(
          id: 'home.progress.cleaned-ratio',
          caption: '打掃完成／總數',
          value: '${today.cleaningDone} / ${today.cleaningTotal}',
        ),
        _ProgressCell(
          id: 'home.progress.inspected-count',
          caption: '查房通過',
          value: '${today.inspectedCount}',
        ),
        _ProgressCell(
          id: 'home.progress.ad-hoc-count',
          caption: '臨時需求完成',
          value: '${today.adHocCompleted}',
        ),
        _ProgressCell(
          id: 'home.progress.remaining-minutes',
          caption: '預估剩餘工時',
          value: '${today.remainingMinutes} 分',
        ),
        // 連續零退回天數的視覺規格還沒下來，先用與四格同一套的最薄呈現
        // 把數值放上畫面。
        _ProgressCell(
          id: 'home.streak-badge',
          caption: '連續零退回天數',
          value: '${today.zeroReturnStreakDays}',
        ),
        // 待開始的任務卡：點一列進那間房的任務頁。清單的順序照後端給的
        // nextTasks，App 不重排。
        for (final next in today.nextTasks)
          TmsSemantics(
            id: 'home.task.${next.roomNo}.card',
            label: '${next.roomNo} ${next.title}',
            child: CupertinoButton(
              onPressed: () {
                Navigator.of(context).push(
                  CupertinoPageRoute<void>(
                    builder: (_) =>
                        TaskScreen(roomNo: next.roomNo, taskId: next.taskId),
                  ),
                );
              },
              child: Text('${next.roomNo} ${next.title}'),
            ),
          ),
      ],
    );
  }
}

/// 今日進度的一格：畫面上是「名稱　值」一行，名稱在掛 id 的節點外面
/// （理由見 [ProgressValue]）。
class _ProgressCell extends StatelessWidget {
  const _ProgressCell({
    required this.id,
    required this.caption,
    required this.value,
  });

  final String id;
  final String caption;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text('$caption  '),
          ProgressValue(id: id, value: value),
        ],
      ),
    );
  }
}
