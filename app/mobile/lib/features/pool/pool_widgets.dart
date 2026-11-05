import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';

/// 房務待認領與工務待認領共用的單卡、欄位列與篩選磚。
///
/// 兩個待認領畫面的資料來源是同一支 `GET /pool`，畫面骨架也一樣：上方一排
/// 篩選磚、下方一列單卡，排序與筆數全由後端決定。差別只有三處，都用參數
/// 表達：單卡識別段（房務用房號、工務用工務單號）、卡內要顯示哪些欄位、
/// 以及工務多一顆認領按鈕。視覺尺寸與顏色集中在本檔的常數，兩邊不各寫一套。

/// 待認領池的來源標示：契約 `PoolSource` → 房務單卡上的來源文字。
///
/// `system` 顯示「自動化規則」：這個來源只會由規則引擎開單（backend dsl 的
/// 「自動化規則＝system」），單卡要讓人一眼看出這張單不是誰交辦的，而是規則
/// 自動開的。
const Map<String, String> housekeepingPoolSourceLabels = {
  'guest_scan': '住客掃碼',
  'desk_forward': '櫃檯轉單',
  'manager_requeue': '主管退回',
  'leave_return': '請假退回',
  'shift_end_return': '班末退池',
  'inspection_return': '查房退回',
  'staff_report': '房務報修',
  'system': '自動化規則',
};

/// 工務待認領的來源標示。工務只標示這兩種來源，其餘來源的單卡不帶來源標示；
/// 同一個 `desk_forward` 在工務寫「櫃檯」、在房務寫「櫃檯轉單」，兩邊的用語
/// 各自由自己的驗收文案決定，所以不共用同一份對照表。
const Map<String, String> engineeringPoolSourceLabels = {
  'staff_report': '房務報修',
  'desk_forward': '櫃檯',
};

/// 單卡與篩選磚的圓角半徑。
const double _cardCornerRadius = 12;
const double _chipCornerRadius = 16;

/// 標亮單卡的底色與框線色（推播帶 `highlight` 房號時用）。
const Color _highlightFillColor = Color(0xFFFFF3CD);
const Color _highlightBorderColor = Color(0xFFE6A700);

/// 一張待認領單卡的外框。
///
/// [id] 是卡本身的 accessibility id；卡內各列另外用 [PoolCardField] 掛
/// `{卡 id}.{欄位}`。[highlighted] 只有房務用得到：推播指定房號時整張卡換成
/// 標亮配色，卡的 id 也由呼叫端換成 `--highlighted` 後綴。
class PoolCard extends StatelessWidget {
  const PoolCard({
    super.key,
    required this.id,
    required this.children,
    this.semanticsLabel,
    this.highlighted = false,
    this.onTap,
  });

  final String id;
  final List<Widget> children;
  final String? semanticsLabel;
  final bool highlighted;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final card = Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: highlighted ? _highlightFillColor : CupertinoColors.white,
        border: Border.all(
          color: highlighted
              ? _highlightBorderColor
              : CupertinoColors.systemGrey4,
          width: highlighted ? 2 : 1,
        ),
        borderRadius: BorderRadius.circular(_cardCornerRadius),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: children,
      ),
    );

    return TmsSemantics(
      id: id,
      label: semanticsLabel,
      child: onTap == null ? card : GestureDetector(onTap: onTap, child: card),
    );
  }
}

/// 單卡內一列可被測試讀值的文字。
///
/// 文字同時掛在 accessibility label 上：容器型 semantics 節點本身沒有
/// label，Appium 以 id 定位後讀到的會是空字串，定位與讀值必須落在同一個
/// 節點。
class PoolCardField extends StatelessWidget {
  const PoolCardField({super.key, required this.id, required this.text});

  final String id;
  final String text;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(id: id, label: text, child: Text(text));
  }
}

/// 一顆待認領篩選磚。
///
/// [countId] 有值時磚上多一段筆數，筆數本身也要能被單獨定位（房務的分類磚
/// 要逐類別驗筆數，工務的分流磚不顯示筆數）。點磚一律是重打一次查詢，
/// 不在本機過濾，所以這個元件只負責外觀與回呼。
class PoolFilterChip extends StatelessWidget {
  const PoolFilterChip({
    super.key,
    required this.id,
    required this.label,
    required this.selected,
    required this.onTap,
    this.countId,
    this.count,
  });

  final String id;
  final String label;
  final bool selected;
  final VoidCallback onTap;
  final String? countId;
  final int? count;

  @override
  Widget build(BuildContext context) {
    final foreground = selected ? CupertinoColors.white : CupertinoColors.black;
    return TmsSemantics(
      id: id,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: selected ? CupertinoColors.activeBlue : CupertinoColors.white,
            border: Border.all(color: CupertinoColors.systemGrey4),
            borderRadius: BorderRadius.circular(_chipCornerRadius),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(label, style: TextStyle(color: foreground)),
              if (countId != null) ...[
                const SizedBox(width: 4),
                TmsSemantics(
                  id: countId!,
                  label: '${count ?? 0}',
                  child: Text(
                    '${count ?? 0}',
                    style: TextStyle(
                      color: selected
                          ? CupertinoColors.white
                          : CupertinoColors.systemGrey,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
