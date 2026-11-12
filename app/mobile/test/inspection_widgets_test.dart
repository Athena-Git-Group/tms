import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/manager/inspection_item_row.dart';
import 'package:tms_staff_app/features/manager/inspection_result_screen.dart';
import 'package:tms_staff_app/features/manager/manager_conflict_alert.dart';

/// 查房結果畫面三種說法只看後端回的 result 與 destination；項目列兩段切換
/// 回報判定；「這張單已被處理」對話框按知道了才結束。
void main() {
  InspectionOutcome outcome({
    required String result,
    required String destination,
    List<InspectionFailedItem> failed = const [],
  }) => InspectionOutcome(
    inspectionId: 'i1',
    taskId: 't1',
    roomNo: '301',
    result: result,
    destination: destination,
    failedItems: failed,
    createdAt: '',
    cleanerName: '小美',
  );

  String resultLabel(WidgetTester tester) =>
      tester.getSemantics(find.bySemanticsIdentifier('inspect.result')).label;

  testWidgets('全部通過：查房通過、已結案，清房者會收到通知', (tester) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: InspectionResultScreen(
          outcome: outcome(result: 'passed', destination: 'closed'),
          cleanerName: '小美',
        ),
      ),
    );
    final label = resultLabel(tester);
    expect(label, contains('301'));
    expect(label, contains('查房通過'));
    expect(label, contains('小美會收到通知'));
  });

  testWidgets('退回原清房者：回到小美名下並列出不合格項', (tester) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: InspectionResultScreen(
          outcome: outcome(
            result: 'failed',
            destination: 'cleaner',
            failed: const [InspectionFailedItem(key: 'bath', label: '浴室無水痕')],
          ),
          cleanerName: '小美',
        ),
      ),
    );
    final label = resultLabel(tester);
    expect(label, contains('退回重做'));
    expect(label, contains('回到小美名下'));
    expect(label, contains('浴室無水痕'));
  });

  testWidgets('退回待認領：小美已下班，回到了房務待認領', (tester) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: InspectionResultScreen(
          outcome: outcome(result: 'failed', destination: 'pool'),
          cleanerName: '小美',
        ),
      ),
    );
    final label = resultLabel(tester);
    expect(label, contains('小美已下班'));
    expect(label, contains('回到了房務待認領'));
  });

  testWidgets('項目列點兩段切換回報通過或不通過', (tester) async {
    final judged = <bool>[];
    await tester.pumpWidget(
      CupertinoApp(
        home: CupertinoPageScaffold(
          child: InspectionItemRow(
            itemKey: 'bed',
            label: '床鋪平整',
            verdict: null,
            showSeparator: false,
            onJudge: judged.add,
          ),
        ),
      ),
    );
    await tester.tap(find.text('通過'));
    await tester.tap(find.text('不通過'));
    expect(judged, [true, false]);
  });

  testWidgets('這張單已被處理：按知道了才關閉', (tester) async {
    var closed = false;
    await tester.pumpWidget(
      CupertinoApp(
        home: Builder(
          builder: (context) => CupertinoButton(
            onPressed: () async {
              await showManagerConflictAlert(context);
              closed = true;
            },
            child: const Text('送出'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('送出'));
    await tester.pumpAndSettle();
    expect(find.text('這張單已被處理'), findsOneWidget);
    expect(closed, isFalse);
    await tester.tap(find.text('知道了'));
    await tester.pumpAndSettle();
    expect(closed, isTrue);
  });
}
