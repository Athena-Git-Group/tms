import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/widgets/tms_option_row.dart';
import 'package:tms_staff_app/core/widgets/tms_sheet.dart';
import 'package:tms_staff_app/features/ticket/ticket_complete_sheet.dart';

void main() {
  Widget host() {
    return const CupertinoApp(
      home: TicketCompleteSheet(taskId: 't-1', roomNo: '418'),
    );
  }

  testWidgets('用的是共用的底部面板外框，標題帶著地點（Main.dc.html:3990）', (tester) async {
    await tester.pumpWidget(host());

    expect(find.byType(TmsSheet), findsOneWidget);
    expect(find.text('完修回報 · 418'), findsOneWidget);
  });

  testWidgets('處理結果是四列清單，每列都寫出送出後系統會做什麼（Main.dc.html:2176）', (
    tester,
  ) async {
    await tester.pumpWidget(host());

    expect(find.text('處理結果'), findsOneWidget);
    expect(find.byType(TmsOptionRow), findsNWidgets(4));
    expect(find.text('已修好，房間可正常使用'), findsOneWidget);
    expect(find.text('通知報修的人與櫃檯'), findsOneWidget);
    expect(find.text('無法修復，房間停用（OOO）'), findsOneWidget);
    expect(find.text('PMS 房況改為停用，並通知櫃檯與主管'), findsOneWidget);
    expect(find.text('不需處理'), findsOneWidget);
  });

  testWidgets('選不需處理時不附照片也能送出', (tester) async {
    await tester.pumpWidget(host());

    await tester.ensureVisible(find.text('不需處理'));
    await tester.tap(find.text('不需處理'));
    await tester.pump();

    expect(
      tester.getSemantics(
        find.bySemanticsIdentifier('completeSheet.submit-button'),
      ),
      isSemantics(hasEnabledState: true, isEnabled: true),
    );
    expect(find.text('完修照片（不需處理可以不附照片）'), findsOneWidget);
  });

  testWidgets('有處理說明欄位（Main.dc.html:2188）', (tester) async {
    await tester.pumpWidget(host());

    expect(find.text('處理說明（選填）'), findsOneWidget);
    expect(find.byType(CupertinoTextField), findsOneWidget);
  });

  testWidgets('一張照片都沒有時不講送出後果，講的是還缺照片（Main.dc.html:3994）', (
    tester,
  ) async {
    await tester.pumpWidget(host());

    await tester.tap(find.text('無法修復，房間停用（OOO）'));
    await tester.pump();

    expect(find.text('送出後 PMS 房況改為停用，櫃檯與主管會收到通知。'), findsNothing);
    expect(find.text('修繕完成必須拍照。沒有相機權限時，可以從相簿選擇。'), findsOneWidget);
  });

  testWidgets('置底鈕寫「送出完修」，照片還沒拍時按不下去（Main.dc.html:3995）', (
    tester,
  ) async {
    await tester.pumpWidget(host());

    final button = tester.widget<TmsSheetButton>(find.byType(TmsSheetButton));
    expect(button.label, '送出完修');
    expect(button.onPressed, isNull);
  });
}
