import 'package:flutter/cupertino.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_grouped_list.dart';
import 'package:tms_staff_app/core/widgets/tms_option_row.dart';

/// 面板選項列與分組列表的量測對帳。
///
/// 每一條斷言的數字都抄自設計原型 `Claude Design/Main.dc.html`，行號寫在該條
/// 旁邊。斷言寫實際數字而不引用 token：比對 token 和它自己永遠會過，讀不出值
/// 有沒有跟著原型走。
///
/// 這四種列被四個畫面共用（延後／中斷面板、報修面板、查一下面板、工務完修回
/// 報），列高差一兩個 pixel 在單一畫面上看不出來，四個擺在一起就看得出來。
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
    // Main.dc.html:1450 原因列 52、1461 類別列 44、1601 建議問題列 56。
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
    // Main.dc.html:1450 — padding: 0 16px，撐高的是 min-height 不是內距，所以
    // 內容比 52 高的時候列高就等於內容高。
    expect(tester.getSize(rows.at(0)).height, 70);
    // Main.dc.html:1601 — padding: 10px 16px，上下各 10，兩行之間 gap 2。
    expect(tester.getSize(rows.at(1)).height, 70 + 2 + 20);
    // Main.dc.html:2179 — padding: 8px 16px，兩行之間 gap 1。
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

    // Main.dc.html:1463 — svg width 16 height 16。
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

    // Main.dc.html:1601-1604 — 列裡只有兩個 span，沒有箭頭也沒有勾。
    expect(find.byType(TmsChevron), findsNothing);
    expect(find.byType(TmsOptionCheck), findsNothing);
    // Main.dc.html:1602-1603 — 主文 17px、範圍 12px。
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

    // Main.dc.html:2182 — font-size: 13px。
    expect(fontSizeOf(tester, '換了濾網'), 13);
    expect(find.byType(TmsOptionCheck), findsOneWidget);
  });

  testWidgets('列尾箭頭是 8 × 13 的描邊圖形，不是方形字框', (tester) async {
    await tester.pumpWidget(
      host(const TmsGroupedSection(children: [TmsGroupedRow(label: '變更密碼')])),
    );

    // Main.dc.html:421 — svg width 8 height 13。
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
    // Main.dc.html:4572 — 設定列的 sep 最後一列算成 none。
    expect(separators(), findsNWidgets(2));

    await tester.pumpWidget(
      host(const TmsGroupedSection(trailingDivider: true, children: rows)),
    );
    // Main.dc.html:1312、1450 — 面板選項列與 SOP 列每一列都寫死 border-bottom。
    expect(separators(), findsNWidgets(3));
  });
}
