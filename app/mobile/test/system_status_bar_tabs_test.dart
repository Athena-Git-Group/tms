import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/connection_status.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';

void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
    ConnectionStatus.instance.setOffline(false);
  });

  testWidgets('離線時切過四個分頁，狀態列的 id 永遠只有一個在樹上', (tester) async {
    final handle = tester.ensureSemantics();
    ApiClient.instance = _FakeApiClient();
    ConnectionStatus.instance.setOffline(true);
    await tester.pumpWidget(const CupertinoApp(home: HousekeepingShell()));
    await tester.pumpAndSettle();

    for (final tab in ['首頁', '待認領', '通知', '我的']) {
      await tester.tap(find.text(tab));
      await tester.pumpAndSettle();
      expect(
        find.bySemanticsIdentifier('system.offline-bar'),
        findsOneWidget,
        reason: '切到 $tab 之後',
      );
      expect(
        find.bySemanticsIdentifier('system.offline-bar.pending-count'),
        findsOneWidget,
        reason: '切到 $tab 之後',
      );
    }
    handle.dispose();
  });
}

class _FakeApiClient extends ApiClient {
  @override
  Future<MyToday> getMyToday() async => const MyToday(
    cleaningDone: 0,
    cleaningTotal: 0,
    inspectedCount: 0,
    completedCount: 0,
    adHocCompleted: 0,
    remainingMinutes: 0,
    todayTasks: [],
    nextTasks: [],
  );

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async => const DeptPool(
    items: <PoolItem>[],
    counts: {'all': 0},
    activeStaffCount: 1,
  );

  @override
  Future<List<NotificationItem>> listNotifications() async =>
      const <NotificationItem>[];
}
