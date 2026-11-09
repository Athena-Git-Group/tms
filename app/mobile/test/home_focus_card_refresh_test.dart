import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/today_refresh.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';

/// 驗首頁主卡會跟著任務狀態換人：手上那一間做完、進到待查房之後，主卡要讓
/// 位給下一間，不能繼續寫著「進行中」。
///
/// 為什麼這組測試重要：任務頁蓋在分頁外殼上面，推進任務狀態的那一下，外殼
/// 那一層的今日進度不會自己變新。沒有這條線，員工做完一間退回首頁，看到的
/// 是進場時那一份舊資料——主卡還叫他「繼續處理」一間他已經做完的房。
void main() {
  // 這組測試用 `pumpAndSettle` 等畫面靜止。進行中卡那顆圓點是無限循環的呼吸
  // 動畫，動態開著就永遠排得出下一幀，`pumpAndSettle` 會等到逾時而不是靜止。
  setUp(() {
    TestWidgetsFlutterBinding.ensureInitialized()
        .platformDispatcher
        .accessibilityFeaturesTestValue = const FakeAccessibilityFeatures(
      disableAnimations: true,
    );
  });

  tearDown(() {
    TestWidgetsFlutterBinding.ensureInitialized()
        .platformDispatcher
        .clearAccessibilityFeaturesTestValue();
    ApiClient.instance = ApiClient();
  });

  testWidgets('手上有進行中的房時，主卡是那一間；那一間轉成待查房之後，主卡換成下一間', (
    tester,
  ) async {
    final client = _FakeApiClient(_doing());
    ApiClient.instance = client;
    await tester.pumpWidget(const CupertinoApp(home: HousekeepingShell()));
    await tester.pumpAndSettle();

    // 主卡的小標帶類別（Main.dc.html:171-172），列表的狀態標籤只有「進行中」，
    // 兩者不會互相誤認。
    expect(find.text('進行中 · 打掃'), findsOneWidget);
    expect(find.text('繼續處理'), findsOneWidget);

    // 任務頁送出完成之後打的就是這一支。
    client.today = _awaitingInspection();
    TodayRefresh.instance.request();
    await tester.pumpAndSettle();

    expect(find.text('進行中 · 打掃'), findsNothing);
    expect(find.text('下一間'), findsOneWidget);
    expect(find.text('開始打掃 303'), findsWidgets);
    expect(client.myTodayCalls, 2);
  });
}

/// 302 還在手上做。
MyToday _doing() => _today(
  todayTasks: const [
    TodayTask(
      taskId: 't-302',
      roomNo: '302',
      title: '302 退房打掃',
      type: 'cleaning',
      state: 'doing',
      redoCount: 0,
      estMin: 30,
      workedMinutes: 22,
      holdReason: null,
      inspection: null,
      inspectionWaived: false,
    ),
  ],
  nextTasks: const [
    NextTask(
      taskId: 't-303',
      roomNo: '303',
      title: '303 退房打掃',
      type: 'cleaning',
      redoCount: 0,
    ),
  ],
);

/// 302 已經做完、等查房；手上沒有進行中的房了。
MyToday _awaitingInspection() => _today(
  todayTasks: const [
    TodayTask(
      taskId: 't-302',
      roomNo: '302',
      title: '302 退房打掃',
      type: 'cleaning',
      state: 'done',
      redoCount: 0,
      estMin: 30,
      workedMinutes: 28,
      holdReason: null,
      inspection: null,
      inspectionWaived: false,
    ),
  ],
  nextTasks: const [
    NextTask(
      taskId: 't-303',
      roomNo: '303',
      title: '303 退房打掃',
      type: 'cleaning',
      redoCount: 0,
    ),
  ],
);

MyToday _today({
  required List<TodayTask> todayTasks,
  required List<NextTask> nextTasks,
}) {
  return MyToday(
    cleaningDone: 2,
    cleaningTotal: 7,
    inspectedCount: 1,
    completedCount: 2,
    adHocCompleted: 3,
    remainingMinutes: 135,
    zeroReturnStreakDays: 5,
    todayTasks: todayTasks,
    nextTasks: nextTasks,
    poolBadgeCount: 0,
    unreadNotificationBadgeCount: 0,
  );
}

/// 記憶體 fake：今日進度可以在測試中途換一份，其餘讀取端點回空。
class _FakeApiClient extends ApiClient {
  _FakeApiClient(this.today);

  MyToday today;

  int myTodayCalls = 0;

  @override
  Future<MyToday> getMyToday() async {
    myTodayCalls += 1;
    return today;
  }

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async {
    return const DeptPool(
      items: <PoolItem>[],
      counts: {'all': 0},
      activeStaffCount: 1,
    );
  }

  @override
  Future<List<NotificationItem>> listNotifications() async {
    return const <NotificationItem>[];
  }
}
