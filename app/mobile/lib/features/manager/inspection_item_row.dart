import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import 'manager_ids.dart';

/// 查房畫面的一個項目列：項目名在左，右邊一組「通過／不通過」兩段切換。
///
/// 還沒判時白底、兩段都未選；判通過整列淡綠底、判不通過整列淡紅底。整列節點
/// 掛 [ManagerIds.inspectItem]、讀值是項目名；兩段各自是帶 selected 狀態的
/// 按鈕節點，測試以 id 找到它就能讀出有沒有被選。
class InspectionItemRow extends StatelessWidget {
  const InspectionItemRow({
    super.key,
    required this.itemKey,
    required this.label,
    required this.verdict,
    required this.showSeparator,
    required this.onJudge,
  });

  /// sop_items.item_key，送出與 accessibility id 都用它。
  final String itemKey;
  final String label;

  /// true 通過、false 不通過、null 還沒判。
  final bool? verdict;
  final bool showSeparator;

  /// 點了其中一段；null 時兩段都不能點（送出中）。
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

/// 「通過／不通過」兩段切換。選中的那段實心：通過綠、不通過紅；沒選的灰字。
///
/// 不用 [TmsSegmentedControl]：那一顆一定有一段被選，這裡兩段可以都沒選（還沒
/// 判），而且選中色要分通過與不通過。
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
      // 每一段要有自己的節點，id 與 selected 才會落在同一個元素上。
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
