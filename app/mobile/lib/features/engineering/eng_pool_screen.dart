import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_category_square.dart';
import '../../core/widgets/tms_category_tile.dart';
import '../../core/widgets/tms_tag.dart';
import '../pool/pool_widgets.dart';
import '../ticket/repair_labels.dart';
import '../ticket/repair_ticket_form_screen.dart';

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
  /// 圖示方塊的配色照原型的工務分流磚逐格指定：急件紅、房務報修橘、櫃檯黃，
  /// 員工在同一排磚上靠顏色而不是讀字找到要的那一格。
  static const _categories = [
    (
      key: 'all',
      label: '全部',
      sourceParam: null,
      countKey: null,
      icon: CupertinoIcons.square_grid_2x2_fill,
      iconBackground: TmsColors.brandContainer,
      iconForeground: TmsColors.brandPrimary,
    ),
    (
      key: 'urgent',
      label: '急件',
      sourceParam: null,
      countKey: 'urgent',
      icon: CupertinoIcons.exclamationmark_triangle_fill,
      iconBackground: TmsColors.errorContainer,
      iconForeground: TmsColors.onErrorContainer,
    ),
    (
      key: 'staff',
      label: '房務報修',
      sourceParam: 'staff_report',
      countKey: 'staffReport',
      icon: CupertinoIcons.wrench_fill,
      iconBackground: TmsColors.brandAccentContainer,
      iconForeground: TmsColors.brandOnAccentContainer,
    ),
    (
      key: 'desk',
      label: '櫃檯',
      sourceParam: 'desk_forward',
      countKey: 'deskForward',
      icon: CupertinoIcons.person_2_fill,
      iconBackground: TmsColors.otherContainer,
      iconForeground: TmsColors.onOtherContainer,
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
    final pool = _pool;
    final content = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 連線狀態列固定在內容最上方：待認領是「搶單」的頁，離線時搶不到單，
        // 員工要先知道現在連不上，而不是按了認領才發現。
        const SystemStatusBar(
          padding: EdgeInsets.fromLTRB(
            TmsSpacing.screenEdge,
            TmsSpacing.md,
            TmsSpacing.screenEdge,
            0,
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(vertical: TmsSpacing.md),
          // 徽章只掛在急件、房務報修、櫃檯三格，數字一律讀後端回的 counts，
          // 不在本機數列表：帶篩選查詢時列表只有該分流的單，本機數出來的是
          // 「這一頁有幾張」而不是「這個分流有幾張」。「全部」那格沒有徽章，
          // 因為下面整排單卡就是它的數字。
          //
          // 這三格是 0 的時候整顆徽章不畫，照原型。房務的分類磚相反，0 也照
          // 畫——那個數字是 `待認領分類標籤顯示如下` 那句斷言要讀的節點，藏
          // 起來就讀不到。兩邊不同是各自的理由造成的，不要為了看起來一致把
          // 其中一邊改掉。
          child: PoolFilterRow(
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
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(_error!),
          ),
        if (pool == null && _error == null)
          const Padding(
            padding: EdgeInsets.all(16),
            child: CupertinoActivityIndicator(),
          ),
        if (pool != null && pool.items.isEmpty)
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text('目前沒有待認領的工務單'),
          ),
        if (pool != null)
          Expanded(
            child: ListView(
              padding: EdgeInsets.only(
                bottom: TmsSpacing.scrollPadBottom(
                  MediaQuery.paddingOf(context).bottom,
                ),
              ),
              children: [for (final item in pool.items) _engPoolCard(item)],
            ),
          ),
      ],
    );

    return TmsSemantics(
      id: 'engPool.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        navigationBar: CupertinoNavigationBar(
          middle: const Text('待認領'),
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
        child: SafeArea(bottom: false, child: content),
      ),
    );
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

    return PoolCard(
      id: '$itemId.card',
      semanticsLabel: '$ticketNo ${item.roomNo} ${item.title}',
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TmsRoomSquare(
              where: item.roomNo,
              label: _categoryShortText(item.category),
              // 待認領裡的單都還沒人接，不會是已完修。
              palette: tmsRepairSquarePalette(
                isUrgent: item.urgent,
                isDone: false,
              ),
            ),
            const SizedBox(width: TmsSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '$ticketNo · ${item.roomNo}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TmsText.headline,
                  ),
                  const SizedBox(height: TmsSpacing.xxs),
                  Text(
                    item.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TmsText.subheadline
                        .copyWith(color: TmsColors.secondaryLabel),
                  ),
                  const SizedBox(height: TmsSpacing.xs),
                  PoolTagRow(
                    tags: [
                      if (item.urgent)
                        const TmsTag(label: '急件', tone: TmsTagTone.danger),
                      if (sourceLabel != null)
                        PoolCardField(
                          id: '$itemId.source-label',
                          text: sourceLabel,
                        ),
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
          onPressed: () => _claim(item),
        ),
      ],
    );
  }
}

/// 給單卡的 56pt 方塊第二行用的類別短名。
///
/// 方塊只有 56pt 寬，「家具／設備」五個字會超出去，所以這一類縮成「設備」。
/// 其餘類別兩個字就放得下，直接沿用 [repairCategoryText] 的說法，讓方塊上的
/// 用語和詳情頁一致。設計稿的對應處在 `Main.dc.html:3825`。
String _categoryShortText(String category) {
  return category == 'furniture_equipment'
      ? '設備'
      : repairCategoryText(category);
}
