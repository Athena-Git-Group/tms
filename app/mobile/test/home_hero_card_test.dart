import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_home_cards.dart';
import 'package:tms_staff_app/core/widgets/tms_motion.dart';
import 'package:tms_staff_app/core/widgets/tms_progress_ring.dart';

/// 首頁兩張主卡（房務與工務共用）盯的是幾件「原型有、照抄時最容易漏掉」的事：
/// 超時的三個表現要一起出現、「下一間」右上角那塊旋轉方塊、卡內統一的間距，
/// 以及進行中那顆圓點真的在呼吸。
void main() {
  Widget host(Widget child) {
    return CupertinoApp(
      home: Align(
        alignment: Alignment.topLeft,
        child: SizedBox(width: 402, child: child),
      ),
    );
  }

  TextStyle styleOf(WidgetTester tester, String text) =>
      tester.widget<Text>(find.text(text)).style!;

  group('進行中卡的計時欄（Main.dc.html:174-182）', () {
    testWidgets('超時時計時轉黃、長出深橘膠囊，分鐘數跟計時同一套捨去', (tester) async {
      // 已做 40 分 30 秒、預估 35 分：已做分鐘減預估分鐘得 5
      // （`specs/truth/features/ios-app/任務執行/dsl.md:46`）。
      await tester.pumpWidget(
        host(
          const TmsHeroTimer(
            workedMinutes: 40,
            workedSeconds: 2430,
            estMin: 35,
          ),
        ),
      );

      expect(find.text('40:30'), findsOneWidget);
      expect(styleOf(tester, '40:30').color, TmsColors.overtimeTimerOnDark);

      expect(find.text('超時 5 分'), findsOneWidget);
      final capsule = tester.widget<Container>(
        find
            .ancestor(of: find.text('超時 5 分'), matching: find.byType(Container))
            .first,
      );
      final decoration = capsule.decoration! as BoxDecoration;
      expect(decoration.color, TmsColors.overtimeCapsule);
      expect(
        capsule.padding,
        const EdgeInsets.symmetric(
          horizontal: TmsSpacing.sm,
          vertical: TmsSpacing.xxs,
        ),
      );
      final capsuleText = styleOf(tester, '超時 5 分');
      expect(capsuleText.color, TmsColors.onOvertimeCapsule);
      expect(capsuleText.fontSize, 12);
      expect(capsuleText.fontWeight, FontWeight.w800);
    });

    testWidgets('剛跨過預估的那一秒就算超時，顯示的是 1 分不是 0 分', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsHeroTimer(
            workedMinutes: 35,
            workedSeconds: 2101,
            estMin: 35,
          ),
        ),
      );

      expect(find.text('超時 1 分'), findsOneWidget);
    });

    testWidgets('沒超時的計時是白的，也沒有膠囊', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsHeroTimer(workedMinutes: 12, workedSeconds: 754, estMin: 35),
        ),
      );

      expect(find.text('12:34'), findsOneWidget);
      expect(styleOf(tester, '12:34').color, TmsColors.brandOnPrimary);
      expect(find.textContaining('超時'), findsNothing);
    });

    testWidgets('超過一小時才補上小時那一段（Main.dc.html:3477）', (tester) async {
      await tester.pumpWidget(
        host(const TmsHeroTimer(workedMinutes: 63, workedSeconds: 3785)),
      );

      expect(find.text('1:03:05'), findsOneWidget);
    });

    testWidgets('後端還沒給走秒的欄位時只講已做分鐘，不補成 35:00', (tester) async {
      await tester.pumpWidget(
        host(const TmsHeroTimer(workedMinutes: 42, estMin: 35)),
      );

      expect(find.text('42 分'), findsOneWidget);
      // 分鐘層級仍判得出超時，只是進位的精度退到分。
      expect(find.text('超時 7 分'), findsOneWidget);
    });
  });

  group('主卡（Main.dc.html:164-199）', () {
    testWidgets('「下一間」右上角是 64 見方、傾斜 10 度的品牌色方塊', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsHeroCard(
            eyebrow: '下一間',
            ornamentIcon: CupertinoIcons.bed_double,
            children: [Text('305')],
          ),
        ),
      );

      final icon = find.byIcon(CupertinoIcons.bed_double);
      expect(icon, findsOneWidget);
      expect(tester.widget<Icon>(icon).size, 34);
      expect(tester.widget<Icon>(icon).color, TmsColors.brandOnPrimary);

      final square = find
          .ancestor(of: icon, matching: find.byType(Container))
          .first;
      expect(tester.getSize(square), const Size(64, 64));
      final decoration =
          tester.widget<Container>(square).decoration! as BoxDecoration;
      expect(decoration.color, TmsColors.brandPrimary);
      expect(decoration.borderRadius, BorderRadius.circular(20));

      // 傾斜 10 度：旋轉矩陣的 cos 項。
      final rotation = tester.widget<Transform>(
        find.ancestor(of: square, matching: find.byType(Transform)).first,
      );
      expect(rotation.transform.getRow(0)[0], closeTo(0.9848, 0.001));
    });

    testWidgets('進行中那張沒有方塊', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsHeroCard(
            onDark: true,
            eyebrow: '進行中 · 打掃',
            children: [Text('305')],
          ),
        ),
      );

      expect(find.byIcon(CupertinoIcons.bed_double), findsNothing);
    });

    testWidgets('進行中那顆圓點走原型的 1.4 秒呼吸（tmsPulse）', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsHeroCard(
            onDark: true,
            eyebrow: '進行中 · 打掃',
            children: [Text('305')],
          ),
        ),
      );

      final motion = tester.widget<TmsMotionView>(find.byType(TmsMotionView));
      expect(motion.spec, TmsMotion.pulse);
    });

    testWidgets('卡內每一段之間一律 14，呼叫端不必自己夾間距', (tester) async {
      const first = Key('hero-first');
      const second = Key('hero-second');
      await tester.pumpWidget(
        host(
          const TmsHeroCard(
            eyebrow: '下一間',
            children: [
              SizedBox(key: first, height: 20, width: 20),
              SizedBox(key: second, height: 20, width: 20),
            ],
          ),
        ),
      );

      final gap =
          tester.getTopLeft(find.byKey(second)).dy -
          tester.getBottomLeft(find.byKey(first)).dy;
      expect(gap, TmsSpacing.md2);
    });
  });

  group('進度環卡（Main.dc.html:135-158）', () {
    testWidgets('環與右欄之間 18、統計格的說明離數字 1', (tester) async {
      const ringCenter = Key('ring-center');
      const details = Key('ring-details');
      const value = Key('stat-value');
      await tester.pumpWidget(
        host(
          const TmsProgressRingCard(
            progress: 0.5,
            ringCenter: SizedBox(key: ringCenter, height: 10, width: 10),
            details: TmsStatBlock(
              key: details,
              value: SizedBox(key: value, height: 20, width: 20),
              caption: '查房通過',
            ),
          ),
        ),
      );

      final ringRight = tester.getTopRight(find.byType(TmsProgressRing)).dx;
      final detailsLeft = tester.getTopLeft(find.byKey(details)).dx;
      expect(detailsLeft - ringRight, TmsSpacing.lg2);

      final captionTop = tester.getTopLeft(find.text('查房通過')).dy;
      expect(captionTop - tester.getBottomLeft(find.byKey(value)).dy, 1);
    });
  });
}
