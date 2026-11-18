import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_home_cards.dart';
import 'package:tms_staff_app/features/engineering/eng_home_screen.dart';
import 'package:tms_staff_app/features/today/my_today_scope.dart';

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

  Future<void> pump(WidgetTester tester, MyToday today) async {
    ApiClient.instance = _FakeApiClient(today);
    await tester.pumpWidget(
      const CupertinoApp(home: MyTodayScope(child: EngHomeScreen())),
    );
    await tester.pumpAndSettle();
  }

  group('問候列（Main.dc.html:857-863）', () {
    testWidgets('日期列接班別與「工務」，問候語帶名字，頭像放姓名末字', (tester) async {
      await pump(tester, _today(tickets: [_ticket(state: 'accepted')]));

      expect(find.textContaining('早班'), findsOneWidget);
      expect(find.textContaining('工務'), findsOneWidget);
      expect(find.text('早安，阿德'), findsOneWidget);
      expect(find.text('德'), findsOneWidget);
    });

    testWidgets('手上一張都不剩、今天有完修過，問候語換成「辛苦了」（Main.dc.html:4079）', (tester) async {
      await pump(
        tester,
        _today(
          openCount: 0,
          completedToday: 2,
          tickets: [_ticket(state: 'done')],
        ),
      );

      expect(find.text('辛苦了，阿德'), findsOneWidget);
      expect(find.text('早安，阿德'), findsNothing);
    });

    testWidgets('畫面最上面沒有導覽列，標題由問候語擔任（Main.dc.html:856）', (tester) async {
      await pump(tester, _today(tickets: [_ticket(state: 'accepted')]));

      expect(find.byType(CupertinoNavigationBar), findsNothing);
      expect(find.text('工務首頁'), findsNothing);
    });
  });

  group('今日進度卡（Main.dc.html:866-894）', () {
    testWidgets('環下寫「張已完修」，右欄第一格是「手上還有 N 張工單」', (tester) async {
      await pump(tester, _today(tickets: [_ticket(state: 'accepted')]));

      expect(find.text('張已完修'), findsOneWidget);
      expect(find.text('今天已完修'), findsNothing);
      expect(find.text('手上還有'), findsOneWidget);
      expect(find.text(' 張工單'), findsOneWidget);
    });

    testWidgets('單位字在掛 id 的節點外面，讀張數的一方拿到的仍是純數字', (tester) async {
      final handle = tester.ensureSemantics();
      await pump(tester, _today(tickets: [_ticket(state: 'accepted')]));

      expect(
        tester
            .getSemantics(
              find.bySemanticsIdentifier('engHome.progress.in-hand-count'),
            )
            .label,
        '3',
      );
      handle.dispose();
    });

    testWidgets('急件與今日保養那一排是 17pt，比上排的張數小一級（Main.dc.html:886、890）', (
      tester,
    ) async {
      await pump(
        tester,
        _today(
          maintenanceDone: 3,
          maintenanceTotal: 6,
          tickets: [_ticket(state: 'accepted')],
        ),
      );

      expect(
        tester.widget<Text>(find.text('3 / 6')).style?.fontSize,
        TmsText.displayStat.fontSize,
      );
    });
  });

  group('主卡（Main.dc.html:898-933）', () {
    testWidgets('維修中那張：地點、標題、計時同一列，計時 22pt', (tester) async {
      await pump(
        tester,
        _today(
          tickets: [_ticket(state: 'doing', title: '冷氣不冷', workedMinutes: 42)],
        ),
      );

      expect(find.text('維修中'), findsNWidgets(2));
      expect(
        tester.widget<Text>(find.text('42 分')).style?.fontSize,
        TmsText.displayTimerCompact.fontSize,
      );
    });

    testWidgets('下一張那張：眉標寫「急件優先」、右上角是扳手、按鈕帶地點', (tester) async {
      await pump(tester, _today(tickets: [_ticket(state: 'accepted')]));

      expect(find.text('下一張（急件優先）'), findsOneWidget);
      expect(find.byIcon(CupertinoIcons.wrench), findsOneWidget);
      expect(find.text('開始維修 401'), findsWidgets);
      expect(find.byIcon(CupertinoIcons.play_fill), findsOneWidget);
    });

    testWidgets('下一張的副標接來源（Main.dc.html:4073）', (tester) async {
      await pump(
        tester,
        _today(
          tickets: [_ticket(state: 'accepted', title: '冷氣不冷')],
        ),
      );

      expect(
        find.descendant(
          of: find.byType(TmsHeroCard),
          matching: find.text('冷氣不冷 · 房務報修'),
        ),
        findsOneWidget,
      );
    });

    testWidgets('急件在主卡上是一顆獨立的標籤，不跟狀態併（Main.dc.html:924）', (tester) async {
      await pump(
        tester,
        _today(tickets: [_ticket(state: 'accepted', isUrgent: true)]),
      );

      expect(
        find.descendant(
          of: find.byType(TmsHeroCard),
          matching: find.text('急件'),
        ),
        findsOneWidget,
      );
      expect(find.text('急件 · 待開始'), findsOneWidget);
    });

    testWidgets('地點是區域而不是房號時字級降到 30（Main.dc.html:921）', (tester) async {
      await pump(
        tester,
        _today(
          tickets: [
            _ticket(state: 'accepted', roomNo: null, isPublicArea: true),
          ],
        ),
      );

      expect(
        tester
            .widget<Text>(
              find.descendant(
                of: find.byType(TmsHeroCard),
                matching: find.text('公共區域'),
              ),
            )
            .style
            ?.fontSize,
        TmsText.displayAreaName.fontSize,
      );
    });
  });

  group('空狀態（Main.dc.html:936-942）', () {
    testWidgets('一張單都沒有時主卡的位置換成空狀態卡', (tester) async {
      await pump(tester, _today(openCount: 0, tickets: const []));

      expect(find.text('手上沒有工務單'), findsOneWidget);
      expect(find.text('新的工務單進來會推播通知你。'), findsOneWidget);
    });

    testWidgets('今天完修過、手上清空時改成正向的那一句', (tester) async {
      await pump(
        tester,
        _today(
          openCount: 0,
          completedToday: 2,
          tickets: [_ticket(state: 'done')],
        ),
      );

      expect(find.text('今天的工務單都完修了'), findsOneWidget);
      expect(find.textContaining('今天完修 2 張'), findsOneWidget);
    });
  });

  group('我的任務（Main.dc.html:947-966）', () {
    testWidgets('區段標題右邊寫排序規則', (tester) async {
      await pump(tester, _today(tickets: [_ticket(state: 'accepted')]));

      expect(find.text('我的任務'), findsOneWidget);
      expect(find.text('依緊急程度排序'), findsOneWidget);
    });

    testWidgets('列主文是工單標題、副標是來源，單號不進文字（Main.dc.html:959-960）', (tester) async {
      await pump(
        tester,
        _today(
          tickets: [_ticket(state: 'accepted', title: '冷氣不冷')],
        ),
      );

      expect(find.text('冷氣不冷'), findsOneWidget);
      expect(find.text('房務報修'), findsOneWidget);
      expect(find.text('W-0412 · 401'), findsNothing);
    });

    testWidgets('單號仍然是這一列的定位節點，BDD 照原本的 id 點得到', (tester) async {
      final handle = tester.ensureSemantics();
      await pump(tester, _today(tickets: [_ticket(state: 'accepted')]));

      expect(
        find.bySemanticsIdentifier('engHome.in-hand.W-0412.card'),
        findsOneWidget,
      );
      handle.dispose();
    });
  });
}

EngineeringTicket _ticket({
  required String state,
  String title = '冷氣不冷',
  String? roomNo = '401',
  bool isPublicArea = false,
  bool isUrgent = false,
  int? workedMinutes,
}) {
  return EngineeringTicket(
    ticketNo: 'W-0412',
    taskId: 'ticket-412',
    roomNo: roomNo,
    isPublicArea: isPublicArea,
    title: title,
    state: state,
    isUrgent: isUrgent,
    source: 'staff_report',
    workedMinutes: workedMinutes,
  );
}

MyToday _today({
  int openCount = 3,
  int completedToday = 0,
  int urgentCount = 0,
  int? maintenanceDone,
  int? maintenanceTotal,
  List<EngineeringTicket> tickets = const <EngineeringTicket>[],
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
      urgentCount: urgentCount,
      maintenanceDone: maintenanceDone,
      maintenanceTotal: maintenanceTotal,
      tickets: tickets,
    ),
  );
}

class _FakeApiClient extends ApiClient {
  _FakeApiClient(this._today);

  final MyToday _today;

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
  Future<MyToday> getMyToday() async => _today;
}
