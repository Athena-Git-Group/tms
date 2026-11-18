import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/push_gateway.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_tab_bar.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';

void main() {
  // 畫面上有無限循環的動畫時，`pumpAndSettle` 會等到逾時。
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

  Future<void> pumpShell(WidgetTester tester) async {
    ApiClient.instance = _FakeApiClient();
    await tester.pumpWidget(const CupertinoApp(home: HousekeepingShell()));
    await tester.pumpAndSettle();
  }

  String labelOf(WidgetTester tester, String id) =>
      tester.getSemantics(find.bySemanticsIdentifier(id)).label;

  testWidgets('登入後落在首頁分頁，今日進度與任務卡的節點都讀得到', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpShell(tester);

    expect(find.bySemanticsIdentifier('home.screen'), findsOneWidget);
    expect(labelOf(tester, 'home.progress.cleaned-ratio'), '2 / 7');
    expect(labelOf(tester, 'home.progress.inspected-count'), '1');
    expect(labelOf(tester, 'home.progress.ad-hoc-count'), '3 單');
    expect(labelOf(tester, 'home.progress.remaining-minutes'), '135 分鐘');

    await tester.drag(find.byType(ListView), const Offset(0, -400));
    await tester.pumpAndSettle();

    expect(labelOf(tester, 'home.task.305.card'), '305 退房清潔');
    expect(find.bySemanticsIdentifier('home.task.306.card'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('點分頁列的「待認領」切到待認領，首頁節點同時收起來', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpShell(tester);

    await tester.tap(find.text('待認領'));
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('pool.screen'), findsOneWidget);
    expect(find.bySemanticsIdentifier('home.screen'), findsNothing);

    handle.dispose();
  });

  testWidgets('疊在外殼上的畫面要求去待認領時，外殼自己切過去', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpShell(tester);

    MainShellTabRequest.goToPool();
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('pool.screen'), findsOneWidget);
    expect(find.bySemanticsIdentifier('home.screen'), findsNothing);
    expect(MainShellTabRequest.pending.value, isNull);

    handle.dispose();
  });

  testWidgets('推播 dest=pool 退回外殼切到待認領、標亮該房，分頁列還在', (tester) async {
    final handle = tester.ensureSemantics();
    final navigatorKey = GlobalKey<NavigatorState>();
    PushGateway.instance.attach(navigatorKey);
    ApiClient.instance = _FakeApiClient(
      pool: const [
        PoolItem(
          taskId: 'task-316',
          roomNo: '316',
          floor: 3,
          title: '衣架 x4',
          category: 'amenity',
          source: 'system',
          urgent: false,
          priorityNotify: false,
          sameFloor: false,
          createdAt: '2026-11-05T08:00:00Z',
        ),
      ],
    );
    await tester.pumpWidget(
      CupertinoApp(navigatorKey: navigatorKey, home: const HousekeepingShell()),
    );
    await tester.pumpAndSettle();

    await PushGateway.instance.handlePushPayload(
      const {'dest': 'pool', 'highlight': '316'},
    );
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('pool.screen'), findsOneWidget);
    expect(find.bySemanticsIdentifier('pool.card.316--highlighted'), findsOneWidget);
    await tester.tap(find.text('首頁'));
    await tester.pumpAndSettle();
    expect(find.bySemanticsIdentifier('home.screen'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('點分頁列的「通知」與「我的」各自切到對應的畫面', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpShell(tester);

    await tester.tap(find.text('通知'));
    await tester.pumpAndSettle();
    expect(find.bySemanticsIdentifier('notification.screen'), findsOneWidget);

    await tester.tap(find.text('我的'));
    await tester.pumpAndSettle();
    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('分頁列浮在內容上：左右各內縮 20，下緣離安全區 21', (tester) async {
    ApiClient.instance = _FakeApiClient();
    await tester.pumpWidget(
      const CupertinoApp(
        home: MediaQuery(
          data: MediaQueryData(
            padding: EdgeInsets.only(bottom: 34),
            disableAnimations: true,
          ),
          child: HousekeepingShell(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final screen = tester.getRect(find.byType(HousekeepingShell));
    final bar = tester.getRect(find.byType(TmsTabBar));

    expect(bar.left - screen.left, 20);
    expect(screen.right - bar.right, 20);
    expect(screen.bottom - bar.bottom, 21);
    expect(bar.height, 62);

    final edge = tester.getRect(find.byType(TmsBottomScrollEdge));
    expect(edge.bottom, screen.bottom);
    expect(edge.height, 62 + 21);
  });

  testWidgets('首頁清單的底部留白讓開整條分頁列', (tester) async {
    ApiClient.instance = _FakeApiClient();
    await tester.pumpWidget(
      const CupertinoApp(
        home: MediaQuery(
          data: MediaQueryData(
            padding: EdgeInsets.only(bottom: 34),
            disableAnimations: true,
          ),
          child: HousekeepingShell(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final list = tester.widget<ListView>(find.byType(ListView));
    expect(
      list.padding,
      EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        TmsSpacing.md,
        TmsSpacing.screenEdge,
        TmsSpacing.scrollPadBottom(34),
      ),
    );
  });
}

class _FakeApiClient extends ApiClient {
  _FakeApiClient({this.pool = const <PoolItem>[]});

  final List<PoolItem> pool;

  @override
  Future<MyToday> getMyToday() async {
    return const MyToday(
      cleaningDone: 2,
      cleaningTotal: 7,
      inspectedCount: 1,
      completedCount: 2,
      adHocCompleted: 3,
      remainingMinutes: 135,
      todayTasks: [
        TodayTask(
          taskId: 'task-305',
          roomNo: '305',
          title: '退房清潔',
          type: 'cleaning',
          state: 'doing',
          redoCount: 0,
          estMin: 30,
          workedMinutes: 12,
          holdReason: null,
          inspection: null,
          inspectionWaived: false,
        ),
        TodayTask(
          taskId: 'task-306',
          roomNo: '306',
          title: '送備品',
          type: 'amenity',
          state: 'accepted',
          redoCount: 0,
          estMin: 10,
          workedMinutes: 0,
          holdReason: null,
          inspection: null,
          inspectionWaived: false,
        ),
      ],
      nextTasks: [
        NextTask(
          taskId: 'task-306',
          roomNo: '306',
          title: '送備品',
          type: 'amenity',
          redoCount: 0,
        ),
      ],
    );
  }

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async {
    return DeptPool(
      items: pool,
      counts: {'all': pool.length},
      activeStaffCount: 1,
    );
  }

  @override
  Future<List<NotificationItem>> listNotifications() async {
    return const <NotificationItem>[];
  }
}
