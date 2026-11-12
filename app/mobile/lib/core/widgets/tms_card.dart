import 'package:flutter/cupertino.dart';

import '../theme.dart';

enum TmsCardStyle {
  dialog,

  hero,

  grouped,

  inSheet,
}

class TmsCard extends StatelessWidget {
  const TmsCard({
    super.key,
    required this.child,
    this.style = TmsCardStyle.grouped,
    this.padding = const EdgeInsets.all(TmsSpacing.lg),
    this.margin,
    this.highlighted = false,
    this.backgroundColor,
    this.onTap,
  });

  final Widget child;
  final TmsCardStyle style;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;

  final bool highlighted;

  final Color? backgroundColor;

  final VoidCallback? onTap;

  double get _radius {
    switch (style) {
      case TmsCardStyle.dialog:
        return TmsRadius.dialog;
      case TmsCardStyle.hero:
        return TmsRadius.cardHero;
      case TmsCardStyle.grouped:
        return TmsRadius.card;
      case TmsCardStyle.inSheet:
        return TmsRadius.cardInSheet;
    }
  }

  @override
  Widget build(BuildContext context) {
    final card = Container(
      margin: margin,
      padding: padding,
      decoration: BoxDecoration(
        color: backgroundColor ?? TmsColors.cardBackground,
        borderRadius: BorderRadius.circular(_radius),
        boxShadow: highlighted
            ? const [BoxShadow(color: TmsColors.brandPrimary, spreadRadius: 3)]
            : null,
      ),
      child: child,
    );
    if (onTap == null) {
      return card;
    }
    return GestureDetector(onTap: onTap, child: card);
  }
}
