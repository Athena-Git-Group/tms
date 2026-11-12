import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_category_square.dart';
import 'package:tms_staff_app/features/home/home_screen.dart';
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

  Future<void> pumpHome(
    WidgetTester tester,
    MyToday today, {
    String? staffName,
  }) async {
    ApiClient.instance = _FakeApiClient(today, staffName);
    await tester.pumpWidget(
      const CupertinoApp(home: MyTodayScope(child: HomeScreen())),
    );
    await tester.pumpAndSettle();
  }

  String dateText() {
    const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
    final now = DateTime.now();
    return '週${weekdays[now.weekday - 1]} ${now.month}/${now.day}';
  }

  double topOf(WidgetTester tester, Finder finder) =>
      tester.getTopLeft(finder).dy;

  group('問候列（Main.dc.html:126-131）', () {
    testWidgets('日期列在上、問候語在下，班別那一段加粗轉黑', (tester) async {
      await pumpHome(
        tester,
        _today(shift: 'morning', floors: const [3]),
        staffName: '阿美',
      );

      final dateLine = find.textContaining(dateText());
      expect(dateLine, findsOneWidget);
      expect(
        tester.widget<Text>(dateLine).textSpan!.toPlainText(),
        '${dateText()} · 早班 · 3 樓',
      );
      expect(
        topOf(tester, dateLine),
        lessThan(topOf(tester, find.text('早安，阿美'))),
      );

      final shiftSpans = <TextSpan>[];
      tester.widget<Text>(dateLine).textSpan!.visitChildren((span) {
        if (span is TextSpan && span.text == '早班') {
          shiftSpans.add(span);
        }
        return true;
      });
      expect(shiftSpans, hasLength(1));
      expect(shiftSpans.single.style!.fontWeight, FontWeight.w600);
      expect(shiftSpans.single.style!.color, TmsColors.label);
    });

    testWidgets('頭像裡是姓名的末字，不是空色塊', (tester) async {
      await pumpHome(tester, _today(), staffName: '阿美');

      expect(find.text('美'), findsOneWidget);
      expect(
        tester.widget<Text>(find.text('美')).style!.fontWeight,
        FontWeight.w800,
      );
    });

    testWidgets('手上還有沒做完的單時是「早安」（Main.dc.html:5108）', (tester) async {
      await pumpHome(
        tester,
        _today(todayTasks: [_task(state: 'deferred')]),
        staffName: '阿美',
      );

      expect(find.text('早安，阿美'), findsOneWidget);
    });

    testWidgets('手上的單全部做完才換成「辛苦了」（Main.dc.html:5108）', (tester) async {
      await pumpHome(
        tester,
        _today(todayTasks: [_task(state: 'inspected')]),
        staffName: '阿美',
      );

      expect(find.text('辛苦了，阿美'), findsOneWidget);
    });

    testWidgets('登入身分沒有姓名時只留招呼，不補一個稱呼', (tester) async {
      await pumpHome(tester, _today());

      expect(find.text('早安'), findsOneWidget);
    });
  });

  group('進度卡（Main.dc.html:133-166）', () {
    testWidgets('預估剩餘工時獨佔一行、單位是「分鐘」，另外兩格並排', (tester) async {
      await pumpHome(tester, _today());

      expect(find.textContaining('分鐘'), findsOneWidget);
      final remaining = find.text('預估剩餘工時');
      final inspected = find.text('查房通過');
      final adHoc = find.text('臨時需求完成');
      expect(topOf(tester, remaining), lessThan(topOf(tester, inspected)));
      expect(topOf(tester, inspected), topOf(tester, adHoc));
    });

    testWidgets('臨時需求完成的值後面接「單」（Main.dc.html:159）', (tester) async {
      await pumpHome(tester, _today());

      expect(find.text('3 單'), findsOneWidget);
    });

    testWidgets('卡底沒有連續零退回天數那一列', (tester) async {
      await pumpHome(tester, _today());

      expect(find.text('連續零退回天數'), findsNothing);
      expect(find.bySemanticsIdentifier('home.streak-badge'), findsNothing);
    });
  });

  group('主卡位置的空狀態（Main.dc.html:213-219、5114-5117）', () {
    testWidgets('手上沒有任何單', (tester) async {
      await pumpHome(tester, _today());

      expect(find.text('手上沒有任務'), findsOneWidget);
      expect(find.text('新需求進來時會推播通知你。'), findsOneWidget);
    });

    testWidgets('待認領池還有單時，說明改成指路到待認領', (tester) async {
      await pumpHome(tester, _today(poolBadgeCount: 4));

      expect(find.text('到「待認領」認領新的單。'), findsOneWidget);
    });

    testWidgets('今天的單都做完了，圖示用成功綠', (tester) async {
      await pumpHome(
        tester,
        _today(
          todayTasks: [
            _task(state: 'done'),
            _task(roomNo: '302', state: 'inspected'),
          ],
        ),
      );

      expect(find.text('今天的任務都完成了'), findsOneWidget);
      expect(find.text('今天完成 2 單，新需求進來時會推播通知你。'), findsOneWidget);
      final icon = tester.widget<Icon>(
        find.byIcon(CupertinoIcons.check_mark_circled),
      );
      expect(icon.color, TmsColors.systemGreen);
    });

    testWidgets('只剩延後的單時指路到下方的我的任務', (tester) async {
      await pumpHome(tester, _today(todayTasks: [_task(state: 'deferred')]));

      expect(find.text('目前沒有可以開始的單'), findsOneWidget);
      expect(find.text('延後的單在下方「我的任務」，準備好再開始。'), findsOneWidget);
    });
  });

  group('下一間卡（Main.dc.html:193-212）', () {
    testWidgets('副標接預估分鐘，主按鈕帶房號與播放三角', (tester) async {
      await pumpHome(
        tester,
        _today(
          todayTasks: [_task(roomNo: '305', state: 'accepted', estMin: 35)],
          nextTasks: const [
            NextTask(
              taskId: 't-305',
              roomNo: '305',
              title: '退房清潔',
              type: 'cleaning',
              redoCount: 0,
            ),
          ],
        ),
      );

      expect(find.text('退房清潔 · 預估 35 分'), findsOneWidget);
      expect(find.text('開始打掃 305'), findsWidgets);
      expect(find.byIcon(CupertinoIcons.play_fill), findsOneWidget);
      expect(find.byIcon(CupertinoIcons.bed_double), findsOneWidget);
    });

    testWidgets('被查房退回過的房多一顆紅底標籤（Main.dc.html:200）', (tester) async {
      await pumpHome(
        tester,
        _today(
          todayTasks: [_task(roomNo: '305', state: 'accepted', redoCount: 2)],
          nextTasks: const [
            NextTask(
              taskId: 't-305',
              roomNo: '305',
              title: '退房清潔',
              type: 'cleaning',
              redoCount: 2,
            ),
          ],
        ),
      );

      expect(find.text('查房退回 · 第 2 次重做'), findsOneWidget);
    });
  });

  group('進行中卡（Main.dc.html:169-192）', () {
    testWidgets('小標接任務類別，房號、標題與計時同一列', (tester) async {
      await pumpHome(
        tester,
        _today(
          todayTasks: [
            _task(roomNo: '305', state: 'doing', estMin: 35, workedMinutes: 12),
          ],
        ),
      );

      expect(find.text('進行中 · 打掃'), findsOneWidget);
      final heroRoomNo = find.byWidgetPredicate(
        (widget) =>
            widget is Text &&
            widget.data == '305' &&
            widget.style?.fontSize == 44,
      );
      expect(
        tester.getBottomLeft(find.text('12 分')).dy,
        tester.getBottomLeft(heroRoomNo).dy,
      );
    });
  });

  group('我的任務列（Main.dc.html:224-244）', () {
    testWidgets('左方塊放房號，主文只有標題，右邊是排序說明', (tester) async {
      await pumpHome(
        tester,
        _today(
          todayTasks: [_task(roomNo: '305', state: 'accepted', estMin: 35)],
        ),
      );

      expect(find.text('依樓層排序'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(TmsCategorySquare),
          matching: find.text('305'),
        ),
        findsOneWidget,
      );
      expect(find.text('退房清潔'), findsOneWidget);
      expect(find.text('305 退房清潔'), findsNothing);
    });

    testWidgets('副標依狀態換說法（Main.dc.html:4215-4224）', (tester) async {
      await pumpHome(
        tester,
        _today(
          todayTasks: [
            _task(roomNo: '301', state: 'accepted', estMin: 35),
            _task(
              roomNo: '302',
              state: 'accepted',
              estMin: 35,
              inspectionWaived: true,
            ),
            _task(roomNo: '303', state: 'doing', estMin: 20, workedMinutes: 26),
            _task(
              roomNo: '304',
              state: 'interrupted',
              estMin: 35,
              workedMinutes: 8,
              holdReason: '等工務：冷氣',
            ),
            _task(
              roomNo: '305',
              state: 'deferred',
              estMin: 35,
              holdReason: '住客請勿打擾',
            ),
            _task(
              roomNo: '306',
              state: 'inspected',
              estMin: 35,
              workedMinutes: 31,
            ),
            _task(roomNo: '307', state: 'accepted', type: 'amenity', estMin: 5),
          ],
        ),
      );

      expect(find.text('約 35 分'), findsOneWidget);
      expect(find.text('約 35 分 · 免查房'), findsOneWidget);
      expect(find.text('已進行 26 分 · 超時 6 分'), findsOneWidget);
      expect(find.text('已做 8 分 · 等工務：冷氣'), findsOneWidget);
      expect(find.text('住客請勿打擾'), findsOneWidget);
      expect(find.text('實際 31 分 · 預估 35 分'), findsOneWidget);
      expect(find.text('臨時需求'), findsOneWidget);
    });
  });
}

