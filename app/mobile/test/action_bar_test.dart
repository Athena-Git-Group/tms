import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_action_bar.dart';
import 'package:tms_staff_app/core/widgets/tms_pill_button.dart';
import 'package:tms_staff_app/core/widgets/tms_sheet.dart';

/// 置底操作列與膠囊按鈕的可量測外觀。
///
/// 這些值在網頁原型裡散在四支畫面的 inline style 與 logic script 兩處，照抄
/// 時很容易只改一支。每一條都寫出原型行號，改設計時回去對那一行就知道還準不準。
void main() {
  Widget host(Widget child, {double bottomInset = 34, double width = 402}) {
    return CupertinoApp(
      home: MediaQuery(
        data: MediaQueryData(viewPadding: EdgeInsets.only(bottom: bottomInset)),
        child: Align(
          alignment: Alignment.bottomCenter,
          child: SizedBox(width: width, child: child),
        ),
      ),
    );
  }

  EdgeInsets barPadding(WidgetTester tester) {
    final container = tester.widget<Container>(find.byType(Container));
    return container.padding! as EdgeInsets;
  }

  group('置底操作列的外框', () {
    testWidgets('詳情頁：上 12、左右 16、底部吃滿安全區（Main.dc.html:1353、4425）', (
      tester,
    ) async {
      await tester.pumpWidget(
        host(
          const TmsActionBar(
            primary: TmsPillButton(label: '開始', onPressed: _noop),
          ),
        ),
      );
      expect(barPadding(tester), const EdgeInsets.fromLTRB(16, 12, 16, 34));
    });

    testWidgets('詳情頁：沒有安全區的機型改留 16（Main.dc.html:4425）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsActionBar(
            primary: TmsPillButton(label: '開始', onPressed: _noop),
          ),
          bottomInset: 0,
        ),
      );
      expect(barPadding(tester).bottom, 16);
    });

    testWidgets('表單頁：上 10、底部是安全區減 14（Main.dc.html:1087、4653）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsFormActionBar(
            children: [TmsPillButton(label: '登入', onPressed: _noop)],
          ),
        ),
      );
      expect(barPadding(tester), const EdgeInsets.fromLTRB(16, 10, 16, 20));
    });

    testWidgets('表單頁：沒有安全區的機型留 8（Main.dc.html:4653）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsFormActionBar(
            children: [TmsPillButton(label: '登入', onPressed: _noop)],
          ),
          bottomInset: 0,
        ),
      );
      expect(barPadding(tester).bottom, 8);
    });

    testWidgets('上緣是一條 0.5pt 的分隔線（Main.dc.html:4459）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsActionBar(
            primary: TmsPillButton(label: '開始', onPressed: _noop),
          ),
        ),
      );
      final container = tester.widget<Container>(
        find.descendant(
          of: find.byType(TmsActionBar),
          matching: find.byType(Container),
        ),
      );
      final decoration = container.decoration! as BoxDecoration;
      expect(decoration.color, TmsColors.cardBackground);
      final top = (decoration.border! as Border).top;
      expect(top.width, 0.5);
      expect(top.color, TmsColors.opaqueSeparator);
    });

    testWidgets('兩顆都沒有的狀態整列不畫', (tester) async {
      await tester.pumpWidget(host(const TmsActionBar()));
      expect(find.byType(Container), findsNothing);
    });
  });

  group('置底操作列的排版', () {
    testWidgets('次要比主要是 1 比 2，中間隔 12（Main.dc.html:1358、1364）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsActionBar(
            secondary: TmsPillButton(
              label: '延後',
              style: TmsPillButtonStyle.outlined,
              onPressed: _noop,
            ),
            primary: TmsPillButton(label: '開始', onPressed: _noop),
          ),
        ),
      );
      final secondary = tester.getRect(find.byType(TmsPillButton).at(0));
      final primary = tester.getRect(find.byType(TmsPillButton).at(1));
      // 兩顆按鈕一起佔掉 402 減左右各 16 再減中間 12，等於 358，1 比 2 分成
      // 119.33 與 238.67。
      const available = 402 - 16 * 2 - 12;
      final bar = tester.getRect(find.byType(TmsActionBar));
      expect(secondary.left - bar.left, moreOrLessEquals(16, epsilon: 0.5));
      expect(bar.right - primary.right, moreOrLessEquals(16, epsilon: 0.5));
      expect(
        primary.left - secondary.right,
        moreOrLessEquals(12, epsilon: 0.5),
      );
      expect(
        primary.width / secondary.width,
        moreOrLessEquals(2, epsilon: 0.05),
      );
      expect(
        secondary.width + primary.width,
        moreOrLessEquals(available.toDouble(), epsilon: 0.5),
      );
    });

    testWidgets('只有一顆時那一顆撐滿整列（Main.dc.html:1845）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsActionBar(
            secondary: TmsPillButton(
              label: '撤回工務單',
              style: TmsPillButtonStyle.dangerOutlined,
              onPressed: _noop,
            ),
          ),
        ),
      );
      expect(
        tester.getSize(find.byType(TmsPillButton)).width,
        moreOrLessEquals(402 - 16 * 2, epsilon: 0.5),
      );
    });

    testWidgets('表單頁兩顆按鈕的淨間距是 4（Main.dc.html:1087、1090）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsFormActionBar(
            children: [
              TmsPillButton(label: '登入', onPressed: _noop),
              _Forgot(),
            ],
          ),
        ),
      );
      final submit = tester.getRect(find.byType(TmsPillButton));
      final forgot = tester.getRect(find.byType(_Forgot));
      expect(forgot.top - submit.bottom, moreOrLessEquals(4, epsilon: 0.01));
    });
  });

  group('主按鈕的 iOS 26 光暈', () {
    List<BoxShadow>? glowOf(WidgetTester tester) {
      final decorated = tester
          .widgetList<DecoratedBox>(find.byType(DecoratedBox))
          .map((d) => d.decoration)
          .whereType<BoxDecoration>()
          .where((d) => (d.boxShadow ?? const <BoxShadow>[]).isNotEmpty);
      return decorated.isEmpty ? null : decorated.first.boxShadow;
    }

    testWidgets('預設那一組是模糊 18、顏色 0x470C7680（Main.dc.html:4460）', (tester) async {
      await tester.pumpWidget(
        host(const TmsPillButton(label: '開始', onPressed: _noop)),
      );
      final shadow = glowOf(tester)!.single;
      expect(shadow.color, const Color(0x470C7680));
      expect(shadow.offset, const Offset(0, 6));
      expect(shadow.blurRadius, 18);
    });

    testWidgets('另一組是模糊 16、顏色 0x4D0C7680（Main.dc.html:4049）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsPillButton(
            label: '登入',
            glow: TmsPillGlow.alt,
            onPressed: _noop,
          ),
        ),
      );
      final shadow = glowOf(tester)!.single;
      expect(shadow.color, const Color(0x4D0C7680));
      expect(shadow.blurRadius, 16);
    });

    testWidgets('停用的按鈕不畫光暈（Main.dc.html:4532）', (tester) async {
      await tester.pumpWidget(
        host(const TmsPillButton(label: '登入', onPressed: null)),
      );
      expect(glowOf(tester), isNull);
    });

    testWidgets('外框與白底的變體不畫光暈（Main.dc.html:1358）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsPillButton(
            label: '延後',
            style: TmsPillButtonStyle.outlined,
            onPressed: _noop,
          ),
        ),
      );
      expect(glowOf(tester), isNull);
    });

    testWidgets('內緣高光是上白下暗兩條 1pt 的線（Main.dc.html:4460）', (tester) async {
      await tester.pumpWidget(
        host(const TmsPillButton(label: '開始', onPressed: _noop)),
      );
      final found = find.descendant(
        of: find.byType(TmsPillButton),
        matching: find.byType(ColoredBox),
      );
      expect(
        tester.widgetList<ColoredBox>(found).map((b) => b.color),
        containsAll(const [Color(0x59FFFFFF), Color(0x14000000)]),
      );
      for (var i = 0; i < found.evaluate().length; i++) {
        expect(tester.getSize(found.at(i)).height, 1);
      }
    });

    testWidgets('另一組沒有下緣暗邊（Main.dc.html:4049）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsPillButton(
            label: '登入',
            glow: TmsPillGlow.alt,
            onPressed: _noop,
          ),
        ),
      );
      final lines = tester
          .widgetList<ColoredBox>(
            find.descendant(
              of: find.byType(TmsPillButton),
              matching: find.byType(ColoredBox),
            ),
          )
          .map((b) => b.color)
          .toList();
      expect(lines, contains(const Color(0x59FFFFFF)));
      expect(lines, isNot(contains(const Color(0x14000000))));
    });
  });

  group('面板置底鈕', () {
    testWidgets('可以按的時候帶光暈，不能按就沒有（Main.dc.html:4942）', (tester) async {
      await tester.pumpWidget(
        host(const TmsSheetButton(label: '送出', onPressed: _noop)),
      );
      final shadow = tester
          .widgetList<DecoratedBox>(
            find.descendant(
              of: find.byType(TmsSheetButton),
              matching: find.byType(DecoratedBox),
            ),
          )
          .map((d) => d.decoration)
          .whereType<BoxDecoration>()
          .expand((d) => d.boxShadow ?? const <BoxShadow>[])
          .single;
      expect(shadow.color, const Color(0x4D0C7680));
      expect(shadow.blurRadius, 16);

      await tester.pumpWidget(
        host(const TmsSheetButton(label: '送出', onPressed: null)),
      );
      expect(
        tester
            .widgetList<DecoratedBox>(find.byType(DecoratedBox))
            .map((d) => d.decoration)
            .whereType<BoxDecoration>()
            .expand((d) => d.boxShadow ?? const <BoxShadow>[]),
        isEmpty,
      );
    });
  });

  testWidgets('紅色描邊的次要鈕用系統紅 1.5（Main.dc.html:1845）', (tester) async {
    await tester.pumpWidget(
      host(
        const TmsPillButton(
          label: '撤回工務單',
          style: TmsPillButtonStyle.dangerOutlined,
          onPressed: _noop,
        ),
      ),
    );
    final border = tester
        .widgetList<DecoratedBox>(find.byType(DecoratedBox))
        .map((d) => d.decoration)
        .whereType<BoxDecoration>()
        .map((d) => d.border)
        .whereType<Border>()
        .first;
    expect(border.top.color, TmsColors.systemRed);
    expect(border.top.width, 1.5);
  });
}

void _noop() {}

/// 送出鈕底下那顆純文字鈕的替身，用來量兩顆之間的間距。
class _Forgot extends StatelessWidget {
  const _Forgot();

  @override
  Widget build(BuildContext context) {
    return const SizedBox(height: 44);
  }
}
