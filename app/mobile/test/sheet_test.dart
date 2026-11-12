import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_glass.dart';
import 'package:tms_staff_app/core/widgets/tms_motion.dart';
import 'package:tms_staff_app/core/widgets/tms_option_row.dart';
import 'package:tms_staff_app/core/widgets/tms_photo_cell.dart';
import 'package:tms_staff_app/core/widgets/tms_sheet.dart';

void main() {
  const safeBottom = 34.0;

  Widget host(Widget child) {
    return CupertinoApp(
      theme: buildTmsCupertinoTheme(),
      home: Builder(
        builder: (context) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            padding: const EdgeInsets.only(bottom: safeBottom),
          ),
          child: Align(alignment: Alignment.bottomCenter, child: child),
        ),
      ),
    );
  }

  testWidgets('面板有抓柄、置中標題與左上圓形關閉鈕，置底動作不跟著內容捲走',
      (tester) async {
    await tester.pumpWidget(
      host(
        TmsSheet(
          title: '延後原因',
          onLeadingTap: () {},
          bottomAction: TmsSheetButton(label: '確認', onPressed: () {}),
          children: const [Text('住客還在房內')],
        ),
      ),
    );

    expect(find.text('延後原因'), findsOneWidget);
    expect(find.text('確認'), findsOneWidget);
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Container &&
            widget.constraints?.maxWidth == 36 &&
            widget.constraints?.maxHeight == 5,
      ),
      findsOneWidget,
    );
    expect(find.text('關閉'), findsNothing);
    expect(find.byIcon(CupertinoIcons.xmark), findsOneWidget);
    expect(tester.getSize(find.byType(TmsGlass)), const Size(44, 44));
  });

  testWidgets('面板上緣是 38pt 圓角，而且帶上緣陰影', (tester) async {
    await tester.pumpWidget(
      host(
        const TmsSheet(title: '延後原因', children: [Text('住客還在房內')]),
      ),
    );

    final decoration = tester
        .widgetList<Container>(find.byType(Container))
        .map((container) => container.decoration)
        .whereType<BoxDecoration>()
        .firstWhere((box) => box.boxShadow != null && box.boxShadow!.isNotEmpty);
    expect(
      decoration.borderRadius,
      const BorderRadius.vertical(top: Radius.circular(TmsRadius.sheet)),
    );
    final shadow = decoration.boxShadow!.single;
    expect(shadow.color, const Color(0x2E000000));
    expect(shadow.offset, const Offset(0, -4));
    expect(shadow.blurRadius, 30);
  });

  testWidgets('左上角要退回上一步時畫的是返回箭頭', (tester) async {
    await tester.pumpWidget(
      host(
        TmsSheet(
          title: '其他原因',
          leading: TmsSheetLeading.back,
          onLeadingTap: () {},
          children: const [Text('請簡單說明原因')],
        ),
      ),
    );

    expect(find.byIcon(CupertinoIcons.chevron_back), findsOneWidget);
    expect(find.byIcon(CupertinoIcons.xmark), findsNothing);
  });

  testWidgets('沒有置底按鈕時，面板底部只留一次安全區', (tester) async {
    await tester.pumpWidget(
      host(
        const TmsSheet(title: '查一下', children: [Text('這間房有什麼要注意的？')]),
      ),
    );

    final sheetBottom = tester.getRect(find.byType(TmsSheet)).bottom;
    final contentBottom = tester.getRect(find.text('這間房有什麼要注意的？')).bottom;
    expect(sheetBottom - contentBottom, safeBottom);
  });

  testWidgets('置底按鈕列的底是漸層，底部留白是安全區本身', (tester) async {
    await tester.pumpWidget(
      host(
        TmsSheet(
          title: '報修',
          bottomAction: TmsSheetButton(label: '送出', onPressed: () {}),
          children: const [Text('冷氣')],
        ),
      ),
    );

    final sheetBottom = tester.getRect(find.byType(TmsSheet)).bottom;
    final buttonBottom = tester.getRect(find.byType(TmsSheetButton)).bottom;
    expect(sheetBottom - buttonBottom, safeBottom);

    final gradient = tester
        .widgetList<Container>(find.byType(Container))
        .map((container) => container.decoration)
        .whereType<BoxDecoration>()
        .map((box) => box.gradient)
        .whereType<LinearGradient>()
        .single;
    expect(gradient.colors.first.a, 0);
    expect(gradient.colors.last, TmsColors.groupedBackground);
  });

  testWidgets('置底按鈕不能按的時候底色是中灰，字仍然是白的', (tester) async {
    await tester.pumpWidget(
      host(
        const TmsSheet(
          title: '報修',
          bottomAction: TmsSheetButton(label: '送出', onPressed: null),
          children: [Text('冷氣')],
        ),
      ),
    );

    final fills = tester
        .widgetList<DecoratedBox>(
          find.descendant(
            of: find.byType(TmsSheetButton),
            matching: find.byType(DecoratedBox),
          ),
        )
        .map(
          (box) => switch (box.decoration) {
            final BoxDecoration d => d.color,
            final ShapeDecoration d => d.color,
            _ => null,
          },
        )
        .whereType<Color>();
    expect(fills, contains(const Color(0xFFC6C6C8)));
    final label = tester.widget<Text>(find.text('送出'));
    expect(label.style?.color, TmsColors.brandOnPrimary);
  });

  testWidgets('面板路由的遮罩濃度與升起時長照設計稿', (tester) async {
    final route = TmsSheetRoute<void>(
      builder: (_) => const TmsSheet(title: '查一下', children: []),
    );

    expect(route.barrierColor, const Color(0x52000000));
    expect(route.barrierDismissible, isTrue);
    expect(route.transitionDuration, TmsMotion.up.duration);
  });

  testWidgets('類別列只有選中的那一列有勾', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TmsOptionRow.category(
              title: const Text('冷氣'),
              selected: true,
              onTap: () {},
            ),
            TmsOptionRow.category(
              title: const Text('水電'),
              selected: false,
              onTap: () {},
            ),
          ],
        ),
      ),
    );

    expect(find.byType(TmsOptionCheck), findsOneWidget);
  });

  testWidgets('原因列畫不出勾', (tester) async {
    await tester.pumpWidget(
      host(
        TmsOptionRow.reason(title: const Text('請勿打擾'), onTap: () {}),
      ),
    );

    expect(find.byType(TmsOptionCheck), findsNothing);
  });

  testWidgets('照片格四態各畫各的，失敗時整格變成重試', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TmsPhotoCell(
              upload: const TmsPhotoFailed(),
              sizeLabel: '19 KB',
              onRetry: () {},
            ),
            const TmsPhotoCell(upload: TmsPhotoUploading(0.2)),
            const TmsPhotoCell(upload: TmsPhotoUploaded()),
          ],
        ),
      ),
    );

    expect(find.text('重試'), findsOneWidget);
    expect(find.byIcon(CupertinoIcons.arrow_clockwise), findsOneWidget);
    expect(find.text('19 KB'), findsOneWidget);
    expect(find.text('20%'), findsOneWidget);
    expect(find.byType(CupertinoActivityIndicator), findsNothing);
    expect(find.byIcon(CupertinoIcons.check_mark), findsOneWidget);
  });

  testWidgets('照片格在不准移除時不畫移除鈕', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TmsPhotoCell(onRemove: () {}),
            const TmsPhotoCell(),
          ],
        ),
      ),
    );

    expect(find.byIcon(CupertinoIcons.xmark), findsOneWidget);
  });
}
