import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/push_gateway.dart';
import 'package:tms_staff_app/core/widgets/tms_category_tile.dart';
import 'package:tms_staff_app/features/engineering/eng_pool_screen.dart';
import 'package:tms_staff_app/features/manager/manager_pool_screen.dart';
import 'package:tms_staff_app/features/manager/pool_dept_preference.dart';
import 'package:tms_staff_app/features/pool/pool_screen.dart';
import 'package:tms_staff_app/features/pool/pool_widgets.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';

void main() {
  late _RecordingApiClient api;

  setUp(() {
    TestWidgetsFlutterBinding.ensureInitialized()
        .platformDispatcher
        .accessibilityFeaturesTestValue = const FakeAccessibilityFeatures(
      disableAnimations: true,
    );
    api = _RecordingApiClient();
    ApiClient.instance = api;
  });

  tearDown(() {
    TestWidgetsFlutterBinding.ensureInitialized().platformDispatcher
        .clearAccessibilityFeaturesTestValue();
    ApiClient.instance = ApiClient();
    PoolDeptPreference.changes.value = null;
    MainShellTabRequest.poolHighlightRoomNo.value = null;
    MainShellTabRequest.poolHighlightTaskId.value = null;
  });

  Future<void> pumpManagerPool(WidgetTester tester) async {
    await tester.pumpWidget(const CupertinoApp(home: ManagerPoolScreen()));
    await tester.pumpAndSettle();
  }

  testWidgets('這台手機沒有紀錄時停在房務，並把房務記下來', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await pumpManagerPool(tester);

    expect(find.byType(HousekeepingPoolList), findsOneWidget);
    expect(api.deptQueries, ['housekeeping']);
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString(PoolDeptPreference.key), 'housekeeping');
  });

  testWidgets('上次停在工務，打開就是工務的池與分流磚', (tester) async {
    SharedPreferences.setMockInitialValues({
      'pool.dept': 'engineering',
    });
    await pumpManagerPool(tester);

    expect(find.byType(EngineeringPoolList), findsOneWidget);
    expect(api.deptQueries, ['engineering']);
    final labels = tester
        .widgetList<TmsCategoryTile>(find.byType(TmsCategoryTile))
        .map((tile) => tile.label)
        .toList();
    expect(labels, ['全部', '急件', '櫃檯', '房務報修']);
  });

  testWidgets('切到工務會換池並記在手機上', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await pumpManagerPool(tester);

    await tester.tap(find.text('工務'));
    await tester.pumpAndSettle();

    expect(find.byType(EngineeringPoolList), findsOneWidget);
    expect(find.byType(HousekeepingPoolList), findsNothing);
    expect(api.deptQueries, ['housekeeping', 'engineering']);
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString(PoolDeptPreference.key), 'engineering');
  });

  testWidgets('主管停在房務，點開帶工務部門的推播，切到工務並標亮那張單', (tester) async {
    final handle = tester.ensureSemantics();
    SharedPreferences.setMockInitialValues({'pool.dept': 'housekeeping'});
    api.session = _manager;
    final navigatorKey = GlobalKey<NavigatorState>();
    PushGateway.instance.attach(navigatorKey);
    await tester.pumpWidget(
      CupertinoApp(
        navigatorKey: navigatorKey,
        home: const ManagerPoolScreen(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(HousekeepingPoolList), findsOneWidget);

    await PushGateway.instance.handlePushPayload(const {
      'target':
          '{"kind":"pool","dept":"engineering","highlightTaskId":"task-1"}',
      'idempotencyKey': 'evt-pool-engineering',
    });
    await tester.pumpAndSettle();

    expect(find.byType(EngineeringPoolList), findsOneWidget);
    expect(
      find.bySemanticsIdentifier('engPool.item.W-0202.card--highlighted'),
      findsOneWidget,
    );
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString(PoolDeptPreference.key), 'engineering');
    handle.dispose();
  });

  testWidgets('員工點開帶部門的推播，不改主管的部門選擇', (tester) async {
    SharedPreferences.setMockInitialValues({'pool.dept': 'housekeeping'});
    final navigatorKey = GlobalKey<NavigatorState>();
    PushGateway.instance.attach(navigatorKey);
    await tester.pumpWidget(
      CupertinoApp(navigatorKey: navigatorKey, home: const SizedBox()),
    );

    await PushGateway.instance.handlePushPayload(const {
      'target': '{"kind":"pool","dept":"engineering"}',
      'idempotencyKey': 'evt-pool-staff',
    });
    await tester.pumpAndSettle();

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString(PoolDeptPreference.key), 'housekeeping');
  });

  testWidgets('主管的單卡沒有認領鈕，員工的有', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await pumpManagerPool(tester);
    expect(find.text('317'), findsOneWidget);
    expect(find.byType(PoolClaimButton), findsNothing);

    await tester.tap(find.text('工務'));
    await tester.pumpAndSettle();
    expect(find.text('冷氣不冷'), findsOneWidget);
    expect(find.byType(PoolClaimButton), findsNothing);

    await tester.pumpWidget(const CupertinoApp(home: PoolScreen()));
    await tester.pumpAndSettle();
    expect(find.byType(PoolClaimButton), findsOneWidget);

    await tester.pumpWidget(const CupertinoApp(home: EngPoolScreen()));
    await tester.pumpAndSettle();
    expect(find.byType(PoolClaimButton), findsOneWidget);
  });
}

const _manager = LoginResult(
  token: 't',
  expiresAt: '2026-11-13T00:00:00Z',
  staffId: 'staff-wang',
  name: '王主管',
  role: 'manager',
  mustChangePassword: false,
);

class _RecordingApiClient extends ApiClient {
  final List<String?> deptQueries = [];

  LoginResult? _fakeSession;

  set session(LoginResult? value) => _fakeSession = value;

  @override
  LoginResult? get session => _fakeSession;

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async {
    deptQueries.add(dept);
    return DeptPool(
      activeStaffCount: 2,
      counts: const {'all': 1},
      items: [
        PoolItem(
          taskId: 'task-1',
          ticketNo: 'W-0202',
          roomNo: dept == 'engineering' ? '306' : '317',
          floor: 3,
          title: dept == 'engineering' ? '冷氣不冷' : '續住整理（接手）',
          category: dept == 'engineering' ? 'air_conditioning' : 'cleaning',
          source: dept == 'engineering' ? 'staff_report' : 'leave_return',
          urgent: dept == 'engineering',
          priorityNotify: false,
          sameFloor: false,
          createdAt: '2026-11-10T09:00:00Z',
        ),
      ],
    );
  }
}
