import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';
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
  static const _categories = [
    (key: 'all', label: '全部'),
    (key: 'amenity', label: '備品'),
    (key: 'cleaning', label: '打掃'),
    (key: 'other', label: '其他'),
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
    // 換分類磚時先清空列表再取新資料：舊分類的單卡留在畫面上會被當成新分類
    // 的結果誤讀（列表順序與張數都是驗收點）。
    setState(() {
      _pool = null;
      _error = null;
    });
    try {
      final pool = await ApiClient.instance.getPool(
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

  void _openTask(PoolItem item) {
    Navigator.of(context).push(
      CupertinoPageRoute<void>(
        builder: (_) => TaskScreen(roomNo: item.roomNo, taskId: item.taskId),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pool = _pool;
    return TmsSemantics(
      id: 'pool.screen',
      child: CupertinoPageScaffold(
        navigationBar: const CupertinoNavigationBar(middle: Text('待認領')),
        child: SafeArea(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _categoryRow(pool?.counts ?? const <String, int>{}),
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
                  child: Text('目前沒有待認領的工單'),
                ),
              if (pool != null)
                Expanded(
                  child: ListView(
                    children: [
                      for (final item in pool.items)
                        _HousekeepingPoolCard(
                          item: item,
                          isHighlighted: item.roomNo == widget.highlightRoomNo,
                          onTap: () => _openTask(item),
                        ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  /// 分類磚列。筆數一律讀後端回的 `counts`，帶 category 查詢時 counts 仍是整池
  /// 的數字，所以切換分類不會讓別的磚數字跟著變。
  Widget _categoryRow(Map<String, int> counts) {
    return Padding(
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          for (final category in _categories)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: PoolFilterChip(
                id: 'pool.category.${category.key}.chip',
                label: category.label,
                selected: _selected == category.key,
                countId: 'pool.category.${category.key}.count',
                count: counts[category.key] ?? 0,
                onTap: () {
                  _selected = category.key;
                  _fetch();
                },
              ),
            ),
        ],
      ),
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
  });

  /// 無房號的單在後端掛在公共區域佔位房上，房號字串就是「公共區域」
  /// （`AutomationRuleService.PUBLIC_AREA_ROOM_NO`）。
  static const String publicAreaRoomNo = '公共區域';

  /// 未接提醒階段（契約 `PoolItem.reminderStage`）→ 單卡標籤 id 後綴與文字。
  /// 只有這兩段，沒有第三段，也不另外做橫幅或徽章。
  static const Map<String, ({String idSuffix, String text})> _reminderTags = {
    'staff_reminded': (
      idSuffix: 'unaccepted-notified-peer',
      text: '未接 · 已提醒同仁',
    ),
    'manager_notified': (
      idSuffix: 'unaccepted-notified-manager',
      text: '未接 · 已通知主管',
    ),
  };

  final PoolItem item;
  final bool isHighlighted;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    // 標亮用同一個「畫面.區塊.房號」骨幹、依狀態切換 id 後綴，照 ui-plan
    // 的 `--state` 修飾慣例：沒標亮是 `pool.card.{roomNo}`，標亮時是
    // `pool.card.{roomNo}--highlighted`。
    final cardId = 'pool.card.${item.roomNo}';
    final isPublicArea = item.roomNo == publicAreaRoomNo;
    final sourceLabel = housekeepingPoolSourceLabels[item.source];
    // 自動化規則開的單，退池原因欄位裝的是「規則引擎命中：{規則名}」，
    // 那是規則說明而不是誰退的；其他來源才是退池原因。
    final reasonFieldId = item.source == 'system'
        ? '$cardId.rule-hit-tag'
        : '$cardId.return-reason';
    final reminderTag = _reminderTags[item.reminderStage];

    return PoolCard(
      id: isHighlighted ? '$cardId--highlighted' : cardId,
      highlighted: isHighlighted,
      onTap: onTap,
      children: [
        Row(
          children: [
            if (isPublicArea)
              TmsSemantics(
                id: 'pool.card.common-area-label',
                label: publicAreaRoomNo,
                child: const Text(
                  publicAreaRoomNo,
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
              )
            else
              Text(
                '${item.roomNo} 號房',
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
            if (item.sameFloor)
              Padding(
                padding: const EdgeInsets.only(left: 8),
                child: PoolCardField(
                  id: '$cardId.same-floor-tag',
                  text: '同樓層',
                ),
              ),
          ],
        ),
        const SizedBox(height: 4),
        Text(item.title),
        if (sourceLabel != null)
          PoolCardField(id: '$cardId.source-tag', text: sourceLabel),
        if (item.returnReason != null)
          PoolCardField(id: reasonFieldId, text: item.returnReason!),
        if (item.handoverNote != null)
          PoolCardField(
            id: '$cardId.handoff-progress',
            text: item.handoverNote!,
          ),
        if (reminderTag != null)
          PoolCardField(
            id: '$cardId.status-tag--${reminderTag.idSuffix}',
            text: reminderTag.text,
          ),
      ],
    );
  }
}
