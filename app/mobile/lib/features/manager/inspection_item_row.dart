import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import 'manager_ids.dart';

class InspectionItemRow extends StatelessWidget {
  const InspectionItemRow({
    super.key,
    required this.itemKey,
    required this.label,
    required this.verdict,
    required this.showSeparator,
    required this.onJudge,
  });

  final String itemKey;
  final String label;

  final bool? verdict;
  final bool showSeparator;

  final ValueChanged<bool>? onJudge;

  @override
  Widget build(BuildContext context) {
    final background = switch (verdict) {
      true => TmsColors.successContainer,
      false => TmsColors.errorContainer,
      null => TmsColors.cardBackground,
    };
    final onJudge = this.onJudge;
    return TmsSemantics(
      id: ManagerIds.inspectItem(itemKey),
      label: label,
      explicitChildNodes: true,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.md2,
          vertical: TmsSpacing.md,
        ),
        decoration: BoxDecoration(
          color: background,
          border: showSeparator
              ? const Border(
                  bottom: BorderSide(
                    color: TmsColors.separator,
                    width: TmsSize.hairline,
                  ),
                )
              : null,
        ),
        child: Row(
          children: [
            Expanded(
              child: ExcludeSemantics(
                child: Text(label, style: TmsText.headline),
              ),
            ),
            const SizedBox(width: TmsSpacing.md),
            InspectionVerdictToggle(
              passId: ManagerIds.inspectItemPassButton(itemKey),
              failId: ManagerIds.inspectItemFailButton(itemKey),
              verdict: verdict,
              onChanged: onJudge,
            ),
          ],
        ),
      ),
    );
  }
}

class InspectionVerdictToggle extends StatelessWidget {
  const InspectionVerdictToggle({
    super.key,
    required this.passId,
    required this.failId,
    required this.verdict,
    required this.onChanged,
  });

  final String passId;
  final String failId;
  final bool? verdict;
  final ValueChanged<bool>? onChanged;

  static const double _trackPadding = 3;
  static const double _segmentVertical = 7;

  @override
  Widget build(BuildContext context) {
    final onChanged = this.onChanged;
    return Container(
      padding: const EdgeInsets.all(_trackPadding),
      decoration: BoxDecoration(
        color: TmsColors.segmentedTrack,
        borderRadius: BorderRadius.circular(TmsRadius.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _segment(
            id: passId,
            label: '通過',
            selected: verdict == true,
            selectedColor: TmsColors.systemGreen,
            onTap: onChanged == null ? null : () => onChanged(true),
          ),
          _segment(
            id: failId,
            label: '不通過',
            selected: verdict == false,
            selectedColor: TmsColors.systemRed,
            onTap: onChanged == null ? null : () => onChanged(false),
          ),
        ],
      ),
    );
  }

  Widget _segment({
    required String id,
    required String label,
    required bool selected,
    required Color selectedColor,
    required VoidCallback? onTap,
  }) {
    return Semantics(
      identifier: id,
      container: true,
      button: true,
      selected: selected,
      label: label,
      child: ExcludeSemantics(
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onTap,
          child: Container(
            padding: const EdgeInsets.symmetric(
              horizontal: TmsSpacing.md2,
              vertical: _segmentVertical,
            ),
            decoration: BoxDecoration(
              color: selected ? selectedColor : null,
              borderRadius: BorderRadius.circular(TmsRadius.pill),
            ),
            child: Text(
              label,
              style: TmsText.subheadlineEmphasized.copyWith(
                color: selected
                    ? TmsColors.brandOnPrimary
                    : TmsColors.secondaryLabel,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
