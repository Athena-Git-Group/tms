import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';
import '../celebrate/celebrate_screen.dart';
import '../ticket/ticket_screen.dart';
import 'ask_sheet.dart';
import 'hold_sheet.dart';
import 'repair_sheet.dart';

/// 任務頁骨架畫面：顯示單一房號的任務內容。
///
/// 資料來源是 [ApiClient.getTask]（`GET /tasks/{id}`）。從待認領池點進來時
/// 會帶 [taskId] 直接查詳情；推播導頁只帶房號，這時先用
/// `GET /pool`、`GET /me/today` 兩個讀取端點反查該房的任務 id——契約目前
/// 沒有「用房號查任務」的端點，進行中或已中斷的任務查不到時，畫面誠實顯示
/// 找不到，等後續 feature 補上正式的查法。
///
/// 當任務狀態是「已中斷」（契約 state 的 `interrupted`）且停住原因
/// （`holdReason`）含「工務已修好」時，顯示恢復提示列（`task.resume-banner`）
/// ——這是 Rule「工務單完修的推播提示可以回去恢復原任務」要驗的畫面狀態。
class TaskScreen extends StatefulWidget {
  const TaskScreen({super.key, required this.roomNo, this.taskId});

  final String roomNo;

  /// 任務 id；池卡點進來會帶，推播導頁只帶房號時為 null、由畫面反查。
  final String? taskId;

  @override
  State<TaskScreen> createState() => _TaskScreenState();
}

class _TaskScreenState extends State<TaskScreen> {
  late Future<TaskDetail?> _taskFuture;

  @override
  void initState() {
    super.initState();
    _taskFuture = _loadTask();
  }

  Future<TaskDetail?> _loadTask() async {
    final taskId = widget.taskId ?? await _resolveTaskIdByRoomNo();
    if (taskId == null) {
      return null;
    }
    return ApiClient.instance.getTask(taskId);
  }

  /// 用房號反查任務 id：先找待認領池，再找我的下一間推薦。
  Future<String?> _resolveTaskIdByRoomNo() async {
    final pool = await ApiClient.instance.getPool();
    for (final item in pool.items) {
      if (item.roomNo == widget.roomNo) {
        return item.taskId;
      }
    }
    final today = await ApiClient.instance.getMyToday();
    for (final next in today.nextTasks) {
      if (next.roomNo == widget.roomNo) {
        return next.taskId;
      }
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'task.screen',
      child: CupertinoPageScaffold(
        navigationBar:
            CupertinoNavigationBar(middle: Text('${widget.roomNo} 的任務頁')),
        child: SafeArea(
          child: FutureBuilder<TaskDetail?>(
            future: _taskFuture,
            builder: (context, snapshot) {
              if (snapshot.hasError) {
                return const Padding(
                  padding: EdgeInsets.all(16),
                  child: Text('載入失敗，請稍後再試'),
                );
              }
              if (snapshot.connectionState != ConnectionState.done) {
                return const Center(child: CupertinoActivityIndicator());
              }
              return _buildTask(snapshot.data);
            },
          ),
        ),
      ),
    );
  }

  Widget _buildTask(TaskDetail? task) {
    final showResumeBanner = task != null &&
        task.state == 'interrupted' &&
        (task.holdReason?.contains('工務已修好') ?? false);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (showResumeBanner)
          TmsSemantics(
            id: 'task.resume-banner',
            child: Container(
              width: double.infinity,
              margin: const EdgeInsets.all(16),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFE6F7EC),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      '${widget.roomNo} 號房的工務已修好，您可以恢復原任務了',
                    ),
                  ),
                  TmsSemantics(
                    id: 'task.status-row.resume-button',
                    child: CupertinoButton(
                      // 骨架畫面先只露出可點的按鈕；呼叫後端把任務從
                      // 「已中斷」轉回「進行中」是後續功能。
                      onPressed: () {},
                      child: const Text('恢復任務'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Text(
            task == null ? '找不到這個房號的任務' : '內容：${task.title ?? ''}',
          ),
        ),
        // 以下是骨架期的開啟入口接線：三個 sheet 的落點與工務單詳情、
        // 完成慶祝的路由。各入口的實際觸發條件（報修圖示、主按鈕完成…）
        // 是後續 feature，這裡先讓畫面可以被導到。
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Wrap(
            children: [
              TmsSemantics(
                id: 'task.hold-button',
                child: CupertinoButton(
                  onPressed: () => HoldSheet.show(context),
                  child: const Text('延後／中斷'),
                ),
              ),
              TmsSemantics(
                id: 'task.repair-button',
                child: CupertinoButton(
                  onPressed: () => RepairSheet.show(context),
                  child: const Text('報修'),
                ),
              ),
              TmsSemantics(
                id: 'task.ask-button',
                child: CupertinoButton(
                  onPressed: () => AskSheet.show(context),
                  child: const Text('查一下'),
                ),
              ),
              CupertinoButton(
                onPressed: () {
                  Navigator.of(context).push(
                    CupertinoPageRoute<void>(
                      builder: (_) => const TicketScreen(),
                    ),
                  );
                },
                child: const Text('工務單詳情'),
              ),
              CupertinoButton(
                onPressed: () {
                  Navigator.of(context).push(
                    CupertinoPageRoute<void>(
                      builder: (_) => const CelebrateScreen(),
                    ),
                  );
                },
                child: const Text('完成慶祝'),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
