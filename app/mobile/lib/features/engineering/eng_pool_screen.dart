import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_category_square.dart';
import '../../core/widgets/tms_category_tile.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_tag.dart';
import '../pool/pool_widgets.dart';
import '../ticket/repair_labels.dart';
import '../ticket/repair_ticket_form_screen.dart';
import '../ticket/ticket_screen.dart';

/// 工務待認領（ui-plan 畫面 13 的 `engPool`）。
///
/// 分流與排序一律交給後端：來源分流磚只是把選擇換成 `GET /pool` 的
/// urgent／source 查詢參數重新取一次，App 不在本機重排、不在本機過濾；
/// 列表照後端回應順序呈現。單卡與分流磚的外觀與房務待認領共用
/// `features/pool/pool_widgets.dart` 的元件。
class EngPoolScreen extends StatefulWidget {
  const EngPoolScreen({super.key});

  @override
  State<EngPoolScreen> createState() => _EngPoolScreenState();
}

class _EngPoolScreenState extends State<EngPoolScreen> {
  /// 分流磚：畫面文字與 accessibility id 的 {source} 段；urgent 是急件
  /// 維度、staff／desk 是來源維度（契約 listDeptPool 的兩個篩選參數）。
  /// `sourceParam` 是該磚要送給後端的 `source` 值，沒有值代表不帶這個參數。
  /// 圖示方塊的配色照原型的工務分流磚逐格指定：急件紅、櫃檯黃、房務報修橘，
  /// 員工在同一排磚上靠顏色而不是讀字找到要的那一格（`Main.dc.html:3839-3842`）。
  ///
  /// 四格的順序也照那四行：來源那兩格是櫃檯在前、房務報修在後。
  ///
  /// 圖示一律是描邊字形（原型四格都是 `fill="none" stroke-width="1.9"`，
  /// `Main.dc.html:981`），不用 `_fill` 實心變體。
  static const _categories = [
    (
      key: 'all',
      label: '全部',
      sourceParam: null,
      countKey: null,
      icon: CupertinoIcons.square_grid_2x2,
      iconBackground: TmsColors.brandContainer,
      iconForeground: TmsColors.brandPrimary,
    ),
    (
      key: 'urgent',
      label: '急件',
      sourceParam: null,
      countKey: 'urgent',
      icon: CupertinoIcons.bolt,
      iconBackground: TmsColors.errorContainer,
      iconForeground: TmsColors.onErrorContainer,
    ),
    (
      key: 'desk',
      label: '櫃檯',
      sourceParam: 'desk_forward',
      countKey: 'deskForward',
      icon: CupertinoIcons.person_2,
      iconBackground: TmsColors.otherContainer,
      iconForeground: TmsColors.onOtherContainer,
    ),
    (
      key: 'staff',
      label: '房務報修',
      sourceParam: 'staff_report',
      countKey: 'staffReport',
      icon: CupertinoIcons.wrench,
      iconBackground: TmsColors.brandAccentContainer,
      iconForeground: TmsColors.brandOnAccentContainer,
    ),
  ];

