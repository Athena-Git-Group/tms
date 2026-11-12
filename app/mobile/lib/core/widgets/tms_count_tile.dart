import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_card.dart';

/// 數字磚：上方類別名、中間一個大數字、下方一行說明，點整格進對應的清單。
///
/// 主管首頁的待查房、待審核、待驗收三格都是它。數字還沒取到時給 null，磚上
/// 顯示「—」而不是 0：0 是「沒有單在等」，跟「還不知道」是兩件事。
///
/// 整格的節點是 [id]，數字另外掛 [countId] 並把數字本身當 label，驗收讀
/// 數字時不必理會磚上其他文字怎麼排。
class TmsCountTile extends StatelessWidget {
  const TmsCountTile({
    super.key,
    required this.id,
    required this.countId,
    required this.title,
    required this.hint,
    required this.count,
    required this.onTap,
  });

  final String id;
  final String countId;
  final String title;
  final String hint;

  /// null 代表還沒取到數字。
  final int? count;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final countText = count?.toString() ?? '—';
    return TmsSemantics(
      id: id,
      explicitChildNodes: true,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: TmsCard(
          padding: const EdgeInsets.all(TmsSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: TmsText.footnoteEmphasized.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
              const SizedBox(height: TmsSpacing.xs),
              TmsSemantics(
                id: countId,
                label: countText,
                child: ExcludeSemantics(
                  child: Text(countText, style: TmsText.displayStat),
                ),
              ),
              const SizedBox(height: TmsSpacing.xs),
              Text(
                hint,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TmsText.caption1Regular.copyWith(
                  color: TmsColors.tertiaryLabel,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
