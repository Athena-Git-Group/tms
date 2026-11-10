import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/today_refresh.dart';
import '../../core/widgets/tms_category_square.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_tag.dart';
import '../shell/main_shell.dart';
import '../task/task_screen.dart';
import 'pool_widgets.dart';

/// 待認領畫面（ui-plan 畫面 2 的 `pool`）：分類磚＋單卡列表。
///
/// 資料來源是 [ApiClient.getPool]（`GET /pool`）。排序、分類筆數與分類篩選
/// 全部由後端決定：點分類磚就是帶 `category` 重打一次查詢，App 不在本機重排、
/// 不在本機重算筆數、不在本機過濾。
class PoolScreen extends StatefulWidget {
  const PoolScreen({super.key, this.highlightRoomNo});

  /// 推播帶 `highlight` 房號時，該房的卡要掛標亮狀態 id
  /// （`pool.card.{roomNo}--highlighted`）。
  final String? highlightRoomNo;

  @override
  State<PoolScreen> createState() => _PoolScreenState();
}

class _PoolScreenState extends State<PoolScreen> {
  /// 分類磚：`key` 同時是契約 `PoolCategory` 的值與 accessibility id 的
  /// `{category}` 段；`all` 是「不帶 category 參數」的彙總磚，不是任務類別。
  /// 圖示方塊的底色與圖示色照原型逐格指定，不從類別色推算：「全部」不是一個
  /// 任務類別，推不出顏色，而備品磚用的是比任務列更亮的橘，兩者刻意不同。
  ///
  /// 只有「全部」是實心圖示，其餘三格是描邊（`Main.dc.html:258-261`）：實心
  /// 的那一格是彙總，描邊的三格是分類。
  static const _categories = [
    (
      key: 'all',
      label: '全部',
      icon: CupertinoIcons.square_grid_2x2_fill,
      iconBackground: TmsColors.brandPrimary,
      iconForeground: TmsColors.brandOnPrimary,
    ),
    (
      key: 'amenity',
      label: '備品',
      icon: CupertinoIcons.cube,
      iconBackground: TmsColors.brandAccentVivid,
      iconForeground: TmsColors.brandOnPrimary,
    ),
    (
      key: 'cleaning',
      label: '打掃',
      icon: CupertinoIcons.sparkles,
      iconBackground: TmsColors.brandContainer,
      iconForeground: TmsColors.brandPrimary,
    ),
    (
      key: 'other',
      label: '其他',
      icon: CupertinoIcons.bubble_left,
      iconBackground: TmsColors.otherContainer,
      iconForeground: TmsColors.onOtherContainer,
    ),
  ];

  String _selected = 'all';
  DeptPool? _pool;
  String? _error;

  /// 這個畫面是房務部門的待認領池（工務有自己的 `engPool`），所以查哪一池
  /// 的答案只有一個：房務。
  static const String _dept = 'housekeeping';

  /// 要不要在查詢裡帶 `dept`。房務員工的帳號上就有部門，照既有作法省略，
  /// 讓後端用帳號上的部門分流；主管與老闆的帳號沒有部門（契約 listDeptPool
  /// 省略 dept 時回 422 `dept_required`），要看房務池就得把部門指明。
  String? get _deptQuery =>
      ApiClient.instance.session?.dept == null ? _dept : null;

  String? _highlightRoomNo;

  @override
  void initState() {
    super.initState();
    _highlightRoomNo =
        widget.highlightRoomNo ?? MainShellTabRequest.poolHighlightRoomNo.value;
    MainShellTabRequest.poolHighlightRoomNo.addListener(_applyHighlightRequest);
    _fetch();
  }

  @override
  void dispose() {
    MainShellTabRequest.poolHighlightRoomNo.removeListener(
      _applyHighlightRequest,
    );
    super.dispose();
  }

  /// 分頁建好後會留在樹上，之後的推播要在這裡接新的標亮房號並重抓一次池。
  void _applyHighlightRequest() {
    _highlightRoomNo = MainShellTabRequest.poolHighlightRoomNo.value;
    _fetch();
  }

