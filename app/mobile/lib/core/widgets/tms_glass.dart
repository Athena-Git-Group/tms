import 'dart:ui';

import 'package:flutter/widgets.dart';

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

  final double blur;

  final Color color;

  final double saturation;

  final BorderRadiusGeometry borderRadius;

  final BoxBorder? border;

  final List<BoxShadow>? boxShadow;

  final EdgeInsetsGeometry? padding;

  final Widget? child;

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

  /// 係數出自 SVG `feColorMatrix type="saturate"` 的定義。
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
