import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';
import '../celebrate/celebrate_screen.dart';
import '../ticket/ticket_screen.dart';
import 'ask_sheet.dart';
import 'hold_sheet.dart';
import 'repair_sheet.dart';
import 'task_callouts.dart';

/// 狀態 chip 的灰底。
const Color _statusChipTint = Color(0xFFEFEFF4);

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

  /// 狀態 chip 的文字。對照表出自 `features/backend/dsl.md` 的「狀態用語對照」，
  /// 全 App 共用同一套用詞：`done` 在打掃類是「待查房」、其他類是「已送達」；
  /// `inspected` 依查房結果分「查房通過」與「完成 · 免查房」。
  static String _statusChipText(TaskDetail task) {
    switch (task.state) {
      case 'pool':
        return '待認領';
      case 'accepted':
        return '待開始';
      case 'doing':
        return '進行中';
      case 'deferred':
        return '已延後';
      case 'interrupted':
        return '已中斷';
      case 'done':
        return task.type == 'cleaning' ? '待查房' : '已送達';
      case 'inspected':
        return task.inspection == 'waived' ? '完成 · 免查房' : '查房通過';
      case 'cancelled':
        return '已取消';
      default:
        return task.state;
    }
  }

  /// 開工：成功就把畫面換成後端回的新狀態；後端用 409 擋下「一次一間」時
  /// 跳提示，任務狀態一個字都不動（被擋下的那一間仍然是待開始）。
  Future<void> _start(TaskDetail task) async {
    try {
      final started = await ApiClient.instance.startTask(task.id);
      if (!mounted) {
        return;
      }
      _showTask(started);
    } on ApiException catch (e) {
      if (!mounted) {
        return;
      }
      await _showBusyAlert(e.errorMessage ?? '還有另一間在進行中');
    }
  }

  /// 任務停在哪個狀態、停住原因記成什麼，一律以後端送出後回傳的那一筆為準。
  /// 畫面若自己先改成「已延後」再送出，後端拒收時員工會看著一個不存在的狀態
  /// 繼續操作下去。
  Future<void> _openHoldSheet(TaskDetail task, HoldSheetMode mode) async {
    final updated = await HoldSheet.show(context, mode: mode, taskId: task.id);
    if (updated == null || !mounted) {
      return;
    }
    _showTask(updated);
  }

  /// 把畫面換成指定的那一筆任務。
  ///
  /// 畫面讀的是一個 `Future`，所以換內容等於換掉那個 `Future`。這個指派不能
  /// 寫成 `setState(() => _taskFuture = ...)`：箭頭函式會把指派的結果（也就是
  /// 那個 `Future`）當成回傳值交給 `setState`，Flutter 在 debug 版會直接拒絕
  /// 這種回呼，畫面一次也不會更新。
  void _showTask(TaskDetail task) {
    setState(() {
      _taskFuture = Future<TaskDetail?>.value(task);
    });
  }

  /// 「一次一間」提示（ui-plan F2-R1 的 `task.busy-alert`）。訊息直接用後端
  /// 409 的 message——哪一間還在進行中是後端說了算，App 不自己湊這句話。
  Future<void> _showBusyAlert(String message) {
    return showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsSemantics(
        id: 'task.busy-alert',
        child: CupertinoAlertDialog(
          content: Text(message),
          actions: [
            TmsSemantics(
              id: 'task.busy-alert.dismiss-button',
              child: CupertinoDialogAction(
                onPressed: () => Navigator.of(dialogContext).pop(),
                child: const Text('知道了'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 目前狀態對應的主按鈕文字；這個狀態沒有主要動作時回 null。
  ///
  /// 對照表出自 `任務執行/dsl.md` 的「任務頁只顯示一個主按鈕」：待開始與
  /// 本人延後都是「開始」（主管延後沒有負責人，不能自己開工），進行中是
  /// 「完成」，已中斷是「恢復處理」。延後／中斷是次要按鈕，不走這裡。
  static String? _primaryActionLabel(TaskDetail task) {
    switch (task.state) {
      case 'accepted':
        return '開始';
      case 'deferred':
        return task.deferredBySelf ? '開始' : null;
      case 'doing':
        return '完成';
      case 'interrupted':
        return '恢復處理';
      default:
        return null;
    }
  }

  Widget _buildTask(TaskDetail? task) {
    if (task == null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text('找不到這個房號的任務'),
          ),
          _buildSkeletonEntries(null),
          const Spacer(),
        ],
      );
    }

    final showResumeBanner = task.state == 'interrupted' &&
        (task.holdReason?.contains('工務已修好') ?? false);
    final primaryActionLabel = _primaryActionLabel(task);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (showResumeBanner)
          Padding(
            padding: const EdgeInsets.all(16),
            child: TaskCallout(
              id: 'task.resume-banner',
              tint: calloutClearedTint,
              children: [
                Row(
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
              ],
            ),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Align(
            alignment: Alignment.centerLeft,
            child: TmsSemantics(
              id: 'task.status-chip',
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: _statusChipTint,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(_statusChipText(task)),
              ),
            ),
          ),
        ),
        if (task.returnInfo != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: ReturnInfoCallout(info: task.returnInfo!),
          ),
        if (task.handovers.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: HandoverCallout(
              entries: task.handovers,
              read: task.handoverRead,
            ),
          ),
        // 停住原因只在任務真的停住時有值，所以沒有原因就整列不畫。不另外指定
        // label：文字本身就是子節點的 label，顯式再給一次會被接成兩份。
        if (task.holdReason != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Align(
              alignment: Alignment.centerLeft,
              child: TmsSemantics(
                id: 'task.status-row.hold-reason',
                child: Text(task.holdReason!),
              ),
            ),
          ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Text('內容：${task.title ?? ''}'),
        ),
        _buildSkeletonEntries(task),
        const Spacer(),
        // 置底主按鈕：一個狀態只露出一個，文字由 [_primaryActionLabel] 的
        // 狀態對照表決定。沒有主要動作的狀態（待查房、已送達、完成…）
        // 整顆不顯示。
        if (primaryActionLabel != null)
          Padding(
            padding: const EdgeInsets.all(16),
            child: SizedBox(
              width: double.infinity,
              // 不另外給 label：按鈕文字本身就是子節點的 label，顯式再給一次
              // 會被 Flutter 接成「開始\n開始」。
              child: TmsSemantics(
                id: 'task.primary-button',
                child: CupertinoButton.filled(
                  // 目前只接「開始」；完成與恢復處理的動作等對應情境進場
                  // 再接（接了也沒有情境在驗，會變成沒人讀的分支）。
                  onPressed:
                      primaryActionLabel == '開始' ? () => _start(task) : () {},
                  child: Text(primaryActionLabel),
                ),
              ),
            ),
          ),
      ],
    );
  }

  /// 骨架期的入口接線：三個 sheet 的落點與工務單詳情、完成慶祝的路由。
  /// 各入口的實際觸發條件（報修圖示、主按鈕完成…）是後續功能，這裡先讓畫面
  /// 可以被導到。找不到任務時 [task] 為 null，延後、中斷與查一下這幾個要有
  /// 任務才做得了的動作不露出——查一下的推薦問題是依任務情境排出來的，沒有
  /// 任務就沒有情境可查。
  Widget _buildSkeletonEntries(TaskDetail? task) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Wrap(
        children: [
          if (task != null)
            TmsSemantics(
              id: 'task.defer-button',
              child: CupertinoButton(
                onPressed: () => _openHoldSheet(task, HoldSheetMode.defer),
                child: const Text('延後'),
              ),
            ),
          if (task != null)
            TmsSemantics(
              id: 'task.interrupt-button',
              child: CupertinoButton(
                onPressed: () => _openHoldSheet(task, HoldSheetMode.interrupt),
                child: const Text('中斷'),
              ),
            ),
          TmsSemantics(
            id: 'task.repair-button',
            child: CupertinoButton(
              onPressed: () => RepairSheet.show(context),
              child: const Text('報修'),
            ),
          ),
          if (task != null)
            TmsSemantics(
              id: 'task.ask-button',
              child: CupertinoButton(
                onPressed: () => AskSheet.show(context, taskId: task.id),
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
    );
  }
}