  Future<void> _fetch() async {
    // 換分類磚時先清空列表再取新資料：舊分類的單卡留在畫面上會被當成新分類
    // 的結果誤讀（列表順序與張數都是驗收點）。
    setState(() {
      _pool = null;
      _error = null;
    });
    try {
      final pool = await ApiClient.instance.getPool(
        dept: _deptQuery,
        category: _selected == 'all' ? null : _selected,
      );
      if (mounted) {
        setState(() => _pool = pool);
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error = '載入待認領失敗，請稍後再試。');
      }
    }
  }

  /// 把池裡的這張單接到自己名下。
  ///
  /// 離線時擋在這裡、不送請求：待認領是全部門在搶的同一批單，誰接到由後端
  /// 決定。離線時先讓畫面當作接到了，等連線恢復才發現單早被別人接走，員工
  /// 會白跑一趟。
  Future<void> _claim(PoolItem item) async {
    if (ConnectionStatus.instance.isOffline) {
      await showOfflineBlockedAlert(context);
      return;
    }
    await ApiClient.instance.claimTask(item.taskId);
    // 接到的單立刻就是「我的任務」的一列，首頁那一份今日進度因此變舊了。
    TodayRefresh.instance.request();
    await _fetch();
  }

  void _openTask(PoolItem item) {
    Navigator.of(context).push(
      CupertinoPageRoute<void>(
        builder: (_) => TaskScreen(roomNo: item.roomNo, taskId: item.taskId),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'pool.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        // 不用 CupertinoNavigationBar：這一頁的標題是內容的一部分，跟著分類磚
        // 與單卡一起捲（`Main.dc.html:251` 的 `<h1>`）。
        // 底部不讓 SafeArea 收邊：單卡要從浮動分頁列底下捲過去，讓開分頁列
        // 的那段留白由清單自己帶。
        child: SafeArea(
          bottom: false,
          child: ListView(
            padding: EdgeInsets.only(
              top: TmsSpacing.md,
              bottom: TmsSpacing.scrollPadBottom(
                MediaQuery.paddingOf(context).bottom,
              ),
            ),
            children: _content(),
          ),
        ),
      ),
    );
  }

  List<Widget> _content() {
    final pool = _pool;
    return [
      // 連線狀態列固定在內容最上方：待認領是「搶單」的頁，離線時搶不到單，
      // 員工要先知道現在連不上，而不是按了認領才發現。
      const SystemStatusBar(
        padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      ),
      const Padding(
        padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
        child: Text('待認領', style: TmsText.largeTitle),
      ),
      const SizedBox(height: TmsSpacing.lg),
      _categoryRow(pool?.counts ?? const <String, int>{}),
      if (_error != null) ...[
        const SizedBox(height: TmsSpacing.lg),
        Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.screenEdge,
          ),
          child: Text(_error!),
        ),
      ],
      if (pool == null && _error == null) ...[
        const SizedBox(height: TmsSpacing.lg),
        const Center(child: CupertinoActivityIndicator()),
      ],
      // 兩種「接不到單」要分開講。一個人都不在職時，池裡就算還躺著單也沒有人
      // 搶得走，畫面要提示補人、而不是說「都接完了」；這時也不把單卡列出來
      // ——卡上的「認領這單」對主管沒有意義，對停用的員工更按不得
      // （dsl「待認領顯示空狀態」要求搶單動作不可用）。
      if (pool != null && pool.activeStaffCount == 0) ...[
        const SizedBox(height: TmsSpacing.lg),
        Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.screenEdge,
          ),
          child: TmsSemantics(
            id: 'pool.empty-state--no-active-staff',
            label: _noActiveStaffMessage,
            child: const Text(_noActiveStaffMessage),
          ),
        ),
      ] else if (pool != null && pool.items.isEmpty) ...[
        const SizedBox(height: TmsSpacing.lg),
        TmsContentUnavailable(
          id: 'pool.empty-state',
          copy: TmsEmptyStates.housekeepingPool(
            filterApplied: _selected != 'all',
            poolHasItems: pool.counts.values.any((count) => count > 0),
          ),
          onAction: () {
            _selected = 'all';
            _fetch();
          },
        ),
      ] else if (pool != null) ...[
        // 單卡自己帶上下各 8 的外距，所以這裡只補 8，分類磚與第一張卡之間才
        // 是原型的 16（`Main.dc.html:249`）。
        const SizedBox(height: TmsSpacing.sm),
        for (final item in pool.items)
          _HousekeepingPoolCard(
            item: item,
            isHighlighted: item.roomNo == _highlightRoomNo,
            onTap: () => _openTask(item),
            onClaim: () => _claim(item),
          ),
      ],
    ];
  }

  static const String _noActiveStaffMessage = '這個部門目前沒有在職員工可以搶單';

  /// 分類磚列。筆數一律讀後端回的 `counts`，帶 category 查詢時 counts 仍是整池
  /// 的數字，所以切換分類不會讓別的磚數字跟著變。
  Widget _categoryRow(Map<String, int> counts) {
    return PoolFilterRow(
      tiles: [
        for (final category in _categories)
          PoolFilterChip(
            id: 'pool.category.${category.key}.chip',
            label: category.label,
            icon: category.icon,
            iconBackground: category.iconBackground,
            iconForeground: category.iconForeground,
            selected: _selected == category.key,
            countId: 'pool.category.${category.key}.count',
            count: counts[category.key] ?? 0,
            onTap: () {
              _selected = category.key;
              _fetch();
            },
          ),
      ],
    );
  }
}

/// 一張房務待認領單卡。卡本身的 id 是 `pool.card.{roomNo}`，卡內各列是
/// `pool.card.{roomNo}.{欄位}`（ui-plan F4）。
class _HousekeepingPoolCard extends StatelessWidget {
  const _HousekeepingPoolCard({
    required this.item,
    required this.isHighlighted,
    required this.onTap,
    required this.onClaim,
  });

