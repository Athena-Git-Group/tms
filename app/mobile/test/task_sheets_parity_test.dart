import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/widgets/tms_option_row.dart';
import 'package:tms_staff_app/core/widgets/tms_pill_button.dart';
import 'package:tms_staff_app/core/widgets/tms_sheet.dart';
import 'package:tms_staff_app/features/task/ask_sheet.dart';
import 'package:tms_staff_app/features/task/hold_sheet.dart';
import 'package:tms_staff_app/features/task/repair_sheet.dart';

void main() {
  setUp(() {
    // 畫面上有無限循環的動畫時，`pumpAndSettle` 會等到逾時。
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

  group('延後／中斷面板（Main.dc.html:1411-1565、4928-4954）', () {
    testWidgets('第一步的標題是問句，而且沒有置底確認鈕', (tester) async {
      await _pumpHoldSheet(tester, mode: HoldSheetMode.defer);

      expect(find.text('為什麼要延後？'), findsOneWidget);
      expect(find.byType(TmsSheetButton), findsNothing);
    });

    testWidgets('點一列預設原因就直接送出，不必再按確認', (tester) async {
      final api = _FakeApiClient();
      final result = await _pumpHoldSheet(
        tester,
        mode: HoldSheetMode.defer,
        api: api,
        act: (tester) async {
          await tester.tap(find.text('住客還在房內'));
          await tester.pumpAndSettle();
        },
      );

      expect(api.deferredReason, '住客還在房內');
      expect(result, isA<HoldSheetSubmitted>());
    });

    testWidgets('原因列上沒有勾', (tester) async {
      await _pumpHoldSheet(tester, mode: HoldSheetMode.defer);

      expect(find.byType(TmsOptionCheck), findsNothing);
    });

    testWidgets('選其他原因換到第二步：清單收起、標題與置底鈕都換掉', (tester) async {
      await _pumpHoldSheet(
        tester,
        mode: HoldSheetMode.defer,
        act: (tester) async {
          await tester.tap(find.text('其他原因'));
          await tester.pumpAndSettle();
        },
      );

      expect(find.text('請勿打擾'), findsNothing);
      expect(find.text('其他原因'), findsOneWidget);
      expect(find.text('請簡單說明原因'), findsOneWidget);
      expect(find.text('確定延後'), findsOneWidget);
      expect(find.text('0 / 60'), findsOneWidget);
    });

    testWidgets('中斷模式選需要工務協助，面板讓位給工務單表單', (tester) async {
      final result = await _pumpHoldSheet(
        tester,
        mode: HoldSheetMode.interrupt,
        act: (tester) async {
          await tester.tap(find.text('需要工務協助'));
          await tester.pumpAndSettle();
        },
      );

      expect(result, isA<HoldSheetNeedsMaintenance>());
    });

    testWidgets('中斷的置底鈕寫確定中斷', (tester) async {
      await _pumpHoldSheet(
        tester,
        mode: HoldSheetMode.interrupt,
        act: (tester) async {
          await tester.tap(find.text('其他原因'));
          await tester.pumpAndSettle();
        },
      );

      expect(find.text('確定中斷'), findsOneWidget);
    });
  });

  group('報修面板（Main.dc.html:1455-1550、4938-4941）', () {
    testWidgets('有狀況說明欄位，附欄位標籤與字數', (tester) async {
      await _pumpRepairSheet(tester);

      expect(find.text('狀況說明（選填）'), findsOneWidget);
      expect(find.text('例如：冷氣開了不冷，出風口有滴水'), findsOneWidget);
      expect(find.text('0 / 60'), findsOneWidget);
    });

    testWidgets('表單上沒有中斷開關，要不要中斷由進來的路徑決定', (tester) async {
      await _pumpRepairSheet(tester);

      expect(find.text('中斷 · 需要工務協助'), findsNothing);
    });

    testWidgets('還沒選類別時說判定要等什麼，沒有照片時說為什麼非拍不可', (tester) async {
      await _pumpRepairSheet(tester);

      expect(find.text('選好類別後，系統會判定是不是急件。'), findsOneWidget);
      expect(find.textContaining('至少 1 張照片才能送出'), findsOneWidget);
    });

    testWidgets('一般件的置底鈕寫送出給主管審核，底色是品牌色', (tester) async {
      await _pumpRepairSheet(
        tester,
        precheck: const RepairTicketPrecheck(urgent: false),
        act: (tester) async {
          await tester.tap(find.text('冷氣'));
          await tester.pumpAndSettle();
        },
      );

      expect(find.text('送出給主管審核'), findsOneWidget);
      expect(
        tester.widget<TmsSheetButton>(find.byType(TmsSheetButton)).style,
        TmsPillButtonStyle.filled,
      );
      expect(find.text('系統判定：一般件'), findsOneWidget);
    });

    testWidgets('急件的置底鈕換文案也換成深紅底', (tester) async {
      await _pumpRepairSheet(
        tester,
        precheck: const RepairTicketPrecheck(
          urgent: true,
          urgentRule: '退房前 2 小時',
        ),
        act: (tester) async {
          await tester.tap(find.text('冷氣'));
          await tester.pumpAndSettle();
        },
      );

      expect(find.text('送出急件（直接給工務）'), findsOneWidget);
      expect(
        tester.widget<TmsSheetButton>(find.byType(TmsSheetButton)).style,
        TmsPillButtonStyle.urgentFilled,
      );
      expect(find.text('系統判定：急件 · 退房前 2 小時'), findsOneWidget);
      expect(find.text('急件由系統依飯店設定的規則判定，房務不能更改。判錯時由主管修正。'), findsOneWidget);
    });

    testWidgets('照片欄位標題寫必填', (tester) async {
      await _pumpRepairSheet(tester);

      expect(find.text('照片（必填，至少 1 張、最多 3 張）'), findsOneWidget);
    });

    testWidgets('重複報修的按鈕不重複講單號', (tester) async {
      await _pumpRepairSheet(
        tester,
        precheck: const RepairTicketPrecheck(
          urgent: false,
          duplicate: RepairTicketSummary(
            taskId: 'task-W-0410',
            ticketNo: 'W-0410',
            roomNo: '305',
            category: 'air_conditioning',
            isUrgent: false,
            state: 'in_progress',
            reporterName: '小林',
          ),
        ),
        act: (tester) async {
          await tester.tap(find.text('冷氣'));
          await tester.pumpAndSettle();
        },
      );

      expect(find.text('補充照片到這張單'), findsOneWidget);
    });
  });

  group('查一下面板（Main.dc.html:1570-1676、5130-5156）', () {
    testWidgets('第一步帶房號，只有建議問題與一行說明，沒有置底鈕', (tester) async {
      await _pumpAskSheet(tester);

      expect(find.text('查一下 · 305'), findsOneWidget);
      expect(find.text('答案只從飯店的 SOP 和這間房的記錄整理，並附上出處。'), findsOneWidget);
      expect(find.byType(TmsSheetButton), findsNothing);
    });

    testWidgets('點一則問題先進載入態：問題氣泡與骨架條都在，清單收起', (tester) async {
      final api = _FakeApiClient(answer: _foundAnswer);
      api.holdAnswer = true;
      await _pumpAskSheet(
        tester,
        api: api,
        act: (tester) async {
          await tester.tap(find.text('305 最近修過什麼？'));
          await tester.pumpAndSettle();
        },
      );

      expect(find.bySemanticsIdentifier('ask.question-bubble'), findsOneWidget);
      expect(find.text('正在查 SOP 和這間房的記錄…'), findsOneWidget);
      expect(find.text('回到任務'), findsOneWidget);
      api.releaseAnswer();
      await tester.pumpAndSettle();
    });

    testWidgets('查到答案時出處帶類型膠囊，並附 AI 免責行與回饋列', (tester) async {
      await _pumpAskSheet(
        tester,
        api: _FakeApiClient(answer: _foundAnswer),
        act: (tester) async {
          await tester.tap(find.text('305 最近修過什麼？'));
          await tester.pumpAndSettle();
        },
      );

      expect(find.text('出處'), findsOneWidget);
      expect(find.text('房間記錄'), findsOneWidget);
      expect(find.text('10/02'), findsOneWidget);
      expect(find.text('答案由 AI 根據上面的資料整理，請以出處內容為準。'), findsOneWidget);
      expect(find.text('這個答案有幫助嗎？'), findsOneWidget);
      expect(find.text('有幫助'), findsOneWidget);
    });

    testWidgets('回饋按下去就換成謝謝，不再問第二次', (tester) async {
      final api = _FakeApiClient(answer: _foundAnswer);
      await _pumpAskSheet(
        tester,
        api: api,
        act: (tester) async {
          await tester.tap(find.text('305 最近修過什麼？'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('有幫助'));
          await tester.pumpAndSettle();
        },
      );

      expect(find.text('謝謝，已記錄。'), findsOneWidget);
      expect(find.text('有幫助'), findsNothing);
      expect(api.feedbackHelpful, isTrue);
    });

    testWidgets('查不到時整張卡說明原因，問主管在卡裡而不是置底', (tester) async {
      await _pumpAskSheet(
        tester,
        api: _FakeApiClient(answer: _notFoundAnswer),
        act: (tester) async {
          await tester.tap(find.text('305 最近修過什麼？'));
          await tester.pumpAndSettle();
        },
      );

      expect(find.text('查不到 305 的相關記錄'), findsOneWidget);
      expect(find.text('可能是沒有記錄，或資料還在更新。系統不會用猜的回答。'), findsOneWidget);
      expect(find.text('問主管'), findsOneWidget);
      expect(find.text('這個答案有幫助嗎？'), findsNothing);
      expect(
        tester.widget<TmsSheetButton>(find.byType(TmsSheetButton)).label,
        '回到任務',
      );
    });

    testWidgets('問主管送出後原地換成已傳給主管', (tester) async {
      final api = _FakeApiClient(answer: _notFoundAnswer);
      await _pumpAskSheet(
        tester,
        api: api,
        act: (tester) async {
          await tester.tap(find.text('305 最近修過什麼？'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('問主管'));
          await tester.pumpAndSettle();
        },
      );

      expect(find.text('已傳給主管，回覆會用推播通知你'), findsOneWidget);
      expect(find.text('問主管'), findsNothing);
      expect(api.escalatedAnswerId, 'a-1');
    });
  });
}

Future<HoldSheetResult?> _pumpHoldSheet(
  WidgetTester tester, {
  required HoldSheetMode mode,
  _FakeApiClient? api,
  Future<void> Function(WidgetTester tester)? act,
}) async {
  ApiClient.instance = api ?? _FakeApiClient();
  HoldSheetResult? result;
  await tester.pumpWidget(
    CupertinoApp(
      home: Builder(
        builder: (context) => CupertinoButton(
          onPressed: () async {
            result = await HoldSheet.show(context, mode: mode, taskId: 't-305');
          },
          child: const Text('開'),
        ),
      ),
    ),
  );
  await tester.tap(find.text('開'));
  await tester.pumpAndSettle();
  await act?.call(tester);
  return result;
}

Future<void> _pumpRepairSheet(
  WidgetTester tester, {
  RepairTicketPrecheck? precheck,
  Future<void> Function(WidgetTester tester)? act,
}) async {
  ApiClient.instance = _FakeApiClient(precheck: precheck);
  await tester.pumpWidget(
    CupertinoApp(
      home: Align(
        alignment: Alignment.bottomCenter,
        child: RepairSheet(
          roomNo: '305',
          sourceTaskId: 't-305',
          onClose: () {},
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  await act?.call(tester);
}

Future<void> _pumpAskSheet(
  WidgetTester tester, {
  _FakeApiClient? api,
  Future<void> Function(WidgetTester tester)? act,
}) async {
  ApiClient.instance = api ?? _FakeApiClient();
  await tester.pumpWidget(
    CupertinoApp(
      home: Builder(
        builder: (context) => CupertinoButton(
          onPressed: () =>
              AskSheet.show(context, taskId: 't-305', roomNo: '305'),
          child: const Text('開'),
        ),
      ),
    ),
  );
  await tester.tap(find.text('開'));
  await tester.pumpAndSettle();
  await act?.call(tester);
}

const AskAnswer _foundAnswer = AskAnswer(
  answerId: 'a-1',
  found: true,
  answer: '有。10/02 工務換過冷氣濾網，原因是住客反映冷氣不冷，之後沒有再報修。',
  sources: <AskSource>[
    AskSource(
      kind: 'room_memory',
      title: '工務單 · 冷氣',
      excerpt: '冷氣不冷 → 更換濾網，測試正常。處理人：工務 阿德',
      updatedOn: '2026-10-02',
    ),
  ],
);

const AskAnswer _notFoundAnswer = AskAnswer(
  answerId: 'a-1',
  found: false,
  notFoundMessage: '查不到 305 的相關記錄',
  sources: <AskSource>[],
);

class _FakeApiClient extends ApiClient {
  _FakeApiClient({this.answer, this.precheck});

  final AskAnswer? answer;
  final RepairTicketPrecheck? precheck;

  bool holdAnswer = false;

  String? deferredReason;
  String? interruptedReason;
  bool? feedbackHelpful;
  String? escalatedAnswerId;

  final Completer<AskAnswer> _pending = Completer<AskAnswer>();

  void releaseAnswer() {
    if (!_pending.isCompleted) {
      _pending.complete(answer ?? _notFoundAnswer);
    }
  }

  @override
  Future<TaskDetail> deferTask(String taskId, String reason) async {
    deferredReason = reason;
    return _stubTask;
  }

  @override
  Future<TaskDetail> interruptTask(String taskId, String reason) async {
    interruptedReason = reason;
    return _stubTask;
  }

  @override
  Future<RepairTicketPrecheck> precheckRepairTicket({
    required String sourceTaskId,
    required String category,
  }) async {
    final result = precheck;
    if (result == null) {
      throw StateError('這支測試沒有準備開單前檢查的結果');
    }
    return result;
  }

  @override
  Future<List<AskSuggestion>> listAskSuggestions(String taskId) async {
    return const <AskSuggestion>[
      AskSuggestion(question: '305 最近修過什麼？', scope: '房間記錄'),
    ];
  }

  @override
  Future<AskAnswer> askRoomMemory({
    required String taskId,
    required String question,
  }) async {
    if (holdAnswer) {
      return _pending.future;
    }
    return answer ?? _notFoundAnswer;
  }

  @override
  Future<void> submitAskFeedback({
    required String answerId,
    required bool helpful,
  }) async {
    feedbackHelpful = helpful;
  }

  @override
  Future<void> escalateAskQuestion(String answerId) async {
    escalatedAnswerId = answerId;
  }
}

final TaskDetail _stubTask = TaskDetail(
  id: 't-305',
  state: 'deferred',
  type: 'cleaning',
  roomId: 'room-305',
  roomNo: '305',
  title: '305 退房打掃',
  checkedSopItems: const <String>[],
  skippedSopItems: const <String>[],
  handovers: const <HandoverEntry>[],
);
