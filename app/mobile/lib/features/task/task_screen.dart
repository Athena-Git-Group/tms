import 'dart:async';

import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/system_status.dart';
import '../../core/test_backdoor.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/today_refresh.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_status_chip.dart';
import '../../core/widgets/tms_step_track.dart';
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

  /// 目前畫面上這一筆任務。
  ///
  /// 導覽列的報修鍵在 `FutureBuilder` 外面，拿不到 builder 參數裡的那一筆，
  /// 但報修要帶原任務 id 才接得回這張單，所以載到之後在這裡留一份。
  TaskDetail? _loadedTask;

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
    _loadedTask = task;
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
        backgroundColor: TmsColors.groupedBackground,
        navigationBar: CupertinoNavigationBar(
          middle: Text('${widget.roomNo} 的任務頁'),
          // 報修在導覽列右側，不在底部動作列：底部一次只留一主一次兩顆，
          // 報修是「做到一半發現房間壞了」的旁支，擺進那一列會讓員工每次都要
          // 先讀完三顆才找得到往下走的那一顆。
          trailing: TmsSemantics(
            id: 'task.repair-button',
            child: CupertinoButton(
              padding: EdgeInsets.zero,
              minimumSize: const Size(
                TmsSize.minTouchTarget,
                TmsSize.minTouchTarget,
              ),
              onPressed: _openRepairSheet,
              child: Text(
                '報修',
                style: TmsText.subheadlineEmphasized.copyWith(
                  color: TmsColors.brandPrimary,
                ),
              ),
            ),
          ),
        ),
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
                            child: CupertinoActivityIndicator(),
                          );
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

  /// 打開報修面板。離線時擋在入口：表單要先有照片才送得出去，而照片得上傳到
  /// 後端才有 id，離線時連第一張都加不進去。
  void _openRepairSheet() {
    if (ConnectionStatus.instance.isOffline) {
      showOfflineBlockedAlert(context);
      return;
    }
    setState(() {
      _repairSheetOpen = true;
      _openRepairSheetTaskId = _loadedTask?.id;
    });
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
      TodayRefresh.instance.request();
    } on ApiException catch (e) {
      await _showBusyAlert(e, fallback: '還有另一間在進行中');
    }
  }

  /// 完成這一間：連線正常時直接送給後端，離線時留在手機上等連線恢復。
  ///
  /// 離線時唯一還能做的事就是把手上進行中的這間做完，所以這裡不擋，改交給
  /// [OfflineQueue]。它會在連線恢復時用同一把 `Idempotency-Key` 送出去，員工
  /// 不必記得回頭再按一次完成。
  Future<void> _complete(TaskDetail task) async {
    final bool sent;
    try {
      sent = await OfflineQueue.instance.submit(
        kind: 'complete',
        send: (idempotencyKey) => ApiClient.instance.completeTask(
          task.id,
          idempotencyKey: idempotencyKey,
        ),
      );
    } on ApiException catch (e) {
      // 連線正常時佇列把後端的拒絕原樣往上拋（例如這張單剛被主管作廢）。
      // 這裡不接住的話，員工按下完成只會看到畫面毫無反應——被拒不能無聲，
      // 原因是後端說了算，App 不自己湊話。
      await _showBusyAlert(e, fallback: '完成沒有成功，請稍後再試');
      return;
    }
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
    TodayRefresh.instance.request();
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
    TodayRefresh.instance.request();
  }

  /// 把畫面換成指定的那一筆任務。
  ///
  /// 畫面讀的是一個 `Future`，所以換內容等於換掉那個 `Future`。這個指派不能
  /// 寫成 `setState(() => _taskFuture = ...)`：箭頭函式會把指派的結果（也就是
  /// 那個 `Future`）當成回傳值交給 `setState`，Flutter 在 debug 版會直接拒絕
  /// 這種回呼，畫面一次也不會更新。
  void _showTask(TaskDetail task) {
    setState(() {
      _loadedTask = task;
      _taskFuture = Future<TaskDetail?>.value(task);
    });
  }

  /// 後端拒絕開工、恢復或完成時的提示（ui-plan F2-R1 的 `task.busy-alert`）。
  /// 訊息直接用後端回應的 message——被拒的原因是後端說了算，App 不自己湊這
  /// 句話；後端沒給 message 時才退回呼叫端給的 `fallback` 文案。畫面已被移除
  /// 時直接不彈，不然對話框會掛在一個已經不存在的畫面上。
  Future<void> _showBusyAlert(ApiException e, {required String fallback}) async {
    if (!mounted) {
      return;
    }
    final message = e.errorMessage ?? fallback;
    return showCupertinoDialog<void>(
      context: context,
      // `task.busy-alert` 掛在內容文字上、不掛在整個對話框上：提示的驗收要
      // 讀這個節點本身的文字，而對話框層的 accessibility 讀值會被 alert 語意
      // 多併進一段「Alert」，拒絕原因就驗不到一字不差了。
      builder: (dialogContext) => CupertinoAlertDialog(
        content: TmsSemantics(
          id: 'task.busy-alert',
          label: message,
          child: ExcludeSemantics(child: Text(message)),
        ),
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
      return const Padding(
        padding: EdgeInsets.all(TmsSpacing.screenEdge),
        child: Align(alignment: Alignment.topLeft, child: Text('找不到這個房號的任務')),
      );
    }

    return Column(
      children: [
        Expanded(
          child: Stack(
            children: [
              // 整頁可捲動。任務頁上從標題區、提示、同房工務單到 SOP 清單，
              // 一屏放不下是常態；捲不動的版面會讓最下面那幾項永遠點不到。
              // 用 SingleChildScrollView 而不是 ListView：ListView 只建視窗內
              // 的那幾列，捲到之前它們不在畫面上，驗收也定位不到。
              SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _buildHeader(task),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(
                        TmsSpacing.screenEdge,
                        TmsSpacing.md2,
                        TmsSpacing.screenEdge,
                        TmsSpacing.xxl,
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: _spaced(_buildSections(task)),
                      ),
                    ),
                  ],
                ),
              ),
              Positioned(
                right: TmsSpacing.screenEdge,
                bottom: TmsSpacing.screenEdge,
                child: _buildAskButton(task),
              ),
            ],
          ),
        ),
        _buildActionBar(task),
      ],
    );
  }

  /// 把各區塊之間插上固定間距。區塊本身只管自己的內容，誰在誰上面、中間空多
  /// 少由這裡決定，各區塊自己帶 margin 的話間距會隨著區塊出現與否忽大忽小。
  static List<Widget> _spaced(List<Widget> sections) {
    final spaced = <Widget>[];
    for (var i = 0; i < sections.length; i++) {
      if (i > 0) {
        spaced.add(const SizedBox(height: TmsSpacing.md2));
      }
      spaced.add(sections[i]);
    }
    return spaced;
  }

  /// 標題區底下的各個區塊，由上到下。沒有內容的區塊整塊不出現。
  List<Widget> _buildSections(TaskDetail task) {
    final sections = <Widget>[
      // 狀態標籤排在提示區最上面：它講的是「這張單現在是什麼狀態」，底下每
      // 一則提示講的是「所以現在要注意什麼」，順序顛倒的話員工得先讀完提示
      // 才知道提示在講哪一種狀態。
      Align(
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
    ];
    if (task.state == 'interrupted' &&
        (task.holdReason?.contains('工務已修好') ?? false)) {
      sections.add(
        TaskCallout(
          id: 'task.resume-banner',
          tint: calloutClearedTint,
          children: [
            Row(
              children: [
                Expanded(child: Text('${widget.roomNo} 號房的工務已修好，您可以恢復原任務了')),
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
      );
    }
    if (_completionPendingSync) {
      sections.add(
        TmsSemantics(
          id: 'task.pending-sync-label',
          label: pendingSyncMessage,
          child: const ExcludeSemantics(child: Text(pendingSyncMessage)),
        ),
      );
    }
    if (task.returnInfo != null) {
      sections.add(ReturnInfoCallout(info: task.returnInfo!));
    }
    if (task.handovers.isNotEmpty) {
      sections.add(
        HandoverCallout(entries: task.handovers, read: task.handoverRead),
      );
    }
    // 停住原因只在任務真的停住時有值，所以沒有原因就整列不畫。不另外指定
    // label：文字本身就是子節點的 label，顯式再給一次會被接成兩份。
    final holdReason = task.holdReason;
    if (holdReason != null) {
      sections.add(
        TmsCard(
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.screenEdge,
            vertical: TmsSpacing.md,
          ),
          child: Align(
            alignment: Alignment.centerLeft,
            child: TmsSemantics(
              id: 'task.status-row.hold-reason',
              child: Text(holdReason, style: TmsText.body),
            ),
          ),
        ),
      );
    }
    sections.add(_buildInfoSection(task));
    final sop = _buildSopChecklist(task);
    if (sop != null) {
      sections.add(sop);
    }
    return sections;
  }

  /// 「這間房的資訊」：同房工務單與工務單詳情入口排成一組白底清單。
  ///
  /// 這些是任務頁的旁支，不是員工要往下走的那一步，所以收進一組可點的列，
  /// 而不是散在底部當一排長得一樣的按鈕。
  Widget _buildInfoSection(TaskDetail task) {
    final rows = <Widget>[
      // 同房工務單區（FR-021）：報修人從這裡進到自己那張單的詳情。順序照
      // 後端給的順序，App 不重排。
      for (final ticket in _repairTickets)
        TmsSemantics(
          id: 'task.repair-ticket.${ticket.ticketNo}.card',
          child: TmsGroupedRow(
            label:
                '${ticket.ticketNo} '
                '${repairCategoryText(ticket.category)}',
            onTap: () {
              Navigator.of(context).push(
                CupertinoPageRoute<void>(
                  builder: (_) => TicketScreen(taskId: ticket.taskId),
                ),
              );
            },
          ),
        ),
      TmsGroupedRow(
        label: '工務單詳情',
        onTap: () {
          Navigator.of(context).push(
            CupertinoPageRoute<void>(builder: (_) => const TicketScreen()),
          );
        },
      ),
    ];
    return TmsGroupedSection(header: '這間房的資訊', children: rows);
  }

  /// 浮在內容右下角的「查一下」。
  ///
  /// 它不在底部動作列裡：查 SOP 或查這間房的記錄是員工做到一半隨時會想做的
  /// 事，壓在動作列裡會跟「往下走」的那一顆混在一起。
  Widget _buildAskButton(TaskDetail task) {
    return TmsSemantics(
      id: 'task.ask-button',
      child: CupertinoButton(
        padding: EdgeInsets.zero,
        borderRadius: BorderRadius.circular(TmsRadius.pill),
        color: TmsColors.brandContainer,
        minimumSize: const Size(56, 56),
        onPressed: () => AskSheet.show(context, taskId: task.id),
        child: Text(
          '查一下',
          style: TmsText.footnoteEmphasized.copyWith(
            color: TmsColors.brandPrimary,
          ),
        ),
      ),
    );
  }

  /// 置底動作列：左邊次要、右邊主要，寬度 1 比 2。
  ///
  /// 一個狀態最多只露出這兩顆。主按鈕的文字由 [_primaryActionLabel] 的狀態
  /// 對照表決定，次要按鈕是待開始的「延後」與進行中的「中斷」；兩顆都沒有的
  /// 狀態（待查房、已送達、完成…）整列不畫。
  Widget _buildActionBar(TaskDetail task) {
    final primaryLabel = _primaryActionLabel(task);
    final secondary = _buildSecondaryButton(task);
    if (primaryLabel == null && secondary == null) {
      return const SizedBox.shrink();
    }
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.screenEdge,
        vertical: TmsSpacing.md,
      ),
      decoration: const BoxDecoration(
        color: TmsColors.cardBackground,
        border: Border(
          top: BorderSide(
            color: TmsColors.opaqueSeparator,
            width: TmsSize.hairline,
          ),
        ),
      ),
      child: Row(
        children: [
          if (secondary != null) Expanded(flex: 1, child: secondary),
          if (secondary != null && primaryLabel != null)
            const SizedBox(width: TmsSpacing.md),
          if (primaryLabel != null)
            Expanded(
              flex: 2,
              // 不另外給 label：按鈕文字本身就是子節點的 label，顯式再給一次
              // 會被 Flutter 接成「開始\n開始」。
              child: TmsSemantics(
                id: 'task.primary-button',
                child: TmsPillButton(
                  label: primaryLabel,
                  // 目前只接「開始」與「完成」；恢復處理等對應情境進場再接
                  // （接了也沒有情境在驗，會變成沒人讀的分支）。
                  onPressed: switch (primaryLabel) {
                    '開始' => () => _start(task),
                    '完成' => () => _complete(task),
                    _ => () {},
                  },
                ),
              ),
            ),
        ],
      ),
    );
  }

  /// 這個狀態的次要按鈕；沒有次要動作時回 null。
  ///
  /// 待開始的單能延後（還沒開工，整張單往後挪），進行中的單能中斷（已經開
  /// 工，停在一半）。其餘狀態兩者都做不了：已經停住的單沒有東西可以再停。
  Widget? _buildSecondaryButton(TaskDetail task) {
    switch (task.state) {
      case 'accepted':
        return TmsSemantics(
          id: 'task.defer-button',
          child: TmsPillButton(
            label: '延後',
            style: TmsPillButtonStyle.outlined,
            onPressed: () => _openHoldSheet(task, HoldSheetMode.defer),
          ),
        );
      case 'doing':
        return TmsSemantics(
          id: 'task.interrupt-button',
          child: TmsPillButton(
            label: '中斷',
            style: TmsPillButtonStyle.outlined,
            onPressed: () => _openHoldSheet(task, HoldSheetMode.interrupt),
          ),
        );
      default:
        return null;
    }
  }

  /// 任務頁最上面的標題區：進行中是品牌色深底，其餘狀態是白底，下緣兩個角
  /// 切成大圓角把它和底下的內容分開。
  ///
  /// 誰當主角依狀態換：進行中時最大的字是計時，房號退到任務名稱前面——這時
  /// 員工早就站在房間裡，要看的是「做多久了」；開始前與停住後換回房號當主
  /// 角，因為那時要確認的是「這是哪一間」。
  ///
  /// 進行中的計時在 App 端逐秒跳動，但起算點一律由後端給（契約的
  /// `segmentStartedAt`），App 只負責把「現在時間減起算點」加上去。畫面不自
  /// 己累加秒數：同一張單可能在別的裝置上被推進，自己累加會和後端的已做時
  /// 間愈走愈遠。後端還沒回起算點時退回只顯示分鐘。
  ///
  /// 標題區放計時或房號、任務名，再一行來源與預估工時，狀態標籤不放這裡：
  /// 這張單走到哪一步由底下的步驟條表達，同一件事在同一屏講兩次，員工會先
  /// 讀標籤再讀步驟條，反而慢。狀態標籤改放在標題區底下的提示區。
  Widget _buildHeader(TaskDetail task) {
    final doing = task.state == 'doing';
    final paused = task.state == 'interrupted' || task.state == 'deferred';
    final foreground = doing ? TmsColors.brandOnPrimary : TmsColors.label;
    final secondary = doing
        ? TmsColors.brandContainer
        : TmsColors.secondaryLabel;
    final title = task.title ?? '';
    // 標題底下那一行的 meta：先講這張單是誰開的，再講預估工時。進行中與停
    // 住時計時那一側已經寫了預估，所以那兩種狀態這裡不再寫一次。
    final metaParts = <String>[
      if (task.source != null) taskSourceText(task.source!),
      if (task.estMin != null && !doing && !paused) '預估 ${task.estMin} 分',
    ];
    return Container(
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.xl,
        TmsSpacing.sm,
        TmsSpacing.xl,
        TmsSpacing.xl,
      ),
      decoration: BoxDecoration(
        color: doing ? TmsColors.brandPrimary : TmsColors.cardBackground,
        borderRadius: const BorderRadius.only(
          bottomLeft: Radius.circular(TmsRadius.xl),
          bottomRight: Radius.circular(TmsRadius.xl),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (doing) ...[
            TaskTimerHero(task: task),
            const SizedBox(height: TmsSpacing.xs2),
            Text(
              '${widget.roomNo} · $title',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TmsText.headline.copyWith(color: foreground),
            ),
          ] else ...[
            _buildRoomHero(task, paused: paused),
            const SizedBox(height: TmsSpacing.xs2),
            Text(
              title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TmsText.headline.copyWith(color: foreground),
            ),
          ],
          if (metaParts.isNotEmpty) ...[
            const SizedBox(height: TmsSpacing.xxs),
            Text(
              metaParts.join(' · '),
              style: TmsText.subheadline.copyWith(color: secondary),
            ),
          ],
          const SizedBox(height: TmsSpacing.sm),
          _buildStepTrack(task, onDark: doing),
        ],
      ),
    );
  }

  /// 開始前與停住後的主角：56pt 的房號，停住的單在右邊接已做時間。
  Widget _buildRoomHero(TaskDetail task, {required bool paused}) {
    final overtimeMinutes = _overtimeMinutes(task);
    final pausedText = '已做 ${task.elapsedMinutes} 分';
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Expanded(
          child: Text(
            widget.roomNo,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TmsText.displayHero,
          ),
        ),
        if (paused)
          Padding(
            padding: const EdgeInsets.only(bottom: TmsSpacing.xs),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              mainAxisSize: MainAxisSize.min,
              children: [
                TmsSemantics(
                  id: 'task.hero.paused-elapsed',
                  label: pausedText,
                  child: ExcludeSemantics(
                    child: Text(
                      pausedText,
                      style: TmsText.displayTimer.copyWith(
                        color: overtimeMinutes != null
                            ? TmsColors.overtimeTimerOnLight
                            : TmsColors.label,
                      ),
                    ),
                  ),
                ),
                if (overtimeMinutes != null) ...[
                  const SizedBox(height: TmsSpacing.xs),
                  _buildOvertimeChip(overtimeMinutes, onDark: false),
                ],
              ],
            ),
          ),
      ],
    );
  }

  /// 步驟條：打掃類有四段，依序是已接、進行中、完成、查房或免查房；其餘類別
  /// 有三段，依序是已接、進行中、送達。
  ///
  /// 目前停在哪一步由後端回的 state 與 inspection 推導。畫面另外記一份進度的
  /// 話，別的裝置把任務推進之後，這份進度就是舊的。
  Widget _buildStepTrack(TaskDetail task, {required bool onDark}) {
    final steps = _stepsOf(task);
    final current = _currentStep(task);
    final currentIndex = steps.indexOf(current);
    // 查房通過代表整張單結案，沒有哪一步還「停在那裡」，四步一律畫成完成。
    final allDone = task.state == 'inspected';
    return Padding(
      padding: const EdgeInsets.only(top: TmsSpacing.md2),
      child: TmsSemantics(
        id: 'task.step-track',
        label: current,
        explicitChildNodes: true,
        child: TmsStepTrack(
          onDark: onDark,
          steps: [
            for (final step in steps)
              _trackStep(
                steps.indexOf(step),
                currentIndex,
                step,
                allDone: allDone,
              ),
          ],
        ),
      ),
    );
  }

  /// 步驟條上的一步：走到哪一步決定圖示形態，id 與讀值用步驟名當識別段。
  static TmsStepTrackStep _trackStep(
    int index,
    int currentIndex,
    String step, {
    required bool allDone,
  }) {
    final TmsStepStatus status;
    if (allDone || index < currentIndex) {
      status = TmsStepStatus.done;
    } else if (index == currentIndex) {
      status = TmsStepStatus.current;
    } else {
      status = TmsStepStatus.pending;
    }
    return TmsStepTrackStep(
      label: step,
      status: status,
      wrap: (node) => TmsSemantics(
        id: 'task.step-track.$step',
        label: step,
        child: ExcludeSemantics(child: node),
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

  /// 清潔 SOP 清單：每一項可以勾選，也可以跳過，上方的進度文字寫成
  /// 「x / y · 跳過不擋流程」。
  ///
  /// 跳過和勾選都算進分子 x。跳過的意思是這一項這次不做，不是還沒做，而且它
  /// 不擋完成：後端在完成那一步會把沒勾也沒跳過的項目一律記成跳過。
  ///
  /// 租戶沒有設定任何打掃 SOP 項，或這不是打掃類任務時，整區不畫。
  Widget? _buildSopChecklist(TaskDetail task) {
    if (_sopItems.isEmpty) {
      return null;
    }
    final checked = task.checkedSopItems.toSet();
    final skipped = task.skippedSopItems.toSet();
    final doneCount = _sopItems
        .where((item) => checked.contains(item.id) || skipped.contains(item.id))
        .length;
    final progress = '$doneCount / ${_sopItems.length} · 跳過不擋流程';
    final ratio = doneCount / _sopItems.length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.xs),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              const Text('清潔 SOP', style: TmsText.title3),
              const SizedBox(width: TmsSpacing.sm),
              Expanded(
                child: TmsSemantics(
                  id: 'task.sop.progress',
                  label: progress,
                  child: ExcludeSemantics(
                    child: Text(
                      progress,
                      textAlign: TextAlign.right,
                      style: TmsText.footnote.copyWith(
                        color: TmsColors.secondaryLabel,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: TmsSpacing.sm),
        // 進度條是給員工掃一眼用的：同一個進度在上面已經有「3 / 8」這個精確
        // 的說法，條狀只負責讓他不必讀數字就知道還剩多少。
        ClipRRect(
          borderRadius: BorderRadius.circular(TmsRadius.pill),
          child: SizedBox(
            height: TmsSpacing.sm,
            child: ColoredBox(
              color: TmsColors.neutralContainer,
              child: FractionallySizedBox(
                alignment: Alignment.centerLeft,
                widthFactor: ratio,
                child: const ColoredBox(color: TmsColors.brandPrimary),
              ),
            ),
          ),
        ),
        const SizedBox(height: TmsSpacing.sm),
        TmsGroupedSection(
          children: [
            for (final item in _sopItems)
              _buildSopRow(
                task: task,
                item: item,
                checked: checked.contains(item.id),
                skipped: skipped.contains(item.id),
              ),
          ],
        ),
      ],
    );
  }

  /// SOP 清單的一列：左邊是可點的項目，右邊是跳過鈕。
  Widget _buildSopRow({
    required TaskDetail task,
    required SopItem item,
    required bool checked,
    required bool skipped,
  }) {
    return Container(
      color: TmsColors.cardBackground,
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.md),
      constraints: const BoxConstraints(minHeight: 48),
      child: Row(
        children: [
          Expanded(
            child: TmsSemantics(
              id: 'task.sop.${item.label}.item',
              child: CupertinoButton(
                padding: EdgeInsets.zero,
                alignment: Alignment.centerLeft,
                minimumSize: const Size(0, 48),
                onPressed: () => _sopCheck(task, item, 'checked'),
                child: Text(
                  checked ? '✓ ${item.label}' : item.label,
                  style: TmsText.body.copyWith(
                    color: skipped ? TmsColors.tertiaryLabel : TmsColors.label,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(width: TmsSpacing.sm),
          TmsSemantics(
            id: 'task.sop.${item.label}.skip-button',
            child: CupertinoButton(
              padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.md),
              borderRadius: BorderRadius.circular(TmsRadius.pill),
              minimumSize: const Size(0, 36),
              color: skipped
                  ? TmsColors.neutralContainer
                  : TmsColors.cardBackground,
              onPressed: () => _sopCheck(task, item, 'skipped'),
              child: Text(
                skipped ? '已跳過' : '跳過',
                style: TmsText.footnoteEmphasized.copyWith(
                  color: skipped
                      ? TmsColors.onNeutralContainer
                      : TmsColors.brandPrimary,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// 勾選或跳過一項 SOP，畫面換成後端回的那一筆任務。
  ///
  /// 畫面先改再送出的話，送失敗的那一下員工看到的勾記就不是後端記下的勾記。
  Future<void> _sopCheck(TaskDetail task, SopItem item, String result) async {
    final updated = await ApiClient.instance.sopCheck(
      task.id,
      sopItemId: item.id,
      result: result,
    );
    if (!mounted) {
      return;
    }
    _showTask(updated);
  }
}

/// 超時膠囊：深底上是深橘底深褐字，白底上是橘淡底棕字。
///
/// 超時只是提醒，不擋任何操作，所以不用錯誤紅——紅色會讓員工以為這張單出
/// 了什麼事。
Widget _buildOvertimeChip(int overtimeMinutes, {required bool onDark}) {
  final text = '超時 $overtimeMinutes 分';
  return TmsSemantics(
    id: 'task.hero.overtime-chip',
    label: text,
    child: ExcludeSemantics(
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.sm2,
          vertical: TmsSpacing.xxs,
        ),
        decoration: BoxDecoration(
          color: onDark
              ? TmsColors.overtimeCapsule
              : TmsColors.brandAccentContainer,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
        ),
        child: Text(
          text,
          style: TmsText.footnoteEmphasized.copyWith(
            color: onDark
                ? TmsColors.onOvertimeCapsule
                : TmsColors.brandOnAccentContainer,
          ),
        ),
      ),
    ),
  );
}

/// 目前已做秒數：契約的走秒公式 `accumulatedSeconds + (現在 − segmentStartedAt)`。
///
/// 兩個欄位缺一就回 null——沒有基準或沒有起算點都走不了秒，呼叫端各自退回
/// 分鐘層級的顯示。基準不能拿 [TaskDetail.elapsedMinutes] 代打：它在進行中
/// 已含正在跑的這一段，再加一次本段會把本段重複累計，顯示約兩倍。
///
/// 「現在」沒給時讀 [deviceNow]，不直接叫 `DateTime.now()`：自動化測試把裝
/// 置時間往前撥時，計時與超時才會跟著撥過去的時刻走。手機時間比起算時刻早
/// （時鐘沒對準）時當本段還沒開始算，不讓計時倒退。
int? _workedSecondsNow(TaskDetail task, DateTime? now) {
  final startedAt = task.segmentStartedAt;
  final base = task.accumulatedSeconds;
  if (startedAt == null || base == null) {
    return null;
  }
  final segment = (now ?? deviceNow()).difference(startedAt).inSeconds;
  return base + (segment > 0 ? segment : 0);
}

/// 超時分鐘數是目前已做分鐘減去預估分鐘，只有進行中而且真的超過時才有值。
///
/// 已做分鐘跟著計時同一條走秒公式逐秒重算（見 [_workedSecondsNow]），換成
/// 整分才進位：畫面開著不操作，超時也會隨時間變大，不停在進頁或上次向後端
/// 取資料那一刻；又因為只有跨過整分值才會變，秒數跳動不會讓超時膠囊忽隱忽
/// 現。走不了秒時退回後端給的已做分鐘。
///
/// 超時在這個畫面上只是提示。算出超時不會呼叫任何後端端點，也不會讓員工
/// 收到推播。
int? _overtimeMinutes(TaskDetail task, {DateTime? now}) {
  final estMin = task.estMin;
  if (task.state != 'doing' || estMin == null) {
    return null;
  }
  final workedSeconds = _workedSecondsNow(task, now);
  final workedMinutes =
      workedSeconds == null ? task.elapsedMinutes : workedSeconds ~/ 60;
  final over = workedMinutes - estMin;
  return over > 0 ? over : null;
}

/// 進行中那張單的計時主角：56pt 的計時，右邊接超時膠囊或預估工時。
///
/// 進行中的單只要後端給了本段的起算時刻（契約的 `segmentStartedAt`），計時
/// 就走到秒並每秒重畫一次，員工看得出時間正在走；起算時刻沒有值時——其餘
/// 狀態一律沒有，後端還沒補上這一欄時也沒有——退回只顯示已做分鐘。
///
/// 畫面不自己累加秒數，每次都拿現在時間減後端給的起算點重算。自己累加的
/// 話，手機睡著或同一張單在別的裝置上被推進之後，秒數會和後端的已做時間
/// 愈走愈遠。
///
/// 超時與計時讀同一個逐秒重算的已做秒數，但超時換成整分才進位：秒數跨過
/// 整分前後膠囊不會忽隱忽現，整分一到才加一分。
class TaskTimerHero extends StatefulWidget {
  const TaskTimerHero({super.key, required this.task, this.clock});

  final TaskDetail task;

  /// 讀「現在幾點」的方法。平常不給，計時就讀手機的時鐘；widget 測試給一個
  /// 自己說了算的時鐘，才能在不真的等一秒的情況下驗計時有往前跳。
  final DateTime Function()? clock;

  @override
  State<TaskTimerHero> createState() => _TaskTimerHeroState();
}

class _TaskTimerHeroState extends State<TaskTimerHero> {
  Timer? _ticker;

  @override
  void initState() {
    super.initState();
    _syncTicker();
  }

  @override
  void didUpdateWidget(TaskTimerHero oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncTicker();
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _ticker = null;
    super.dispose();
  }

  /// 只有真的要逐秒重畫時才留著計時器。走不了秒的單（起算時刻或累計秒數還
  /// 沒給）上留一支每秒醒來的計時器，會白白把畫面重畫一次又一次，電池也跟
  /// 著耗。
  void _syncTicker() {
    final wanted = widget.task.segmentStartedAt != null &&
        widget.task.accumulatedSeconds != null;
    if (wanted && _ticker == null) {
      _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) {
          setState(() {});
        }
      });
    } else if (!wanted) {
      _ticker?.cancel();
      _ticker = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final task = widget.task;
    // 「現在」整個 frame 只讀一次，計時字串與超時膠囊拿同一個時刻去算。
    // 各讀各的時刻時，兩次讀取可能跨在整秒或整分的交界兩側，同一幀上
    // 計時已進位、超時卻還沒跟上。
    final now = widget.clock?.call() ?? deviceNow();
    final overtimeMinutes = _overtimeMinutes(task, now: now);
    final timerText = taskTimerText(task, now: now);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        TmsSemantics(
          id: 'task.hero.running-elapsed',
          label: timerText,
          child: ExcludeSemantics(
            child: Text(
              timerText,
              style: TmsText.displayHero.copyWith(
                color: overtimeMinutes != null
                    ? TmsColors.overtimeTimerOnDark
                    : TmsColors.brandOnPrimary,
              ),
            ),
          ),
        ),
        const SizedBox(width: TmsSpacing.sm2),
        Padding(
          padding: const EdgeInsets.only(bottom: TmsSpacing.sm),
          child: overtimeMinutes != null
              ? _buildOvertimeChip(overtimeMinutes, onDark: true)
              : Text(
                  task.estMin == null ? '' : '/ 預估 ${task.estMin} 分',
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.brandContainer,
                  ),
                ),
        ),
      ],
    );
  }
}

/// 計時主角上的那串字。
///
/// 走得了秒時寫成「分:秒」，超過一小時才補上小時那一段；走不了秒（契約的
/// `accumulatedSeconds` 或 `segmentStartedAt` 還沒給）時只講已做分鐘。公式
/// 與退回條件見 [_workedSecondsNow]。
String taskTimerText(TaskDetail task, {DateTime? now}) {
  final total = _workedSecondsNow(task, now);
  if (total == null) {
    return '${task.elapsedMinutes} 分';
  }
  final hours = total ~/ 3600;
  final minutes = (total % 3600) ~/ 60;
  final seconds = total % 60;
  final paddedSeconds = seconds.toString().padLeft(2, '0');
  if (hours > 0) {
    return '$hours:${minutes.toString().padLeft(2, '0')}:$paddedSeconds';
  }
  return '${minutes.toString().padLeft(2, '0')}:$paddedSeconds';
}

/// 任務來源（契約 `tasks.source`）在畫面上的說法。用語跟待認領池的來源標籤
/// 同一套，讓同一張單在兩個畫面上被講成同一件事。認不得的值原樣回傳，不猜
/// 一個說法。
String taskSourceText(String source) {
  switch (source) {
    case 'guest':
      return '住客掃碼';
    case 'desk':
      return '櫃檯轉單';
    case 'system':
      return '自動化規則';
    case 'staff':
      return '房務報修';
    default:
      return source;
  }
}
