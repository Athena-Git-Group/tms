import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_category_tile.dart';
import '../../core/widgets/tms_tag.dart';

/// 房務待認領與工務待認領共用的單卡、欄位列與分類磚。
///
/// 兩個待認領畫面的資料來源是同一支 `GET /pool`，畫面骨架也一樣：上方一排
/// 分類磚、下方一列單卡，排序與筆數全由後端決定。差別只有三處，都用參數
/// 表達：單卡識別段（房務用房號、工務用工務單號）、卡內要顯示哪些欄位、
/// 以及分類磚的圖示與配色。卡與磚的外觀來自 `core/widgets/` 的共用元件，
/// 兩邊不各寫一套。

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

/// 一張待認領單卡的外框。
///
/// [id] 是卡本身的 accessibility id；卡內各列另外用 [PoolCardField] 掛
/// `{卡 id}.{欄位}`。[highlighted] 只有房務用得到：推播指定房號時整張卡加上
/// 品牌色外環，卡的 id 也由呼叫端換成 `--highlighted` 後綴。
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
    final spaced = <Widget>[];
    for (final child in children) {
      if (spaced.isNotEmpty) {
        spaced.add(const SizedBox(height: TmsSpacing.md));
      }
      spaced.add(child);
    }
    return TmsSemantics(
      id: id,
      label: semanticsLabel,
      child: TmsCard(
        style: TmsCardStyle.hero,
        margin: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.screenEdge,
          vertical: TmsSpacing.xs2,
        ),
        highlighted: highlighted,
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: spaced,
        ),
      ),
    );
  }
}

/// 單卡上的一顆標籤，同時是測試要讀值的那個節點。
///
/// 卡上的附加資訊（來源、退池原因、接手進度、未接提醒）全部長這個樣子：同樣
/// 大小的膠囊，只有顏色不同。做成一樣大小的膠囊是因為這幾段文字沒有主從之
/// 分——員工要的是「掃過去看有沒有紅的」，不是逐行讀完。
///
/// 文字同時掛在 accessibility label 上：容器型 semantics 節點本身沒有 label，
/// Appium 以 id 定位後讀到的會是空字串，定位與讀值必須落在同一個節點。
class PoolCardField extends StatelessWidget {
  const PoolCardField({
    super.key,
    required this.id,
    required this.text,
    this.tone = TmsTagTone.neutralStrong,
  });

  final String id;
  final String text;
  final TmsTagTone tone;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      label: text,
      child: TmsTag(label: text, tone: tone),
    );
  }
}

/// 單卡上的標籤列：幾顆標籤橫著排，排不下就換行。
class PoolTagRow extends StatelessWidget {
  const PoolTagRow({super.key, required this.tags});

  final List<Widget> tags;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: TmsTag.gap,
      runSpacing: TmsTag.gap,
      children: tags,
    );
  }
}

/// 單卡底部那顆滿寬的「認領這單」。
class PoolClaimButton extends StatelessWidget {
  const PoolClaimButton({super.key, required this.id, required this.onPressed});

  final String id;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      child: GestureDetector(
        onTap: onPressed,
        child: Container(
          constraints: const BoxConstraints(minHeight: 46),
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: TmsColors.brandPrimary,
            borderRadius: BorderRadius.circular(TmsRadius.pill),
          ),
          child: Text(
            '認領這單',
            style: TmsText.headline.copyWith(color: TmsColors.brandOnPrimary),
          ),
        ),
      ),
    );
  }
}

/// 一格待認領分類磚。
///
/// [countId] 有值時磚上多一顆數量徽章，徽章本身也要能被單獨定位（房務的分類
/// 磚要逐類別驗筆數，工務的分流磚後端沒有對應的筆數可讀）。點磚一律是重打一
/// 次查詢，不在本機過濾，所以這個元件只負責外觀與回呼。
class PoolFilterChip extends StatelessWidget {
  const PoolFilterChip({
    super.key,
    required this.id,
    required this.label,
    required this.icon,
    required this.iconBackground,
    required this.iconForeground,
    required this.selected,
    required this.onTap,
    this.countId,
    this.count,
  });

  final String id;
  final String label;
  final IconData icon;
  final Color iconBackground;
  final Color iconForeground;
  final bool selected;
  final VoidCallback onTap;
  final String? countId;
  final int? count;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      child: TmsCategoryTile(
        label: label,
        icon: icon,
        iconBackground: iconBackground,
        iconForeground: iconForeground,
        selected: selected,
        onTap: onTap,
        badge: countId == null
            ? null
            : TmsSemantics(
                id: countId!,
                label: '${count ?? 0}',
                child: TmsCountBadge(count: count ?? 0),
              ),
      ),
    );
  }
}

/// 一排分類磚：四格等寬，間距照設計的網格。
class PoolFilterRow extends StatelessWidget {
  const PoolFilterRow({super.key, required this.tiles});

  final List<Widget> tiles;

  @override
  Widget build(BuildContext context) {
    final children = <Widget>[];
    for (final tile in tiles) {
      if (children.isNotEmpty) {
        children.add(const SizedBox(width: TmsCategoryTile.gap));
      }
      children.add(Expanded(child: tile));
    }
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: children),
    );
  }
}
