import 'package:flutter/cupertino.dart';

import '../../core/test_backdoor.dart';
import '../../core/tms_semantics.dart';
import '../task/task_screen.dart';

/// 待認領骨架畫面：卡片列表，支援「標亮」某張卡——通常是推播帶
/// `highlight` 房號導過來時，該房的卡要在可視範圍內並掛標亮狀態 id。
///
/// 資料來源是 [TestHooks.seedTasks] 這份測試後門灌進來的 fixture，真後端
/// 接線前的過渡做法；見 `core/test_backdoor.dart` 檔頭說明。
class PoolScreen extends StatelessWidget {
  const PoolScreen({super.key, this.highlightRoomNo});

  /// 推播帶 `highlight` 房號時，該房的卡要掛標亮狀態 id
  /// （`pool.card.{roomNo}--highlighted`）。
  final String? highlightRoomNo;

  @override
  Widget build(BuildContext context) {
    final tasks = TestHooks.instance.seedTasks
        .where((task) => task.status == '待認領')
        .toList();

    return TmsSemantics(
      id: 'pool.screen',
      child: CupertinoPageScaffold(
        navigationBar: const CupertinoNavigationBar(middle: Text('待認領')),
        child: SafeArea(
          child: tasks.isEmpty
              ? const Center(child: Text('目前沒有待認領的工單'))
              : ListView.builder(
                  itemCount: tasks.length,
                  itemBuilder: (context, index) {
                    final task = tasks[index];
                    final isHighlighted = task.roomNo == highlightRoomNo;
                    return _PoolCard(
                      task: task,
                      isHighlighted: isHighlighted,
                      onTap: () {
                        Navigator.of(context).push(
                          CupertinoPageRoute<void>(
                            builder: (_) => TaskScreen(roomNo: task.roomNo),
                          ),
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
    required this.task,
    required this.isHighlighted,
    required this.onTap,
  });

  final SeedTask task;
  final bool isHighlighted;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    // 標亮用同一個「畫面.區塊.房號」骨幹、依狀態切換 id 後綴，照 ui-plan
    // 的 `--state` 修飾慣例：沒標亮是 `pool.card.{roomNo}`，標亮時是
    // `pool.card.{roomNo}--highlighted`。
    final id = isHighlighted
        ? 'pool.card.${task.roomNo}--highlighted'
        : 'pool.card.${task.roomNo}';

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
              Text('${task.roomNo} 號房', style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              Text(task.content),
            ],
          ),
        ),
      ),
    );
  }
}
