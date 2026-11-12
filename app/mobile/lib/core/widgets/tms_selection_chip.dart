import 'package:flutter/cupertino.dart';

import '../theme.dart';

class TmsSelectionChip extends StatelessWidget {
  const TmsSelectionChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
    this.trailing,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final foreground =
        selected ? TmsColors.brandOnPrimary : TmsColors.labelSoft;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        constraints: const BoxConstraints(minHeight: TmsSize.minTouchTarget),
        padding: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.md,
          vertical: TmsSpacing.sm,
        ),
        decoration: BoxDecoration(
          color: selected
              ? TmsColors.brandPrimary
              : TmsColors.neutralContainer,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              label,
              style: TmsText.subheadlineEmphasized.copyWith(color: foreground),
            ),
            if (trailing != null) ...[
              const SizedBox(width: TmsSpacing.xs2),
              trailing!,
            ],
          ],
        ),
      ),
    );
  }
}
