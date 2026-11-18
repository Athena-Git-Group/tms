import 'package:flutter/cupertino.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_grouped_list.dart';
import 'package:tms_staff_app/core/widgets/tms_option_row.dart';

void main() {
  Widget host(Widget child) {
    return CupertinoApp(
      theme: buildTmsCupertinoTheme(),
      home: Align(alignment: Alignment.topCenter, child: child),
    );
  }

  double fontSizeOf(WidgetTester tester, String text) {
    return tester
        .renderObject<RenderParagraph>(find.text(text))
        .text
        .style!
        .fontSize!;
  }

  testWidgets('三種單行／雙行選項列各有自己的最小高度', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TmsOptionRow.reason(title: const Text('請勿打擾'), onTap: () {}),
            TmsOptionRow.category(
              title: const Text('空調'),
              selected: false,
              onTap: () {},
            ),
            TmsOptionRow.suggestion(
              title: const Text('備品放在哪裡？'),
              scope: const Text('全館'),
              onTap: () {},
            ),
          ],
        ),
      ),
    );

    final heights = tester
        .widgetList<TmsOptionRow>(find.byType(TmsOptionRow))
        .map((row) => tester.getSize(find.byWidget(row)).height)
        .toList();
    expect(heights, <double>[52, 44, 56]);
  });

  testWidgets('單行列沒有上下內距，文字靠列高撐在中央', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TmsOptionRow.reason(
              title: const SizedBox(height: 70, width: 100),
              onTap: () {},
            ),
            TmsOptionRow.suggestion(
              title: const SizedBox(height: 70, width: 100),
              scope: const SizedBox.shrink(),
              onTap: () {},
            ),
            TmsOptionRow.result(
              title: const SizedBox(height: 70, width: 100),
              detail: const SizedBox.shrink(),
              selected: false,
              onTap: () {},
            ),
          ],
        ),
      ),
    );

    final rows = find.byType(TmsOptionRow);
    expect(tester.getSize(rows.at(0)).height, 70);
    expect(tester.getSize(rows.at(1)).height, 70 + 2 + 20);
    expect(tester.getSize(rows.at(2)).height, 70 + 1 + 16);
  });

  testWidgets('勾只畫在選中的那一列，尺寸照原型的 16', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TmsOptionRow.category(
              title: const Text('空調'),
              selected: true,
              onTap: () {},
            ),
            TmsOptionRow.category(
              title: const Text('浴室'),
              selected: false,
              onTap: () {},
            ),
          ],
        ),
      ),
    );

    expect(find.byType(TmsOptionCheck), findsOneWidget);
    expect(tester.getSize(find.byType(TmsOptionCheck)), const Size(16, 16));
  });

  testWidgets('建議問題列尾端什麼都不畫，範圍那行是 12', (tester) async {
    await tester.pumpWidget(
      host(
        TmsOptionRow.suggestion(
          title: const Text('備品放在哪裡？'),
          scope: const Text('全館'),
          onTap: () {},
        ),
      ),
    );

    expect(find.byType(TmsChevron), findsNothing);
    expect(find.byType(TmsOptionCheck), findsNothing);
    expect(fontSizeOf(tester, '備品放在哪裡？'), 17);
    expect(fontSizeOf(tester, '全館'), 12);
  });

  testWidgets('處理結果列的說明那行是 13，不是建議問題列的 12', (tester) async {
    await tester.pumpWidget(
      host(
        TmsOptionRow.result(
          title: const Text('已修復'),
          detail: const Text('換了濾網'),
          selected: true,
          onTap: () {},
        ),
      ),
    );

    expect(fontSizeOf(tester, '換了濾網'), 13);
    expect(find.byType(TmsOptionCheck), findsOneWidget);
  });

  testWidgets('列尾箭頭是 8 × 13 的描邊圖形，不是方形字框', (tester) async {
    await tester.pumpWidget(
      host(const TmsGroupedSection(children: [TmsGroupedRow(label: '變更密碼')])),
    );

    expect(tester.getSize(find.byType(TmsChevron)), const Size(8, 13));
  });

  testWidgets('分組列表的底線只在要求時補在最後一列底下', (tester) async {
    Finder separators() {
      return find.byWidgetPredicate(
        (widget) => widget is ColoredBox && widget.color == TmsColors.separator,
      );
    }

    const rows = [
      TmsGroupedRow(label: '推播通知'),
      TmsGroupedRow(label: '語言'),
      TmsGroupedRow(label: '文字大小'),
    ];

    await tester.pumpWidget(host(const TmsGroupedSection(children: rows)));
    expect(separators(), findsNWidgets(2));

    await tester.pumpWidget(
      host(const TmsGroupedSection(trailingDivider: true, children: rows)),
    );
    expect(separators(), findsNWidgets(3));
  });
}
