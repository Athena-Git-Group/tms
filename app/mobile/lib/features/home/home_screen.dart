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
                        TmsSemantics(
                          id: 'home.progress.cleaned-ratio',
                          label:
                              '今日打掃 ${today.cleaningDone}/${today.cleaningTotal}',
                          child: Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: Text(
                              '今日打掃 ${today.cleaningDone}/${today.cleaningTotal}',
                            ),
                          ),
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
