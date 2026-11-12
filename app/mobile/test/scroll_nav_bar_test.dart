import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_glass.dart';
import 'package:tms_staff_app/core/widgets/tms_scroll_nav_bar.dart';

void main() {
  Widget host({
    required bool scrolled,
    Color restingBackground = TmsColors.brandPrimary,
    Color restingForeground = TmsColors.brandOnPrimary,
    String restingTitle = '打掃任務',
    String scrolledTitle = '305 · 12:34',
    String? titleSuffix,
    Widget? trailing,
  }) {
    return CupertinoApp(
      home: TmsScrollNavBar(
        scrolled: scrolled,
        restingBackground: restingBackground,
        restingForeground: restingForeground,
        restingTitle: restingTitle,
        scrolledTitle: scrolledTitle,
        titleSuffix: titleSuffix,
        leading: TmsNavBarBackButton(onPressed: () {}),
        trailing: trailing,
      ),
    );
  }

  group('凍結導覽列（Main.dc.html:1147-1161、5034-5044）', () {
    testWidgets('靜止時與標題區同底色，寫的是未捲動那一組標題', (tester) async {
      await tester.pumpWidget(host(scrolled: false));

      expect(find.text('打掃任務'), findsOneWidget);
      expect(find.text('305 · 12:34'), findsNothing);

      final background = tester.widget<ColoredBox>(
        find.descendant(
          of: find.byType(TmsScrollNavBar),
          matching: find.byType(ColoredBox),
        ),
      );
      expect(background.color, TmsColors.brandPrimary);
      expect(find.byType(TmsGlass).evaluate().length, 1);
    });

    testWidgets('捲過門檻後換毛玻璃，模糊 12、飽和度 160（Main.dc.html:5040）', (tester) async {
      await tester.pumpWidget(host(scrolled: true));

      expect(find.text('305 · 12:34'), findsOneWidget);
      expect(find.text('打掃任務'), findsNothing);

      final glass = tester.widget<TmsGlass>(
        find
            .descendant(
              of: find.byType(TmsScrollNavBar),
              matching: find.byType(TmsGlass),
            )
            .first,
      );
      expect(glass.blur, 12);
      expect(glass.saturation, 160);
    });

    testWidgets('超時那一段接在標題後面，用白底的超時色（Main.dc.html:5037）', (tester) async {
      await tester.pumpWidget(host(scrolled: true, titleSuffix: ' 超時'));

      final text = tester.widget<Text>(find.byType(Text).first);
      final span = text.textSpan! as TextSpan;
      expect(span.text, '305 · 12:34');
      final tail = span.children!.single as TextSpan;
      expect(tail.text, ' 超時');
      expect(tail.style!.color, TmsColors.overtimeTimerOnLight);
    });

    testWidgets('超時那一段靜止時不顯示', (tester) async {
      await tester.pumpWidget(host(scrolled: false, titleSuffix: ' 超時'));

      expect(find.text('打掃任務'), findsOneWidget);
      final text = tester.widget<Text>(find.text('打掃任務'));
      expect(text.textSpan, isNull);
    });

    testWidgets('標題用等寬數字，計時逐秒跳動時字不會左右晃（Main.dc.html:1153）', (tester) async {
      await tester.pumpWidget(host(scrolled: true));

      final text = tester.widget<Text>(find.byType(Text).first);
      expect(text.style!.fontSize, 17);
      expect(text.style!.fontWeight, FontWeight.w600);
      expect(text.style!.fontFeatures, TmsText.tabularNumbers);
    });
  });

  group('導覽列圓鈕（Main.dc.html:1150-1158、4455-4457）', () {
    testWidgets('44×44 圓鈕，深色標題區上底色壓到兩成', (tester) async {
      await tester.pumpWidget(host(scrolled: false));

      final glass = tester.widget<TmsGlass>(find.byType(TmsGlass));
      expect(glass.color, TmsColors.navButtonSurfaceOnDark);
      expect(glass.blur, 12);
      expect(glass.saturation, 180);

      final size = tester.getSize(find.byType(TmsNavBarBackButton));
      expect(size.width, greaterThanOrEqualTo(TmsSize.minTouchTarget));
      expect(size.height, greaterThanOrEqualTo(TmsSize.minTouchTarget));
    });

    testWidgets('淺色標題區上底色換成接近不透明的白', (tester) async {
      await tester.pumpWidget(
        host(
          scrolled: false,
          restingBackground: TmsColors.cardBackground,
          restingForeground: TmsColors.label,
        ),
      );

      final glass = tester.widget<TmsGlass>(find.byType(TmsGlass));
      expect(glass.color, TmsColors.navButtonSurface);
    });

    testWidgets('返回鈕只有箭頭，沒有「返回」兩個字，但讀得到無障礙標籤', (tester) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(host(scrolled: false));

      expect(find.text('返回'), findsNothing);
      expect(find.bySemanticsLabel('返回'), findsOneWidget);
      expect(find.bySemanticsIdentifier('nav.back-button'), findsOneWidget);
      handle.dispose();
    });

    testWidgets('右側項目的圖示跟著導覽列前景色，不必自己指定顏色', (tester) async {
      await tester.pumpWidget(
        host(
          scrolled: false,
          trailing: TmsNavBarButton(
            semanticsLabel: '報修',
            onPressed: () {},
            child: const Icon(CupertinoIcons.wrench),
          ),
        ),
      );

      final iconTheme = IconTheme.of(
        tester.element(find.byIcon(CupertinoIcons.wrench)),
      );
      expect(iconTheme.color, TmsColors.brandOnPrimary);
      expect(iconTheme.size, TmsSize.navBarItemIcon);
    });
  });

  group('捲動門檻（Main.dc.html:5044）', () {
    Widget tracked(ScrollController controller) {
      return CupertinoApp(
        home: TmsScrollNavBarScroll(
          builder: (context, scrolled) => Stack(
            children: [
              ListView(
                controller: controller,
                children: <Widget>[
                  for (var i = 0; i < 40; i++)
                    const SizedBox(height: 40, child: Text('列')),
                ],
              ),
              Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: TmsScrollNavBar(
                  scrolled: scrolled,
                  restingBackground: TmsColors.brandPrimary,
                  restingForeground: TmsColors.brandOnPrimary,
                  restingTitle: '打掃任務',
                  scrolledTitle: '305 · 打掃任務',
                ),
              ),
            ],
          ),
        ),
      );
    }

    testWidgets('捲到 72 還算靜止，超過才換成捲動後那一組', (tester) async {
      final controller = ScrollController();
      addTearDown(controller.dispose);
      await tester.pumpWidget(tracked(controller));

      expect(find.text('打掃任務'), findsOneWidget);

      controller.jumpTo(72);
      await tester.pump();
      expect(find.text('打掃任務'), findsOneWidget);

      controller.jumpTo(73);
      await tester.pump();
      expect(find.text('305 · 打掃任務'), findsOneWidget);

      controller.jumpTo(0);
      await tester.pump();
      expect(find.text('打掃任務'), findsOneWidget);
    });
  });
}
