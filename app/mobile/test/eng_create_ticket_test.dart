import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/widgets/tms_sheet.dart';
import 'package:tms_staff_app/core/widgets/tms_tip_popover.dart';
import 'package:tms_staff_app/features/engineering/eng_home_screen.dart';
import 'package:tms_staff_app/features/engineering/eng_pool_screen.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';
import 'package:tms_staff_app/features/ticket/repair_ticket_form_sheet.dart';
import 'package:tms_staff_app/features/today/my_today_scope.dart';

/// 開工單的入口從工務待認領的導覽列搬到工務首頁的浮動按鈕
/// （`Main.dc.html:4051`），表單本身從整頁改成底部面板
/// （`Main.dc.html:2197`）。這一組驗的是員工走得到、填得完、送得出去。
void main() {
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

  Future<_FakeApiClient> pumpEngHome(
    WidgetTester tester, {
    int tabIndex = MainShellTab.homeIndex,
  }) async {
    _useIPhoneSurface(tester);
    final api = _FakeApiClient();
    ApiClient.instance = api;
    await tester.pumpWidget(
      CupertinoApp(
        home: MainShellTab(
          index: tabIndex,
          child: const MyTodayScope(child: EngHomeScreen()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return api;
  }

  group('開工單入口（Main.dc.html:4051）', () {
    testWidgets('工務首頁右下角有開工單浮動按鈕', (tester) async {
      await pumpEngHome(tester);

      expect(_byId('engHome.create-ticket-fab'), findsOneWidget);
    });

    testWidgets('工務待認領沒有開工單入口，也沒有導覽列', (tester) async {
      ApiClient.instance = _FakeApiClient();
      await tester.pumpWidget(const CupertinoApp(home: EngPoolScreen()));
      await tester.pumpAndSettle();

      expect(find.text('開工單'), findsNothing);
      expect(find.byType(CupertinoNavigationBar), findsNothing);
    });

    testWidgets('第一次進首頁有教學氣泡，按 × 之後不再出現', (tester) async {
      await pumpEngHome(tester);

      expect(find.text('在這裡開工單'), findsOneWidget);
      expect(find.text('巡檢時發現問題，拍照就能直接開單。'), findsOneWidget);

      await tester.tap(
        find.descendant(
          of: find.byType(TmsTipPopover),
          matching: find.byType(CupertinoButton),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('在這裡開工單'), findsNothing);
    });

    testWidgets('員工已經切到別的分頁，氣泡就不再出現（Main.dc.html:4046）', (tester) async {
      await pumpEngHome(tester, tabIndex: 1);

      expect(find.text('在這裡開工單'), findsNothing);
    });

    testWidgets('按下浮動按鈕升起開工單面板，氣泡同時收掉', (tester) async {
      await pumpEngHome(tester);

      await tester.tap(_byId('engHome.create-ticket-fab'));
      await tester.pumpAndSettle();

      expect(find.byType(TmsSheet), findsOneWidget);
      expect(find.text('開工單'), findsOneWidget);
      expect(find.text('在這裡開工單'), findsNothing);
    });
  });

  group('開工單面板（Main.dc.html:2197）', () {
    Future<_FakeApiClient> pumpSheet(WidgetTester tester) async {
      _useIPhoneSurface(tester);
      final api = _FakeApiClient();
      ApiClient.instance = api;
      await tester.pumpWidget(
        CupertinoApp(
          home: Builder(
            builder: (context) => CupertinoButton(
              onPressed: () => RepairTicketFormSheet.show(context),
              child: const Text('開'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('開'));
      await tester.pumpAndSettle();
      return api;
    }

    testWidgets('預設是客房：有房號欄與客房說明行，沒有誰來處理', (tester) async {
      await pumpSheet(tester);

      expect(_byId('repair.room-field'), findsOneWidget);
      expect(_byId('repair.room-only-note'), findsOneWidget);
      expect(find.text('誰來處理'), findsNothing);
    });

    testWidgets('切到公共區域：房號欄收掉，換成誰來處理分段與它的說明', (tester) async {
      await pumpSheet(tester);

      await tester.tap(_byId('repair.public-area-toggle'));
      await tester.pumpAndSettle();

      expect(_byId('repair.room-field'), findsNothing);
      expect(_byId('repair.room-only-note'), findsNothing);
      expect(find.text('誰來處理'), findsOneWidget);
      expect(_byId('repair.who.pool.segment'), findsOneWidget);
      expect(
        find.text('工務自己開的單不用主管審核。公共區域的單可以放進待認領，其他工務同仁也接得到。'),
        findsOneWidget,
      );
    });

    testWidgets('地點兩段都在，類別走膠囊列，狀況說明有欄位', (tester) async {
      await pumpSheet(tester);

      expect(_byId('repair.room-type-toggle'), findsOneWidget);
      expect(_byId('repair.public-area-toggle'), findsOneWidget);
      expect(find.text('類別'), findsOneWidget);
      expect(_byId('repair.category.air_conditioning.chip'), findsOneWidget);
      expect(_byId('repair.description-field'), findsOneWidget);
    });

    testWidgets('照片卡文案照原型，一張都沒拍時說明為什麼非拍不可', (tester) async {
      await pumpSheet(tester);

      expect(find.text('照片（必填，至少 1 張、最多 3 張）'), findsOneWidget);
      expect(find.text('加入照片'), findsOneWidget);
      expect(_byId('repair.photo.required-note'), findsOneWidget);
    });

    testWidgets('房號不在房間主檔裡就送不出去', (tester) async {
      final api = await pumpSheet(tester);

      await tester.enterText(find.byType(CupertinoTextField).first, '999');
      await tester.tap(_byId('repair.category.air_conditioning.chip'));
      await tester.pumpAndSettle();
      await _addPhoto(tester);

      await tester.tap(find.text('建立工單'));
      await tester.pumpAndSettle();

      expect(api.createdRoomId, isNull);
      expect(api.createdIsPublicArea, isNull);
      expect(find.byType(TmsSheet), findsOneWidget);
    });

    testWidgets('房號、類別、照片都齊了就送得出去，送的是房間 id', (tester) async {
      final api = await pumpSheet(tester);

      await tester.enterText(find.byType(CupertinoTextField).first, '418');
      await tester.tap(_byId('repair.category.plumbing_electrical.chip'));
      await tester.pumpAndSettle();
      await _addPhoto(tester);

      expect(find.text('建立工單'), findsOneWidget);

      await tester.tap(find.text('建立工單'));
      await tester.pumpAndSettle();

      expect(api.createdRoomId, 'room-418');
      expect(api.createdIsPublicArea, isFalse);
      expect(api.createdAssigneeId, 'staff-1');
      expect(find.byType(TmsSheet), findsNothing);
    });

    testWidgets('公共區域選「放進待認領」時不指派負責人', (tester) async {
      final api = await pumpSheet(tester);

      await tester.tap(_byId('repair.public-area-toggle'));
      await tester.pumpAndSettle();
      await tester.tap(_byId('repair.who.pool.segment'));
      await tester.tap(_byId('repair.category.other.chip'));
      await tester.pumpAndSettle();
      await _addPhoto(tester);

      await tester.tap(find.text('建立工單'));
      await tester.pumpAndSettle();

      expect(api.createdIsPublicArea, isTrue);
      expect(api.createdRoomId, isNull);
      expect(api.createdAssigneeId, isNull);
    });
  });
}

Finder _byId(String id) => find.bySemanticsIdentifier(id);

/// 這一組畫面的按鈕都貼在畫面下緣，800x600 的預設測試視窗放不完整張面板，
/// 點下去會落在視窗外面。改成與基準截圖同一台 iPhone 17 的邏輯尺寸。
void _useIPhoneSurface(WidgetTester tester) {
  tester.view.physicalSize = const Size(402, 874);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

/// 加一張照片：點「加入照片」→ 在系統選單上選「拍照」。
Future<void> _addPhoto(WidgetTester tester) async {
  await tester.tap(find.text('加入照片'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('拍照'));
  await tester.pumpAndSettle();
}

class _FakeApiClient extends ApiClient {
  String? createdRoomId;
  bool? createdIsPublicArea;
  String? createdAssigneeId;
  String? createdDescription;

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
  Future<MyToday> getMyToday() async => MyToday(
    cleaningDone: 0,
    cleaningTotal: 0,
    inspectedCount: 0,
    completedCount: 0,
    adHocCompleted: 0,
    remainingMinutes: 0,
    zeroReturnStreakDays: 0,
    todayTasks: const <TodayTask>[],
    nextTasks: const <NextTask>[],
    shift: 'morning',
    engineeringSummary: const EngineeringSummary(
      openCount: 0,
      completedToday: 0,
      urgentCount: 0,
      tickets: <EngineeringTicket>[],
    ),
  );

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async => const DeptPool(
    items: <PoolItem>[],
    counts: <String, int>{},
    activeStaffCount: 1,
  );

  @override
  Future<List<RoomSummary>> listRooms() async => const [
    RoomSummary(id: 'room-418', roomNo: '418'),
  ];

  @override
  Future<RepairTicketDetail> readRepairTicket(String taskId) async =>
      throw const ApiException(statusCode: 500, body: '{}');

  @override
  Future<String> createRepairPhoto({required String purpose}) async =>
      'photo-1';

  @override
  Future<void> uploadRepairPhotoContent(
    String photoId,
    List<int> jpegBytes,
  ) async {}

  @override
  Future<RepairTicketDetail> createRepairTicket({
    String? sourceTaskId,
    String? roomId,
    bool isPublicArea = false,
    required String category,
    String? description,
    required List<String> photoIds,
    bool interruptSourceTask = false,
    String? assigneeId,
    String? idempotencyKey,
  }) async {
    createdRoomId = roomId;
    createdIsPublicArea = isPublicArea;
    createdAssigneeId = assigneeId;
    createdDescription = description;
    return RepairTicketDetail.fromJson(const {
      'taskId': 'task-1',
      'ticketNo': 'W-0601',
      'roomNo': '418',
      'category': 'plumbing_electrical',
      'isUrgent': false,
      'state': 'accepted',
      'reporterName': '阿德',
      'createdAt': '2026-11-09T00:00:00Z',
    });
  }
}
