import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/cupertino.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_callout.dart';

/// 狀態列是任務頁與工務單詳情共用的提示元件（`Main.dc.html:1221-1245` 與
/// `Main.dc.html:1980-1991`）。這組測試盯的是三件事：一個畫面只會出現一條狀態
/// 列而且是最該管的那一條、六種語氣的顏色沒有串位、人手打的原因不會被當成系統
/// 說明。
void main() {
  const boundaryKey = Key('callout-boundary');
  const width = 375.0;

  Widget host(Widget child) {
    return CupertinoApp(
      home: Align(
        alignment: Alignment.topLeft,
        child: RepaintBoundary(
          key: boundaryKey,
          child: SizedBox(width: width, child: child),
        ),
      ),
    );
  }

  Widget slot(List<TmsCalloutSpec?> candidates) {
    return host(TmsCalloutSlot(candidates: candidates));
  }

  TmsStatusCallout status(
    TmsCalloutKind kind,
    TmsCalloutTone tone,
    String title, {
    String? reason,
    List<TmsCalloutAction> actions = const <TmsCalloutAction>[],
  }) {
    return TmsStatusCallout(
      kind: kind,
      tone: tone,
      title: title,
      body: '$title 的說明',
      reason: reason,
      actions: actions,
    );
  }

  /// 畫面上某一點實際畫出來的顏色。
  Future<Color> colorAt(WidgetTester tester, Offset at) async {
    final boundary = tester.renderObject<RenderRepaintBoundary>(
      find.byKey(boundaryKey),
    );
    final bytes = await tester.runAsync(() async {
      final image = await boundary.toImage(pixelRatio: 1);
      final data = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
      image.dispose();
      return data!.buffer.asUint8List();
    });
    final offset = ((at.dy.round() * width.round()) + at.dx.round()) * 4;
    final rgba = Uint8List.sublistView(bytes!, offset, offset + 4);
    return Color.fromARGB(rgba[3], rgba[0], rgba[1], rgba[2]);
  }

  /// 狀態列左側那顆圓形圖示。`CustomPaint` 在 `CupertinoApp` 底下還有別的，
  /// 所以照尺寸認。
  final toneIcon = find.byWidgetPredicate(
    (widget) =>
        widget is CustomPaint &&
        widget.size == const Size(TmsSize.calloutIcon, TmsSize.calloutIcon),
  );

  /// 狀態列裡自己畫的圖形，由上而下：圓形圖示，以及文字鍵後面的箭頭。
  final paintedShapes = find.descendant(
    of: find.byType(TmsCalloutSlot),
    matching: find.byType(CustomPaint),
  );

  Offset iconCenter(WidgetTester tester) => tester.getCenter(toneIcon);

  testWidgets('同時成立好幾條時只畫優先序最前的那一條', (tester) async {
    await tester.pumpWidget(
      slot([
        status(TmsCalloutKind.taskHeld, TmsCalloutTone.warn, '請勿打擾'),
        status(TmsCalloutKind.taskReturned, TmsCalloutTone.error, '查房退回'),
        status(TmsCalloutKind.taskCancelled, TmsCalloutTone.neutral, '這單已取消'),
      ]),
    );

    expect(find.text('查房退回'), findsOneWidget);
    expect(find.text('請勿打擾'), findsNothing);
    expect(find.text('這單已取消'), findsNothing);
  });

  testWidgets('候選給的順序不影響挑出來的那一條', (tester) async {
    await tester.pumpWidget(
      slot([
        status(TmsCalloutKind.taskFinished, TmsCalloutTone.success, '查房通過'),
        status(TmsCalloutKind.taskHeld, TmsCalloutTone.warn, '請勿打擾'),
      ]),
    );

    expect(find.text('請勿打擾'), findsOneWidget);
  });

  testWidgets('工務單詳情的優先序是暫停最前、急件最後', (tester) async {
    await tester.pumpWidget(
      slot([
        status(TmsCalloutKind.ticketUrgent, TmsCalloutTone.error, '急件'),
        status(TmsCalloutKind.ticketPaused, TmsCalloutTone.warn, '已暫停'),
      ]),
    );

    expect(find.text('已暫停'), findsOneWidget);
    expect(find.text('急件'), findsNothing);
  });

  testWidgets('一條都不成立時整塊不佔高度', (tester) async {
    await tester.pumpWidget(slot([null, null]));

    expect(tester.getSize(find.byType(TmsCalloutSlot)).height, 0);
  });

  testWidgets('兩個畫面的候選不能混在同一個位置', (tester) async {
    expect(
      () => TmsCalloutSlot.highestPriority([
        status(TmsCalloutKind.taskHeld, TmsCalloutTone.warn, '請勿打擾'),
        status(TmsCalloutKind.ticketPaused, TmsCalloutTone.warn, '已暫停'),
      ]),
      throwsAssertionError,
    );
  });

  /// 語氣對照表出自 `Main.dc.html:4299-4305`。底色是語氣的淡底，圖示那顆實心圓
  /// 是同色系的飽和色，兩者成對；串位的話同一種嚴重度在兩個畫面會是不同顏色。
  final tones = <TmsCalloutTone, List<Color>>{
    TmsCalloutTone.error: [TmsColors.errorContainer, TmsColors.systemRed],
    TmsCalloutTone.warn: [
      TmsColors.brandAccentContainer,
      TmsColors.systemOrange,
    ],
    TmsCalloutTone.caution: [
      TmsColors.brandAccentContainer,
      TmsColors.systemOrange,
    ],
    TmsCalloutTone.info: [TmsColors.brandContainer, TmsColors.brandPrimary],
    TmsCalloutTone.success: [TmsColors.successContainer, TmsColors.systemGreen],
    TmsCalloutTone.neutral: [
      TmsColors.neutralContainer,
      TmsColors.quaternaryLabel,
    ],
  };

  for (final entry in tones.entries) {
    testWidgets('${entry.key.name} 的底色與圖示顏色照原型', (tester) async {
      await tester.pumpWidget(
        slot([status(TmsCalloutKind.taskHeld, entry.key, '標題')]),
      );

      final box = tester.widget<Container>(
        find
            .descendant(
              of: find.byType(TmsCalloutSlot),
              matching: find.byType(Container),
            )
            .first,
      );
      final decoration = box.decoration! as BoxDecoration;
      expect(decoration.color, entry.value[0]);

      // 圓心左邊 9.5 點還在圓內（半徑約 11.9），也離中間的白色符號夠遠，不會
      // 取到符號邊緣的混色。
      final inCircle = await colorAt(
        tester,
        iconCenter(tester) + const Offset(-9.5, 0),
      );
      expect(inCircle, entry.value[1]);
    });
  }

  testWidgets('圖示中間的符號是白的', (tester) async {
    await tester.pumpWidget(
      slot([status(TmsCalloutKind.taskReturned, TmsCalloutTone.error, '查房退回')]),
    );

    expect(await colorAt(tester, iconCenter(tester)), TmsColors.brandOnPrimary);
  });

  testWidgets('圖示是 26 點見方', (tester) async {
    await tester.pumpWidget(
      slot([status(TmsCalloutKind.taskReturned, TmsCalloutTone.error, '查房退回')]),
    );

    expect(toneIcon, findsOneWidget);
  });

  testWidgets('人手打的原因自成一行、用正文黑字，說明段用次級字色', (tester) async {
    await tester.pumpWidget(
      slot([
        status(
          TmsCalloutKind.taskCancelled,
          TmsCalloutTone.neutral,
          '這單已取消',
          reason: '主管：住客延後退房，今天不用打掃',
        ),
      ]),
    );

    final reason = tester.widget<Text>(find.text('主管：住客延後退房，今天不用打掃'));
    expect(reason.style!.color, TmsColors.label);
    final body = tester.widget<Text>(find.text('這單已取消 的說明'));
    expect(body.style!.color, TmsColors.secondaryLabel);
    expect(body.style!.fontSize, 15);
  });

  testWidgets('沒有原因時不留空行', (tester) async {
    await tester.pumpWidget(
      slot([
        status(TmsCalloutKind.taskFinished, TmsCalloutTone.success, '查房通過'),
      ]),
    );

    expect(find.byType(Text), findsNWidgets(2));
  });

  testWidgets('文字鍵可以按，只有會換頁的那一顆帶箭頭', (tester) async {
    var resubmitted = 0;
    var opened = 0;
    await tester.pumpWidget(
      slot([
        status(
          TmsCalloutKind.taskHeld,
          TmsCalloutTone.warn,
          '請勿打擾',
          actions: [
            TmsCalloutAction(label: '補資料再送出', onPressed: () => resubmitted++),
            TmsCalloutAction(
              label: '查看工務單',
              showsChevron: true,
              onPressed: () => opened++,
            ),
          ],
        ),
      ]),
    );

    await tester.tap(find.text('補資料再送出'));
    await tester.tap(find.text('查看工務單'));
    expect(resubmitted, 1);
    expect(opened, 1);
    // 狀態列的圓形圖示一顆，「查看工務單」後面的箭頭一顆。
    expect(paintedShapes, findsNWidgets(2));
  });

  testWidgets('有狀態列時教學讓位', (tester) async {
    await tester.pumpWidget(
      slot([
        status(TmsCalloutKind.taskHeld, TmsCalloutTone.warn, '請勿打擾'),
        TmsTeachingCallout(
          title: '到房間門口再按開始',
          body: '系統會從那一刻起計時。',
          onDismiss: () {},
        ),
      ]),
    );

    expect(find.text('到房間門口再按開始'), findsNothing);
  });

  testWidgets('沒有狀態列時教學出現，右上角可以關掉', (tester) async {
    var dismissed = 0;
    await tester.pumpWidget(
      slot([
        null,
        TmsTeachingCallout(
          title: '到房間門口再按開始',
          body: '系統會從那一刻起計時。',
          onDismiss: () => dismissed++,
        ),
      ]),
    );

    expect(find.text('到房間門口再按開始'), findsOneWidget);
    await tester.tap(find.byIcon(CupertinoIcons.xmark));
    expect(dismissed, 1);
  });

  testWidgets('交辦引言：內文在上、出處在下，左側有品牌色色條', (tester) async {
    var acknowledged = 0;
    await tester.pumpWidget(
      host(
        TmsQuoteCallout(
          title: '交辦 1 則 · 開工前請先看',
          entries: const [
            TmsQuoteEntry(text: '浴缸排水慢，先別放水', meta: '交班單 · 阿美 · 09:30'),
          ],
          onAcknowledge: () => acknowledged++,
        ),
      ),
    );

    final text = tester.getTopLeft(find.text('浴缸排水慢，先別放水'));
    final meta = tester.getTopLeft(find.text('交班單 · 阿美 · 09:30'));
    expect(text.dy, lessThan(meta.dy));

    await tester.tap(find.text('知道了'));
    expect(acknowledged, 1);

    final card = tester.getRect(find.byType(TmsQuoteCallout));
    expect(
      await colorAt(tester, Offset(card.left + 2, card.center.dy)),
      TmsColors.brandPrimary,
    );
    // 色條只有 4 點寬，右邊就是白底。
    expect(
      await colorAt(tester, Offset(card.left + 6, card.center.dy)),
      TmsColors.brandOnPrimary,
    );
  });

  testWidgets('狀態摘要卡：圓形底隨狀態變色，符號白色', (tester) async {
    await tester.pumpWidget(
      host(
        const TmsStatusSummaryCard(
          glyph: TmsStatusSummaryGlyph.pause,
          glyphBackground: TmsColors.systemOrange,
          title: '工務 阿德 暫停中',
          detail: '原因：等零件。',
        ),
      ),
    );

    expect(find.byIcon(CupertinoIcons.pause_solid), findsOneWidget);
    final circle = find.ancestor(
      of: find.byIcon(CupertinoIcons.pause_solid),
      matching: find.byType(Container),
    );
    expect(
      tester.getSize(circle.first),
      const Size(TmsSize.statusSummaryIcon, TmsSize.statusSummaryIcon),
    );
    final decoration =
        tester.widget<Container>(circle.first).decoration! as BoxDecoration;
    expect(decoration.color, TmsColors.systemOrange);
    expect(decoration.shape, BoxShape.circle);
  });
}
