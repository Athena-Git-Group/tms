import 'package:flutter/cupertino.dart';

import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_category_tile.dart';
import '../../core/widgets/tms_pill_button.dart';
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

/// 等多久才算「等太久」：到這個分鐘數，等待標籤就從中性轉成紅的。
///
/// 門檻放在這裡而不是各畫面各寫一個數字，是因為房務與工務的員工對「這單擱
/// 著了」的感覺要一致；主管兩邊都看，兩邊的紅標準不同會讓他誤判哪一池比較
/// 擠。
const int poolWaitedMinutesAlarmThreshold = 20;

/// 單卡上的等待時間標籤：這張單在池裡等了多久。
///
/// 給房務與工務兩個待認領畫面共用，回傳 null 表示這張卡不畫這顆標籤。
/// 不畫的情況有兩種：後端沒給等待分鐘（[waitedMinutes] 為 null），或這張單
/// 已經進了未接提醒階段（[reminderStage] 有值）——那時卡上改掛「未接 · 已提
/// 醒同仁／已通知主管」，那顆標籤講的是同一件事而且更嚴重，兩顆並排只會讓
/// 員工多讀一行。
///
/// 標籤本身不掛 accessibility id：等待分鐘每分鐘都在變，不是驗收讀得出定值
/// 的欄位。
Widget? poolWaitedTag({int? waitedMinutes, String? reminderStage}) {
  if (waitedMinutes == null || reminderStage != null) {
    return null;
  }
  // 不滿一分鐘寫「剛進來」而不是「等 0 分」：員工要的是「這單還熱著、可以
  // 接」，而 0 這個數字會被讀成計時還沒開始。
  final label = waitedMinutes < 1 ? '剛進來' : '等 $waitedMinutes 分';
  return TmsTag(
    label: label,
    tone: waitedMinutes >= poolWaitedMinutesAlarmThreshold
        ? TmsTagTone.danger
        : TmsTagTone.neutral,
  );
}

/// 單卡上的同樓層標籤：這張單在不在員工自己負責的樓層。
///
/// 給房務與工務兩個待認領畫面共用，回傳 null 表示這張卡不畫這顆標籤。
/// 標籤只在同樓層時出現，連「優先通知」也要同樓層才講
/// （`Main.dc.html:4251-4252`）：跨樓層的單搶過去得先走一趟，催不同樓層的人
/// 去接只會讓她放下手邊那間房。
///
/// [id] 一律是同一個，因為兩種說法佔的是卡上同一個位置、回答的是同一個問題。
Widget? poolSameFloorTag({
  required String id,
  required bool sameFloor,
  required bool priorityNotify,
}) {
  if (!sameFloor) {
    return null;
  }
  return PoolCardField(
    id: id,
    text: priorityNotify ? '優先通知 · 同樓層' : '同樓層 · 離你最近',
    tone: priorityNotify ? TmsTagTone.brand : TmsTagTone.info,
  );
}

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
        // 上下各 8，相鄰兩卡之間就是原型那一列的 `gap: 16px`
        // （`Main.dc.html:249`、`973`）。
        margin: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.screenEdge,
          vertical: TmsSpacing.sm,
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
/// 卡上的附加情況（同樓層、退池原因、未接提醒、等多久）全部長這個樣子：同樣
/// 大小的膠囊，只有顏色不同。做成一樣大小的膠囊是因為這幾段文字沒有主從之
/// 分——員工要的是「掃過去看有沒有紅的」，不是逐行讀完。來源與接手進度不走
/// 這裡：前者是副標那一行，後者是卡內整塊的備註。
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

/// 單卡標題底下那一行副標（`Main.dc.html:279`、`996`）。
///
/// 副標不是標籤：它講的是這張單從哪來、大概要做多久，是讀完標題後接著讀的
/// 一句話，所以排成一整行灰字而不是一顆膠囊。排不下就截字，不換行——卡的
/// 高度要在列表裡齊平，員工掃列表時靠的是每張卡一樣高。
class PoolCardSubtitle extends StatelessWidget {
  const PoolCardSubtitle({super.key, required this.id, required this.text});

  final String id;
  final String text;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      label: text,
      child: Text(
        text,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TmsText.subheadline.copyWith(color: TmsColors.secondaryLabel),
      ),
    );
  }
}

/// 卡內整寬的「做到哪」備註塊（`Main.dc.html:290`）。
///
/// 接手別人做到一半的單時，前一個人交代的進度是要照著做的指示，不是一句附加
/// 說明。它因此佔掉卡內一整塊灰底，不跟標籤擠在同一列——標籤可以掃過去，這段
/// 要讀完。
class PoolCardNote extends StatelessWidget {
  const PoolCardNote({super.key, required this.id, required this.text});

  final String id;
  final String text;

  static const String prefix = '做到哪：';

  @override
  Widget build(BuildContext context) {
    final full = '$prefix$text';
    return TmsSemantics(
      id: id,
      label: full,
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.md,
          vertical: TmsSpacing.sm2,
        ),
        decoration: BoxDecoration(
          color: TmsColors.groupedBackground,
          borderRadius: BorderRadius.circular(TmsRadius.md),
        ),
        child: Text(
          full,
          style: TmsText.subheadline.copyWith(color: TmsColors.labelSoft),
        ),
      ),
    );
  }
}

class PoolTagRow extends StatelessWidget {
  const PoolTagRow({super.key, required this.tags});

  final List<Widget> tags;

