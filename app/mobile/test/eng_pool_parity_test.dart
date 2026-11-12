import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_category_square.dart';
import 'package:tms_staff_app/core/widgets/tms_category_tile.dart';
import 'package:tms_staff_app/features/engineering/eng_pool_screen.dart';
import 'package:tms_staff_app/features/pool/pool_widgets.dart';
import 'package:tms_staff_app/features/ticket/ticket_screen.dart';

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

  Future<void> pumpEngPool(WidgetTester tester, DeptPool pool) async {
    ApiClient.instance = _FakeApiClient(pool);
    await tester.pumpWidget(const CupertinoApp(home: EngPoolScreen()));
    await tester.pumpAndSettle();
  }

  testWidgets('單卡主標是工單標題、副標是來源（Main.dc.html:998-999）', (tester) async {
    await pumpEngPool(tester, _pool());

    final title = tester.widget<Text>(find.text('冷氣不冷'));
    expect(title.style?.fontWeight, TmsText.headline.fontWeight);
    expect(title.style?.fontSize, TmsText.headline.fontSize);

    final subtitle = find.ancestor(
      of: find.text('房務報修'),
      matching: find.byType(PoolCardSubtitle),
    );
    expect(subtitle, findsOneWidget);
    expect(find.text('W-0412 · 401'), findsNothing);
  });

  testWidgets('大標在捲動內容裡，不是導覽列標題（Main.dc.html:975）', (tester) async {
    await pumpEngPool(tester, _pool());

    final heading = tester.widget<Text>(find.text('待認領'));
    expect(heading.style, TmsText.largeTitle);
    expect(
      find.descendant(
        of: find.byType(CupertinoNavigationBar),
        matching: find.text('待認領'),
      ),
      findsNothing,
    );
  });

  testWidgets('分流磚依序是全部、急件、櫃檯、房務報修（Main.dc.html:3839-3842）', (
    tester,
  ) async {
    await pumpEngPool(tester, _pool());

    final labels = tester
        .widgetList<TmsCategoryTile>(find.byType(TmsCategoryTile))
        .map((tile) => tile.label)
        .toList();
    expect(labels, ['全部', '急件', '櫃檯', '房務報修']);

    final icons = tester
        .widgetList<TmsCategoryTile>(find.byType(TmsCategoryTile))
        .map((tile) => tile.icon)
        .toList();
    expect(icons, [
      CupertinoIcons.square_grid_2x2,
      CupertinoIcons.bolt,
      CupertinoIcons.person_2,
      CupertinoIcons.wrench,
    ]);
  });

  testWidgets('無房號的單，方塊上寫的就是「公共區域」（Main.dc.html:3850）', (tester) async {
    await pumpEngPool(tester, _pool(roomNo: PoolItem.publicAreaRoomNo));

    expect(
      find.ancestor(
        of: find.text('公共區域'),
        matching: find.byType(TmsRoomSquare),
      ),
      findsOneWidget,
    );
  });

  testWidgets('有房號的單，卡上不出現「公共區域」', (tester) async {
    await pumpEngPool(tester, _pool());

    expect(find.text('公共區域'), findsNothing);
  });

  testWidgets('點單卡上半開這張單的工務單詳情（Main.dc.html:992）', (tester) async {
    await pumpEngPool(tester, _pool());

    await tester.tap(find.text('冷氣不冷'));
    await tester.pumpAndSettle();

    expect(find.byType(TicketScreen), findsOneWidget);
  });

  testWidgets('分類磚收得下自繪圖示', (tester) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: TmsCategoryTile(
          label: '打掃',
          iconChild: const SizedBox(width: 24, height: 24, key: _painted),
          iconBackground: TmsColors.brandContainer,
          iconForeground: TmsColors.brandPrimary,
          selected: false,
          onTap: () {},
        ),
      ),
    );

    expect(find.byKey(_painted), findsOneWidget);
    expect(find.byType(Icon), findsNothing);
  });
}

const Key _painted = Key('painted-icon');

DeptPool _pool({String roomNo = '401'}) {
  return DeptPool(
    activeStaffCount: 4,
    counts: const {'all': 1},
    items: [
      PoolItem(
        taskId: 'task-412',
        ticketNo: 'W-0412',
        roomNo: roomNo,
        floor: 4,
        title: '冷氣不冷',
        category: 'air_conditioning',
        source: 'staff_report',
        urgent: false,
        priorityNotify: false,
        sameFloor: false,
        createdAt: '2026-11-08T09:00:00Z',
      ),
    ],
  );
}

class _FakeApiClient extends ApiClient {
  _FakeApiClient(this.pool);

  final DeptPool pool;

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async => pool;
}
