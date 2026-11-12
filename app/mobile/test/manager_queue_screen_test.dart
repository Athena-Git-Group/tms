import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/landing.dart';
import 'package:tms_staff_app/features/manager/manager_queue_screen.dart';
import 'package:tms_staff_app/features/manager/manager_queues_store.dart';

void main() {
  ManagerQueuesStore storeWith(Map<String, dynamic> json) =>
      ManagerQueuesStore()..value = ManagerQueues.fromJson(json);

  Map<String, dynamic> verifyItem(String no, {bool failed = false}) => {
    'taskId': 't-$no',
    'roomNo': '301',
    'isPublicArea': false,
    'title': '門鎖電池更換',
    'waitingSince': '2026-11-10T09:48:00+08:00',
    'waitingSinceIsToday': true,
    'ticketNo': no,
    'category': 'lock',
    'origin': 'reported',
    'resolutionType': 'fixed',
    'wasVerificationFailed': failed,
  };

  testWidgets('待驗收照後端順序畫列，重修再完修的列標「重修後再驗」', (tester) async {
    final store = storeWith({
      'counts': {'verify': 2},
      'verify': [verifyItem('W-0108'), verifyItem('W-0116', failed: true)],
    });
    await tester.pumpWidget(
      CupertinoApp(
        home: ManagerQueueScreen(
          kind: ManagerKind.engineering,
          queue: 'verify',
          store: store,
        ),
      ),
    );
    expect(find.text('待驗收'), findsOneWidget);
    expect(find.text('W-0108'), findsOneWidget);
    expect(find.text('重修後再驗'), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('W-0108')).dy,
      lessThan(tester.getTopLeft(find.text('W-0116')).dy),
    );
  });

  testWidgets('工務主管以待查房開清單時直接退回，不畫任何列', (tester) async {
    final store = storeWith({
      'counts': {'inspection': 1},
      'inspection': [
        {
          'taskId': 't1',
          'roomNo': '301',
          'isPublicArea': false,
          'title': '退房清潔',
          'waitingSince': '2026-11-10T10:20:00+08:00',
          'waitingSinceIsToday': true,
          'cleanerName': '小美',
          'redoCount': 0,
        },
      ],
    });
    await tester.pumpWidget(
      CupertinoApp(
        home: Builder(
          builder: (context) => CupertinoButton(
            child: const Text('open'),
            onPressed: () => Navigator.of(context).push(
              CupertinoPageRoute<void>(
                builder: (_) => ManagerQueueScreen(
                  kind: ManagerKind.engineering,
                  queue: 'inspection',
                  store: store,
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.text('301'), findsNothing);
    expect(find.byType(ManagerQueueScreen), findsNothing);
    expect(find.text('open'), findsOneWidget);
  });
}