TodayTask _task({
  String roomNo = '301',
  String title = '退房清潔',
  String type = 'cleaning',
  String state = 'accepted',
  int redoCount = 0,
  int? estMin,
  int workedMinutes = 0,
  String? holdReason,
  bool inspectionWaived = false,
}) {
  return TodayTask(
    taskId: 't-$roomNo',
    roomNo: roomNo,
    title: title,
    type: type,
    state: state,
    redoCount: redoCount,
    estMin: estMin,
    workedMinutes: workedMinutes,
    holdReason: holdReason,
    inspection: null,
    inspectionWaived: inspectionWaived,
  );
}

MyToday _today({
  List<TodayTask> todayTasks = const <TodayTask>[],
  List<NextTask> nextTasks = const <NextTask>[],
  String? shift,
  List<int>? floors,
  int poolBadgeCount = 0,
}) {
  return MyToday(
    cleaningDone: 2,
    cleaningTotal: 7,
    inspectedCount: 1,
    completedCount: 2,
    adHocCompleted: 3,
    remainingMinutes: 135,
    todayTasks: todayTasks,
    nextTasks: nextTasks,
    shift: shift,
    floors: floors,
    poolBadgeCount: poolBadgeCount,
  );
}

class _FakeApiClient extends ApiClient {
  _FakeApiClient(this.today, this.staffName);

  final MyToday today;
  final String? staffName;

  @override
  LoginResult? get session {
    final name = staffName;
    if (name == null) {
      return null;
    }
    return LoginResult(
      token: 'token',
      expiresAt: '2026-11-09T00:00:00Z',
      staffId: 'staff-1',
      name: name,
      role: 'staff',
      mustChangePassword: false,
    );
  }

  @override
  Future<MyToday> getMyToday() async => today;
}
