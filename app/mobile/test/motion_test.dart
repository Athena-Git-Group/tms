import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/widgets/tms_motion.dart';
import 'package:tms_staff_app/core/widgets/tms_progress_ring.dart';

/// 這組測試盯的是動畫的秒數與曲線有沒有偏離設計原型 `Claude Design/Main.dc.html`，
/// 以及系統開啟減少動態時該停的有沒有停。這兩件事壞掉時畫面照樣跑得起來、
/// 也不會有任何錯誤訊息，只有拿原型逐幀比對才看得出來。
void main() {
  Widget host(Widget child, {bool disableAnimations = false}) {
    return MediaQuery(
      data: MediaQueryData(disableAnimations: disableAnimations),
      child: CupertinoApp(home: Center(child: child)),
    );
  }

  double opacityIn(WidgetTester tester) {
    return tester
        .widgetList<Opacity>(
          find.descendant(
            of: find.byType(TmsMotionView),
            matching: find.byType(Opacity),
          ),
        )
        .first
        .opacity;
  }

  group('原型的九條動畫規則', () {
    // 原型的宣告在 Main.dc.html:31-37（七組 keyframes 的 animation 速記）、
    // Main.dc.html:139（進度環的 stroke-dasharray transition）與
    // Main.dc.html:1308（進度條的 width transition）。
    const expected = <String, (int, Curve)>{
      'tmsFlash': (350, Curves.easeOut),
      'tmsUp': (260, Curves.easeOut),
      'tmsPop': (420, Curves.easeOut),
      'tmsDrop': (280, Curves.easeOut),
      'tmsSlide': (240, Curves.easeOut),
      'tmsFloat': (1600, Curves.easeOut),
      'tmsPulse': (
        1400,
        Curves.easeInOut,
      ),
      'ringSweep': (500, Curves.easeOut),
      'barSweep': (300, Curves.easeOut),
    };

    test('九條都在，沒有多也沒有少', () {
      expect(
        TmsMotion.all.map((spec) => spec.name).toSet(),
        expected.keys.toSet(),
      );
    });

    for (final spec in TmsMotion.all) {
      test('${spec.name} 的時長與曲線與原型相同', () {
        final (milliseconds, curve) = expected[spec.name]!;
        expect(spec.duration, Duration(milliseconds: milliseconds));
        expect(spec.curve, curve);
      });
    }

    test('只有原型第 40 行點名的五種會因減少動態停播', () {
      final stopped = TmsMotion.all
          .where((spec) => spec.stopsForReducedMotion)
          .map((spec) => spec.name)
          .toSet();
      expect(stopped, {
        'tmsPop',
        'tmsDrop',
        'tmsSlide',
        'tmsFloat',
        'tmsPulse',
      });
    });
  });

  group('減少動態（Main.dc.html:40）', () {
    const stoppedSpecs = <TmsMotionSpec>[
      TmsMotion.pop,
      TmsMotion.drop,
      TmsMotion.slide,
      TmsMotion.float,
      TmsMotion.pulse,
    ];

    for (final spec in stoppedSpecs) {
      testWidgets('${spec.name} 在 disableAnimations 為真時完全不跑', (tester) async {
        await tester.pumpWidget(
          host(
            TmsMotionView(
              spec: spec,
              child: const SizedBox(width: 20, height: 20),
            ),
          ),
        );
        expect(
          tester.binding.transientCallbackCount,
          greaterThan(0),
          reason: '沒開減少動態時 ${spec.name} 本來就該在跑，否則下一段驗不出差別',
        );

        await tester.pumpWidget(
          host(
            TmsMotionView(
              spec: spec,
              child: const SizedBox(width: 20, height: 20),
            ),
            disableAnimations: true,
          ),
        );
        expect(tester.binding.transientCallbackCount, 0);
        expect(opacityIn(tester), 1.0);
        expect(
          spec.durationIn(tester.element(find.byType(TmsMotionView))),
          Duration.zero,
        );
      });
    }

    testWidgets('tmsFlash 與 tmsUp 不在停播清單內，照樣播', (tester) async {
      for (final spec in const [TmsMotion.flash, TmsMotion.up]) {
        await tester.pumpWidget(
          host(
            TmsMotionView(
              key: ValueKey(spec.name),
              spec: spec,
              child: const SizedBox(width: 20, height: 20),
            ),
            disableAnimations: true,
          ),
        );
        expect(
          tester.binding.transientCallbackCount,
          greaterThan(0),
          reason: '${spec.name} 不該因為減少動態而停',
        );
        await tester.pumpAndSettle();
      }
    });
  });

  group('播放', () {
    testWidgets('tmsDrop 在 .28 秒內從淡出落到定位', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsMotionView(
            spec: TmsMotion.drop,
            child: SizedBox(width: 20, height: 20),
          ),
        ),
      );
      expect(opacityIn(tester), 0.0);
      await tester.pump(const Duration(milliseconds: 140));
      expect(opacityIn(tester), greaterThan(0.0));
      expect(opacityIn(tester), lessThan(1.0));
      await tester.pump(const Duration(milliseconds: 140));
      expect(opacityIn(tester), 1.0);
    });

    testWidgets('tmsPulse 的 1.4 秒走完亮到暗再回亮一整圈', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsMotionView(
            spec: TmsMotion.pulse,
            child: SizedBox(width: 20, height: 20),
          ),
        ),
      );
      expect(opacityIn(tester), 1.0);
      await tester.pump(const Duration(milliseconds: 700));
      expect(opacityIn(tester), closeTo(0.45, 0.001));
      await tester.pump(const Duration(milliseconds: 700));
      expect(opacityIn(tester), closeTo(1.0, 0.001));
    });

    testWidgets('彩屑可以用 delay 錯開，等待期間停在起點', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsMotionView(
            spec: TmsMotion.float,
            delay: Duration(milliseconds: 300),
            child: SizedBox(width: 20, height: 20),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 200));
      expect(opacityIn(tester), 1.0);
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pump(const Duration(milliseconds: 800));
      expect(opacityIn(tester), lessThan(1.0));
    });
  });

  group('進度環', () {
    testWidgets('弧長用 ringSweep 的 .5 秒 ease-out 補間', (tester) async {
      await tester.pumpWidget(
        host(const TmsProgressRing(progress: 0.2, child: SizedBox.shrink())),
      );
      final builder = tester.widget<TweenAnimationBuilder<double>>(
        find.byType(TweenAnimationBuilder<double>),
      );
      expect(builder.duration, const Duration(milliseconds: 500));
      expect(builder.curve, Curves.easeOut);
    });
  });
}
