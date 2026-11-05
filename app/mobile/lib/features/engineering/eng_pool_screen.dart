import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';
import '../pool/pool_widgets.dart';
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
  static const _categories = [
    (key: 'all', label: '全部', sourceParam: null),
    (key: 'urgent', label: '急件', sourceParam: null),
    (key: 'staff', label: '房務報修', sourceParam: 'staff_report'),
    (key: 'desk', label: '櫃檯', sourceParam: 'desk_forward'),
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
        builder: (dialogContext) => TmsSemantics(
          id: 'engPool.claim-conflict-alert',
          label: message,
          child: CupertinoAlertDialog(
            content: Text(message),
            actions: [
              CupertinoDialogAction(
                onPressed: () => Navigator.of(dialogContext).pop(),
                child: const Text('知道了'),
              ),
            ],
          ),
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
        Padding(
          padding: const EdgeInsets.all(16),
          child: Wrap(
            spacing: 8,
            children: [
              for (final category in _categories)
                PoolFilterChip(
                  id: 'engPool.category.${category.key}.chip',
                  label: category.label,
                  selected: _selected == category.key,
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
              children: [
                for (final item in pool.items) _engPoolCard(item),
              ],
            ),
          ),
      ],
    );

    return TmsSemantics(
      id: 'engPool.screen',
      child: CupertinoPageScaffold(
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
        child: SafeArea(child: content),
      ),
    );
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

    return PoolCard(
      id: '$itemId.card',
      semanticsLabel: '$ticketNo ${item.roomNo} ${item.title}',
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('$ticketNo · ${item.roomNo}'),
            if (item.urgent) const Text('急件'),
          ],
        ),
        Text(item.title),
        if (sourceLabel != null)
          PoolCardField(id: '$itemId.source-label', text: sourceLabel),
        TmsSemantics(
          id: '$itemId.claim-button',
          child: CupertinoButton(
            onPressed: () => _claim(item),
            child: const Text('認領這單'),
          ),
        ),
      ],
    );
  }
}
