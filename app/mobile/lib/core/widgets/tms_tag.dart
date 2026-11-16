import 'package:flutter/widgets.dart';

import '../theme.dart';

enum TmsTagTone {
  brand,

  info,

  warn,

  redo,

  scheduled,

  danger,

  neutral,

  neutralStrong,
}

class TmsTag extends StatelessWidget {
  const TmsTag({super.key, required this.label, required this.tone});

  final String label;
  final TmsTagTone tone;

  static const double gap = TmsSpacing.xs2;

  Color get _background {
    switch (tone) {
      case TmsTagTone.brand:
        return TmsColors.brandPrimary;
      case TmsTagTone.info:
        return TmsColors.brandContainer;
      case TmsTagTone.warn:
        return TmsColors.brandAccentContainer;
      case TmsTagTone.redo:
        return TmsColors.redoContainer;
      case TmsTagTone.scheduled:
        return TmsColors.scheduledContainer;
      case TmsTagTone.danger:
        return TmsColors.errorContainer;
      case TmsTagTone.neutral:
        return TmsColors.groupedBackground;
      case TmsTagTone.neutralStrong:
        return TmsColors.neutralContainer;
    }
  }

  Color get _foreground {
    switch (tone) {
      case TmsTagTone.brand:
        return TmsColors.brandOnPrimary;
      case TmsTagTone.info:
        return TmsColors.brandOnContainerStrong;
      case TmsTagTone.warn:
        return TmsColors.brandOnAccentContainer;
      case TmsTagTone.redo:
        return TmsColors.onRedoContainer;
      case TmsTagTone.scheduled:
        return TmsColors.onScheduledContainer;
      case TmsTagTone.danger:
        return TmsColors.onErrorContainer;
      case TmsTagTone.neutral:
      case TmsTagTone.neutralStrong:
        return TmsColors.onNeutralContainer;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.sm, vertical: 3),
      decoration: BoxDecoration(
        color: _background,
        borderRadius: BorderRadius.circular(TmsRadius.pill),
      ),
      child: Text(
        label,
        maxLines: 1,
        softWrap: false,
        style: TmsText.caption1.copyWith(color: _foreground),
      ),
    );
  }
}
