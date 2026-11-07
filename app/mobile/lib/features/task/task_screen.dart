import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_status_chip.dart';
import '../celebrate/celebrate_screen.dart';
import '../ticket/repair_labels.dart';
import '../ticket/ticket_screen.dart';
import 'ask_sheet.dart';
import 'hold_sheet.dart';
import 'repair_sheet.dart';
import 'task_callouts.dart';

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

  /// 這一間的完成已經留在手機上、還沒送到後端。
  ///
  /// 離線完成在畫面上既不是成功也不是失敗，員工需要知道「做完了，但還沒
  /// 送出去」，否則他會以為單子已經結了，或以為剛才白做了。
  bool _completionPendingSync = false;

  /// 報修面板是不是正貼在這一頁上。面板不另開路由（理由見 [RepairSheet]），
  /// 所以開關它就是任務頁自己的一個狀態。
  bool _repairSheetOpen = false;

  /// 報修面板開啟時的原任務 id（契約的 sourceTaskId）。
  String? _openRepairSheetTaskId;

  /// 這一間房未撤回的工務單，順序照後端給的順序。
  List<RepairTicketSummary> _repairTickets = const <RepairTicketSummary>[];

  /// 這個租戶的打掃 SOP 項，順序照後端給的 `sortOrder`。只有打掃類任務要用。
  List<SopItem> _sopItems = const <SopItem>[];

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
    final task = await ApiClient.instance.getTask(taskId);
    _repairTickets = await _loadRepairTickets(task);
    _sopItems = await _loadSopItems(task);
    return task;
  }

  /// 打掃 SOP 項清單。任務詳情只給已勾／已跳過的 id，項目名稱與全集都在
  /// `GET /sop-items`，所以兩邊都要拿到才畫得出清單與 `x / y` 進度。
  ///
  /// 只有打掃類任務有 SOP 清單；取不到時當成沒有清單（整區不畫），不讓一個
  /// 附帶區塊把整張任務頁擋在載入失敗上。
  Future<List<SopItem>> _loadSopItems(TaskDetail task) async {
    if (task.type != 'cleaning') {
      return const <SopItem>[];
    }
    try {
      final items = await ApiClient.instance.listSopItems();
      return items
          .where((item) => item.appliesTo == 'cleaning' && item.active)
          .toList()
        ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
    } catch (_) {
      return const <SopItem>[];
    }
  }

  /// 這一間房未撤回的工務單（FR-021 的同房工務單區）。取不到時當成沒有：
  /// 工務單區是任務頁的附帶資訊，不該因為它讀不到就讓整張任務頁打不開。
  Future<List<RepairTicketSummary>> _loadRepairTickets(TaskDetail task) async {
    final roomId = task.roomId;
    if (roomId == null) {
      return const <RepairTicketSummary>[];
    }
    try {
      return await ApiClient.instance.listRepairTickets(roomId);
    } catch (_) {
      return const <RepairTicketSummary>[];
    }
  }

  /// 用房號反查任務 id：先找待認領池，再找今天自己名下的單。
  ///
  /// 今天自己的單走 todayTasks（今日全部，含進行中與已延後的），不是只收
  /// 待開始的 nextTasks——否則被推播叫回一張進行中的單時會反查不到。
  Future<String?> _resolveTaskIdByRoomNo() async {
    final pool = await ApiClient.instance.getPool();
    for (final item in pool.items) {
      if (item.roomNo == widget.roomNo) {
        return item.taskId;
      }
    }
    final today = await ApiClient.instance.getMyToday();
    for (final task in today.todayTasks) {
      if (task.roomNo == widget.roomNo) {
        return task.taskId;
      }
    }
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
          child: Stack(
            children: [
              Column(
                children: [
                  // 連線狀態列固定在內容最上方：員工在任務頁上報修、送出、
                  // 完成都要知道現在是不是離線、手機上還押著幾筆。
                  const SystemStatusBar(),
                  Expanded(
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
                          return const Center(
                              child: CupertinoActivityIndicator());
                        }
                        return _buildTask(snapshot.data);
                      },
                    ),
                  ),
                ],
              ),
              if (_repairSheetOpen)
                Align(
                  alignment: Alignment.bottomCenter,
                  child: RepairSheet(
                    roomNo: widget.roomNo,
                    sourceTaskId: _openRepairSheetTaskId,
                    onClose: () => setState(() {
                      _repairSheetOpen = false;
                      _openRepairSheetTaskId = null;
                    }),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  /// 開工：成功就把畫面換成後端回的新狀態；後端用 409 擋下「一次一間」時
  /// 跳提示，任務狀態一個字都不動（被擋下的那一間仍然是待開始）。
  Future<void> _start(TaskDetail task) async {
    // 離線時開工一律擋在這裡，不送請求：開工要由後端判定「一次只做一間」，
    // 離線時沒有人能做這個判定，先放行會讓同一位員工手上出現兩筆進行中。
    if (ConnectionStatus.instance.isOffline) {
      await showOfflineBlockedAlert(context);
      return;
    }
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

  /// 完成這一間：連線正常時直接送給後端，離線時留在手機上等連線恢復。
  ///
  /// 離線時唯一還能做的事就是把手上進行中的這間做完，所以這裡不擋，改交給
  /// [OfflineQueue]。它會在連線恢復時用同一把 `Idempotency-Key` 送出去，員工
  /// 不必記得回頭再按一次完成。
  Future<void> _complete(TaskDetail task) async {
    final sent = await OfflineQueue.instance.submit(
      kind: 'complete',
      send: (idempotencyKey) => ApiClient.instance
          .completeTask(task.id, idempotencyKey: idempotencyKey),
    );
    if (!mounted) {
      return;
    }
    if (!sent) {
      setState(() => _completionPendingSync = true);
      return;
    }
    final completed = await ApiClient.instance.getTask(task.id);
    if (!mounted) {
      return;
    }
    _showTask(completed);
    // 慶祝頁給的是「一間房做完了」的回饋，所以只有打掃類走這一段；備品類送達
    // 之後留在任務頁。
    if (completed.type == 'cleaning') {
      await Navigator.of(context).push(
        CupertinoPageRoute<void>(
          builder: (_) => CelebrateScreen(
            actualMinutes: completed.elapsedMinutes,
            estimateMinutes: completed.estMin,
          ),
        ),
      );
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
              child: TmsStatusChip(
                state: task.state,
                type: task.type,
                inspection: task.inspection,
              ),
            ),
          ),
        ),
        _buildHero(task),
        _buildStepTrack(task),
        if (_completionPendingSync)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: TmsSemantics(
              id: 'task.pending-sync-label',
              label: pendingSyncMessage,
              child: const ExcludeSemantics(child: Text(pendingSyncMessage)),
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
        // 同房工務單區（FR-021）：報修人從這裡進到自己那張單的詳情。順序照
        // 後端給的順序，App 不重排。
        for (final ticket in _repairTickets)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Align(
              alignment: Alignment.centerLeft,
              child: TmsSemantics(
                id: 'task.repair-ticket.${ticket.ticketNo}.card',
                child: CupertinoButton(
                  padding: EdgeInsets.zero,
                  onPressed: () {
                    Navigator.of(context).push(
                      CupertinoPageRoute<void>(
                        builder: (_) => TicketScreen(taskId: ticket.taskId),
                      ),
                    );
                  },
                  child: Text('${ticket.ticketNo} '
                      '${repairCategoryText(ticket.category)}'),
                ),
              ),
            ),
          ),
        _buildSopChecklist(task),
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
                  // 目前只接「開始」與「完成」；恢復處理等對應情境進場再接
                  // （接了也沒有情境在驗，會變成沒人讀的分支）。
                  onPressed: switch (primaryActionLabel) {
                    '開始' => () => _start(task),
                    '完成' => () => _complete(task),
                    _ => () {},
                  },
                  child: Text(primaryActionLabel),
                ),
              ),
            ),
          ),
      ],
    );
  }

  /// 計時區：停住的單顯示停住當下的已做時間，進行中的單超過預估工時時顯示
  /// 超時提示。兩個數字都取後端回的已做分鐘與預估分鐘。
  ///
  /// 這裡刻意不在 App 端跑計時器。同一張單可能在別的裝置上被推進，畫面自己
  /// 累加的秒數會和後端的已做時間對不起來。
  Widget _buildHero(TaskDetail task) {
    final paused = task.state == 'interrupted' || task.state == 'deferred';
    final overtimeMinutes = _overtimeMinutes(task);
    if (!paused && overtimeMinutes == null) {
      return const SizedBox.shrink();
    }
    final pausedText = '已做 ${task.elapsedMinutes} 分';
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          TmsSpacing.lg, TmsSpacing.md, TmsSpacing.lg, 0),
      child: Row(
        children: [
          if (paused)
            TmsSemantics(
              id: 'task.hero.paused-elapsed',
              label: pausedText,
              child: ExcludeSemantics(
                child: Text(pausedText, style: TmsText.displayTimer),
              ),
            ),
          if (overtimeMinutes != null)
            TmsSemantics(
              id: 'task.hero.overtime-chip',
              label: '超時 $overtimeMinutes 分',
              child: ExcludeSemantics(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: TmsSpacing.sm2, vertical: TmsSpacing.xxs),
                  decoration: BoxDecoration(
                    color: TmsColors.overtimeCapsule,
                    borderRadius: BorderRadius.circular(TmsRadius.pill),
                  ),
                  child: Text(
                    '超時 $overtimeMinutes 分',
                    style: TmsText.footnoteEmphasized
                        .copyWith(color: TmsColors.onOvertimeCapsule),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  /// 超時分鐘數是已做分鐘減去預估分鐘，只有進行中而且真的超過時才有值。
  ///
  /// 超時在這個畫面上只是提示。算出超時不會呼叫任何後端端點，也不會讓員工
  /// 收到推播。
  static int? _overtimeMinutes(TaskDetail task) {
    final estMin = task.estMin;
    if (task.state != 'doing' || estMin == null) {
      return null;
    }
    final over = task.elapsedMinutes - estMin;
    return over > 0 ? over : null;
  }

  /// 步驟條：打掃類有四段，依序是已接、進行中、完成、查房或免查房；其餘類別
  /// 有三段，依序是已接、進行中、送達。
  ///
  /// 目前停在哪一步由後端回的 state 與 inspection 推導。畫面另外記一份進度的
  /// 話，別的裝置把任務推進之後，這份進度就是舊的。
  Widget _buildStepTrack(TaskDetail task) {
    final steps = _stepsOf(task);
    final current = _currentStep(task);
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          TmsSpacing.lg, TmsSpacing.md, TmsSpacing.lg, 0),
      child: TmsSemantics(
        id: 'task.step-track',
        label: current,
        explicitChildNodes: true,
        child: Row(
          children: [
            for (final step in steps)
              Expanded(
                child: TmsSemantics(
                  id: 'task.step-track.$step',
                  label: step,
                  child: ExcludeSemantics(
                    child: Column(
                      children: [
                        Container(
                          height: 3,
                          decoration: BoxDecoration(
                            color: _stepReached(steps, current, step)
                                ? TmsColors.brandPrimary
                                : TmsColors.systemGray2,
                            borderRadius:
                                BorderRadius.circular(TmsRadius.pill),
                          ),
                        ),
                        const SizedBox(height: TmsSpacing.xs),
                        Text(
                          step,
                          style: step == current
                              ? TmsText.footnoteEmphasized
                              : TmsText.footnote
                                  .copyWith(color: TmsColors.tertiaryLabel),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  /// 這張任務的步驟條有哪幾段。
  ///
  /// 打掃類的最後一段依免查房旗標分成兩種寫法。鎖定免查的單完成後直接結案，
  /// 不會經過查房這一站，所以最後一段寫「免查房」。
  static List<String> _stepsOf(TaskDetail task) {
    if (task.type != 'cleaning') {
      return const <String>['已接', '進行中', '送達'];
    }
    final waived = task.inspectionWaived || task.inspection == 'waived';
    return <String>['已接', '進行中', '完成', waived ? '免查房' : '查房'];
  }

  /// 目前停在哪一步。
  ///
  /// 待開始與已延後都還沒開工，算在「已接」。已中斷是開工之後停住的，仍然算
  /// 在「進行中」這一段。
  static String _currentStep(TaskDetail task) {
    final steps = _stepsOf(task);
    switch (task.state) {
      case 'doing':
      case 'interrupted':
        return '進行中';
      case 'done':
        return task.type == 'cleaning' ? '完成' : '送達';
      case 'inspected':
        return steps.last;
      default:
        return '已接';
    }
  }

  /// 這一步走到了沒有。目前這一步與排在它前面的每一步都算走到。
  static bool _stepReached(List<String> steps, String current, String step) {
    return steps.indexOf(step) <= steps.indexOf(current);
  }

  /// 清潔 SOP 清單：每一項可以勾選，也可以跳過，上方的進度文字寫成
  /// 「x / y · 跳過不擋流程」。
  ///
  /// 跳過和勾選都算進分子 x。跳過的意思是這一項這次不做，不是還沒做，而且它
  /// 不擋完成：後端在完成那一步會把沒勾也沒跳過的項目一律記成跳過。
  ///
  /// 租戶沒有設定任何打掃 SOP 項，或這不是打掃類任務時，整區不畫。
  Widget _buildSopChecklist(TaskDetail task) {
    if (_sopItems.isEmpty) {
      return const SizedBox.shrink();
    }
    final checked = task.checkedSopItems.toSet();
    final skipped = task.skippedSopItems.toSet();
    final doneCount = _sopItems
        .where((item) => checked.contains(item.id) || skipped.contains(item.id))
        .length;
    final progress = '$doneCount / ${_sopItems.length} · 跳過不擋流程';
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          TmsSpacing.lg, TmsSpacing.md, TmsSpacing.lg, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text('清潔 SOP', style: TmsText.title3),
              const SizedBox(width: TmsSpacing.sm),
              TmsSemantics(
                id: 'task.sop.progress',
                label: progress,
                child: ExcludeSemantics(
                  child: Text(
                    progress,
                    style: TmsText.caption1
                        .copyWith(color: TmsColors.secondaryLabel),
                  ),
                ),
              ),
            ],
          ),
          for (final item in _sopItems)
            Row(
              children: [
                Expanded(
                  child: TmsSemantics(
                    id: 'task.sop.${item.label}.item',
                    child: CupertinoButton(
                      padding: EdgeInsets.zero,
                      alignment: Alignment.centerLeft,
                      onPressed: () => _sopCheck(task, item, 'checked'),
                      child: Text(
                        checked.contains(item.id)
                            ? '✓ ${item.label}'
                            : item.label,
                        style: TmsText.body,
                      ),
                    ),
                  ),
                ),
                TmsSemantics(
                  id: 'task.sop.${item.label}.skip-button',
                  child: CupertinoButton(
                    padding:
                        const EdgeInsets.symmetric(horizontal: TmsSpacing.md),
                    onPressed: () => _sopCheck(task, item, 'skipped'),
                    child: Text(
                      skipped.contains(item.id) ? '已跳過' : '跳過',
                      style: TmsText.caption1
                          .copyWith(color: TmsColors.brandPrimary),
                    ),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }

  /// 勾選或跳過一項 SOP，畫面換成後端回的那一筆任務。
  ///
  /// 畫面先改再送出的話，送失敗的那一下員工看到的勾記就不是後端記下的勾記。
  Future<void> _sopCheck(TaskDetail task, SopItem item, String result) async {
    final updated = await ApiClient.instance
        .sopCheck(task.id, sopItemId: item.id, result: result);
    if (!mounted) {
      return;
    }
    _showTask(updated);
  }

  /// 骨架期的入口接線：三個 sheet 的落點與工務單詳情的路由。
  /// 各入口的實際觸發條件（報修圖示…）是後續功能，這裡先讓畫面
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
              onPressed: () {
                if (ConnectionStatus.instance.isOffline) {
                  showOfflineBlockedAlert(context);
                  return;
                }
                setState(() {
                  _repairSheetOpen = true;
                  _openRepairSheetTaskId = task?.id;
                });
              },
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
        ],
      ),
    );
  }
}
