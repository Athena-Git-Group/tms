import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_category_tile.dart';
import 'package:tms_staff_app/core/widgets/tms_glass.dart';
import 'package:tms_staff_app/core/widgets/tms_tab_bar.dart';

void main() {
  const barWidth = 362.0;

  final tapped = <int>[];

  setUp(tapped.clear);

  Future<void> pumpBar(
    WidgetTester tester, {
    int currentIndex = 0,
    List<TmsTabItem>? items,
  }) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: Center(
          child: SizedBox(
            width: barWidth,
            child: TmsTabBar(
              currentIndex: currentIndex,
              onSelect: tapped.add,
              items:
                  items ??
                  const [
                    TmsTabItem(label: '首頁', glyph: TmsTabGlyph.home),
                    TmsTabItem(label: '待認領', glyph: TmsTabGlyph.pool),
                    TmsTabItem(label: '通知', glyph: TmsTabGlyph.notification),
                    TmsTabItem(label: '我的', glyph: TmsTabGlyph.me),
                  ],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('玻璃底板的模糊、飽和度、底色、邊框與陰影照原型', (tester) async {
    await pumpBar(tester);

    final glass = tester.widget<TmsGlass>(find.byType(TmsGlass));
    expect(glass.blur, 24);
    expect(glass.saturation, 180);
    expect(glass.color, const Color(0x8CFFFFFF));
    expect(
      glass.border,
      Border.all(color: const Color(0xB3FFFFFF), width: TmsSize.hairline),
    );
    expect(glass.boxShadow, <BoxShadow>[
      const BoxShadow(
        color: Color(0x1F000000),
        offset: Offset(0, 4),
        blurRadius: 16,
      ),
    ]);
    expect(glass.borderRadius, BorderRadius.circular(31));
  });

  testWidgets('分頁列高 62，上緣疊著一條 1pt 的白色高光', (tester) async {
    await pumpBar(tester);

    expect(tester.getSize(find.byType(TmsTabBar)).height, 62);

    final highlight = find.byWidgetPredicate(
      (widget) =>
          widget is ColoredBox && widget.color == TmsColors.glassHighlight,
    );
    expect(highlight, findsOneWidget);
    final highlightRect = tester.getRect(highlight);
    expect(highlightRect.height, 1);
    expect(highlightRect.top, tester.getRect(find.byType(TmsTabBar)).top);
  });

  testWidgets('只有選中那一格有膠囊底，圓角 27', (tester) async {
    await pumpBar(tester, currentIndex: 2);

    final pills = tester
        .widgetList<DecoratedBox>(find.byType(DecoratedBox))
        .map((box) => box.decoration)
        .whereType<BoxDecoration>()
        .where((d) => d.color == TmsColors.tabBarSelectedPill)
        .toList();

    expect(pills, hasLength(1));
    expect(pills.single.borderRadius, BorderRadius.circular(27));
  });

  testWidgets('選中那格走品牌色，其餘三格是 #1C1C1E 而不是 #8E8E93', (tester) async {
    await pumpBar(tester, currentIndex: 1);

    TextStyle styleOf(String label) =>
        tester.widget<Text>(find.text(label)).style!;

    expect(styleOf('待認領').color, TmsColors.brandPrimary);
    for (final label in ['首頁', '通知', '我的']) {
      expect(styleOf(label).color, const Color(0xFF1C1C1E));
    }
  });

  testWidgets('分頁名是 10pt/600，圖示是 24 見方', (tester) async {
    await pumpBar(tester);

    final style = tester.widget<Text>(find.text('首頁')).style!;
    expect(style.fontSize, 10);
    expect(style.fontWeight, FontWeight.w600);

    final glyphs = find.descendant(
      of: find.byType(TmsTabBar),
      matching: find.byType(CustomPaint),
    );
    expect(glyphs, findsNWidgets(4));
    for (var i = 0; i < 4; i++) {
      expect(tester.getSize(glyphs.at(i)), const Size(24, 24));
    }
  });

  testWidgets('四格平分扣掉內距與格間之後的寬度', (tester) async {
    await pumpBar(tester);

    final bar = tester.getRect(find.byType(TmsTabBar));
    final first = tester.getRect(find.text('首頁'));
    final last = tester.getRect(find.text('我的'));

    const cellWidth = (barWidth - 4 * 2 - 2 * 3) / 4;
    expect(first.center.dx - bar.left, closeTo(4 + cellWidth / 2, 0.01));
    expect(bar.right - last.center.dx, closeTo(4 + cellWidth / 2, 0.01));
  });

  testWidgets('徽章落在該格左緣的 56%、按鈕上緣下方 3，且高 18', (tester) async {
    await pumpBar(
      tester,
      items: const [
        TmsTabItem(label: '首頁', glyph: TmsTabGlyph.home),
        TmsTabItem(label: '待認領', glyph: TmsTabGlyph.pool, badgeCount: 3),
        TmsTabItem(label: '通知', glyph: TmsTabGlyph.notification),
        TmsTabItem(label: '我的', glyph: TmsTabGlyph.me),
      ],
    );

    final badges = find.byType(TmsCountBadge);
    expect(badges, findsOneWidget);

    const cellWidth = (barWidth - 4 * 2 - 2 * 3) / 4;
    expect(cellWidth, 87);

    final bar = tester.getRect(find.byType(TmsTabBar));
    final cellLeft = bar.left + 4 + cellWidth + 2;
    final cellTop = bar.top + 4;
    final badge = tester.getRect(badges);

    expect(badge.left - cellLeft, closeTo(48.72, 0.01));
    expect(badge.top - cellTop, 3);
    expect(badge.height, 18);
    expect(tester.getSize(find.byType(TmsTabBar)).height - 4 * 2, 54);
  });

  testWidgets('計數是 0 的那幾格不畫徽章', (tester) async {
    await pumpBar(
      tester,
      items: const [
        TmsTabItem(label: '首頁', glyph: TmsTabGlyph.home),
        TmsTabItem(label: '待認領', glyph: TmsTabGlyph.pool, badgeCount: 0),
        TmsTabItem(label: '通知', glyph: TmsTabGlyph.notification),
        TmsTabItem(label: '我的', glyph: TmsTabGlyph.me),
      ],
    );

    expect(find.byType(TmsCountBadge), findsNothing);
  });

  testWidgets('整格都可以點，不是只有文字可以點', (tester) async {
    await pumpBar(tester);

    final bar = tester.getRect(find.byType(TmsTabBar));
    const cellWidth = (barWidth - 4 * 2 - 2 * 3) / 4;

    await tester.tap(find.text('通知'));
    await tester.tapAt(
      Offset(bar.left + 4 + (cellWidth + 2) * 3 + 2, bar.top + 6),
    );

    expect(tapped, <int>[2, 3]);
  });

  testWidgets('soft scroll edge 的模糊與飽和度照原型，上緣淡出', (tester) async {
    await tester.pumpWidget(
      const CupertinoApp(
        home: Align(
          alignment: Alignment.bottomCenter,
          child: TmsBottomScrollEdge(height: 83),
        ),
      ),
    );

    final backdrop = tester.widget<BackdropFilter>(find.byType(BackdropFilter));
    expect(backdrop.filter, TmsGlass.filterFor(blur: 12, saturation: 160));

    expect(tester.getSize(find.byType(TmsBottomScrollEdge)).height, 83);

    final gradient = tester
        .widgetList<DecoratedBox>(find.byType(DecoratedBox))
        .map((box) => box.decoration)
        .whereType<BoxDecoration>()
        .firstWhere((d) => d.gradient != null)
        .gradient!;
    expect(gradient, isA<LinearGradient>());
    final linear = gradient as LinearGradient;
    expect(linear.begin, Alignment.bottomCenter);
    expect(linear.end, Alignment.topCenter);
    expect(linear.stops, <double>[0, 0.4, 1]);
    expect(linear.colors.first, const Color(0xB8F2F2F7));
    expect(linear.colors.last.a, 0);
  });
}
