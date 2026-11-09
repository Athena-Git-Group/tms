import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_glass.dart';
import 'package:tms_staff_app/core/widgets/tms_motion.dart';
import 'package:tms_staff_app/core/widgets/tms_option_row.dart';
import 'package:tms_staff_app/core/widgets/tms_photo_cell.dart';
import 'package:tms_staff_app/core/widgets/tms_sheet.dart';

/// 面板外框與面板裡那兩個零件的行為測試。
///
/// 這三個東西被三個面板共用，壞掉的話是三個畫面一起壞，而面板只有在模擬器上
/// 跑整個流程才看得到，所以在這裡先釘住幾件一看就知道有沒有壞的事。
void main() {
  /// 面板貼在畫面底部，底部安全區照 iPhone 的 34pt 算：底部留白的算式只有在
  /// 安全區不是 0 的機型上才看得出算錯。
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
    // 抓柄是 36 × 5 的小圓條，面板上只有它這一個尺寸。
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Container &&
            widget.constraints?.maxWidth == 36 &&
            widget.constraints?.maxHeight == 5,
      ),
      findsOneWidget,
    );
    // 左上角是 44pt 圓形玻璃鈕，裡面畫 ✕，不是一顆文字鈕（`Main.dc.html:1428`）。
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

    // 面板本體是整支外框唯一有上緣陰影的那一層（`Main.dc.html:4932`）。
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

    // 安全區之外再加一次 16 的話，有瀏海的機型會比設計稿多留 16pt
    //（`Main.dc.html:4935`）。
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

    // 捲到按鈕後面的內容要淡掉而不是被硬切，所以按鈕列的底是上緣透明的漸層
    //（`Main.dc.html:1562`）。
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

    // 不能按的時候底色是固定的中灰（`Main.dc.html:4941`），框架自己的停用淡色
    // 跟設計稿對不上。底色由膠囊按鈕內層畫，不假設它落在哪一層 decoration。
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

    // 框架預設的遮罩比設計稿淡（`Main.dc.html:1414`），升起也比設計稿慢
    //（`Main.dc.html:29`）。
    expect(route.barrierColor, const Color(0x52000000));
    expect(route.barrierDismissible, isTrue);
    expect(route.transitionDuration, TmsMotion.up.duration);
  });

  testWidgets('選項列只有選中的那一列有勾', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TmsOptionRow(
              title: const Text('請勿打擾'),
              selected: true,
              onTap: () {},
            ),
            TmsOptionRow(
              title: const Text('住客還在房內'),
              onTap: () {},
            ),
          ],
        ),
      ),
    );

    expect(find.byIcon(CupertinoIcons.check_mark), findsOneWidget);
  });

  testWidgets('照片格四態各畫各的，失敗時才出現重試', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TmsPhotoCell(
              state: TmsPhotoState.failed,
              watermark: const Text('305 · 10:12'),
              failureAction: CupertinoButton(
                onPressed: () {},
                child: const Text('重試'),
              ),
            ),
            const TmsPhotoCell(state: TmsPhotoState.uploading),
            const TmsPhotoCell(state: TmsPhotoState.done),
          ],
        ),
      ),
    );

    expect(find.text('重試'), findsOneWidget);
    expect(find.text('305 · 10:12'), findsOneWidget);
    expect(find.byType(CupertinoActivityIndicator), findsOneWidget);
    // 完成態的綠角標。
    expect(find.byIcon(CupertinoIcons.check_mark), findsOneWidget);
  });
}
