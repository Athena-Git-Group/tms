import 'dart:math' as math;

import 'package:flutter/cupertino.dart';

import '../theme.dart';

class _ActionBarChrome extends StatelessWidget {
  const _ActionBarChrome({
    required this.topPadding,
    required this.bottomPadding,
    required this.child,
  });

  final double topPadding;
  final double bottomPadding;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        topPadding,
        TmsSpacing.screenEdge,
        bottomPadding,
      ),
      decoration: const BoxDecoration(
        color: TmsColors.cardBackground,
        border: Border(
          top: BorderSide(
            color: TmsColors.opaqueSeparator,
            width: TmsSize.hairline,
          ),
        ),
      ),
      child: child,
    );
  }
}

class TmsActionBar extends StatelessWidget {
  const TmsActionBar({super.key, this.hint, this.secondary, this.primary});

  final Widget? hint;

  final Widget? secondary;

  final Widget? primary;

  static double bottomInset(BuildContext context) {
    final inset = MediaQuery.viewPaddingOf(context).bottom;
    return inset > 0 ? inset : TmsSpacing.lg;
  }

  @override
  Widget build(BuildContext context) {
    final hint = this.hint;
    final secondary = this.secondary;
    final primary = this.primary;
    if (secondary == null && primary == null) {
      return const SizedBox.shrink();
    }
    final row = Row(
      children: <Widget>[
        if (secondary != null) Expanded(child: secondary),
        if (secondary != null && primary != null)
          const SizedBox(width: TmsSpacing.md),
        if (primary != null) Expanded(flex: 2, child: primary),
      ],
    );
    return _ActionBarChrome(
      topPadding: TmsSpacing.md,
      bottomPadding: bottomInset(context),
      child: hint == null
          ? row
          : Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                hint,
                const SizedBox(height: TmsSpacing.sm),
                row,
              ],
            ),
    );
  }
}

class TmsFormActionBar extends StatelessWidget {
  const TmsFormActionBar({super.key, required this.children});

  final List<Widget> children;

  static double bottomInset(BuildContext context) {
    final actionBottom = TmsActionBar.bottomInset(context);
    return actionBottom > TmsSpacing.lg
        ? math.max(actionBottom - 14, TmsSpacing.sm)
        : TmsSpacing.sm;
  }

  @override
  Widget build(BuildContext context) {
    final spaced = <Widget>[];
    for (final child in children) {
      if (spaced.isNotEmpty) {
        spaced.add(const SizedBox(height: TmsSpacing.xs));
      }
      spaced.add(child);
    }
    return _ActionBarChrome(
      topPadding: TmsSpacing.sm2,
      bottomPadding: bottomInset(context),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: spaced,
      ),
    );
  }
}
