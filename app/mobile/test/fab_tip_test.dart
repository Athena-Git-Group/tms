import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_fab.dart';
import 'package:tms_staff_app/core/widgets/tms_glass.dart';
import 'package:tms_staff_app/core/widgets/tms_tip_popover.dart';

/// 驗浮動按鈕與首次使用教學氣泡畫出來的是原型 iOS 26 那一版。
///
/// 這些值在原型裡是 inline style 與 logic script 算出來的數字，改錯了螢幕上
/// 看得出來但沒有人會紅，所以在這裡逐項釘住。
void main() {
  group('浮動按鈕', () {
    var taps = 0;

    setUp(() => taps = 0);

    Future<void> pumpFab(
      WidgetTester tester, {
      TmsFabGlyph glyph = TmsFabGlyph.plus,
    }) async {
      await tester.pumpWidget(
        CupertinoApp(
          home: Center(
            child: TmsFab(
              glyph: glyph,
              semanticsLabel: '開工單',
              onPressed: () => taps++,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('玻璃底板的模糊、飽和度、底色、邊框與陰影照原型', (tester) async {
      // Main.dc.html:4055-4056
      await pumpFab(tester);

      final glass = tester.widget<TmsGlass>(find.byType(TmsGlass));
      expect(glass.blur, 16);
      expect(glass.saturation, 180);
      expect(glass.color, const Color(0x9EFFFFFF));
      expect(glass.border, Border.all(color: const Color(0xCCFFFFFF)));
      expect(glass.boxShadow, <BoxShadow>[
        const BoxShadow(
          color: Color(0x29000000),
          offset: Offset(0, 8),
          blurRadius: 24,
        ),
      ]);
      expect(glass.borderRadius, BorderRadius.circular(TmsRadius.pill));
    });

    testWidgets('按鈕 56 見方，上緣疊著一條 1pt 的白色高光', (tester) async {
      // Main.dc.html:1030 — width/height 56px
      // Main.dc.html:4056 — inset 0 1px 0 rgba(255,255,255,.9)
      await pumpFab(tester);

      expect(tester.getSize(find.byType(TmsFab)), const Size(56, 56));

      final highlight = find.byWidgetPredicate(
        (widget) =>
            widget is ColoredBox && widget.color == TmsColors.fabHighlight,
      );
      expect(highlight, findsOneWidget);
      expect(tester.getSize(highlight).height, 1);
    });

    testWidgets('按鈕只有圖示，用途寫在無障礙標籤上', (tester) async {
      // Main.dc.html:1030 — aria-label 「開工單」，按鈕內只有一個 SVG
      await pumpFab(tester);

      expect(find.text('開工單'), findsNothing);
      expect(tester.getSemantics(find.byType(TmsFab)).label, contains('開工單'));

      await tester.tap(find.byType(TmsFab));
      expect(taps, 1);
    });

    testWidgets('加號 26 見方、筆畫 2.4，問號泡泡 24 見方、筆畫 2', (tester) async {
      // Main.dc.html:1031、1346
      await pumpFab(tester);
      expect(tester.getSize(find.byType(CustomPaint).last), const Size(26, 26));

      await pumpFab(tester, glyph: TmsFabGlyph.ask);
      expect(tester.getSize(find.byType(CustomPaint).last), const Size(24, 24));

      expect(TmsFabGlyphPainter.plusStrokeWidth, 2.4);
      expect(TmsFabGlyphPainter.askStrokeWidth, 2);
      expect(TmsFabGlyphPainter.strokeColor, const Color(0xFF0C7680));
    });

    test('浮在分頁列上方時，下緣位置與原型同一條算式', () {
      // Main.dc.html:4051 — max(safeBottom - 13, 10) + 62 + 16
      expect(TmsFab.bottomAboveTabBar(34), 99);
      expect(TmsFab.bottomAboveTabBar(0), 88);
    });

    test('浮在置底動作列上方時，沒有動作列才自己讓開底部安全區', () {
      // Main.dc.html:5027 — hasPrimary ? 16 : safeBottom + 16
      expect(
        TmsFab.bottomAboveActionBar(
          hasActionBar: true,
          bottomSafeAreaInset: 34,
        ),
        16,
      );
      expect(
        TmsFab.bottomAboveActionBar(
          hasActionBar: false,
          bottomSafeAreaInset: 34,
        ),
        50,
      );
    });

    test('被按鈕蓋住的捲動區底部留白是按鈕下緣再加 72', () {
      // Main.dc.html:5028
      expect(TmsFab.scrollPadBottom(16), 88);
    });
  });

  group('首次使用教學氣泡', () {
    var dismissed = 0;

    setUp(() => dismissed = 0);

    Future<void> pumpTip(WidgetTester tester, {double width = 260}) async {
      await tester.pumpWidget(
        CupertinoApp(
          home: Center(
            child: TmsTipPopover(
              title: '在這裡開工單',
              message: '巡檢時發現問題，拍照就能直接開單。',
              width: width,
              onDismiss: () => dismissed++,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('白卡的寬度、圓角與陰影照原型', (tester) async {
      // Main.dc.html:1037 — width 260、border-radius 16、
      // box-shadow 0 10px 30px rgba(0,0,0,.18)
      await pumpTip(tester);

      expect(tester.getSize(find.byType(TmsTipPopover)).width, 260);

      final decoration =
          tester
                  .widget<Container>(
                    find.descendant(
                      of: find.byType(TmsTipPopover),
                      matching: find.byType(Container),
                    ),
                  )
                  .decoration!
              as BoxDecoration;
      expect(decoration.color, TmsColors.cardBackground);
      expect(decoration.borderRadius, BorderRadius.circular(16));
      expect(decoration.boxShadow, <BoxShadow>[
        const BoxShadow(
          color: Color(0x2E000000),
          offset: Offset(0, 10),
          blurRadius: 30,
        ),
      ]);
    });

    testWidgets('標題 17/600、說明 15 行高 1.4 的 #3C3C43', (tester) async {
      // Main.dc.html:1038-1040
      await pumpTip(tester);

      final title = tester.widget<Text>(find.text('在這裡開工單'));
      expect(title.style!.fontSize, 17);
      expect(title.style!.fontWeight, FontWeight.w600);

      final message = tester.widget<Text>(find.text('巡檢時發現問題，拍照就能直接開單。'));
      expect(message.style!.fontSize, 15);
      expect(message.style!.height, 1.4);
      expect(message.style!.color, const Color(0xFF3C3C43));
    });

    testWidgets('文字離卡片上緣 14、左緣 14', (tester) async {
      // Main.dc.html:1037-1038 — padding 12px 8px 12px 14px，文字欄再自己加 2
      await pumpTip(tester);

      final card = tester.getRect(find.byType(TmsTipPopover));
      final title = tester.getTopLeft(find.text('在這裡開工單'));
      expect(title.dy - card.top, 14);
      expect(title.dx - card.left, 14);
    });

    testWidgets('× 的觸控範圍 44 見方，按下去就回報看過了', (tester) async {
      // Main.dc.html:1041 — 44×44，內含 13 見方的 ×
      await pumpTip(tester);

      final close = find.byType(CupertinoButton);
      expect(tester.getSize(close), const Size(44, 44));
      expect(tester.getSemantics(close).label, contains('關閉提示'));

      await tester.tap(close);
      expect(dismissed, 1);
    });

    testWidgets('箭頭尖是 14 見方、轉 45 度，壓在卡片右下角外面', (tester) async {
      // Main.dc.html:1045 — right 22、bottom -6
      await pumpTip(tester);

      final arrow = find.byWidgetPredicate(
        // 轉 45 度之後第一個係數是 cos45，不再是 1。
        (widget) => widget is Transform && widget.transform.storage[0] != 1.0,
      );
      expect(arrow, findsOneWidget);
      expect(tester.getSize(arrow), const Size(14, 14));

      final card = tester.getRect(find.byType(TmsTipPopover));
      final center = tester.getCenter(arrow);
      expect(card.right - center.dx, 29);
      expect(center.dy - card.bottom, -1);
    });

    test('氣泡下緣落在它介紹的那顆按鈕上方 12', () {
      // Main.dc.html:4052 — tipBottom = 按鈕下緣 + 56 + 12
      expect(TmsTipPopover.bottomAbove(99, TmsFab.diameter), 167);
    });
  });
}
