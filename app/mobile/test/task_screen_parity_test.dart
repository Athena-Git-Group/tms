import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/test_backdoor.dart';
import 'package:tms_staff_app/core/widgets/tms_callout.dart';
import 'package:tms_staff_app/core/widgets/tms_fab.dart';
import 'package:tms_staff_app/core/widgets/tms_tip_popover.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

/// 任務頁對齊設計原型的幾件事：狀態列一頁只畫一則、SOP 清單的勾選框與跳過
/// 條件、進度分子只數勾選的、「這間房的資訊」最多兩列。
///
/// 這幾條互相牽動：進度分子與置底那行「還有 N 項沒勾」數的是同一批項目，狀態
/// 列的優先序決定說明泡泡要不要讓位。放在同一支測試裡，動其中一邊時另一邊走
/// 樣會當場被讀出來。
void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  Future<void> pumpTask(
    WidgetTester tester, {
    required TaskDetail task,
    List<SopItem> sopItems = const <SopItem>[],
    List<RepairTicketSummary> tickets = const <RepairTicketSummary>[],
  }) async {
    ApiClient.instance = _FakeApiClient(
      task: task,
      sopItems: sopItems,
      tickets: tickets,
    );
    await tester.pumpWidget(
      CupertinoApp(
        // 同一支測試連續 pump 兩種狀態時換一把 key：沒換的話 Flutter 認得同一個
        // State，initState 不會再跑一次，第二次讀到的還是第一次那筆任務。
        home: TaskScreen(
          key: UniqueKey(),
          roomNo: task.roomNo ?? '305',
          taskId: task.id,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  group('狀態列（Main.dc.html:1221-1223、4306-4324）', () {
    testWidgets('原型拿掉的狀態 chip 不出現', (tester) async {
      await pumpTask(tester, task: _task(state: 'doing'));

      expect(find.bySemanticsIdentifier('task.status-chip'), findsNothing);
    });

    testWidgets('退回與停住同時成立時只畫退回那一則', (tester) async {
      await pumpTask(
        tester,
        task: _task(
          state: 'accepted',
          holdReason: '等工務：水電',
          returnInfo: const ReturnInfo(summary: '查房退回：換床單鋪床不合格 · 第 1 次重做'),
        ),
      );

      expect(find.byType(TmsCalloutSlot), findsOneWidget);
      expect(
        find.bySemanticsIdentifier('task.status-row.return-info'),
        findsOneWidget,
      );
      expect(
        find.bySemanticsIdentifier('task.status-row.hold-reason'),
        findsNothing,
      );
    });

    testWidgets('停住的單以停住原因當標題，說明講接下來能做什麼', (tester) async {
      await pumpTask(
        tester,
        task: _task(state: 'deferred', holdReason: '住客還在房裡'),
      );

      expect(find.text('住客還在房裡'), findsOneWidget);
      expect(find.textContaining('準備好再按「開始打掃」直接開工。'), findsOneWidget);
    });

    testWidgets('做完等查房與查房通過各有自己的狀態列', (tester) async {
      await pumpTask(tester, task: _task(state: 'done'));
      expect(find.text('等主管查房'), findsOneWidget);

      await pumpTask(tester, task: _task(state: 'inspected'));
      expect(find.text('查房通過'), findsOneWidget);
    });

    testWidgets('待開始而且沒有別的狀況時，畫的是開工前的固定教學', (tester) async {
      await pumpTask(tester, task: _task(state: 'accepted'));

      expect(find.text('到房間門口再按開始'), findsOneWidget);
    });
  });

  group('SOP 清單（Main.dc.html:1300-1326、4285-4291）', () {
    final sopItems = <SopItem>[
      const SopItem(
        id: 's1',
        appliesTo: 'cleaning',
        itemKey: 'bath',
        label: '浴室刷洗',
      ),
      const SopItem(
        id: 's2',
        appliesTo: 'cleaning',
        itemKey: 'bed',
        label: '換床單',
      ),
      const SopItem(
        id: 's3',
        appliesTo: 'cleaning',
        itemKey: 'floor',
        label: '吸地',
      ),
    ];

    testWidgets('進度分子只數勾選的，跳過不計入', (tester) async {
      await pumpTask(
        tester,
        task: _task(
          state: 'doing',
          checkedSopItems: const ['s1'],
          skippedSopItems: const ['s2'],
        ),
        sopItems: sopItems,
      );

      expect(find.text('1 / 3 · 跳過不擋流程'), findsOneWidget);
    });

    testWidgets('置底的提示數的是沒勾的項目，跳過的也算沒勾', (tester) async {
      await pumpTask(
        tester,
        task: _task(
          state: 'doing',
          checkedSopItems: const ['s1'],
          skippedSopItems: const ['s2'],
        ),
        sopItems: sopItems,
      );

      expect(find.text('還有 2 項沒勾，完成後會記錄為跳過'), findsOneWidget);
    });

    testWidgets('跳過的項目畫刪除線', (tester) async {
      await pumpTask(
        tester,
        task: _task(state: 'doing', skippedSopItems: const ['s2']),
        sopItems: sopItems,
      );

      final label = tester.widget<Text>(find.text('換床單'));
      expect(label.style?.decoration, TextDecoration.lineThrough);
    });

    testWidgets('已經勾起來的項目不給跳過鈕', (tester) async {
      await pumpTask(
        tester,
        task: _task(state: 'doing', checkedSopItems: const ['s1']),
        sopItems: sopItems,
      );

      expect(
        find.bySemanticsIdentifier('task.sop.浴室刷洗.skip-button'),
        findsNothing,
      );
      expect(
        find.bySemanticsIdentifier('task.sop.換床單.skip-button'),
        findsOneWidget,
      );
    });

    testWidgets('不是進行中時整列點不動，也沒有跳過鈕', (tester) async {
      await pumpTask(
        tester,
        task: _task(state: 'accepted'),
        sopItems: sopItems,
      );

      expect(
        find.bySemanticsIdentifier('task.sop.浴室刷洗.skip-button'),
        findsNothing,
      );
      final button = tester.widget<CupertinoButton>(
        find.descendant(
          of: find.bySemanticsIdentifier('task.sop.浴室刷洗.item'),
          matching: find.byType(CupertinoButton),
        ),
      );
      expect(button.onPressed, isNull);
    });

    testWidgets('查房通過的單整個 SOP 區不畫', (tester) async {
      await pumpTask(
        tester,
        task: _task(state: 'inspected'),
        sopItems: sopItems,
      );

      expect(find.text('清潔 SOP'), findsNothing);
    });
  });

  group('這間房的資訊（Main.dc.html:1284-1298、4340-4349）', () {
    testWidgets('工務單不管幾張都只佔一列，右側寫摘要', (tester) async {
      await pumpTask(
        tester,
        task: _task(state: 'doing'),
        tickets: <RepairTicketSummary>[
          _ticket(ticketNo: 'W-0410', state: 'accepted'),
          _ticket(ticketNo: 'W-0411', state: 'done'),
        ],
      );

      expect(find.text('工務單'), findsOneWidget);
      expect(find.text('2 張・1 張未結案'), findsOneWidget);
      expect(find.text('工務單詳情'), findsNothing);
    });

    testWidgets('只有一張時右側寫單號、類別與目前階段', (tester) async {
      await pumpTask(
        tester,
        task: _task(state: 'doing'),
        tickets: <RepairTicketSummary>[
          _ticket(ticketNo: 'W-0410', state: 'accepted'),
        ],
      );

      expect(find.text('W-0410 冷氣・已接單'), findsOneWidget);
    });

    testWidgets('兩類都沒有內容時整區不畫', (tester) async {
      await pumpTask(tester, task: _task(state: 'doing'));

      expect(find.text('這間房的資訊'), findsNothing);
    });

    testWidgets('看過的交辦收成一列，沒看過的升到頁面上', (tester) async {
      final handovers = <HandoverEntry>[
        HandoverEntry(
          kind: 'shift_handover',
          author: '王姐',
          content: 'VIP 下午 3 點入住',
          at: DateTime(2026, 11, 8, 7, 55),
        ),
      ];

      await pumpTask(
        tester,
        task: _task(state: 'accepted', handovers: handovers),
      );
      expect(find.text('交辦 1 則 · 開工前請先看'), findsOneWidget);
      expect(
        find.bySemanticsIdentifier('task.info-row.handovers'),
        findsNothing,
      );

      await pumpTask(
        tester,
        task: _task(state: 'doing', handovers: handovers, handoverRead: true),
      );
      expect(find.text('交辦 1 則 · 開工前請先看'), findsNothing);
      expect(find.text('1 則'), findsOneWidget);
    });
  });

  group('標題區與導覽列（Main.dc.html:1147-1219、4351）', () {
    testWidgets('任務名稱不重複房號', (tester) async {
      await pumpTask(
        tester,
        task: _task(state: 'doing', title: '305 退房打掃'),
      );

      expect(find.text('305 · 退房打掃'), findsOneWidget);
    });

    testWidgets('免查房與停住期間的交辦都寫進標題底下那一行', (tester) async {
      await pumpTask(
        tester,
        task: _task(
          state: 'deferred',
          inspectionWaived: true,
          holdReason: '住客還在房裡',
          handovers: <HandoverEntry>[
            const HandoverEntry(
              kind: 'desk_note',
              author: '小葉',
              content: '11:00 前請勿打擾',
            ),
          ],
        ),
      );

      expect(find.text('排房指派 · 預估 35 分 · 免查房 · 有交代'), findsOneWidget);
    });

    testWidgets('已延後的單不畫計時，已中斷的單畫計時與已暫停那一行', (tester) async {
      await pumpTask(
        tester,
        task: _task(state: 'deferred', holdReason: '住客還在房裡'),
      );
      expect(
        find.bySemanticsIdentifier('task.hero.paused-elapsed'),
        findsNothing,
      );

      await pumpTask(
        tester,
        task: _task(
          state: 'interrupted',
          holdReason: '等工務：水電',
          accumulatedSeconds: 754,
        ),
      );
      expect(find.text('12:34'), findsOneWidget);
      expect(find.text('已暫停 · 預估 35 分'), findsOneWidget);
    });

    testWidgets('作廢的單不給報修鈕也不給查一下', (tester) async {
      await pumpTask(tester, task: _task(state: 'cancelled'));

      expect(find.bySemanticsIdentifier('task.repair-button'), findsNothing);
      expect(find.bySemanticsIdentifier('task.ask-button'), findsNothing);
    });
  });

  group('導覽列計時（Main.dc.html:5036）', () {
    testWidgets('捲過標題區之後，導覽列上的計時跟著標題區同一支計時器逐秒跳', (tester) async {
      addTearDown(TestHooks.instance.reset);
      final startedAt = DateTime(2026, 11, 9, 9);
      TestHooks.instance.injectedClockIso = startedAt
          .add(const Duration(minutes: 12, seconds: 5))
          .toIso8601String();

      ApiClient.instance = _FakeApiClient(
        task: _task(
          state: 'doing',
          accumulatedSeconds: 0,
          segmentStartedAt: startedAt,
        ),
        // 內容要長到捲得動，導覽列才會換成捲動後那一組標題。
        sopItems: <SopItem>[
          for (var i = 0; i < 8; i++)
            SopItem(
              id: 's$i',
              appliesTo: 'cleaning',
              itemKey: 'k$i',
              label: '項目 $i',
            ),
        ],
        tickets: const <RepairTicketSummary>[],
      );
      await tester.pumpWidget(
        const CupertinoApp(
          home: TaskScreen(roomNo: '305', taskId: 't-305'),
        ),
      );
      await tester.pump();
      await tester.pump();

      await tester.drag(
        find.byType(SingleChildScrollView),
        const Offset(0, -200),
      );
      await tester.pump();
      expect(find.text('305 · 12:05'), findsOneWidget);

      TestHooks.instance.injectedClockIso = startedAt
          .add(const Duration(minutes: 12, seconds: 6))
          .toIso8601String();
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('305 · 12:06'), findsOneWidget);

      // 離開畫面讓那支每秒醒來的計時器停掉，否則 flutter test 會判定有計時器
      // 沒取消。
      await tester.pumpWidget(const CupertinoApp(home: SizedBox.shrink()));
      await tester.pump(const Duration(seconds: 2));
    });
  });

  group('查一下（Main.dc.html:1330-1348、4436）', () {
    testWidgets('是圖示浮動鈕，第一次進來還帶一顆說明泡泡', (tester) async {
      await pumpTask(tester, task: _task(state: 'doing'));

      expect(find.byType(TmsFab), findsOneWidget);
      expect(find.text('查一下'), findsNothing);
      expect(find.byType(TmsTipPopover), findsOneWidget);
    });

    testWidgets('有要處理的狀態列時，說明泡泡讓位', (tester) async {
      await pumpTask(
        tester,
        task: _task(
          state: 'accepted',
          returnInfo: const ReturnInfo(summary: '查房退回：床鋪不合格 · 第 1 次重做'),
        ),
      );

      expect(find.byType(TmsTipPopover), findsNothing);
    });
  });
}

TaskDetail _task({
  required String state,
  String type = 'cleaning',
  String title = '305 退房打掃',
  int? estMin = 35,
  int elapsedMinutes = 12,
  int? accumulatedSeconds,
  DateTime? segmentStartedAt,
  bool inspectionWaived = false,
  String? holdReason,
  List<String> checkedSopItems = const <String>[],
  List<String> skippedSopItems = const <String>[],
  List<HandoverEntry> handovers = const <HandoverEntry>[],
  bool handoverRead = false,
  ReturnInfo? returnInfo,
}) {
  return TaskDetail(
    id: 't-305',
    state: state,
    type: type,
    roomId: 'room-305',
    roomNo: '305',
    title: title,
    estMin: estMin,
    elapsedMinutes: elapsedMinutes,
    accumulatedSeconds: accumulatedSeconds,
    segmentStartedAt: segmentStartedAt,
    inspectionWaived: inspectionWaived,
    holdReason: holdReason,
    checkedSopItems: checkedSopItems,
    skippedSopItems: skippedSopItems,
    handovers: handovers,
    handoverRead: handoverRead,
    returnInfo: returnInfo,
  );
}

RepairTicketSummary _ticket({
  required String ticketNo,
  required String state,
  bool isUrgent = false,
}) {
  return RepairTicketSummary(
    taskId: 'task-$ticketNo',
    ticketNo: ticketNo,
    roomNo: '305',
    category: 'air_conditioning',
    isUrgent: isUrgent,
    state: state,
    reporterName: '小林',
  );
}

/// 記憶體 fake：固定回同一筆任務、同一份 SOP 與同一批工務單。
class _FakeApiClient extends ApiClient {
  _FakeApiClient({
    required this.task,
    required this.sopItems,
    required this.tickets,
  });

  final TaskDetail task;
  final List<SopItem> sopItems;
  final List<RepairTicketSummary> tickets;

  @override
  Future<TaskDetail> getTask(String taskId) async => task;

  @override
  Future<List<SopItem>> listSopItems() async => sopItems;

  @override
  Future<List<RepairTicketSummary>> listRepairTickets(String roomId) async =>
      tickets;

  @override
  Future<TaskDetail> sopCheck(
    String taskId, {
    required String sopItemId,
    required String result,
  }) async => task;
}