  /// 未接提醒階段（契約 `PoolItem.reminderStage`）→ 單卡標籤 id 後綴、文字與
  /// 語氣。只有這兩段，沒有第三段，也不另外做橫幅或徽章。
  ///
  /// 兩段的語氣刻意不同：第一段只是提醒同仁，第二段已經驚動主管，紅色讓它在
  /// 一列標籤裡先被看到。
  static const Map<String, ({String idSuffix, String text, TmsTagTone tone})>
  _reminderTags = {
    'staff_reminded': (
      idSuffix: 'unaccepted-notified-peer',
      text: '未接 · 已提醒同仁',
      tone: TmsTagTone.warn,
    ),
    'manager_notified': (
      idSuffix: 'unaccepted-notified-manager',
      text: '未接 · 已通知主管',
      tone: TmsTagTone.danger,
    ),
  };

  final PoolItem item;
  final bool isHighlighted;
  final VoidCallback onTap;
  final VoidCallback onClaim;

  @override
  Widget build(BuildContext context) {
    // 標亮用同一個「畫面.區塊.房號」骨幹、依狀態切換 id 後綴，照 ui-plan
    // 的 `--state` 修飾慣例：沒標亮是 `pool.card.{roomNo}`，標亮時是
    // `pool.card.{roomNo}--highlighted`。
    final cardId = 'pool.card.${item.roomNo}';
    // 副標整行的設計是「{來源} · 約 N 分」，但 `GET /pool` 的 PoolItem 沒有
    // 預估分鐘這個欄位，所以只寫得出來源那一段。後端補上欄位之前，這裡寧可
    // 短一截也不湊一個數字：單卡上的分鐘數員工會拿來排今天要先做哪一間。
    final sourceLabel = housekeepingPoolSourceLabels[item.source];
    // 自動化規則開的單，退池原因欄位裝的是「規則引擎命中：{規則名}」，
    // 那是規則說明而不是誰退的；其他來源才是退池原因。
    final reasonFieldId = item.source == 'system'
        ? '$cardId.rule-hit-tag'
        : '$cardId.return-reason';
    final reminderTag = _reminderTags[item.reminderStage];
    final sameFloorTag = poolSameFloorTag(
      id: '$cardId.same-floor-tag',
      sameFloor: item.sameFloor,
      priorityNotify: item.priorityNotify,
    );
    final waitedTag = poolWaitedTag(
      waitedMinutes: item.waitedMinutes,
      reminderStage: item.reminderStage,
    );

    final categoryAppearance = tmsCategoryAppearance(item.category);
    final roomSquare = TmsRoomSquare(
      where: item.roomNo,
      label: categoryAppearance.label,
      palette: categoryAppearance.palette,
    );

    return PoolCard(
      id: isHighlighted ? '$cardId--highlighted' : cardId,
      highlighted: isHighlighted,
      onTap: onTap,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 公共區域的單沒有房號，方塊上寫的就是「公共區域」四個字；驗收
            // 要讀的是方塊上這段文字，所以 id 掛在方塊本身。
            if (item.isPublicArea)
              TmsSemantics(
                id: 'pool.card.common-area-label',
                label: PoolItem.publicAreaRoomNo,
                child: roomSquare,
              )
            else
              roomSquare,
            const SizedBox(width: TmsSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TmsText.headline,
                  ),
                  if (sourceLabel != null) ...[
                    const SizedBox(height: TmsSpacing.xs),
                    PoolCardSubtitle(id: '$cardId.subtitle', text: sourceLabel),
                  ],
                  const SizedBox(height: TmsSpacing.xs),
                  PoolTagRow(
                    tags: [
                      ?sameFloorTag,
                      if (item.returnReason != null)
                        PoolCardField(
                          id: reasonFieldId,
                          text: item.returnReason!,
                        ),
                      if (reminderTag != null)
                        PoolCardField(
                          id: '$cardId.status-tag--${reminderTag.idSuffix}',
                          text: reminderTag.text,
                          tone: reminderTag.tone,
                        ),
                      // 等待時間排在整列最後：前面幾顆講的是「這張單是什麼
                      // 單」，它講的是「它擱了多久」，員工先認單再看久不久。
                      ?waitedTag,
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
        // 設計上接手的單還要在標籤列掛一顆「接手 {前一位負責人} 的進度」，
        // 但 PoolItem 只給得出交代內容、給不出前一位負責人是誰。後端補上這個
        // 欄位之前，整顆標籤不畫，交代內容由這塊備註自己講完。
        if (item.handoverNote != null)
          PoolCardNote(
            id: '$cardId.handoff-progress',
            text: item.handoverNote!,
          ),
        PoolClaimButton(id: '$cardId.claim-button', onPressed: onClaim),
      ],
    );
  }
}
