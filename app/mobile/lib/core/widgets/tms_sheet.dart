import 'dart:math' as math;

import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_glass.dart';
import 'tms_motion.dart';
import 'tms_pill_button.dart';

enum TmsSheetLeading {
  close(CupertinoIcons.xmark, 15, '關閉'),

  back(CupertinoIcons.chevron_back, 18, '返回');

  const TmsSheetLeading(this.icon, this.iconSize, this.label);

  final IconData icon;

  final double iconSize;

  final String label;
}

class TmsSheetRoute<T> extends PopupRoute<T> {
  TmsSheetRoute({required this.builder});

  final WidgetBuilder builder;

  static Future<T?> show<T>(BuildContext context, WidgetBuilder builder) {
    return Navigator.of(context, rootNavigator: true)
        .push(TmsSheetRoute<T>(builder: builder));
  }

  @override
  Color get barrierColor => TmsColors.scrimSheet;

  @override
  bool get barrierDismissible => true;

  @override
  String get barrierLabel => '關閉面板';

  @override
  Duration get transitionDuration => TmsMotion.up.duration;

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) {
    return Align(alignment: Alignment.bottomCenter, child: builder(context));
  }

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    return SlideTransition(
      position: Tween<Offset>(begin: const Offset(0, 1), end: Offset.zero)
          .animate(
        CurvedAnimation(parent: animation, curve: TmsMotion.up.curve),
      ),
      child: child,
    );
  }
}

class TmsSheet extends StatelessWidget {
  const TmsSheet({
    super.key,
    required this.title,
    required this.children,
    this.leading = TmsSheetLeading.close,
    this.onLeadingTap,
    this.backgroundColor = TmsColors.groupedBackground,
    this.bottomAction,
  });

  final String title;

  final TmsSheetLeading leading;

  final VoidCallback? onLeadingTap;

  final Color backgroundColor;

  final List<Widget> children;

  final Widget? bottomAction;

  static const double _gapToScreenTop = 40;

  static const double _titleRowHeight = 52;

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final bottomInset = math.max(media.padding.bottom, TmsSpacing.lg);
    final action = bottomAction;
    final barHeight = TmsSpacing.lg + TmsSheetButton.height + bottomInset;

    final content = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i > 0) {
        content.add(const SizedBox(height: TmsSpacing.md));
      }
      content.add(children[i]);
    }

    return Container(
      width: double.infinity,
      constraints: BoxConstraints(
        maxHeight: media.size.height - _gapToScreenTop,
      ),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(TmsRadius.sheet),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x2E000000),
            offset: Offset(0, -4),
            blurRadius: 30,
          ),
        ],
      ),
      child: Padding(
        padding: EdgeInsets.only(bottom: media.viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: TmsSpacing.xs2),
            Container(
              width: 36,
              height: 5,
              decoration: BoxDecoration(
                color: TmsColors.opaqueSeparator,
                borderRadius: BorderRadius.circular(3),
              ),
            ),
            const SizedBox(height: TmsSpacing.md),
            _titleRow(),
            Flexible(
              child: Stack(
                children: [
                  SingleChildScrollView(
                    padding: EdgeInsets.fromLTRB(
                      TmsSpacing.screenEdge,
                      TmsSpacing.md,
                      TmsSpacing.screenEdge,
                      action == null
                          ? bottomInset
                          : TmsSpacing.md + barHeight,
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: content,
                    ),
                  ),
                  if (action != null)
                    Positioned(
                      left: 0,
                      right: 0,
                      bottom: 0,
                      child: _bottomBar(action, bottomInset, barHeight),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _bottomBar(Widget action, double bottomInset, double height) {
    return Container(
      constraints: BoxConstraints(minHeight: height),
      padding: EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        TmsSpacing.lg,
        TmsSpacing.screenEdge,
        bottomInset,
      ),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [backgroundColor.withAlpha(0), backgroundColor],
          stops: [0, TmsSpacing.lg / height],
        ),
      ),
      child: action,
    );
  }

  Widget _titleRow() {
    final onTap = onLeadingTap;
    return Container(
      constraints: const BoxConstraints(minHeight: _titleRowHeight),
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      child: Row(
        children: [
          Expanded(
            child: Align(
              alignment: Alignment.centerLeft,
              child: onTap == null
                  ? const SizedBox.shrink()
                  : _leadingButton(onTap),
            ),
          ),
          Text(title, style: TmsText.headline),
          const Expanded(child: SizedBox.shrink()),
        ],
      ),
    );
  }

  Widget _leadingButton(VoidCallback onTap) {
    return SizedBox(
      width: TmsSize.minTouchTarget,
      height: TmsSize.minTouchTarget,
      child: TmsGlass(
        blur: 12,
        color: const Color(0xB3FFFFFF),
        borderRadius: BorderRadius.circular(TmsRadius.pill),
        border: Border.all(color: const Color(0xCCFFFFFF)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x14000000),
            offset: Offset(0, 2),
            blurRadius: 10,
          ),
        ],
        child: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: onTap,
          child: Icon(
            leading.icon,
            size: leading.iconSize,
            color: TmsColors.labelSoft,
            semanticLabel: leading.label,
          ),
        ),
      ),
    );
  }
}

class TmsSheetButton extends StatelessWidget {
  const TmsSheetButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.style = TmsPillButtonStyle.filled,
    this.child,
  });

  final String label;

  final VoidCallback? onPressed;

  final TmsPillButtonStyle style;

  final Widget? child;

  static const double height = 52;

  @override
  Widget build(BuildContext context) {
    return TmsPillButton(
      label: label,
      style: style,
      height: height,
      glow: TmsPillGlow.alt,
      onPressed: onPressed,
      child: child,
    );
  }
}
