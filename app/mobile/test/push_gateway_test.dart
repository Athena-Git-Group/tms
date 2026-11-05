import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/push_gateway.dart';
import 'package:tms_staff_app/core/test_backdoor.dart';
import 'package:tms_staff_app/features/home/home_screen.dart';
import 'package:tms_staff_app/features/pool/pool_screen.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

/// 對應「F3 點開推播的去處」：這三條各驗一個骨架環節——
/// 待認領畫面能標亮指定房號、推播 payload 能把 App 導到正確畫面、
/// 任務頁能認出「工務已修好」並露出恢復提示列。
void main() {
  setUp(() {
    TestHooks.instance.reset();
  });

  testWidgets('seed 後待認領畫面渲染卡片並標亮指定房號', (tester) async {
    final handle = tester.ensureSemantics();

    TestHooks.instance.seedTasks = [
      const SeedTask(roomNo: '316', content: '衣架 x4', status: '待認領'),
      const SeedTask(roomNo: '317', content: '退房清潔', status: '待認領'),
    ];

    await tester.pumpWidget(
      const CupertinoApp(home: PoolScreen(highlightRoomNo: '316')),
    );
    await tester.pump();

    expect(find.bySemanticsIdentifier('pool.screen'), findsOneWidget);
    expect(find.bySemanticsIdentifier('pool.card.316--highlighted'), findsOneWidget);
    expect(find.bySemanticsIdentifier('pool.card.316'), findsNothing);
    expect(find.bySemanticsIdentifier('pool.card.317'), findsOneWidget);
    expect(find.bySemanticsIdentifier('pool.card.317--highlighted'), findsNothing);

    handle.dispose();
  });

  testWidgets('push payload dest=task room=305 把 App 導到 305 的任務頁', (tester) async {
    final handle = tester.ensureSemantics();

    final navigatorKey = GlobalKey<NavigatorState>();
    PushGateway.instance.attach(navigatorKey);

    TestHooks.instance.seedTasks = [
      const SeedTask(roomNo: '305', content: '退房清潔', status: '進行中'),
    ];

    await tester.pumpWidget(
      CupertinoApp(navigatorKey: navigatorKey, home: const HomeScreen()),
    );
    await tester.pump();

    expect(find.bySemanticsIdentifier('home.screen'), findsOneWidget);

    PushGateway.instance.handlePushPayload(const {'dest': 'task', 'room': '305'});
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('task.screen'), findsOneWidget);
    expect(find.text('305 的任務頁'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('已中斷且停住原因含工務已修好時，任務頁顯示恢復提示列', (tester) async {
    final handle = tester.ensureSemantics();

    TestHooks.instance.seedTasks = [
      const SeedTask(
        roomNo: '305',
        content: '退房清潔',
        status: '已中斷',
        elapsedMinutes: 15,
        pausedReason: '工務已修好：水電',
      ),
    ];

    await tester.pumpWidget(
      const CupertinoApp(home: TaskScreen(roomNo: '305')),
    );
    await tester.pump();

    expect(find.bySemanticsIdentifier('task.resume-banner'), findsOneWidget);
    expect(find.bySemanticsIdentifier('task.status-row.resume-button'), findsOneWidget);
    expect(find.textContaining('305'), findsWidgets);
    expect(find.textContaining('您'), findsOneWidget);

    handle.dispose();
  });
}
