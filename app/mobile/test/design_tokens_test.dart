import 'dart:ui';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_glass.dart';

/// 設計 token 與毛玻璃底板的對帳測試。
///
/// 每一條斷言的數字都抄自設計原型 `Claude Design/Main.dc.html`，行號寫在該條
/// 旁邊。原型是這些值的唯一出處，所以斷言裡刻意寫實際數字而不引用 token
/// 本身：比對 token 和它自己永遠會過，讀不出值有沒有跟著原型走。
void main() {
  group('色彩 token', () {
    test('進行中卡脈動點與慶祝頁彩屑的淺橘', () {
      // Main.dc.html:171, 902, 1378 — background: #FFB783
      expect(TmsColors.brandAccentSoft, const Color(0xFFFFB783));
    });

    test('工務單列的 chevron 比其餘列深一階', () {
      // Main.dc.html:612 — stroke="#C4C4C7"（原型唯一一處）
      // Main.dc.html:421 等六處 — stroke="#C4C4C6"
      expect(TmsColors.chevron, const Color(0xFFC4C4C6));
    });
  });

  group('字級 token', () {
    test('中日韓字形的遞補順序照原型的字型堆疊', () {
      // Main.dc.html:43 — font-family: -apple-system, 'SF Pro Text',
      // 'PingFang TC', 'Noto Sans TC', sans-serif
      // 前兩項是平台預設的系統字型，Flutter 不需要宣告。
      expect(TmsText.fontFamilyStack, <String>['PingFang TC', 'Noto Sans TC']);
    });

    test('每一級字級都掛上同一份遞補順序', () {
      const styles = <TextStyle>[
        TmsText.largeTitle,
        TmsText.title1,
        TmsText.title2,
        TmsText.title3,
        TmsText.title3Regular,
        TmsText.title3Emphasized,
        TmsText.headline,
        TmsText.body,
        TmsText.callout,
        TmsText.subheadline,
        TmsText.subheadlineEmphasized,
        TmsText.subheadlineMedium,
        TmsText.footnote,
        TmsText.footnoteEmphasized,
        TmsText.footnoteMedium,
        TmsText.caption1,
        TmsText.caption2,
        TmsText.caption3,
        TmsText.displayHero,
        TmsText.displayRoomNo,
        TmsText.displayMetric,
        TmsText.displayTimer,
      ];
      for (final style in styles) {
        expect(style.fontFamilyFallback, <String>[
          'PingFang TC',
          'Noto Sans TC',
        ]);
      }
    });

    test('分頁列標籤與照片字條的 10pt', () {
      // Main.dc.html:785, 1134 — font-size: 10px; font-weight: 600
      expect(TmsText.caption3.fontSize, 10);
      expect(TmsText.caption3.fontWeight, FontWeight.w600);
      expect(TmsText.caption3.height, isNull);
    });

    test('動作選單的 20pt 兩種字重', () {
      // Main.dc.html:1686, 1687 — font-size: 20px（未宣告字重，等於 400）
      expect(TmsText.title3Regular.fontSize, 20);
      expect(TmsText.title3Regular.fontWeight, FontWeight.w400);
      // Main.dc.html:1689 — font-size: 20px; font-weight: 600
      expect(TmsText.title3Emphasized.fontSize, 20);
      expect(TmsText.title3Emphasized.fontWeight, FontWeight.w600);
    });

    test('單位字的 13pt/500', () {
      // Main.dc.html:150, 881, 1388 — font-size: 13px; font-weight: 500
      expect(TmsText.footnoteMedium.fontSize, 13);
      expect(TmsText.footnoteMedium.fontWeight, FontWeight.w500);
      expect(TmsText.footnoteMedium.height, 18 / 13);
    });

    test('步驟條未達標籤的 15pt/500', () {
      // Main.dc.html:1214 — font-size: 15px; font-weight: {{s.weight}}
      // Main.dc.html:3884 — weight: cur ? 700 : 500
      expect(TmsText.subheadlineMedium.fontSize, 15);
      expect(TmsText.subheadlineMedium.fontWeight, FontWeight.w500);
    });
  });

  group('浮動分頁列與捲動底部留白', () {
    test('分頁列的尺寸與左右邊距', () {
      // Main.dc.html:1126 — height: 62px; border-radius: 31px
      expect(TmsSize.floatingTabBarHeight, 62);
      expect(TmsSize.floatingTabBarRadius, 31);
      // Main.dc.html:4442 — tabSide: 20
      expect(TmsSpacing.floatingTabBarSide, 20);
    });

    test('分頁列下緣距離是安全區扣 13，最少 10', () {
      // Main.dc.html:4441 — Math.max(D.bottom - 13, 10)
      expect(TmsSpacing.floatingTabBarBottom(34), 21);
      expect(TmsSpacing.floatingTabBarBottom(20), 10);
      expect(TmsSpacing.floatingTabBarBottom(0), 10);
    });

    test('捲動底部留白讓開整條分頁列再加 20', () {
      // Main.dc.html:4449 — scrollPadBottom: 62 + tab26Bottom + 20
      expect(TmsSpacing.scrollPadBottom(34), 62 + 21 + 20);
      expect(TmsSpacing.scrollPadBottom(0), 62 + 10 + 20);
    });
  });

  group('TmsGlass', () {
    test('CSS blur(24px) 的標準差原封不動傳給 ImageFilter', () {
      // Main.dc.html:1126 — backdrop-filter: blur(24px) saturate(180%)
      expect(
        TmsGlass.filterFor(blur: 24).toString(),
        startsWith('ImageFilter.blur(24.0, 24.0'),
      );
      expect(
        TmsGlass.filterFor(blur: 24),
        ImageFilter.blur(sigmaX: 24, sigmaY: 24),
      );
    });

    test('飽和度 100 時不多疊一層色彩矩陣', () {
      expect(
        TmsGlass.filterFor(blur: 12, saturation: 100),
        ImageFilter.blur(sigmaX: 12, sigmaY: 12),
      );
    });

    test('飽和度不是 100 時，模糊外面再套色彩矩陣', () {
      final filter = TmsGlass.filterFor(blur: 24, saturation: 180);
      expect(filter, isNot(ImageFilter.blur(sigmaX: 24, sigmaY: 24)));
      expect(filter.toString(), contains('24.0, 24.0'));
      // SVG feColorMatrix type="saturate" 在 s = 180% 時的係數，
      // 亮度權重 0.213 / 0.715 / 0.072。
      const s = 1.8;
      expect(
        filter,
        ImageFilter.compose(
          outer: ColorFilter.matrix(<double>[
            0.213 + 0.787 * s, 0.715 - 0.715 * s, 0.072 - 0.072 * s, 0, 0, //
            0.213 - 0.213 * s, 0.715 + 0.285 * s, 0.072 - 0.072 * s, 0, 0, //
            0.213 - 0.213 * s, 0.715 - 0.715 * s, 0.072 + 0.928 * s, 0, 0, //
            0, 0, 0, 1, 0, //
          ]),
          inner: ImageFilter.blur(sigmaX: 24, sigmaY: 24),
        ),
      );
    });

    testWidgets('分頁列用的那組參數畫出對應的模糊、底色、邊框與陰影', (tester) async {
      // Main.dc.html:1126 — 浮動分頁列
      const tabBarShadow = BoxShadow(
        color: Color(0x1F000000),
        offset: Offset(0, 4),
        blurRadius: 16,
      );
      await tester.pumpWidget(
        const Directionality(
          textDirection: TextDirection.ltr,
          child: Center(
            child: SizedBox(
              width: 320,
              height: TmsSize.floatingTabBarHeight,
              child: TmsGlass(
                blur: 24,
                saturation: 180,
                color: Color(0x8CFFFFFF),
                borderRadius: BorderRadius.all(
                  Radius.circular(TmsSize.floatingTabBarRadius),
                ),
                border: Border.fromBorderSide(
                  BorderSide(color: Color(0xB3FFFFFF), width: TmsSize.hairline),
                ),
                boxShadow: <BoxShadow>[tabBarShadow],
                padding: EdgeInsets.all(4),
                child: SizedBox.shrink(),
              ),
            ),
          ),
        ),
      );

      final backdrop = tester.widget<BackdropFilter>(
        find.byType(BackdropFilter),
      );
      expect(backdrop.filter, TmsGlass.filterFor(blur: 24, saturation: 180));

      final decorations = tester
          .widgetList<DecoratedBox>(find.byType(DecoratedBox))
          .map((box) => box.decoration)
          .cast<BoxDecoration>()
          .toList();

      final shadowLayer = decorations.firstWhere((d) => d.boxShadow != null);
      expect(shadowLayer.boxShadow, <BoxShadow>[tabBarShadow]);

      final surface = decorations.firstWhere((d) => d.color != null);
      expect(surface.color, const Color(0x8CFFFFFF));
      expect(surface.border, isA<Border>());
      expect(
        surface.borderRadius,
        BorderRadius.circular(TmsSize.floatingTabBarRadius),
      );

      // 陰影要畫在裁切外面，否則會被自己的圓角切掉。
      expect(
        find.descendant(
          of: find.byType(ClipRRect),
          matching: find.byWidget(
            tester.widget(
              find.byWidgetPredicate(
                (w) => w is DecoratedBox && w.decoration == shadowLayer,
              ),
            ),
          ),
        ),
        findsNothing,
      );

      expect(tester.getSize(find.byType(TmsGlass).first).height, 62);
    });
  });
}
