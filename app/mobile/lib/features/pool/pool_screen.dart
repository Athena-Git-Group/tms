import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';
import '../task/task_screen.dart';

/// 待認領骨架畫面：卡片列表，支援「標亮」某張卡——通常是推播帶
/// `highlight` 房號導過來時，該房的卡要在可視範圍內並掛標亮狀態 id。
///
/// 資料來源是 [ApiClient.getPool]（`GET /pool`）：後端依部門回傳待認領池，
/// 排序後端已排好，畫面照單渲染，不在前端重排。
class PoolScreen extends StatefulWidget {
  const PoolScreen({super.key, this.highlightRoomNo});

  /// 推播帶 `highlight` 房號時，該房的卡要掛標亮狀態 id
  /// （`pool.card.{roomNo}--highlighted`）。
  final String? highlightRoomNo;

  @override
  State<PoolScreen> createState() => _PoolScreenState();
}

class _PoolScreenState extends State<PoolScreen> {
  late Future<DeptPool> _poolFuture;

  @override
  void initState() {
    super.initState();
    _poolFuture = ApiClient.instance.getPool();
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'pool.screen',
      child: CupertinoPageScaffold(
        navigationBar: const CupertinoNavigationBar(middle: Text('待認領')),
        child: SafeArea(
          child: FutureBuilder<DeptPool>(
            future: _poolFuture,
            builder: (context, snapshot) {
              if (snapshot.hasError) {
                return const Center(child: Text('載入失敗，請稍後再試'));
              }
              if (!snapshot.hasData) {
                return const Center(child: CupertinoActivityIndicator());
              }
              final items = snapshot.data!.items;
              if (items.isEmpty) {
                return const Center(child: Text('目前沒有待認領的工單'));
              }
              return ListView.builder(
                itemCount: items.length,
                itemBuilder: (context, index) {
                  final item = items[index];
                  final isHighlighted = item.roomNo == widget.highlightRoomNo;
                  return _PoolCard(
                    item: item,
                    isHighlighted: isHighlighted,
                    onTap: () {
                      Navigator.of(context).push(
                        CupertinoPageRoute<void>(
                          builder: (_) => TaskScreen(
                            roomNo: item.roomNo,
                            taskId: item.taskId,
                          ),
                        ),
                      );
                    },
                  );
                },
              );
            },
          ),
        ),
      ),
    );
  }
}

class _PoolCard extends StatelessWidget {
  const _PoolCard({
    required this.item,
    required this.isHighlighted,
    required this.onTap,
  });

  final PoolItem item;
  final bool isHighlighted;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    // 標亮用同一個「畫面.區塊.房號」骨幹、依狀態切換 id 後綴，照 ui-plan
    // 的 `--state` 修飾慣例：沒標亮是 `pool.card.{roomNo}`，標亮時是
    // `pool.card.{roomNo}--highlighted`。
    final id = isHighlighted
        ? 'pool.card.${item.roomNo}--highlighted'
        : 'pool.card.${item.roomNo}';

    return TmsSemantics(
      id: id,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: isHighlighted
                ? const Color(0xFFFFF3CD)
                : CupertinoColors.white,
            border: Border.all(
              color: isHighlighted
                  ? const Color(0xFFE6A700)
                  : CupertinoColors.systemGrey4,
              width: isHighlighted ? 2 : 1,
            ),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('${item.roomNo} 號房', style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              Text(item.title),
            ],
          ),
        ),
      ),
    );
  }
}
