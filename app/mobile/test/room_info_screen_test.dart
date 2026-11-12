import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/task/room_info_screen.dart';

void main() {
  testWidgets('交辦整頁逐則寫出內文與出處，底下再給一句這裡收的是什麼', (tester) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: HandoverListScreen(
          roomNo: '305',
          entries: <HandoverEntry>[
            HandoverEntry(
              kind: 'shift_handover',
              author: '王姐',
              content: 'VIP 下午 3 點入住，迎賓水果已備在備品間第二層。',
              at: DateTime(2026, 11, 8, 7, 55),
            ),
            const HandoverEntry(
              kind: 'desk_note',
              author: '小葉',
              content: '住客要求 11:00 前請勿打擾。',
            ),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('交辦 · 305'), findsOneWidget);
    expect(find.text('VIP 下午 3 點入住，迎賓水果已備在備品間第二層。'), findsOneWidget);
    expect(find.text('交班單 · 王姐 · 07:55'), findsOneWidget);
    expect(find.text('櫃檯備註 · 小葉'), findsOneWidget);
    expect(find.text('主管交代、接手進度、交班單與櫃檯備註，同一間房的都收在這裡。'), findsOneWidget);
  });

  testWidgets('工務單清單每列寫單號與類別，急件另外掛一顆徽章', (tester) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: RepairTicketListScreen(
          roomNo: '305',
          tickets: <RepairTicketSummary>[
            const RepairTicketSummary(
              taskId: 'task-1',
              ticketNo: 'W-0410',
              roomNo: '305',
              category: 'air_conditioning',
              isUrgent: true,
              state: 'doing',
              reporterName: '小林',
            ),
            const RepairTicketSummary(
              taskId: 'task-2',
              ticketNo: 'W-0411',
              roomNo: '305',
              category: 'plumbing_electrical',
              isUrgent: false,
              state: 'rejected',
              reporterName: '阿美',
            ),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('工務單 · 305'), findsOneWidget);
    expect(find.text('W-0410 · 冷氣'), findsOneWidget);
    expect(find.text('急件'), findsOneWidget);
    expect(find.text('W-0411 · 水電'), findsOneWidget);
    expect(find.text('主管未核准'), findsOneWidget);
  });
}
