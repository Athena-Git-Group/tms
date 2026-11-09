import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_tab_bar.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';

/// 驗分頁外殼把四個畫面接對了，而且首頁上每一個要被讀值的節點都還在。
///
/// 驗收用的工具靠分頁列上的文字切分頁、靠 accessibility id 定位與讀值。
/// 這組測試走同一條路，所以分頁列的文字或首頁的 id 一旦被改掉就會先在這裡
/// 紅掉，不必等整套模擬器驗收跑完才發現。
void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  Future<void> pumpShell(WidgetTester tester) async {
    ApiClient.instance = _FakeApiClient();
    await tester.pumpWidget(const CupertinoApp(home: HousekeepingShell()));
    await tester.pumpAndSettle();
  }

  String labelOf(WidgetTester tester, String id) =>
      tester.getSemantics(find.bySemanticsIdentifier(id)).label;

  testWidgets('登入後落在首頁分頁，今日進度與任務卡的節點都讀得到', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpShell(tester);

    expect(find.bySemanticsIdentifier('home.screen'), findsOneWidget);
    expect(labelOf(tester, 'home.progress.cleaned-ratio'), '2 / 7');
    expect(labelOf(tester, 'home.progress.inspected-count'), '1');
    expect(labelOf(tester, 'home.progress.ad-hoc-count'), '3');
    expect(labelOf(tester, 'home.progress.remaining-minutes'), '135 分');
    expect(labelOf(tester, 'home.streak-badge'), '5');

    // 任務清單在進度卡與主卡底下，測試視窗一屏放不完，先捲到清單再讀。
    await tester.drag(find.byType(ListView), const Offset(0, -400));
    await tester.pumpAndSettle();

    expect(labelOf(tester, 'home.task.305.card'), '305 退房清潔');
    expect(find.bySemanticsIdentifier('home.task.306.card'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('點分頁列的「待認領」切到待認領，首頁節點同時收起來', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpShell(tester);

    await tester.tap(find.text('待認領'));
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('pool.screen'), findsOneWidget);
    expect(find.bySemanticsIdentifier('home.screen'), findsNothing);

    handle.dispose();
  });

  testWidgets('點分頁列的「通知」與「我的」各自切到對應的畫面', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpShell(tester);

    await tester.tap(find.text('通知'));
    await tester.pumpAndSettle();
    expect(find.bySemanticsIdentifier('notification.screen'), findsOneWidget);

    await tester.tap(find.text('我的'));
    await tester.pumpAndSettle();
    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('分頁列浮在內容上：左右各內縮 20，下緣離安全區 21', (tester) async {
    // Main.dc.html:1126,4441-4442 — left/right: tabSide 20、
    // bottom: Math.max(safeBottom - 13, 10)，安全區 34 時是 21
    ApiClient.instance = _FakeApiClient();
    await tester.pumpWidget(
      const CupertinoApp(
        home: MediaQuery(
          data: MediaQueryData(padding: EdgeInsets.only(bottom: 34)),
          child: HousekeepingShell(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final screen = tester.getRect(find.byType(HousekeepingShell));
    final bar = tester.getRect(find.byType(TmsTabBar));

    expect(bar.left - screen.left, 20);
    expect(screen.right - bar.right, 20);
    expect(screen.bottom - bar.bottom, 21);
    expect(bar.height, 62);

    // soft scroll edge 蓋住分頁列佔到的那一整段。
    final edge = tester.getRect(find.byType(TmsBottomScrollEdge));
    expect(edge.bottom, screen.bottom);
    expect(edge.height, 62 + 21);
  });

  testWidgets('首頁清單的底部留白讓開整條分頁列', (tester) async {
    // Main.dc.html:4449 — scrollPadBottom: 62 + tab26Bottom + 20
    ApiClient.instance = _FakeApiClient();
    await tester.pumpWidget(
      const CupertinoApp(
        home: MediaQuery(
          data: MediaQueryData(padding: EdgeInsets.only(bottom: 34)),
          child: HousekeepingShell(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final list = tester.widget<ListView>(find.byType(ListView));
    expect(
      list.padding,
      EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        TmsSpacing.md,
        TmsSpacing.screenEdge,
        TmsSpacing.scrollPadBottom(34),
      ),
    );
  });
}

/// 記憶體 fake：首頁、待認領與通知三個畫面開啟時要打的讀取端點都在這裡回
/// 固定資料，測試不碰網路。
class _FakeApiClient extends ApiClient {
  @override
  Future<MyToday> getMyToday() async {
    return const MyToday(
      cleaningDone: 2,
      cleaningTotal: 7,
      inspectedCount: 1,
      completedCount: 2,
      adHocCompleted: 3,
      remainingMinutes: 135,
      zeroReturnStreakDays: 5,
      todayTasks: [
        TodayTask(
          taskId: 'task-305',
          roomNo: '305',
          title: '退房清潔',
          type: 'cleaning',
          state: 'doing',
          redoCount: 0,
          estMin: 30,
          workedMinutes: 12,
          holdReason: null,
          inspection: null,
          inspectionWaived: false,
        ),
        TodayTask(
          taskId: 'task-306',
          roomNo: '306',
          title: '送備品',
          type: 'amenity',
          state: 'accepted',
          redoCount: 0,
          estMin: 10,
          workedMinutes: 0,
          holdReason: null,
          inspection: null,
          inspectionWaived: false,
        ),
      ],
      nextTasks: [
        NextTask(
          taskId: 'task-306',
          roomNo: '306',
          title: '送備品',
          type: 'amenity',
          redoCount: 0,
        ),
      ],
    );
  }

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async {
    return const DeptPool(
      items: <PoolItem>[],
      counts: {'all': 0},
      activeStaffCount: 1,
    );
  }

  @override
  Future<List<NotificationItem>> listNotifications() async {
    return const <NotificationItem>[];
  }
}
