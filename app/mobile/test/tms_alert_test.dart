import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_alert.dart';
import 'package:tms_staff_app/core/widgets/tms_glass.dart';

void main() {
  Future<void> pumpAlert(WidgetTester tester, TmsAlert alert) {
    return tester.pumpWidget(CupertinoApp(home: alert));
  }

  TmsAlert sampleAlert({
    String? title,
    String? messageId,
    List<TmsAlertAction>? actions,
  }) {
    return TmsAlert(
      title: title,
      message: '這單已被王小明認領',
      messageId: messageId,
      actions:
          actions ??
          [TmsAlertAction(label: '知道了', onPressed: () {})],
    );
  }

  group('版型', () {
    testWidgets('寬 300、圓角 34、半透明白底、模糊 30 飽和 180', (tester) async {
      await pumpAlert(tester, sampleAlert());

      expect(tester.getSize(find.byType(TmsGlass)).width, 300);
      final glass = tester.widget<TmsGlass>(find.byType(TmsGlass));
      expect(glass.borderRadius, BorderRadius.circular(34));
      expect(glass.color, const Color(0xD1FFFFFF));
      expect(glass.blur, 30);
      expect(glass.saturation, 180);
    });

    testWidgets('對話框陰影照原型', (tester) async {
      await pumpAlert(tester, sampleAlert());

      final glass = tester.widget<TmsGlass>(find.byType(TmsGlass));
      expect(glass.boxShadow, [
        const BoxShadow(
          color: Color(0x40000000),
          offset: Offset(0, 20),
          blurRadius: 50,
        ),
      ]);
    });

    testWidgets('文字區內距 22/22/22/6，按鈕區 16/14/16/16', (tester) async {
      await pumpAlert(tester, sampleAlert(title: '無法登出'));

      final insets = tester
          .widgetList<Padding>(find.byType(Padding))
          .map((p) => p.padding)
          .toList();
      expect(insets, contains(const EdgeInsets.fromLTRB(22, 22, 22, 6)));
      expect(insets, contains(const EdgeInsets.fromLTRB(16, 14, 16, 16)));
    });

    testWidgets('標題 17/600、內文 15 行高 1.4，兩段都靠左', (tester) async {
      await pumpAlert(tester, sampleAlert(title: '無法登出'));

      final title = tester.widget<Text>(find.text('無法登出'));
      expect(title.style!.fontSize, 17);
      expect(title.style!.fontWeight, FontWeight.w600);
      expect(title.textAlign, TextAlign.left);

      final message = tester.widget<Text>(find.text('這單已被王小明認領'));
      expect(message.style!.fontSize, 15);
      expect(message.style!.height, 1.4);
      expect(message.textAlign, TextAlign.left);
    });

    testWidgets('沒有標題時只畫內文', (tester) async {
      await pumpAlert(tester, sampleAlert());

      expect(find.byType(Text), findsNWidgets(2));
      expect(find.text('這單已被王小明認領'), findsOneWidget);
      expect(find.text('知道了'), findsOneWidget);
    });
  });

  group('按鈕', () {
    testWidgets('膠囊外型、高 48，兩顆之間留 10', (tester) async {
      await pumpAlert(
        tester,
        sampleAlert(
          actions: [
            TmsAlertAction(label: '取消', onPressed: () {}),
            TmsAlertAction(
              label: '登出',
              style: TmsAlertActionStyle.destructive,
              onPressed: () {},
            ),
          ],
        ),
      );

      final buttons = tester.widgetList<CupertinoButton>(
        find.byType(CupertinoButton),
      );
      for (final button in buttons) {
        expect(button.borderRadius, BorderRadius.circular(999));
        expect(button.minimumSize!.height, 48);
      }
      final cancel = tester.getRect(find.widgetWithText(CupertinoButton, '取消'));
      final logout = tester.getRect(find.widgetWithText(CupertinoButton, '登出'));
      expect(logout.left - cancel.right, 10);
      expect(cancel.width, logout.width);
      expect(cancel.height, 48);
    });

    testWidgets('次要鈕半透明灰底品牌色字 400', (tester) async {
      await pumpAlert(tester, sampleAlert());

      final button = tester.widget<CupertinoButton>(
        find.byType(CupertinoButton),
      );
      expect(button.color, const Color(0x29787880));
      final label = tester.widget<Text>(find.text('知道了'));
      expect(label.style!.color, const Color(0xFF0C7680));
      expect(label.style!.fontWeight, FontWeight.w400);
      expect(label.style!.fontSize, 17);
    });

    testWidgets('主要鈕品牌色實心白字 600', (tester) async {
      await pumpAlert(
        tester,
        sampleAlert(
          actions: [
            TmsAlertAction(
              label: '確定',
              style: TmsAlertActionStyle.primary,
              onPressed: () {},
            ),
          ],
        ),
      );

      final button = tester.widget<CupertinoButton>(
        find.byType(CupertinoButton),
      );
      expect(button.color, const Color(0xFF0C7680));
      final label = tester.widget<Text>(find.text('確定'));
      expect(label.style!.color, const Color(0xFFFFFFFF));
      expect(label.style!.fontWeight, FontWeight.w600);
    });

    testWidgets('破壞性鈕紅字 600，底色與次要鈕相同', (tester) async {
      await pumpAlert(
        tester,
        sampleAlert(
          actions: [
            TmsAlertAction(
              label: '登出',
              style: TmsAlertActionStyle.destructive,
              onPressed: () {},
            ),
          ],
        ),
      );

      final button = tester.widget<CupertinoButton>(
        find.byType(CupertinoButton),
      );
      expect(button.color, const Color(0x29787880));
      final label = tester.widget<Text>(find.text('登出'));
      expect(label.style!.color, const Color(0xFFFF3B30));
      expect(label.style!.fontWeight, FontWeight.w600);
    });

    testWidgets('按下去會叫到呼叫端給的動作', (tester) async {
      var tapped = 0;
      await pumpAlert(
        tester,
        sampleAlert(
          actions: [TmsAlertAction(label: '知道了', onPressed: () => tapped++)],
        ),
      );

      await tester.tap(find.text('知道了'));
      await tester.pumpAndSettle();
      expect(tapped, 1);
    });
  });

  group('accessibility id', () {
    testWidgets('內文與按鈕各自掛得上 id，內文讀得到訊息文字', (tester) async {
      await pumpAlert(
        tester,
        sampleAlert(
          messageId: 'engPool.claim-conflict-alert',
          actions: [
            TmsAlertAction(
              label: '知道了',
              id: 'engPool.claim-conflict-alert.dismiss-button',
              onPressed: () {},
            ),
          ],
        ),
      );

      final message = find.bySemanticsIdentifier(
        'engPool.claim-conflict-alert',
      );
      expect(message, findsOneWidget);
      expect(
        tester.getSemantics(message).label,
        '這單已被王小明認領',
      );
      expect(
        find.bySemanticsIdentifier(
          'engPool.claim-conflict-alert.dismiss-button',
        ),
        findsOneWidget,
      );
    });

    testWidgets('沒有給 id 時不多掛節點', (tester) async {
      await pumpAlert(tester, sampleAlert());

      expect(find.bySemanticsIdentifier('engPool.claim-conflict-alert'),
          findsNothing);
    });
  });

  group('圓角 token', () {
    test('Alert 與整頁對話卡各有自己的圓角', () {
      expect(TmsRadius.alert, 34);
      expect(TmsRadius.dialog, 36);
    });
  });
}
