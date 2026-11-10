import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';
import 'package:tms_staff_app/features/ticket/ticket_screen.dart';

/// 驗工務首頁會跟著工務單的狀態變：在待認領接了一張單、或在詳情頁把一張單
/// 修完，回到首頁看到的要是新的那一份今日進度，不是進場時取的那一份。
///
/// 為什麼這組測試重要：待認領與工務單詳情都不在首頁那一層底下，它們改了後端
/// 的狀態，首頁讀的 `GET /me/today` 不會自己變新。少了那一聲「資料舊了」的
/// 廣播，工務完修一張單退回首頁，首頁仍寫著待開始；剛接到的單也不會出現在
/// 「我的任務」。這組測試走的是真的畫面路徑（點分頁、點認領鈕、點完修回報），
/// 而不是直接觸發廣播，所以漏呼叫的那一處會在這裡紅。
void main() {
  // 維修中那張卡的脈動圓點是無限循環動畫，動態開著 `pumpAndSettle` 就永遠等
  // 不到靜止。
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

  testWidgets('在待認領接了一張工務單之後，首頁「我的任務」出現那一張', (tester) async {
    _useIPhoneSurface(tester);
    final client = _FakeApiClient(
      today: _today(openCount: 0, tickets: const []),
      pool: _poolWith(_poolItem()),
    );
    ApiClient.instance = client;
    await tester.pumpWidget(const CupertinoApp(home: EngineeringShell()));
    await tester.pumpAndSettle();

    expect(find.text('手上沒有工務單'), findsOneWidget);
    expect(find.text('我的任務'), findsNothing);

    await tester.tap(find.text('待認領'));
    await tester.pumpAndSettle();
    await tester.tap(_byId('engPool.item.W-0412.claim-button'));
    await tester.pumpAndSettle();
    expect(client.claimed, ['task-412']);

    await tester.tap(find.text('首頁'));
    await tester.pumpAndSettle();

    expect(find.text('我的任務'), findsOneWidget);
    expect(_byId('engHome.in-hand.W-0412.card'), findsOneWidget);
    expect(find.text('手上沒有工務單'), findsNothing);
  });

  testWidgets('在詳情頁把一張待開始的單開工並完修之後，首頁那一列從待開始變已完修', (
    tester,
  ) async {
    _useIPhoneSurface(tester);
    final client = _FakeApiClient(
      today: _today(openCount: 1, tickets: [_ticket(state: 'accepted')]),
      pool: _poolWith(),
      ticket: _detail(state: 'accepted'),
    );
    ApiClient.instance = client;
    await tester.pumpWidget(const CupertinoApp(home: EngineeringShell()));
    await tester.pumpAndSettle();

    expect(find.text('待開始'), findsOneWidget);
    expect(find.text('已完修'), findsNothing);

    await tester.tap(_byId('engHome.in-hand.W-0412.card'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('開始維修'));
    await tester.pumpAndSettle();
    expect(client.started, ['task-412']);

    await tester.tap(find.text('完修回報'));
    await tester.pumpAndSettle();
    await tester.tap(_byId('completeSheet.result.fixed'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('加入照片'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('拍照'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('送出完修'));
    await tester.pumpAndSettle();
    expect(client.completed, ['task-412']);

    Navigator.of(tester.element(find.byType(TicketScreen))).pop();
    await tester.pumpAndSettle();

    expect(find.text('已完修'), findsOneWidget);
    expect(find.text('待開始'), findsNothing);
    expect(find.text('下一張（急件優先）'), findsNothing);
  });
}

/// 詳情頁與完修面板的按鈕都貼在畫面下緣，800x600 的預設測試視窗放不完整張
/// 面板，點下去會落在視窗外面。改成與基準截圖同一台 iPhone 17 的邏輯尺寸。
void _useIPhoneSurface(WidgetTester tester) {
  tester.view.physicalSize = const Size(402, 874);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Finder _byId(String id) => find.bySemanticsIdentifier(id);

EngineeringTicket _ticket({required String state}) {
  return EngineeringTicket(
    ticketNo: 'W-0412',
    taskId: 'task-412',
    roomNo: '401',
    isPublicArea: false,
    title: '冷氣不冷',
    state: state,
    isUrgent: false,
    source: 'staff_report',
  );
}

MyToday _today({
  required int openCount,
  int completedToday = 0,
  required List<EngineeringTicket> tickets,
}) {
  return MyToday(
    cleaningDone: 0,
    cleaningTotal: 0,
    inspectedCount: 0,
    completedCount: 0,
    adHocCompleted: 0,
    remainingMinutes: 0,
    todayTasks: const <TodayTask>[],
    nextTasks: const <NextTask>[],
    shift: 'morning',
    engineeringSummary: EngineeringSummary(
      openCount: openCount,
      completedToday: completedToday,
      urgentCount: 0,
      tickets: tickets,
    ),
  );
}

PoolItem _poolItem() {
  return const PoolItem(
    taskId: 'task-412',
    ticketNo: 'W-0412',
    roomNo: '401',
    floor: 4,
    title: '冷氣不冷',
    category: 'air_conditioning',
    source: 'staff_report',
    urgent: false,
    priorityNotify: false,
    sameFloor: false,
    createdAt: '2026-11-10T09:00:00Z',
  );
}

DeptPool _poolWith([PoolItem? item]) {
  return DeptPool(
    items: [?item],
    counts: {'all': item == null ? 0 : 1},
    activeStaffCount: 1,
  );
}

RepairTicketDetail _detail({required String state}) {
  return RepairTicketDetail(
    taskId: 'task-412',
    ticketNo: 'W-0412',
    roomNo: '401',
    category: 'air_conditioning',
    isUrgent: false,
    state: state,
    reporterName: '阿美',
    assigneeName: '阿德',
  );
}

/// 記憶體 fake，扮演一個狀態會真的改變的後端。
///
/// 只是回固定資料的 fake 抓不到這條 bug：首頁不重查也「看起來」正確，因為
/// 新舊兩份一模一樣。所以這裡的認領、開始、完修都會把下一次 `GET /me/today`
/// 的回應換成改過狀態的那一份；首頁沒有重查，拿到的就還是舊的。
class _FakeApiClient extends ApiClient {
  _FakeApiClient({required this.today, required this.pool, this.ticket});

  MyToday today;
  DeptPool pool;
  RepairTicketDetail? ticket;

  final List<String> claimed = [];
  final List<String> started = [];
  final List<String> completed = [];

  @override
  LoginResult? get session => const LoginResult(
    token: 'token',
    expiresAt: '2026-11-10T00:00:00Z',
    staffId: 'staff-1',
    name: '阿德',
    dept: 'engineering',
    role: 'staff',
    mustChangePassword: false,
  );

  @override
  Future<MyToday> getMyToday() async => today;

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async => pool;

  @override
  Future<List<NotificationItem>> listNotifications() async =>
      const <NotificationItem>[];

  @override
  Future<void> claimTask(String taskId) async {
    claimed.add(taskId);
    pool = _poolWith();
    today = _today(openCount: 1, tickets: [_ticket(state: 'accepted')]);
  }

  @override
  Future<RepairTicketDetail> readRepairTicket(String taskId) async =>
      ticket!;

  @override
  Future<TaskDetail> startTask(String taskId) async {
    started.add(taskId);
    ticket = _detail(state: 'doing');
    today = _today(openCount: 1, tickets: [_ticket(state: 'doing')]);
    return TaskDetail(id: taskId, state: 'doing', type: 'repair');
  }

  @override
  Future<TaskCompletion?> completeTask(
    String taskId, {
    List<String>? photoIds,
    String? resolutionType,
    String? idempotencyKey,
  }) async {
    completed.add(taskId);
    ticket = _detail(state: 'done');
    today = _today(
      openCount: 0,
      completedToday: 1,
      tickets: [_ticket(state: 'done')],
    );
    return null;
  }

  @override
  Future<String> createRepairPhoto({required String purpose}) async =>
      'photo-1';

  @override
  Future<void> uploadRepairPhotoContent(
    String photoId,
    List<int> jpegBytes,
  ) async {}
}
