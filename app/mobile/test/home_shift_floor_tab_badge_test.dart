import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/widgets/tms_category_tile.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';

void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  String todayLabel() {
    const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
    final now = DateTime.now();
    return '週${weekdays[now.weekday - 1]} ${now.month}/${now.day}';
  }

  Finder badgeText(String text) => find.descendant(
    of: find.byType(TmsCountBadge),
    matching: find.text(text),
  );

  Future<_FakeApiClient> pumpShell(WidgetTester tester, MyToday today) async {
    final client = _FakeApiClient(today);
    ApiClient.instance = client;
    await tester.pumpWidget(const CupertinoApp(home: HousekeepingShell()));
    await tester.pumpAndSettle();
    return client;
  }

  testWidgets('四個欄位都缺席時，問候列只有日期、分頁列沒有任何徽章', (tester) async {
    await pumpShell(tester, _today());

    expect(find.text(todayLabel()), findsOneWidget);
    expect(find.byType(TmsCountBadge), findsNothing);
  });

  testWidgets('欄位齊全時，日期後面接班別與樓層，待認領與通知各掛一顆徽章', (tester) async {
    await pumpShell(
      tester,
      _today(
        shift: '早班',
        floors: const [3, 4],
        poolBadgeCount: 5,
        unreadNotificationBadgeCount: 2,
      ),
    );

    expect(find.text('${todayLabel()} · 早班 · 3、4 樓'), findsOneWidget);
    expect(find.byType(TmsCountBadge), findsNWidgets(2));
    expect(badgeText('5'), findsOneWidget);
    expect(badgeText('2'), findsOneWidget);
  });

  testWidgets('只有班別、沒有樓層時樓層那一段整段不出現', (tester) async {
    await pumpShell(tester, _today(shift: '晚班', floors: const []));

    expect(find.text('${todayLabel()} · 晚班'), findsOneWidget);
  });

  testWidgets('一次進場只打一支 `GET /me/today`——徽章與首頁共用同一份', (tester) async {
    final client = await pumpShell(tester, _today(poolBadgeCount: 5));

    expect(client.myTodayCalls, 1);
  });

  testWidgets('計數是 0 時不畫徽章', (tester) async {
    await pumpShell(
      tester,
      _today(poolBadgeCount: 0, unreadNotificationBadgeCount: 0),
    );

    expect(find.byType(TmsCountBadge), findsNothing);
  });

  test('回應裡沒有這四個鍵時解析不丟例外，班別與樓層為 null、兩個計數為 0', () {
    final today = MyToday.fromJson(const {
      'cleaningDone': 2,
      'cleaningTotal': 7,
      'inspectedCount': 1,
      'completedCount': 2,
      'adHocCompleted': 3,
      'remainingMinutes': 135,
    });

    expect(today.shift, isNull);
    expect(today.floors, isNull);
    expect(today.poolBadgeCount, 0);
    expect(today.unreadNotificationBadgeCount, 0);
  });

  test('回應帶齊四個鍵時照契約解析', () {
    final today = MyToday.fromJson(const {
      'cleaningDone': 2,
      'cleaningTotal': 7,
      'inspectedCount': 1,
      'completedCount': 2,
      'adHocCompleted': 3,
      'remainingMinutes': 135,
      'shift': '早班',
      'floors': [3, 4],
      'poolBadgeCount': 5,
      'unreadNotificationBadgeCount': 2,
    });

    expect(today.shift, '早班');
    expect(today.floors, [3, 4]);
    expect(today.poolBadgeCount, 5);
    expect(today.unreadNotificationBadgeCount, 2);
  });
}

MyToday _today({
  String? shift,
  List<int>? floors,
  int poolBadgeCount = 0,
  int unreadNotificationBadgeCount = 0,
}) {
  return MyToday(
    cleaningDone: 2,
    cleaningTotal: 7,
    inspectedCount: 1,
    completedCount: 2,
    adHocCompleted: 3,
    remainingMinutes: 135,
    todayTasks: const <TodayTask>[],
    nextTasks: const <NextTask>[],
    shift: shift,
    floors: floors,
    poolBadgeCount: poolBadgeCount,
    unreadNotificationBadgeCount: unreadNotificationBadgeCount,
  );
}

class _FakeApiClient extends ApiClient {
  _FakeApiClient(this.today);

  final MyToday today;

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