  String _selected = 'all';
  DeptPool? _pool;
  String? _error;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    // 換分流磚時先清空列表再取新資料：舊分類的單卡留在畫面上會被當成
    // 新分類的結果誤讀（列表順序與張數都是驗收點）。
    setState(() {
      _pool = null;
      _error = null;
    });
    try {
      final selected = _categories.firstWhere(
        (category) => category.key == _selected,
      );
      final pool = await ApiClient.instance.getPool(
        urgent: selected.key == 'urgent' ? true : null,
        source: selected.sourceParam,
      );
      if (mounted) {
        setState(() {
          _pool = pool;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = '載入待認領失敗，請稍後再試。';
        });
      }
    }
  }

  Future<void> _claim(PoolItem item) async {
    try {
      await ApiClient.instance.claimTask(item.taskId);
      await _fetch();
    } on ApiException catch (e) {
      // 撞單（409）：這張單已被別人接走。先重取列表讓它從待認領消失，
      // 再彈提示——提示文字直接用後端的 message（帶認領人姓名）。
      final message = e.errorMessage ?? '這單已被其他同事認領';
      await _fetch();
      if (!mounted) {
        return;
      }
      await showCupertinoDialog<void>(
        context: context,
        builder: (dialogContext) => TmsAlert(
          message: message,
          messageId: 'engPool.claim-conflict-alert',
          actions: [
            TmsAlertAction(
              label: '知道了',
              style: TmsAlertActionStyle.primary,
              onPressed: () => Navigator.of(dialogContext).pop(),
            ),
          ],
        ),
      );
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = '認領失敗，請稍後再試。';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'engPool.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        // 導覽列只剩開工單那顆鈕：這一頁的標題是內容的一部分，跟著分流磚與
        // 單卡一起捲（`Main.dc.html:975` 的 `<h1>`）。
        navigationBar: CupertinoNavigationBar(
          trailing: TmsSemantics(
            id: 'engPool.create-ticket-fab',
            child: CupertinoButton(
              padding: EdgeInsets.zero,
              onPressed: () {
                Navigator.of(context).push(
                  CupertinoPageRoute<void>(
                    builder: (_) => const RepairTicketFormScreen(),
                  ),
                );
              },
              child: const Text('開工單'),
            ),
          ),
        ),
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
        padding: EdgeInsets.fromLTRB(
          TmsSpacing.screenEdge,
          0,
          TmsSpacing.screenEdge,
          TmsSpacing.md,
        ),
      ),
      const Padding(
        padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
        child: Text('待認領', style: TmsText.largeTitle),
      ),
      const SizedBox(height: TmsSpacing.lg),
      // 徽章只掛在急件、櫃檯、房務報修三格，數字一律讀後端回的 counts，
      // 不在本機數列表：帶篩選查詢時列表只有該分流的單，本機數出來的是
      // 「這一頁有幾張」而不是「這個分流有幾張」。「全部」那格沒有徽章，
      // 因為下面整排單卡就是它的數字。
      //
      // 這三格是 0 的時候整顆徽章不畫，照原型。房務的分類磚相反，0 也照
      // 畫——那個數字是 `待認領分類標籤顯示如下` 那句斷言要讀的節點，藏
      // 起來就讀不到。兩邊不同是各自的理由造成的，不要為了看起來一致把
      // 其中一邊改掉。
      PoolFilterRow(
        tiles: [
          for (final category in _categories)
            PoolFilterChip(
              id: 'engPool.category.${category.key}.chip',
              label: category.label,
              icon: category.icon,
              iconBackground: category.iconBackground,
              iconForeground: category.iconForeground,
              selected: _selected == category.key,
              count: _badgeCount(category.countKey),
              unselectedBackground: TmsCategoryTile.translucentBackground,
              onTap: () {
                _selected = category.key;
                _fetch();
              },
            ),
        ],
      ),
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
      if (pool != null && pool.items.isEmpty) ...[
        const SizedBox(height: TmsSpacing.lg),
        TmsContentUnavailable(
          id: 'engPool.empty-state',
          copy: TmsEmptyStates.engineeringPool(
            filterApplied: _selected != 'all',
            poolHasItems: pool.counts.values.any((count) => count > 0),
          ),
          onAction: () {
            _selected = 'all';
            _fetch();
          },
        ),
      ] else if (pool != null) ...[
        // 單卡自己帶上下各 8 的外距，所以這裡只補 8，分流磚與第一張卡之間才
        // 是原型的 16（`Main.dc.html:973`）。
        const SizedBox(height: TmsSpacing.sm),
        for (final item in pool.items) _engPoolCard(item),
      ],
    ];
  }

  /// 替一格分流磚算出徽章上的數字；回 null 時那一格不畫徽章。
  ///
  /// 徽章的用途是把工務叫去還有單可接的那一格，所以沒有單可接就不該有紅
  /// 點：筆數是 0 的格子不畫。「全部」那格沒有對應的筆數（[countKey] 為
  /// null），下面整排單卡就是它的數字。後端還沒給這個維度的筆數時欄位缺
  /// 席，這一格同樣留白——畫成 0 會讓工務以為那個分流真的空了。
  int? _badgeCount(String? countKey) {
    if (countKey == null) {
      return null;
    }
    final count = _pool?.counts[countKey];
    if (count == null || count == 0) {
      return null;
    }
    return count;
  }

  /// 一張工務待認領單卡，id 以工務單號當識別段：
  /// `engPool.item.{單號}.card`，卡內各列是 `engPool.item.{單號}.{欄位}`。
  Widget _engPoolCard(PoolItem item) {
    // 工務池的單都是工務單、一定有單號；沒有單號的資料列不是這個畫面能
    // 呈現的東西，直接跳過而不是畫出一張定位不到的卡。
    final ticketNo = item.ticketNo;
    if (ticketNo == null) {
      return const SizedBox.shrink();
    }
    final itemId = 'engPool.item.$ticketNo';
    final sourceLabel = engineeringPoolSourceLabels[item.source];
    final waitedTag = poolWaitedTag(
      waitedMinutes: item.waitedMinutes,
      reminderStage: item.reminderStage,
    );
    final roomSquare = TmsRoomSquare(
      where: item.roomNo,
      label: repairCategoryShortText(item.category),
      // 待認領裡的單都還沒人接，不會是已完修。
      palette: tmsRepairSquarePalette(isUrgent: item.urgent, isDone: false),
    );

    return PoolCard(
      id: '$itemId.card',
      semanticsLabel: '$ticketNo ${item.roomNo} ${item.title}',
      // 卡的上半整塊可點，開這張單的工務單詳情（`Main.dc.html:992`）。工務在
      // 決定要不要接之前得先看照片與描述，只有認領鈕可按的話，他只能先接再
      // 看，接錯了還要放回去。
      onTap: () => Navigator.of(context).push(
        CupertinoPageRoute<void>(
          builder: (_) => TicketScreen(taskId: item.taskId),
        ),
      ),
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 原型的方塊寫區域名（餐廳、1F 大廳）、另外掛一顆「公共區域」標籤
            // （`Main.dc.html:3850`）。後端沒有區域名這個欄位，無房號的單房號
            // 字串就是「公共區域」四個字，所以方塊已經把這件事說完了，再掛一
            // 顆一模一樣的標籤只是讓同一句話出現兩次。要讀的節點掛在方塊上，
            // 與房務待認領同一套。
            if (item.isPublicArea)
              TmsSemantics(
                id: '$itemId.common-area-label',
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
                  // 主標是工單標題、副標是來源（`Main.dc.html:998-999`）。
                  // 工務掃這一列要先讀到「壞的是什麼」，單號只在對單時才用
                  // 得上，房號左邊的方塊已經寫了。
                  Text(
                    item.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TmsText.headline,
                  ),
                  if (sourceLabel != null) ...[
                    const SizedBox(height: TmsSpacing.xs),
                    // 原型的副標是來源說明全句（「住客掃碼 · 規則命中
                    // 「冷氣」」「櫃檯 林佩君 派單（日文已翻譯）」），而
                    // `PoolItem` 只給得出來源 enum，報修人與規則命中都沒有
                    // 欄位。缺的那幾段不湊，副標只寫來源這一段。
                    PoolCardSubtitle(
                      id: '$itemId.source-label',
                      text: sourceLabel,
                    ),
                  ],
                  const SizedBox(height: TmsSpacing.xs),
                  PoolTagRow(
                    tags: [
                      // 原型的急件標籤帶判定規則（「急件 · 安全類：漏水」，
                      // `Main.dc.html:3848`），但 `PoolItem` 沒有規則欄位，
                      // 所以只寫得出「急件」兩個字。
                      if (item.urgent)
                        const TmsTag(label: '急件', tone: TmsTagTone.danger),
                      // 等待時間排在整列最後：前面幾顆講的是「這張單是什麼
                      // 單」，它講的是「它擱了多久」，工務先認單再看久不久。
                      ?waitedTag,
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
        PoolClaimButton(
          id: '$itemId.claim-button',
          glow: TmsPillGlow.alt,
          onPressed: () => _claim(item),
        ),
      ],
    );
  }
}
