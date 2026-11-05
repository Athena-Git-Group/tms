import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';
import '../ticket/repair_labels.dart';
import '../ticket/repair_ticket_form_screen.dart';

/// 工務待認領（ui-plan 畫面 13 的 `engPool`）。
///
/// 分流與排序一律交給後端：來源分流磚只是把選擇換成 `GET /pool` 的
/// urgent／source 查詢參數重新取一次，App 不在本機重排、不在本機過濾；
/// 列表照後端回應順序呈現。
class EngPoolScreen extends StatefulWidget {
  const EngPoolScreen({super.key});

  @override
  State<EngPoolScreen> createState() => _EngPoolScreenState();
}

class _EngPoolScreenState extends State<EngPoolScreen> {
  /// 分流磚：畫面文字與 accessibility id 的 {source} 段；urgent 是急件
  /// 維度、staff／desk 是來源維度（契約 listDeptPool 的兩個篩選參數）。
  static const _categories = [
    (source: 'all', label: '全部'),
    (source: 'urgent', label: '急件'),
    (source: 'staff', label: '房務報修'),
    (source: 'desk', label: '櫃檯'),
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
      final pool = await ApiClient.instance.getPool(
        urgent: _selected == 'urgent' ? true : null,
        source: switch (_selected) {
          'staff' => 'staff_report',
          'desk' => 'desk_forward',
          _ => null,
        },
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
                TmsSemantics(
                  id: 'engPool.category.${category.source}.chip',
                  child: GestureDetector(
                    onTap: () {
                      _selected = category.source;
                      _fetch();
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 6,
                      ),
                      decoration: BoxDecoration(
                        color: _selected == category.source
                            ? CupertinoColors.activeBlue
                            : CupertinoColors.white,
                        border: Border.all(
                          color: CupertinoColors.systemGrey4,
                        ),
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Text(
                        category.label,
                        style: TextStyle(
                          color: _selected == category.source
                              ? CupertinoColors.white
                              : CupertinoColors.black,
                        ),
                      ),
                    ),
                  ),
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
                for (final item in pool.items) _poolCard(item),
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

  Widget _poolCard(PoolItem item) {
    // 工務池的單都是工務單、一定有單號；防衛性地跳過沒有單號的資料列，
    // 單卡的 accessibility id 以單號當識別段。
    final ticketNo = item.ticketNo;
    if (ticketNo == null) {
      return const SizedBox.shrink();
    }
    final sourceLabel = poolSourceLabels[item.source];
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: TmsSemantics(
        id: 'engPool.item.$ticketNo.card',
        label: '$ticketNo ${item.roomNo} ${item.title}',
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: CupertinoColors.white,
            border: Border.all(color: CupertinoColors.systemGrey4),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
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
                TmsSemantics(
                  id: 'engPool.item.$ticketNo.source-label',
                  label: sourceLabel,
                  child: Text(sourceLabel),
                ),
              TmsSemantics(
                id: 'engPool.item.$ticketNo.claim-button',
                child: CupertinoButton(
                  onPressed: () => _claim(item),
                  child: const Text('認領這單'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