  @override
  Widget build(BuildContext context) {
    return Wrap(spacing: TmsTag.gap, runSpacing: TmsTag.gap, children: tags);
  }
}

class PoolClaimButton extends StatelessWidget {
  const PoolClaimButton({
    super.key,
    required this.id,
    required this.onPressed,
    this.glow = TmsPillGlow.standard,
  });

  final String id;
  final VoidCallback onPressed;

  /// 要畫哪一組光暈。房務待認領用預設那一組，工務待認領在原型裡用的是
  /// [TmsPillGlow.alt]。
  final TmsPillGlow? glow;

  /// 認領鈕比置底的主按鈕矮一截：它在單卡裡面，跟整張卡比例要對得上。
  static const double _height = 46;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      child: TmsPillButton(
        label: '認領這單',
        height: _height,
        glow: glow,
        onPressed: onPressed,
      ),
    );
  }
}

/// 一格待認領分類磚。
///
/// [count] 大於 0 時磚上多一顆數量徽章，0 或不給值時沒有徽章。再給 [countId]，
/// 徽章就是一個能被單獨定位、讀得出數字的節點。房務的分類磚要逐類別驗筆數，
/// 所以兩個都給；工務的分流磚沒有斷言在讀，只給 [count]。點磚一律是重打一次
/// 查詢，不在本機過濾，所以這個元件只負責外觀與回呼。
///
/// [unselectedBackground] 透傳給 [TmsCategoryTile]，由呼叫端決定磚沒選中時疊
/// 在什麼底色上。
class PoolFilterChip extends StatelessWidget {
  const PoolFilterChip({
    super.key,
    required this.id,
    required this.label,
    required this.iconBackground,
    required this.iconForeground,
    required this.selected,
    required this.onTap,
    this.icon,
    this.iconChild,
    this.countId,
    this.count,
    this.unselectedBackground,
  });

  final String id;
  final String label;

  /// 圖示字形與自繪圖示，語意同 [TmsCategoryTile] 的同名欄位。
  final IconData? icon;
  final Widget? iconChild;

  final Color iconBackground;
  final Color iconForeground;
  final bool selected;
  final VoidCallback onTap;
  final String? countId;
  final int? count;
  final Color? unselectedBackground;

  @override
  Widget build(BuildContext context) {
    final countValue = count;
    Widget? badge;
    // 筆數 0 的分類不掛徽章，理由見 [TmsCountBadge] 的類別註解。
    if (countValue != null && countValue > 0) {
      badge = TmsCountBadge(count: countValue);
      final id = countId;
      if (id != null) {
        badge = TmsSemantics(id: id, label: '$countValue', child: badge);
      }
    }
    return TmsSemantics(
      id: id,
      child: TmsCategoryTile(
        label: label,
        icon: icon,
        iconChild: iconChild,
        iconBackground: iconBackground,
        iconForeground: iconForeground,
        selected: selected,
        onTap: onTap,
        badge: badge,
        unselectedBackground: unselectedBackground,
      ),
    );
  }
}

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
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: children,
      ),
    );
  }
}

/// 待認領一頁的捲動骨架：連線狀態列、大標、（主管的）部門切換，接著是呼叫端
/// 給的分類磚與單卡。
///
/// 房務、工務與主管三個待認領畫面的骨架一樣，差別只在分類磚與單卡，所以骨架
/// 在這裡只畫一次。大標跟著內容捲而不是放導覽列（`Main.dc.html:251`、`975`）；
/// 底部不讓 SafeArea 收邊，單卡要從浮動分頁列底下捲過去，開分頁列的那段留白
/// 由清單自己帶。
class PoolScrollView extends StatelessWidget {
  const PoolScrollView({
    super.key,
    required this.children,
    this.aboveCategories,
  });

  /// 分類磚起算的內容。
  final List<Widget> children;

  /// 夾在大標與分類磚之間的那一塊；主管的部門切換放這裡，員工的待認領沒有。
  final Widget? aboveCategories;

  @override
  Widget build(BuildContext context) {
    final aboveCategories = this.aboveCategories;
    return SafeArea(
      bottom: false,
      child: ListView(
        padding: EdgeInsets.only(
          top: TmsSpacing.md,
          bottom: TmsSpacing.scrollPadBottom(
            MediaQuery.paddingOf(context).bottom,
          ),
        ),
        children: [
          // 連線狀態列固定在內容最上方：待認領是「搶單」的頁，離線時搶不到單，
          // 員工要先知道現在連不上，而不是按了認領才發現。
          const SystemStatusBar(
            padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
            child: Text('待認領', style: TmsText.largeTitle),
          ),
          if (aboveCategories != null) ...[
            const SizedBox(height: TmsSpacing.md),
            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: TmsSpacing.screenEdge,
              ),
              child: aboveCategories,
            ),
          ],
          const SizedBox(height: TmsSpacing.lg),
          ...children,
        ],
      ),
    );
  }
}

/// 列表載入失敗時分類磚底下那一行說明。
class PoolLoadError extends StatelessWidget {
  const PoolLoadError({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(
        left: TmsSpacing.screenEdge,
        right: TmsSpacing.screenEdge,
        top: TmsSpacing.lg,
      ),
      child: Text(message),
    );
  }
}

/// 列表還在載入時分類磚底下的轉圈。
class PoolLoading extends StatelessWidget {
  const PoolLoading({super.key});

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.only(top: TmsSpacing.lg),
      child: Center(child: CupertinoActivityIndicator()),
    );
  }
}
