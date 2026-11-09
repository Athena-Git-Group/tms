import 'dart:ui';

import 'package:flutter/widgets.dart';

/// 毛玻璃底板：把背後已經畫好的內容模糊並提高飽和度，再疊上一層半透明底色、
/// 邊框與陰影。浮動分頁列、底部面板、捲動後凍結的導覽列、Alert 對話框與 FAB
/// 都站在這一塊底板上。
///
/// ## 為什麼要有這個元件
///
/// 這五處的外觀差別只在「模糊多少、底色多透、圓角多大、邊框與陰影長怎樣」，
/// 機制完全相同。若讓每支畫面各自組一次 [ClipRRect] + [BackdropFilter] +
/// [ImageFilter]，模糊值會隨手寫偏，而且沒有一個地方可以一次對帳。
///
/// ## 設計值怎麼從網頁原型換算過來
///
/// 原型用 CSS 的 `backdrop-filter: blur(24px) saturate(180%)`。
///
/// CSS `blur(<length>)` 的參數就是高斯模糊的標準差，和 [ImageFilter.blur] 的
/// `sigmaX`／`sigmaY` 同一個量，所以 [blur] 原封不動傳進去，不乘也不除。
/// 這點容易和 CSS `box-shadow` 的第三個參數搞混。那一個是直徑式的「模糊半徑」，
/// 標準差只有它的一半。兩者剛好都叫 blur，但不是同一個數。
///
/// `saturate(180%)` 換算成 [saturation] 等於 180。實作用的是 SVG
/// `feColorMatrix` 的飽和度矩陣，和瀏覽器算同一條公式，所以同一組數字在兩邊
/// 得到同樣的結果。
///
/// ## 實機質感不等於這裡畫出來的東西
///
/// iOS 26 的 Liquid Glass 由系統渲染，含折射與環境光反應，這個元件做不到。
/// 可量測的部分（尺寸、圓角、邊框、陰影、模糊標準差、飽和度）才是對帳的對象。
class TmsGlass extends StatelessWidget {
  const TmsGlass({
    super.key,
    required this.blur,
    required this.color,
    this.saturation = 100,
    this.borderRadius = BorderRadius.zero,
    this.border,
    this.boxShadow,
    this.padding,
    this.child,
  });

  /// 高斯模糊的標準差，直接取原型 `blur(<n>px)` 的 n。
  final double blur;

  /// 疊在模糊結果上的半透明底色，取原型的 `background`。
  final Color color;

  /// 飽和度百分比，取原型 `saturate(<n>%)` 的 n。100 代表不調整。
  final double saturation;

  /// 圓角。模糊、底色、邊框與陰影共用同一個圓角，不會各走各的。
  final BorderRadiusGeometry borderRadius;

  /// 邊框，取原型的 `border`。
  final BoxBorder? border;

  /// 外陰影，取原型 `box-shadow` 的非 `inset` 段落。CSS 的模糊半徑與
  /// [BoxShadow.blurRadius] 是同一個量，照抄即可。
  ///
  /// `inset` 高光（例如分頁列的 `inset 0 1px 0 rgba(255,255,255,.8)`）畫不進
  /// [BoxDecoration]，由呼叫端自行在 [child] 上緣疊一條線。
  final List<BoxShadow>? boxShadow;

  /// 底板內距。
  final EdgeInsetsGeometry? padding;

  final Widget? child;

  /// 模糊與飽和度合成後的濾鏡。測試用它比對實際掛上去的值。
  static ImageFilter filterFor({
    required double blur,
    double saturation = 100,
  }) {
    final blurFilter = ImageFilter.blur(sigmaX: blur, sigmaY: blur);
    if (saturation == 100) {
      return blurFilter;
    }
    return ImageFilter.compose(
      outer: ColorFilter.matrix(_saturationMatrix(saturation / 100)),
      inner: blurFilter,
    );
  }

  /// SVG `feColorMatrix type="saturate"` 的係數，三個通道的亮度權重是
  /// 0.213、0.715、0.072。
  static List<double> _saturationMatrix(double s) {
    return <double>[
      0.213 + 0.787 * s, 0.715 - 0.715 * s, 0.072 - 0.072 * s, 0, 0, //
      0.213 - 0.213 * s, 0.715 + 0.285 * s, 0.072 - 0.072 * s, 0, 0, //
      0.213 - 0.213 * s, 0.715 - 0.715 * s, 0.072 + 0.928 * s, 0, 0, //
      0, 0, 0, 1, 0, //
    ];
  }

  @override
  Widget build(BuildContext context) {
    Widget surface = ClipRRect(
      borderRadius: borderRadius,
      clipBehavior: Clip.antiAlias,
      child: BackdropFilter(
        filter: filterFor(blur: blur, saturation: saturation),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: color,
            borderRadius: borderRadius,
            border: border,
          ),
          child: padding == null
              ? child
              : Padding(padding: padding!, child: child),
        ),
      ),
    );

    final shadow = boxShadow;
    if (shadow != null && shadow.isNotEmpty) {
      // 陰影畫在 ClipRRect 外面，畫在裡面會被自己的圓角切掉。
      surface = DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: borderRadius,
          boxShadow: shadow,
        ),
        child: surface,
      );
    }

    return surface;
  }
}
