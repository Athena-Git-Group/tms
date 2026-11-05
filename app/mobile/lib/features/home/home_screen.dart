import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';
import '../engineering/eng_home_screen.dart';
import '../me/me_screen.dart';
import '../notification/notification_screen.dart';
import '../pool/pool_screen.dart';
import '../task/task_screen.dart';

/// 首頁骨架畫面。
///
/// 資料來源是 [ApiClient.getMyToday]（`GET /me/today`）：目前只把今日進度
/// 的打掃完成數／總數渲染成一行文字，證明資料線已接通。F1（今日進度四項、
/// 下一間順序…）的完整畫面留給後續 feature 補。
/// 今日進度的一格：畫面上是「名稱　值」一行，但掛 id 的節點只包住值。
///
/// 讀值的一方要的是值本身（「2 / 7」「135 分」），所以格子名稱放在 [TmsSemantics]
/// 外面當兄弟節點。名稱若包進同一個節點，它的文字會跟節點的 label 併在一起，讀值
/// 的一方得去剝前綴——這是實跑打出來的行為，不是推測。
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
          TmsSemantics(id: id, child: Text(value)),
        ],
      ),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late Future<MyToday> _todayFuture;

  @override
  void initState() {
    super.initState();
    _todayFuture = ApiClient.instance.getMyToday();
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'home.screen',
      child: CupertinoPageScaffold(
        navigationBar: const CupertinoNavigationBar(middle: Text('首頁')),
        child: SafeArea(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                FutureBuilder<MyToday>(
                  future: _todayFuture,
                  builder: (context, snapshot) {
                    final today = snapshot.data;
                    if (today == null) {
                      // 載入中或失敗先不佔版面；今日進度的完整呈現與錯誤
                      // 提示是 F1 的畫面行為，骨架只證明資料線接通。
                      return const SizedBox.shrink();
                    }
                    // 今日進度（進度環卡）的第一格，id 照 ui-plan 的 F1 R1；
                    // 「登入成功，進入首頁」的驗收也以這一格出現＝今日進度
                    // 已載入為準。
                    return Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // 今日進度四格。四個值全部直接取自 GET /me/today，App 不
                        // 自己算也不自己湊：第四格是「查房通過」（inspectedCount），
                        // 不是今天完成單數（completedCount，那個值留給完成慶祝頁）。
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
                        // 連續零退回天數。ui-plan 把這塊標成「待補畫面」，視覺規格
                        // 還沒下來，所以先用與四格同一套的最薄呈現把數值放上畫面。
                        _ProgressCell(
                          id: 'home.streak-badge',
                          caption: '連續零退回天數',
                          value: '${today.zeroReturnStreakDays}',
                        ),
                        // 今日任務列表：點一列進那間房的任務頁（ui-plan 畫面 1
                        // 「點任務列表項目進任務頁」）。資料只有 GET /me/today
                        // 的 nextTasks 這一個來源。
                        for (final next in today.nextTasks)
                          TmsSemantics(
                            id: 'home.task.${next.roomNo}.card',
                            label: '${next.roomNo} ${next.title}',
                            child: CupertinoButton(
                              onPressed: () {
                                Navigator.of(context).push(
                                  CupertinoPageRoute<void>(
                                    builder: (_) => TaskScreen(
                                      roomNo: next.roomNo,
                                      taskId: next.taskId,
                                    ),
                                  ),
                                );
                              },
                              child: Text('${next.roomNo} ${next.title}'),
                            ),
                          ),
                      ],
                    );
                  },
                ),
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
      ),
    );
  }
}
