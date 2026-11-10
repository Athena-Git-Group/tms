import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/widgets/tms_category_tile.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';

/// 驗「班別／負責樓層／兩顆分頁徽章」這四個後端分批落地的欄位，在落地前與
/// 落地後都呈現正確。
///
/// 為什麼這組測試重要：四個欄位目前後端一個都還沒回，回應裡連鍵都不會出現。
/// 缺席時畫面必須跟現在一模一樣（日期後面不多出分隔點、分頁列不多出徽章），
/// 否則整個房務端在後端補完之前都是壞的；而欄位補上之後，同一份程式碼要自己
/// 長出內容，不能再改一次 App。
void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  /// 今天的日期字串，與首頁問候列用的是同一個算法（Main.dc.html:128）。
  String todayLabel() {
    const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
    final now = DateTime.now();
    return '週${weekdays[now.weekday - 1]} ${now.month}/${now.day}';
  }

  /// 只在徽章裡面找這個數字。首頁上別處也有數字（例如進度卡那幾格），直接用
  /// `find.text` 會找到不是徽章的那一個。
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

/// 記憶體 fake：只回今日進度，其餘讀取端點回空，測試不碰網路。
class _FakeApiClient extends ApiClient {
  _FakeApiClient(this.today);

  final MyToday today;

  /// 這支端點被打了幾次。分頁列的徽章與首頁的數字必須出自同一次請求，
  /// 這個計數就是那件事的證據。
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
