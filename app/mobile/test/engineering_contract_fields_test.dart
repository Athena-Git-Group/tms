import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/engineering/eng_home_screen.dart';
import 'package:tms_staff_app/features/today/my_today_scope.dart';
import 'package:tms_staff_app/features/engineering/eng_pool_screen.dart';
import 'package:tms_staff_app/features/pool/pool_screen.dart';

/// 工務首頁與兩個待認領畫面要顯示的幾個數字（今天預計要修幾張、今日保養、
/// 這張單已做幾分鐘、在池裡等了幾分鐘、各分流還有幾張）都是後端之後才會開始
/// 回的欄位。在那之前每一筆都會缺席，所以每一個欄位都要有兩種畫法，這組測試
/// 一次釘住兩邊：欄位缺席時畫面要跟現在一模一樣，欄位齊全時新的內容要出現在
/// 該出現的位置。
///
/// 缺席那半邊比齊全那半邊重要：後端先上線的是現在這份回應，齊全那半邊要等
/// 批次 2 落地才會真的跑到。
void main() {
  // 這組測試一律用 `pumpAndSettle` 等畫面不再有下一幀。工務首頁維修中那張卡
  // 的圓點是無限循環的呼吸動畫，動態開著就永遠排得出下一幀，`pumpAndSettle`
  // 會等到逾時而不是等到畫面靜止。
  setUp(() {
    TestWidgetsFlutterBinding.ensureInitialized()
        .platformDispatcher
        .accessibilityFeaturesTestValue = const FakeAccessibilityFeatures(
      disableAnimations: true,
    );
  });

  tearDown(() {
    TestWidgetsFlutterBinding.ensureInitialized().platformDispatcher
        .clearAccessibilityFeaturesTestValue();
    ApiClient.instance = ApiClient();
  });

  Future<void> pumpEngHome(WidgetTester tester, MyToday today) async {
    ApiClient.instance = _FakeApiClient(today: today);
    // 今日進度取在外殼那一層（main_shell），工務首頁自己不再建 scope；
    // 這個測試單獨渲染工務首頁，所以要補上外殼平常提供的那一層。
    await tester.pumpWidget(
      const CupertinoApp(home: MyTodayScope(child: EngHomeScreen())),
    );
    await tester.pumpAndSettle();
  }

  Future<void> pumpEngPool(WidgetTester tester, DeptPool pool) async {
    ApiClient.instance = _FakeApiClient(pool: pool);
    await tester.pumpWidget(const CupertinoApp(home: EngPoolScreen()));
    await tester.pumpAndSettle();
  }

  Future<void> pumpHousekeepingPool(WidgetTester tester, DeptPool pool) async {
    ApiClient.instance = _FakeApiClient(pool: pool);
    await tester.pumpWidget(const CupertinoApp(home: PoolScreen()));
    await tester.pumpAndSettle();
  }

  group('工務首頁', () {
    testWidgets('三個欄位都缺席時，進度環分母退回相加、保養那格與計時都不畫', (tester) async {
      await pumpEngHome(tester, _engToday());

      // 今天已完修 2 張、手上還有 3 張，分母是這兩個數字相加。
      expect(find.text(' / 5'), findsOneWidget);
      expect(find.text('今日保養'), findsNothing);
      expect(find.text('42 分'), findsNothing);
      // 維修中那張主卡其餘的內容照舊。
      expect(find.text('維修中'), findsWidgets);
      expect(find.text('401'), findsWidgets);
      expect(tester.takeException(), isNull);
    });

    testWidgets('三個欄位齊全時，分母改用預計量、保養那格與已做分鐘都出現', (tester) async {
      await pumpEngHome(
        tester,
        _engToday(
          plannedToday: 8,
          maintenanceDone: 3,
          maintenanceTotal: 6,
          doingWorkedMinutes: 42,
        ),
      );

      expect(find.text(' / 8'), findsOneWidget);
      expect(find.text(' / 5'), findsNothing);
      expect(find.text('今日保養'), findsOneWidget);
      expect(find.text('3 / 6'), findsOneWidget);
      expect(find.text('42 分'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('只有保養的其中一個欄位有值時，那一格仍然整格不畫', (tester) async {
      await pumpEngHome(tester, _engToday(maintenanceDone: 3));

      expect(find.text('今日保養'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('預計量是 0 時分母就是 0，環不會因為除以 0 而出錯', (tester) async {
      await pumpEngHome(tester, _engToday(plannedToday: 0));

      expect(find.text(' / 0'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  group('工務待認領', () {
    testWidgets('等待分鐘與分流筆數都缺席時，單卡與分流磚維持現在的樣子', (tester) async {
      await pumpEngPool(tester, _engPool());

      expect(find.textContaining('等 '), findsNothing);
      expect(find.text('剛進來'), findsNothing);
      // 徽章上的數字一顆都沒有；磚上的文字照舊。
      for (final label in const ['全部', '急件', '房務報修', '櫃檯']) {
        expect(find.text(label), findsWidgets);
      }
      expect(find.text('W-0412 · 401'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('等待分鐘與分流筆數齊全時，單卡掛等待標籤、三格掛數量徽章', (tester) async {
      await pumpEngPool(
        tester,
        _engPool(
          waitedMinutes: 7,
          counts: const {
            'all': 9,
            'urgent': 4,
            'staffReport': 3,
            'deskForward': 2,
          },
        ),
      );

      expect(find.text('等 7 分'), findsOneWidget);
      expect(find.text('4'), findsOneWidget);
      expect(find.text('3'), findsOneWidget);
      expect(find.text('2'), findsOneWidget);
      // 「全部」那格沒有徽章，所以整池的 9 不會出現在磚上。
      expect(find.text('9'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('筆數是 0 的分流不畫徽章', (tester) async {
      await pumpEngPool(
        tester,
        _engPool(
          counts: const {
            'all': 1,
            'urgent': 0,
            'staffReport': 1,
            'deskForward': 0,
          },
        ),
      );

      expect(find.text('0'), findsNothing);
      expect(find.text('1'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  group('房務待認領', () {
    testWidgets('等待分鐘缺席時單卡維持現在的樣子，筆數 0 的分類磚沒有徽章', (tester) async {
      final handle = tester.ensureSemantics();
      await pumpHousekeepingPool(tester, _housekeepingPool());

      expect(find.textContaining('等 '), findsNothing);
      expect(find.text('剛進來'), findsNothing);
      // 筆數 0 的分類連徽章節點都不存在（Main.dc.html:4232 的 `hasCount`）。
      expect(
        find.bySemanticsIdentifier('pool.category.cleaning.count'),
        findsNothing,
      );
      expect(find.text('0'), findsNothing);
      handle.dispose();
      expect(tester.takeException(), isNull);
    });

    testWidgets('等待分鐘有值時單卡掛等待標籤，超過門檻才轉成要處理的語氣', (tester) async {
      await pumpHousekeepingPool(tester, _housekeepingPool(waitedMinutes: 25));

      expect(find.text('等 25 分'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('還不滿一分鐘的單寫「剛進來」，不寫「等 0 分」', (tester) async {
      await pumpHousekeepingPool(tester, _housekeepingPool(waitedMinutes: 0));

      expect(find.text('剛進來'), findsOneWidget);
      expect(find.text('等 0 分'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('已經進了未接提醒階段的單改掛提醒標籤，不再並排等待時間', (tester) async {
      await pumpHousekeepingPool(
        tester,
        _housekeepingPool(waitedMinutes: 30, reminderStage: 'manager_notified'),
      );

      expect(find.text('未接 · 已通知主管'), findsOneWidget);
      expect(find.text('等 30 分'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  });

  group('解析', () {
    test('欄位缺席時三個模型都收成 null，不丟例外', () {
      final summary = EngineeringSummary.fromJson(const {
        'openCount': 3,
        'completedToday': 2,
        'urgentCount': 1,
        'tickets': <dynamic>[],
      });
      expect(summary.plannedToday, isNull);
      expect(summary.maintenanceDone, isNull);
      expect(summary.maintenanceTotal, isNull);

      final ticket = EngineeringTicket.fromJson(const {
        'taskId': 'ticket-1',
        'isPublicArea': false,
        'title': '冷氣不冷',
        'state': 'doing',
        'isUrgent': false,
        'source': 'staff_report',
      });
      expect(ticket.workedMinutes, isNull);

      final item = PoolItem.fromJson(const {
        'taskId': 'task-1',
        'roomNo': '401',
        'floor': 4,
        'title': '冷氣不冷',
        'category': 'repair',
        'source': 'staff_report',
        'urgent': false,
        'priorityNotify': false,
        'sameFloor': false,
        'createdAt': '2026-11-08T09:00:00Z',
      });
      expect(item.waitedMinutes, isNull);
    });

    test('欄位齊全時照收，counts 裡值是 null 的 key 不收進來', () {
      final summary = EngineeringSummary.fromJson(const {
        'openCount': 3,
        'completedToday': 2,
        'urgentCount': 1,
        'plannedToday': 8,
        'maintenanceDone': 3,
        'maintenanceTotal': 6,
        'tickets': <dynamic>[],
      });
      expect(summary.plannedToday, 8);
      expect(summary.maintenanceDone, 3);
      expect(summary.maintenanceTotal, 6);

      final pool = DeptPool.fromJson(const {
        'items': <dynamic>[],
        'activeStaffCount': 4,
        'counts': {
          'all': 9,
          'urgent': 4,
          'staffReport': 3,
          'deskForward': null,
        },
      });
      expect(pool.counts['urgent'], 4);
      expect(pool.counts['staffReport'], 3);
      expect(pool.counts.containsKey('deskForward'), isFalse);
    });
  });
}

/// 工務今日進度：今天已完修 2 張、手上 3 張（其中一張維修中）。
/// 每個參數都是後端之後才會開始回的欄位，不給就是缺席。
MyToday _engToday({
  int? plannedToday,
  int? maintenanceDone,
  int? maintenanceTotal,
  int? doingWorkedMinutes,
}) {
  return MyToday(
    cleaningDone: 0,
    cleaningTotal: 0,
    inspectedCount: 0,
    completedCount: 2,
    adHocCompleted: 0,
    remainingMinutes: 0,
    zeroReturnStreakDays: 0,
    todayTasks: const <TodayTask>[],
    nextTasks: const <NextTask>[],
    engineeringSummary: EngineeringSummary(
      openCount: 3,
      completedToday: 2,
      urgentCount: 1,
      plannedToday: plannedToday,
      maintenanceDone: maintenanceDone,
      maintenanceTotal: maintenanceTotal,
      tickets: [
        EngineeringTicket(
          ticketNo: 'W-0412',
          taskId: 'ticket-412',
          roomNo: '401',
          isPublicArea: false,
          title: '冷氣不冷',
          state: 'doing',
          isUrgent: false,
          source: 'staff_report',
          workedMinutes: doingWorkedMinutes,
        ),
      ],
    ),
  );
}

DeptPool _engPool({int? waitedMinutes, Map<String, int>? counts}) {
  return DeptPool(
    activeStaffCount: 4,
    counts: counts ?? const {'all': 1},
    items: [
      PoolItem(
        taskId: 'task-412',
        ticketNo: 'W-0412',
        roomNo: '401',
        floor: 4,
        title: '冷氣不冷',
        category: 'repair',
        source: 'staff_report',
        urgent: false,
        priorityNotify: false,
        sameFloor: false,
        createdAt: '2026-11-08T09:00:00Z',
        waitedMinutes: waitedMinutes,
      ),
    ],
  );
}

DeptPool _housekeepingPool({int? waitedMinutes, String? reminderStage}) {
  return DeptPool(
    activeStaffCount: 4,
    counts: const {'all': 1, 'cleaning': 0, 'amenity': 1, 'other': 0},
    items: [
      PoolItem(
        taskId: 'task-317',
        roomNo: '317',
        floor: 3,
        title: '送備品',
        category: 'amenity',
        source: 'guest_scan',
        urgent: false,
        priorityNotify: false,
        sameFloor: false,
        createdAt: '2026-11-08T09:00:00Z',
        reminderStage: reminderStage,
        waitedMinutes: waitedMinutes,
      ),
    ],
  );
}

/// 記憶體 fake：這幾個畫面開啟時要打的讀取端點都在這裡回固定資料，測試不碰
/// 網路。
class _FakeApiClient extends ApiClient {
  _FakeApiClient({this.today, this.pool});

  final MyToday? today;
  final DeptPool? pool;

  @override
  Future<MyToday> getMyToday() async => today!;

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async =>
      pool!;
}
