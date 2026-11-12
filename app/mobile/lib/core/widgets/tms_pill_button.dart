import 'package:flutter/cupertino.dart';

import '../theme.dart';

enum TmsPillButtonStyle {
  filled,

  urgentFilled,

  outlined,

  dangerOutlined,

  inverse,
}

@immutable
class TmsPillGlow {
  const TmsPillGlow({
    required this.outer,
    required this.innerTop,
    this.innerBottom,
  });

  final List<BoxShadow> outer;

  final Color innerTop;

  final Color? innerBottom;

  static const TmsPillGlow standard = TmsPillGlow(
    outer: <BoxShadow>[
      BoxShadow(color: Color(0x470C7680), offset: Offset(0, 6), blurRadius: 18),
    ],
    innerTop: Color(0x59FFFFFF),
    innerBottom: Color(0x14000000),
  );

  static const TmsPillGlow alt = TmsPillGlow(
    outer: <BoxShadow>[
      BoxShadow(color: Color(0x4D0C7680), offset: Offset(0, 6), blurRadius: 16),
    ],
    innerTop: Color(0x59FFFFFF),
  );
}

class TmsPillButton extends StatelessWidget {
  const TmsPillButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.style = TmsPillButtonStyle.filled,
    this.height = TmsSize.primaryButtonHeight,
    this.glow = TmsPillGlow.standard,
    this.child,
  });

  final String label;

  final VoidCallback? onPressed;

  final TmsPillButtonStyle style;

  final double height;

  final TmsPillGlow? glow;

  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null;
    final Color background;
    final Color disabledBackground;
    final Color foreground;
    switch (style) {
      case TmsPillButtonStyle.filled:
        background = TmsColors.brandPrimary;
        disabledBackground = TmsColors.opaqueSeparator;
        foreground = TmsColors.brandOnPrimary;
      case TmsPillButtonStyle.urgentFilled:
        background = TmsColors.onErrorContainer;
        disabledBackground = TmsColors.opaqueSeparator;
        foreground = TmsColors.brandOnPrimary;
      case TmsPillButtonStyle.outlined:
        background = TmsColors.cardBackground;
        disabledBackground = TmsColors.cardBackground;
        foreground = enabled
            ? TmsColors.brandPrimary
            : TmsColors.opaqueSeparator;
      case TmsPillButtonStyle.dangerOutlined:
        background = TmsColors.cardBackground;
        disabledBackground = TmsColors.cardBackground;
        foreground = enabled ? TmsColors.systemRed : TmsColors.opaqueSeparator;
      case TmsPillButtonStyle.inverse:
        background = TmsColors.brandOnPrimary;
        disabledBackground = TmsColors.brandOnPrimary;
        foreground = TmsColors.brandPrimary;
    }
    final Widget button = CupertinoButton(
      padding: EdgeInsets.zero,
      borderRadius: BorderRadius.circular(TmsRadius.pill),
      color: background,
      disabledColor: disabledBackground,
      minimumSize: Size(double.infinity, height),
      onPressed: onPressed,
      child:
          child ??
          Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TmsText.headline.copyWith(color: foreground),
          ),
    );
    switch (style) {
      case TmsPillButtonStyle.outlined:
      case TmsPillButtonStyle.dangerOutlined:
        return DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(TmsRadius.pill),
            border: Border.all(color: foreground, width: 1.5),
          ),
          child: button,
        );
      case TmsPillButtonStyle.inverse:
        return button;
      case TmsPillButtonStyle.filled:
      case TmsPillButtonStyle.urgentFilled:
        final glow = this.glow;
        if (glow == null || !enabled) {
          return button;
        }
        return _glow(glow, button);
    }
  }

  Widget _glow(TmsPillGlow glow, Widget button) {
    final radius = BorderRadius.circular(TmsRadius.pill);
    final innerBottom = glow.innerBottom;
    return DecoratedBox(
      decoration: BoxDecoration(borderRadius: radius, boxShadow: glow.outer),
      child: ClipRRect(
        borderRadius: radius,
        clipBehavior: Clip.antiAlias,
        child: Stack(
          children: <Widget>[
            button,
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              height: 1,
              child: ColoredBox(color: glow.innerTop),
            ),
            if (innerBottom != null)
              Positioned(
                bottom: 0,
                left: 0,
                right: 0,
                height: 1,
                child: ColoredBox(color: innerBottom),
              ),
          ],
        ),
      ),
    );
  }
}
