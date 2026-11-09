import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/system_status.dart';
import '../../core/test_backdoor.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/today_refresh.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_callout.dart';
import '../../core/widgets/tms_category_square.dart';
import '../../core/widgets/tms_fab.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_motion.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_scroll_nav_bar.dart';
import '../../core/widgets/tms_step_track.dart';
import '../../core/widgets/tms_tip_popover.dart';
import '../celebrate/celebrate_screen.dart';
import '../shell/main_shell.dart';
import '../ticket/repair_labels.dart';
import '../ticket/ticket_screen.dart';
import 'ask_sheet.dart';
import 'hold_sheet.dart';
import 'repair_sheet.dart';
import 'room_info_screen.dart';

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

  /// 這次的報修是從中斷面板的「需要工務協助」轉過來的，送出時要同時把原任務
  /// 轉為已中斷。從導覽列的報修圖示進來時是 false，原任務照常進行。
  bool _repairInterruptsSourceTask = false;

  /// 這一間房未撤回的工務單，順序照後端給的順序。
  List<RepairTicketSummary> _repairTickets = const <RepairTicketSummary>[];

  /// 這個租戶的打掃 SOP 項，順序照後端給的 `sortOrder`。只有打掃類任務要用。
  List<SopItem> _sopItems = const <SopItem>[];

  /// 員工已經按過交辦的「知道了」。
  ///
  /// 後端的 `handoverRead` 要等開工那一刻才會翻成已讀，但員工在這一頁按了
  /// 「知道了」之後就不該再看到那一塊升在頁面上。契約沒有「標記交辦已讀」的
  /// 端點，所以這一下先記在畫面上。
  bool _handoverAcked = false;

  /// 開工前的固定教學已經被關掉。
  ///
  /// 原型記的是「顯示過一次就不再出現」，跨工作階段保留。App 這一側沒有本機
  /// 偏好設定可以寫，所以目前只記到離開這一頁為止。
  bool _teachingDismissed = false;

  /// 「查一下」的說明泡泡已經看過。按 ×、按下「查一下」或離開這一頁都算看過。
  bool _askTipSeen = false;

  /// 目前畫面上這一筆任務。
  ///
  /// 報修面板是在員工按下報修鍵的那一刻才開，當下讀不到 `FutureBuilder`
  /// builder 參數裡的那一筆，但報修要帶原任務 id 才接得回這張單，所以載到
  /// 之後在這裡留一份。
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
        // 整頁不包 SafeArea：標題區與凍結導覽列的品牌色要一路鋪到螢幕頂
        // （`Main.dc.html:1149`），置底操作列也自己吃掉底部安全區。包一層
        // SafeArea 會在上下各切出一條白邊。
        child: Stack(
          children: [
            _buildPage(),
            // 面板升起時背後那一頁要壓暗，點暗掉的地方要能把面板收回去。
            // 壓暗只是一塊疊上去的顏色，不是 modal barrier，底下那一頁的
            // 離線列與待同步筆數仍然讀得到（理由見 [RepairSheet]）。
            if (_repairSheetOpen)
              Positioned.fill(
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: _closeRepairSheet,
                  child: const ColoredBox(color: TmsColors.scrimSheet),
                ),
              ),
            if (_repairSheetOpen)
              Align(
                alignment: Alignment.bottomCenter,
                child: TmsMotionView(
                  spec: TmsMotion.up,
                  child: RepairSheet(
                    roomNo: widget.roomNo,
                    sourceTaskId: _openRepairSheetTaskId,
                    onClose: _closeRepairSheet,
                    interruptSourceTask: _repairInterruptsSourceTask,
                    onBack: _repairInterruptsSourceTask
                        ? _backToInterruptReasons
                        : null,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  /// 連線狀態列佔不佔高度，決定頂部安全區由誰讓開。
  ///
  /// 連線正常時那一列不佔高度，安全區交給凍結導覽列吃掉，品牌色才鋪得到螢幕
  /// 頂；真的有連線狀況要講時那一列升到最上面，安全區換它讓開，導覽列不必再
  /// 讓一次。兩者都讓的話，離線時標題區上方會多出一條空白。
  Widget _buildPage() {
    return ValueListenableBuilder<bool>(
      valueListenable: ConnectionStatus.instance.offline,
      builder: (context, offline, _) => ValueListenableBuilder<bool>(
        valueListenable: ConnectionStatus.instance.unstable,
        builder: (context, unstable, _) =>
            _buildPageBody(showsStatusBar: offline || unstable),
      ),
    );
  }

  Widget _buildPageBody({required bool showsStatusBar}) {
    final safeTop = MediaQuery.paddingOf(context).top;
    final navTopInset = showsStatusBar ? 0.0 : safeTop;
    return Column(
      children: [
        SystemStatusBar(
          padding: EdgeInsets.fromLTRB(
            TmsSpacing.screenEdge,
            safeTop,
            TmsSpacing.screenEdge,
            0,
          ),
        ),
        Expanded(
          child: TmsScrollNavBarScroll(
            builder: (context, scrolled) => FutureBuilder<TaskDetail?>(
              future: _taskFuture,
              builder: (context, snapshot) {
                final Widget content;
                if (snapshot.hasError) {
                  content = const Padding(
                    padding: EdgeInsets.all(16),
                    child: Text('載入失敗，請稍後再試'),
                  );
                } else if (snapshot.connectionState != ConnectionState.done) {
                  content = const Center(child: CupertinoActivityIndicator());
                } else {
                  content = _buildTask(snapshot.data, topInset: navTopInset);
                }
                // 導覽列在載入中與載入失敗時也要在，員工才隨時回得去上一頁。
                return Stack(
                  children: [
                    content,
                    Positioned(
                      top: 0,
                      left: 0,
                      right: 0,
                      child: _buildNavBar(
                        scrolled,
                        snapshot.data,
                        topInset: navTopInset,
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ),
      ],
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
      _repairInterruptsSourceTask = false;
    });
  }

  void _closeRepairSheet() {
    setState(() {
      _repairSheetOpen = false;
      _openRepairSheetTaskId = null;
      _repairInterruptsSourceTask = false;
    });
  }

  /// 從「需要工務協助」轉過來的報修表單，左上角那顆鈕要走回中斷原因清單。
  ///
  /// 這兩層在畫面上是同一個面板換了內容（`Main.dc.html:1455`），在程式裡是兩
  /// 層：報修表單貼在任務頁上（理由見 [RepairSheet]），而中斷面板是一條路由。
  /// 回到上一步因此是「收掉表單、重新開中斷面板」，而不是把表單退回去。
  void _backToInterruptReasons() {
    final task = _loadedTask;
    _closeRepairSheet();
    if (task != null) {
      _openHoldSheet(task, HoldSheetMode.interrupt);
    }
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

  /// 把已中斷的這一間接回來繼續做。
  ///
  /// 離線時擋在這裡的理由同 [_start]：回到進行中要由後端判定「一次只做一
  /// 間」，離線時沒有人能做這個判定，先放行會讓同一位員工手上出現兩筆進行中。
  Future<void> _resume(TaskDetail task) async {
    if (ConnectionStatus.instance.isOffline) {
      await showOfflineBlockedAlert(context);
      return;
    }
    try {
      final resumed = await ApiClient.instance.resumeTask(task.id);
      if (!mounted) {
        return;
      }
      _showTask(resumed);
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
    TaskCompletion? completion;
    try {
      sent = await OfflineQueue.instance.submit(
        kind: 'complete',
        send: (idempotencyKey) async {
          completion = await ApiClient.instance.completeTask(
            task.id,
            idempotencyKey: idempotencyKey,
          );
        },
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
      // 離線時後端還沒收到這一筆，去向與今日進度都算不出來，但員工該拿到的
      // 回饋不欠：完成畫面照常出現，卡上那一行說這一筆還押在手機上。
      if (task.type == 'cleaning') {
        await Navigator.of(context).push(_celebrateRoute(task, queued: true));
      }
      return;
    }
    var completed = completion?.task;
    if (completed == null) {
      completed = await ApiClient.instance.getTask(task.id);
      if (!mounted) {
        return;
      }
    }
    _showTask(completed);
    TodayRefresh.instance.request();
    // 慶祝頁給的是「一間房做完了」的回饋，所以只有打掃類走這一段；備品類送達
    // 之後留在任務頁。
    if (completed.type == 'cleaning') {
      await Navigator.of(context).push(
        _celebrateRoute(
          completed,
          completion: completion,
          onNextStep: _goToNextStep,
        ),
      );
    }
  }

  /// 從完成畫面接著走：還有下一間就直接開那一間的任務頁，都清完了就回到
  /// 待認領。
  ///
  /// 兩種去向都先把完成畫面與剛做完的那一頁關掉，員工按返回才不會退回一張
  /// 已經做完的單。
  void _goToNextStep(TaskNextStep step) {
    final navigator = Navigator.of(context);
    navigator.popUntil((route) => route.isFirst);
    if (step.kind == TaskNextStepKind.allClear) {
      MainShellTabRequest.goToPool();
      return;
    }
    final roomNo = step.roomNo;
    if (roomNo == null) {
      return;
    }
    navigator.push(
      CupertinoPageRoute<void>(
        builder: (_) => TaskScreen(roomNo: roomNo, taskId: step.taskId),
      ),
    );
  }

  /// 任務停在哪個狀態、停住原因記成什麼，一律以後端送出後回傳的那一筆為準。
  /// 畫面若自己先改成「已延後」再送出，後端拒收時員工會看著一個不存在的狀態
  /// 繼續操作下去。
  Future<void> _openHoldSheet(TaskDetail task, HoldSheetMode mode) async {
    final result = await HoldSheet.show(context, mode: mode, taskId: task.id);
    if (result == null || !mounted) {
      return;
    }
    switch (result) {
      case HoldSheetSubmitted(:final task):
        _showTask(task);
        TodayRefresh.instance.request();
      case HoldSheetNeedsMaintenance():
        setState(() {
          _repairSheetOpen = true;
          _openRepairSheetTaskId = task.id;
          _repairInterruptsSourceTask = true;
        });
    }
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
      // `task.busy-alert` 掛在內文那一行上：驗收要一字不差讀到後端回的拒絕
      // 原因，而對話框層的節點除了內文還含標題與按鈕的文字，讀出來會多出東西。
      builder: (dialogContext) => TmsAlert(
        message: message,
        messageId: 'task.busy-alert',
        actions: [
          TmsAlertAction(
            label: '知道了',
            id: 'task.busy-alert.dismiss-button',
            style: TmsAlertActionStyle.primary,
            onPressed: () => Navigator.of(dialogContext).pop(),
          ),
        ],
      ),
    );
  }

  /// 任務頁頂端那條凍結導覽列。
  ///
  /// 標題寫這張單的身分，讓員工在任何捲動位置都認得出自己停在哪一間房。還
  /// 沒捲動時標題區自己就寫著房號，導覽列不重複，只寫任務類別；標題區捲走
  /// 之後房號補回標題，進行中的單再把類別換成正在走的計時，員工不必捲回去
  /// 就看得到還剩多少時間。
  ///
  /// 任務還沒載回來時只寫得出房號，那是這一頁唯一不必等後端就確定的事。
  Widget _buildNavBar(
    bool scrolled,
    TaskDetail? task, {
    required double topInset,
  }) {
    final doing = task?.state == 'doing';
    final kindLabel = task == null
        ? null
        : '${tmsCategoryAppearance(task.type).label}任務';
    final withRoom = kindLabel == null
        ? widget.roomNo
        : '${widget.roomNo} · $kindLabel';
    // 計時只在進行中與已中斷的單上有意義。已延後的單還沒開工，寫一個停著
    // 的計時會讓員工以為時間正在走。
    final showTimer = task != null && (doing || task.state == 'interrupted');
    return TmsSemantics(
      id: 'task.nav.${widget.roomNo}',
      explicitChildNodes: true,
      child: TmsScrollNavBar(
        scrolled: scrolled,
        topInset: topInset,
        restingBackground: doing
            ? TmsColors.brandPrimary
            : TmsColors.cardBackground,
        restingForeground: doing ? TmsColors.brandOnPrimary : TmsColors.label,
        restingTitle: doing ? withRoom : (kindLabel ?? widget.roomNo),
        scrolledTitle: showTimer
            ? '${widget.roomNo} · ${taskTimerText(task)}'
            : withRoom,
        titleSuffix:
            task != null && _overtimeMinutes(task) != null ? ' 超時' : null,
        leading: TmsNavBarBackButton(
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        trailing: task == null || !_canRepair(task)
            ? null
            : TmsSemantics(
                id: 'task.repair-button',
                // 報修在導覽列右側，不在底部動作列：底部一次只留一主一次兩顆，
                // 報修是「做到一半發現房間壞了」的旁支，擺進那一列會讓員工每
                // 次都要先讀完三顆才找得到往下走的那一顆。
                child: TmsNavBarButton(
                  semanticsLabel: '報修',
                  onPressed: _openRepairSheet,
                  child: const Icon(
                    CupertinoIcons.wrench,
                    size: TmsSize.navBarItemIcon,
                  ),
                ),
              ),
      ),
    );
  }

  /// 這張單現在能不能報修（`Main.dc.html:4373` 的 `dt.mine && state !== 'cancelled'`）。
  ///
  /// 已經作廢的單不必再報修。還躺在待認領池裡的單也不給：報修要掛回原任務，
  /// 沒有負責人的單掛不上去。契約目前沒有「這張單是不是我的」這一欄，所以用
  /// 「不在池裡」當作有負責人。
  static bool _canRepair(TaskDetail task) =>
      task.state != 'cancelled' && task.state != 'pool';

  /// 目前狀態的主按鈕：按鈕上寫什麼、按下去做什麼。沒有主要動作時回 null。
  ///
  /// 對照表出自 `Main.dc.html:4331-4334`：待開始是「開始」，已延後是「開始打掃」
  /// 或「開始處理」（延後的單按一下直接開工，不必先回到待開始），進行中是
  /// 「完成」，已中斷是「恢復處理」。延後／中斷是次要按鈕，不走這裡。
  ({String label, VoidCallback onPressed})? _primaryAction(TaskDetail task) {
    switch (task.state) {
      case 'accepted':
        return (label: '開始', onPressed: () => _start(task));
      case 'deferred':
        return (label: taskStartLabel(task), onPressed: () => _start(task));
      case 'doing':
        return (label: '完成', onPressed: () => _complete(task));
      case 'interrupted':
        return (label: '恢復處理', onPressed: () => _resume(task));
      default:
        return null;
    }
  }

  Widget _buildTask(TaskDetail? task, {required double topInset}) {
    if (task == null) {
      return const Padding(
        padding: EdgeInsets.all(TmsSpacing.screenEdge),
        child: Align(alignment: Alignment.topLeft, child: Text('找不到這個房號的任務')),
      );
    }

    // 「查一下」讓開操作列的依據是有沒有主按鈕（`Main.dc.html:5027`）。次要按鈕
    // 只出現在待開始與進行中，那兩種狀態一定也有主按鈕。
    final fabBottom = TmsFab.bottomAboveActionBar(
      hasActionBar: _primaryAction(task) != null,
      bottomSafeAreaInset: MediaQuery.paddingOf(context).bottom,
    );
    // 作廢的單沒有東西好查（`Main.dc.html:4372`）。
    final canAsk = task.state != 'cancelled';
    return Column(
      children: [
        Expanded(
          // 說明泡泡的箭頭尖是從卡片下緣凸出去的，預設的裁切會把它削掉。
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              // 整頁可捲動。任務頁上從標題區、提示、同房工務單到 SOP 清單，
              // 一屏放不下是常態；捲不動的版面會讓最下面那幾項永遠點不到。
              // 用 SingleChildScrollView 而不是 ListView：ListView 只建視窗內
              // 的那幾列，捲到之前它們不在畫面上，驗收也定位不到。
              SingleChildScrollView(
                // 導覽列疊在內容上面，所以內容自己要讓開導覽列那麼高，否則
                // 標題區最上面那一行永遠鑽在導覽列底下讀不到。
                padding: EdgeInsets.only(
                  top: topInset + TmsSize.frozenNavBarHeight,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _buildHeader(task),
                    Padding(
                      padding: EdgeInsets.fromLTRB(
                        TmsSpacing.screenEdge,
                        TmsSpacing.md2,
                        TmsSpacing.screenEdge,
                        // 「查一下」浮在內容上面，捲到底時最後一列要能整列
                        // 離開它底下（`Main.dc.html:5028`）。
                        TmsFab.scrollPadBottom(fabBottom),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: _spaced(_buildSections(task)),
                      ),
                    ),
                  ],
                ),
              ),
              if (canAsk && _showsAskTip(task))
                Positioned(
                  right: TmsFab.sideInset,
                  bottom: TmsTipPopover.bottomAbove(fabBottom, TmsFab.diameter),
                  child: TmsTipPopover(
                    title: '有問題可以在這裡問',
                    message: '查 SOP，或這間房修過什麼、住客有什麼要求。',
                    width: _askTipWidth,
                    onDismiss: () => setState(() => _askTipSeen = true),
                  ),
                ),
              if (canAsk)
                Positioned(
                  right: TmsFab.sideInset,
                  bottom: fabBottom,
                  child: _buildAskButton(task),
                ),
            ],
          ),
        ),
        _buildActionBar(task),
      ],
    );
  }

  /// 「查一下」的說明泡泡寬度。`Main.dc.html:1332` — width: 264px
  static const double _askTipWidth = 264;

  /// 第一次進任務頁的說明泡泡要不要出現（`Main.dc.html:4436`）。
  ///
  /// 一次只給員工一個提示：這一頁已經有固定教學、或有出事了要處理的狀態列時，
  /// 泡泡讓位，等下次再說。
  bool _showsAskTip(TaskDetail task) {
    if (_askTipSeen) {
      return false;
    }
    final spec = TmsCalloutSlot.highestPriority(_calloutCandidates(task));
    if (spec is TmsTeachingCallout) {
      return false;
    }
    return !(spec is TmsStatusCallout && spec.tone == TmsCalloutTone.error);
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
    final sections = <Widget>[];
    final candidates = _calloutCandidates(task);
    if (TmsCalloutSlot.highestPriority(candidates) != null) {
      sections.add(TmsCalloutSlot(candidates: candidates));
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
    final handovers = _buildHandoverQuote(task);
    if (handovers != null) {
      sections.add(handovers);
    }
    final info = _buildInfoSection(task);
    if (info != null) {
      sections.add(info);
    }
    final sop = _buildSopChecklist(task);
    if (sop != null) {
      sections.add(sop);
    }
    return sections;
  }

  /// 這張單當下成立的每一條狀態列規則，成立就給一則候選。
  ///
  /// 一頁只畫其中名次最前的一則，名次由 [TmsCalloutKind] 的宣告順序決定，這
  /// 裡只負責說「現在成立的是哪幾條」。文案出自 `Main.dc.html:4306-4324`。
  ///
  /// 後端沒給的欄位那一段整段不寫，也不填佔位字：畫殼填假值會讓驗收分不出
  /// 「做好了」和「資料還沒來」。
  List<TmsCalloutSpec?> _calloutCandidates(TaskDetail task) {
    final isClean = task.type == 'cleaning';
    final waived =
        isClean && (task.inspectionWaived || task.inspection == 'waived');
    final returnInfo = task.returnInfo;
    final holdReason = task.holdReason;
    final held = task.state == 'deferred' || task.state == 'interrupted';
    return <TmsCalloutSpec?>[
      if (returnInfo != null)
        TmsStatusCallout(
          kind: TmsCalloutKind.taskReturned,
          tone: TmsCalloutTone.error,
          title: returnInfo.title,
          reason: returnInfo.reason.isEmpty ? null : returnInfo.reason,
          // 原型這一句前面還有「請重做「床鋪」相關項目。」，那是後端給的不合格
          // 項名稱（`Main.dc.html:4309` 的 `dt.failed`）。契約目前沒有這一欄，
          // 所以那一句整句不寫。
          body: '計時與 SOP 勾記已重設，第一次的紀錄仍保留在任務歷程。',
          idPrefix: 'task.status-row.return-info',
          wrap: (node) => TmsSemantics(
            id: 'task.status-row.return-info',
            explicitChildNodes: true,
            child: node,
          ),
        ),
      if (task.state == 'cancelled')
        TmsStatusCallout(
          kind: TmsCalloutKind.taskCancelled,
          tone: TmsCalloutTone.neutral,
          title: '這單已取消',
          body: '不需要再處理，也不計入完成數與工時。',
          idPrefix: 'task.status-row.cancelled',
          wrap: (node) => TmsSemantics(
            id: 'task.status-row.cancelled',
            explicitChildNodes: true,
            child: node,
          ),
        ),
      if (held && holdReason != null) _heldCallout(task, holdReason),
      if (task.state == 'accepted' && returnInfo == null && !_teachingDismissed)
        TmsTeachingCallout(
          title: isClean ? '到房間門口再按開始' : '拿好物品再按開始',
          body: isClean
              ? '系統會從那一刻起計時。${waived ? '這單免查房，按完成就結案。' : ''}'
              : '送達住客手上後按完成。',
          onDismiss: () => setState(() => _teachingDismissed = true),
          wrap: (node) =>
              TmsSemantics(id: 'task.status-row.teaching', child: node),
        ),
      if (task.state == 'done')
        TmsStatusCallout(
          kind: TmsCalloutKind.taskAwaitingInspection,
          tone: TmsCalloutTone.info,
          title: isClean ? '等主管查房' : '已送達',
          body: isClean
              ? '查房通過後，房況會自動同步 PMS 變成可售。'
              : '住客會收到完成通知並可評分。',
          idPrefix: 'task.status-row.awaiting-inspection',
          wrap: (node) => TmsSemantics(
            id: 'task.status-row.awaiting-inspection',
            explicitChildNodes: true,
            child: node,
          ),
        ),
      if (task.state == 'inspected')
        TmsStatusCallout(
          kind: TmsCalloutKind.taskFinished,
          tone: TmsCalloutTone.success,
          title: waived ? '完成 · 免查房' : '查房通過',
          body: waived
              ? '房況已回寫 PMS：住客房・已整理（OC）。主管抽查若有問題，仍可退回重做。'
              : '房況已回寫 PMS，這間可以賣了。',
          idPrefix: 'task.status-row.finished',
          wrap: (node) => TmsSemantics(
            id: 'task.status-row.finished',
            explicitChildNodes: true,
            child: node,
          ),
        ),
    ];
  }

  /// 已延後或已中斷的狀態列（`Main.dc.html:4311-4318`）。
  ///
  /// 標題直接寫後端記下的停住原因，說明才講「所以現在能做什麼」。停在工務單
  /// 上的那幾種說法要工務單的階段、主管批註與照片張數才寫得出來，契約的
  /// `RepairTicketSummary` 沒有這些欄位，所以一律用沒有工務單的那一句。
  TmsCalloutSpec _heldCallout(TaskDetail task, String holdReason) {
    final repaired = holdReason.contains('工務已修好');
    final ticket = _repairTickets.length == 1 ? _repairTickets.single : null;
    return TmsStatusCallout(
      kind: TmsCalloutKind.taskHeld,
      tone: TmsCalloutTone.warn,
      title: holdReason,
      body: task.state == 'deferred'
          ? '單仍在你名下，可以先做別間。準備好再按「${taskStartLabel(task)}」直接開工。'
          : '計時已暫停、SOP 勾記保留。回來後按「恢復處理」接著做。',
      actions: <TmsCalloutAction>[
        if (repaired)
          TmsCalloutAction(
            label: '恢復任務',
            onPressed: () => _resume(task),
            wrap: (node) => TmsSemantics(
              id: 'task.status-row.resume-button',
              child: node,
            ),
          ),
        if (ticket != null)
          TmsCalloutAction(
            label: '查看工務單',
            showsChevron: true,
            onPressed: () => _openTicket(ticket.taskId),
            wrap: (node) => TmsSemantics(
              id: 'task.status-row.open-ticket-button',
              child: node,
            ),
          ),
      ],
      // 停住原因那一段文字要被原樣讀到，所以顯式掛在節點的 label 上；
      // explicitChildNodes 讓說明與文字鍵留在自己的節點上，不被接進來。
      wrap: (node) {
        final reasonNode = TmsSemantics(
          id: 'task.status-row.hold-reason',
          label: holdReason,
          explicitChildNodes: true,
          child: node,
        );
        return repaired
            ? TmsSemantics(
                id: 'task.resume-banner',
                explicitChildNodes: true,
                child: reasonNode,
              )
            : reasonNode;
      },
    );
  }

  /// 開工前還沒看過的交辦，升到頁面上一次（`Main.dc.html:1268`、`4338`）。
  ///
  /// 看過之後就收回「這間房的資訊」那一列，不再佔整頁的高度：交辦是開工前要
  /// 讀的東西，不是做到一半還要一直看見的東西。
  Widget? _buildHandoverQuote(TaskDetail task) {
    if (!_memoPromo(task)) {
      return null;
    }
    final entries = task.handovers;
    return TmsSemantics(
      id: 'task.handover.callout',
      explicitChildNodes: true,
      child: TmsQuoteCallout(
        title: '交辦 ${entries.length} 則 · 開工前請先看',
        onAcknowledge: () => setState(() => _handoverAcked = true),
        entries: <TmsQuoteEntry>[
          for (var i = 0; i < entries.length; i++)
            TmsQuoteEntry(
              text: entries[i].content,
              meta: taskHandoverMeta(entries[i]),
              idPrefix: 'task.handover.$i',
            ),
        ],
      ),
    );
  }

  bool _memoPromo(TaskDetail task) =>
      task.handovers.isNotEmpty &&
      task.state == 'accepted' &&
      !_handoverRead(task);

  bool _handoverRead(TaskDetail task) => task.handoverRead || _handoverAcked;

  /// 「這間房的資訊」：交辦與工務單各一列，點了推整頁。
  ///
  /// 最多兩列，一類一列（`Main.dc.html:4340-4349`）。頁面的高度因此不隨這間房
  /// 的工務單張數變長，員工要的那一步也不會被一串旁支推到螢幕外。兩列都沒有
  /// 內容時整區不畫。
  Widget? _buildInfoSection(TaskDetail task) {
    final rows = <Widget>[];
    if (task.handovers.isNotEmpty && !_memoPromo(task)) {
      rows.add(
        TmsSemantics(
          id: 'task.info-row.handovers',
          child: _InfoRow(
            label: '交辦',
            value: '${task.handovers.length} 則',
            unread: !_handoverRead(task),
            unreadId: 'task.handover.unread-dot',
            onTap: () {
              setState(() => _handoverAcked = true);
              Navigator.of(context).push(
                CupertinoPageRoute<void>(
                  builder: (_) => HandoverListScreen(
                    roomNo: widget.roomNo,
                    entries: task.handovers,
                  ),
                ),
              );
            },
          ),
        ),
      );
    }
    if (_repairTickets.isNotEmpty) {
      final rejected = _repairTickets.any(
        (ticket) => ticket.state == 'rejected',
      );
      rows.add(
        TmsSemantics(
          id: 'task.info-row.repair-tickets',
          child: _InfoRow(
            label: '工務單',
            value: _repairTicketsValue(),
            valueColor: rejected
                ? TmsColors.onErrorContainer
                : TmsColors.quaternaryLabel,
            onTap: () {
              // 只有一張就直接開那一張：中間再隔一層只有一列的清單，員工要多
              // 點一次才看得到內容。
              if (_repairTickets.length == 1) {
                _openTicket(_repairTickets.single.taskId);
                return;
              }
              Navigator.of(context).push(
                CupertinoPageRoute<void>(
                  builder: (_) => RepairTicketListScreen(
                    roomNo: widget.roomNo,
                    tickets: _repairTickets,
                  ),
                ),
              );
            },
          ),
        ),
      );
    }
    if (rows.isEmpty) {
      return null;
    }
    return TmsGroupedSection(header: '這間房的資訊', children: rows);
  }

  /// 工務單那一列右側的值（`Main.dc.html:4346`）：一張寫單號、類別與目前階段，
  /// 多張只寫張數與還有幾張沒結案。
  String _repairTicketsValue() {
    if (_repairTickets.length == 1) {
      final ticket = _repairTickets.single;
      return '${ticket.ticketNo} ${repairCategoryText(ticket.category)}'
          '・${repairStateText(ticket.state)}';
    }
    final open = _repairTickets.where(_isOpenTicket).length;
    return '${_repairTickets.length} 張・'
        '${open > 0 ? '$open 張未結案' : '都已結案'}';
  }

  /// 這張工務單還沒結案：已完修與已撤回以外都算。
  static bool _isOpenTicket(RepairTicketSummary ticket) =>
      ticket.state != 'done' && ticket.state != 'cancelled';

  void _openTicket(String taskId) {
    Navigator.of(context).push(
      CupertinoPageRoute<void>(
        builder: (_) =>
            TicketScreen(view: TicketView.housekeeping, taskId: taskId),
      ),
    );
  }

  /// 浮在內容右下角的「查一下」。
  ///
  /// 它不在底部動作列裡：查 SOP 或查這間房的記錄是員工做到一半隨時會想做的
  /// 事，壓在動作列裡會跟「往下走」的那一顆混在一起。
  Widget _buildAskButton(TaskDetail task) {
    return TmsSemantics(
      id: 'task.ask-button',
      child: TmsFab(
        glyph: TmsFabGlyph.ask,
        semanticsLabel: '查一下：查 SOP 或這間房的記錄',
        onPressed: () {
          // 按過這一顆就等於知道它是做什麼的，說明泡泡不必再出現。
          setState(() => _askTipSeen = true);
          AskSheet.show(context, taskId: task.id, roomNo: widget.roomNo);
        },
      ),
    );
  }

  /// 置底動作列。
  ///
  /// 一個狀態最多只露出兩顆。主按鈕由 [_primaryAction] 的狀態對照表決定，次要
  /// 按鈕是待開始的「延後」與進行中的「中斷」。打掃到一半按完成前，上面再補
  /// 一行還有幾項沒勾。
  Widget _buildActionBar(TaskDetail task) {
    final primary = _primaryAction(task);
    return TmsActionBar(
      hint: _buildUncheckedHint(task),
      secondary: _buildSecondaryButton(task),
      primary: primary == null
          ? null
          // 不另外給 label：按鈕文字本身就是子節點的 label，顯式再給一次
          // 會被 Flutter 接成「開始\n開始」。
          : TmsSemantics(
              id: 'task.primary-button',
              child: TmsPillButton(
                label: primary.label,
                onPressed: primary.onPressed,
              ),
            ),
    );
  }

  /// 按完成之前那一行提醒（`Main.dc.html:1354`、`4379`）。
  ///
  /// 沒勾的項目在完成那一刻會被記成跳過，員工按下去之前要先知道這件事。只在
  /// 打掃類的進行中單上出現，而且真的還有沒勾的才出現。
  Widget? _buildUncheckedHint(TaskDetail task) {
    if (task.state != 'doing' || task.type != 'cleaning' || _sopItems.isEmpty) {
      return null;
    }
    final checked = task.checkedSopItems.toSet();
    final unchecked = _sopItems
        .where((item) => !checked.contains(item.id))
        .length;
    if (unchecked == 0) {
      return null;
    }
    final text = '還有 $unchecked 項沒勾，完成後會記錄為跳過';
    return TmsSemantics(
      id: 'task.sop.unchecked-hint',
      label: text,
      child: ExcludeSemantics(
        child: Text(
          text,
          textAlign: TextAlign.center,
          style: TmsText.footnote.copyWith(color: TmsColors.secondaryLabel),
        ),
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
    // 已延後的單還沒開工，右側不寫計時（`Main.dc.html:4350` 的
    // `timed = doing || paused`，原型的 paused 就是契約的 interrupted）。寫一個
    // 停著的計時會讓員工以為時間正在走。
    final interrupted = task.state == 'interrupted';
    final foreground = doing ? TmsColors.brandOnPrimary : TmsColors.label;
    final secondary = doing
        ? TmsColors.headerMetaOnDark
        : TmsColors.headerMetaOnLight;
    final title = taskTitleText(task, widget.roomNo);
    final meta = _metaLine(task);
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
            TaskTimerHero(
              task: task,
              onTick: () {
                if (mounted) {
                  setState(() {});
                }
              },
            ),
            const SizedBox(height: TmsSpacing.xs2),
            Text(
              '${widget.roomNo} · $title',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TmsText.headline.copyWith(color: foreground),
            ),
          ] else ...[
            _buildRoomHero(task, showTimer: interrupted),
            const SizedBox(height: TmsSpacing.xs2),
            Text(
              title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TmsText.headline.copyWith(color: foreground),
            ),
          ],
          if (meta != null) ...[
            const SizedBox(height: TmsSpacing.xs2),
            Text(meta, style: TmsText.subheadline.copyWith(color: secondary)),
          ],
          const SizedBox(height: TmsSpacing.xs2),
          _buildStepTrack(task, onDark: doing),
        ],
      ),
    );
  }

  /// 標題底下那一行（`Main.dc.html:4351`）：這張單是誰開的、估多久、是不是免
  /// 查房、停住期間有沒有人留話。
  ///
  /// 計時正在走或停在一半時，預估已經寫在計時那一側，這裡不再寫一次。契約沒
  /// 有原型那個房間標記欄位（`dt.tag`，例如 VIP），所以那一段不寫。
  String? _metaLine(TaskDetail task) {
    final isClean = task.type == 'cleaning';
    final timed = task.state == 'doing' || task.state == 'interrupted';
    final waived =
        isClean && (task.inspectionWaived || task.inspection == 'waived');
    final held = task.state == 'deferred' || task.state == 'interrupted';
    final parts = <String>[
      if (task.source != null)
        taskSourceText(task.source!)
      else if (isClean)
        '排房指派',
      if (task.estMin != null && !timed) '預估 ${task.estMin} 分',
      if (waived) '免查房',
      if (task.handovers.isNotEmpty && held) '有交代',
    ];
    return parts.isEmpty ? null : parts.join(' · ');
  }

  /// 開始前與停住後的主角：56pt 的房號，停在一半的單在右邊接已做時間。
  Widget _buildRoomHero(TaskDetail task, {required bool showTimer}) {
    final overtimeMinutes = _overtimeMinutes(task);
    final timerText = taskTimerText(task);
    final timerSub = _timerSubText(task);
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
        if (showTimer)
          Padding(
            padding: const EdgeInsets.only(bottom: TmsSpacing.xs),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              mainAxisSize: MainAxisSize.min,
              children: [
                TmsSemantics(
                  id: 'task.hero.paused-elapsed',
                  label: timerText,
                  child: ExcludeSemantics(
                    child: Text(
                      timerText,
                      style: TmsText.displayTimer.copyWith(
                        color: overtimeMinutes != null
                            ? TmsColors.overtimeTimerOnLight
                            : TmsColors.label,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: TmsSpacing.xs),
                if (overtimeMinutes != null)
                  _buildOvertimeChip(
                    overtimeMinutes,
                    onDark: false,
                    compact: true,
                  )
                else if (timerSub != null)
                  Text(
                    timerSub,
                    style: TmsText.footnote.copyWith(
                      color: TmsColors.headerMetaOnLight,
                    ),
                  ),
              ],
            ),
          ),
      ],
    );
  }

  /// 計時數字底下那一行（`Main.dc.html:4367`）：停在一半的單先講它停住了，再講
  /// 原本估多久。沒有預估值時只講停住。
  String? _timerSubText(TaskDetail task) {
    final held = task.state == 'interrupted';
    final est = task.estMin;
    if (est == null) {
      return held ? '已暫停' : null;
    }
    return '${held ? '已暫停 · ' : ''}預估 $est 分';
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
  /// 分子只數勾選的（`Main.dc.html:4291`）。跳過的意思是這一項這次不做，不是
  /// 做完了，把它算進分子會讓進度條講出一個沒做過的數字。跳過不擋完成：後端
  /// 在完成那一步會把沒勾也沒跳過的項目一律記成跳過。
  ///
  /// 租戶沒有設定任何打掃 SOP 項、這不是打掃類任務，或這張單已經查房通過或
  /// 作廢時，整區不畫（`Main.dc.html:4369`）。
  Widget? _buildSopChecklist(TaskDetail task) {
    if (_sopItems.isEmpty ||
        task.type != 'cleaning' ||
        task.state == 'inspected' ||
        task.state == 'cancelled') {
      return null;
    }
    final checked = task.checkedSopItems.toSet();
    final skipped = task.skippedSopItems.toSet();
    final doneCount = _sopItems
        .where((item) => checked.contains(item.id))
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
              // 勾掉一項之後條子滑過去，不是直接跳到新寬度：跳一下會讓員工
              // 來不及看出剛才那一下算進去了沒有（`Main.dc.html:1308`）。
              child: TweenAnimationBuilder<double>(
                tween: Tween<double>(end: ratio),
                duration: TmsMotion.barSweep.durationIn(context),
                curve: TmsMotion.barSweep.curve,
                builder: (context, width, _) => FractionallySizedBox(
                  alignment: Alignment.centerLeft,
                  widthFactor: width,
                  child: const ColoredBox(color: TmsColors.brandPrimary),
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: TmsSpacing.sm),
        TmsGroupedSection(
          // 最後一列也畫一條底線：這一組底下還接著置底操作列，那條線是在說
          // 清單到這裡為止（`Main.dc.html:1312`）。
          trailingDivider: true,
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

  /// SOP 清單的一列：左邊是勾選框與項目名稱，右邊是跳過鈕。
  ///
  /// 不是進行中就整列點不動（`Main.dc.html:4285`）：還沒開工或已經停住的單上
  /// 勾一項，後端記下的時刻會對不上實際動手的時刻。已經勾起來的項目不給跳過
  /// 鈕——那一項已經做完了，跳過它沒有意義。
  Widget _buildSopRow({
    required TaskDetail task,
    required SopItem item,
    required bool checked,
    required bool skipped,
  }) {
    final doing = task.state == 'doing';
    final canSkip = doing && !checked;
    return Container(
      color: TmsColors.cardBackground,
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.xs,
        TmsSpacing.xs2,
        TmsSpacing.sm,
        TmsSpacing.xs2,
      ),
      constraints: const BoxConstraints(minHeight: 48),
      child: Row(
        children: [
          Expanded(
            child: TmsSemantics(
              id: 'task.sop.${item.label}.item',
              enabled: doing,
              child: CupertinoButton(
                padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.sm),
                alignment: Alignment.centerLeft,
                minimumSize: const Size(0, 48),
                onPressed: doing
                    ? () => _sopCheck(task, item, 'checked')
                    : null,
                child: Row(
                  children: [
                    _SopCheckBox(checked: checked),
                    const SizedBox(width: TmsSpacing.md),
                    Expanded(
                      child: Text(
                        item.label,
                        style: TmsText.body.copyWith(
                          color: skipped || !doing
                              ? TmsColors.tertiaryLabel
                              : TmsColors.label,
                          decoration: skipped
                              ? TextDecoration.lineThrough
                              : null,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          if (canSkip) ...[
            const SizedBox(width: TmsSpacing.sm),
            TmsSemantics(
              id: 'task.sop.${item.label}.skip-button',
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: skipped
                      ? TmsColors.neutralContainer
                      : TmsColors.cardBackground,
                  borderRadius: BorderRadius.circular(TmsRadius.pill),
                  border: Border.all(color: TmsColors.opaqueSeparator),
                ),
                child: CupertinoButton(
                  padding: const EdgeInsets.symmetric(
                    horizontal: TmsSpacing.md,
                  ),
                  minimumSize: const Size(0, 36),
                  onPressed: () => _sopCheck(task, item, 'skipped'),
                  child: Text(
                    skipped ? '已跳過' : '跳過',
                    style: TmsText.footnoteEmphasized.copyWith(
                      color: TmsColors.onNeutralContainer,
                    ),
                  ),
                ),
              ),
            ),
          ],
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

/// 完成慶祝的路由。
///
/// `opaque: false` 是這張卡能成立的前提：慶祝頁是一張浮在半透明遮罩上的對話
/// 卡，遮罩底下要看得到剛做完的那張任務頁。用一般的不透明路由推上去的話，
/// Flutter 不會再畫底下那一頁，遮罩後面什麼都沒有，整個畫面只會是一片黑。
///
/// 轉場用淡入而不是 iOS 預設的右滑：遮罩從旁邊滑進來會像換了一頁，淡入才讀得
/// 出「這張卡蓋在原本那一頁上面」。
PageRouteBuilder<void> _celebrateRoute(
  TaskDetail task, {
  TaskCompletion? completion,
  bool queued = false,
  void Function(TaskNextStep step)? onNextStep,
}) {
  return PageRouteBuilder<void>(
    opaque: false,
    barrierDismissible: false,
    transitionDuration: const Duration(milliseconds: 200),
    reverseTransitionDuration: const Duration(milliseconds: 160),
    pageBuilder: (_, _, _) => CelebrateScreen(
      roomNo: task.roomNo,
      subline: _celebrateSubline(task),
      actualMinutes: task.elapsedMinutes,
      estimateMinutes: task.estMin,
      todayNth: completion?.cleaningDoneToday,
      nextStep: completion?.nextStep,
      queued: queued,
      onNextStep: onNextStep,
    ),
    transitionsBuilder: (_, animation, _, child) =>
        FadeTransition(opacity: animation, child: child),
  );
}

/// 完成畫面上那一句「這一間接下來會怎樣」。
///
/// 三種說法由剛完成的那張單決定，不是由完成畫面自己挑：免查房是開工當下依
/// 租戶設定鎖在任務上的，之後設定變更不影響，所以這裡讀的是任務上的鎖定值，
/// 不是租戶設定。
String _celebrateSubline(TaskDetail task) {
  if (task.type != 'cleaning') {
    return '住客會收到完成通知';
  }
  final waived = task.inspectionWaived || task.inspection == 'waived';
  return waived ? '免查房，直接完成' : '已通知主管查房';
}

/// 超時膠囊：深底上是深橘底深褐字，白底上是橘淡底棕字。
///
/// 超時只是提醒，不擋任何操作，所以不用錯誤紅——紅色會讓員工以為這張單出
/// 了什麼事。
///
/// [compact] 是房號當主角時的那一顆：計時字級小一級，膠囊跟著收一級
/// （`Main.dc.html:1189` 的 `2px 8px` 與 12pt，對上 `1172` 的 `3px 10px` 與 13pt）。
Widget _buildOvertimeChip(
  int overtimeMinutes, {
  required bool onDark,
  bool compact = false,
}) {
  final text = '超時 $overtimeMinutes 分';
  return TmsSemantics(
    id: 'task.hero.overtime-chip',
    label: text,
    child: ExcludeSemantics(
      child: Container(
        padding: EdgeInsets.symmetric(
          horizontal: compact ? TmsSpacing.sm : TmsSpacing.sm2,
          vertical: compact ? TmsSpacing.xxs : 3,
        ),
        decoration: BoxDecoration(
          color: onDark
              ? TmsColors.overtimeCapsule
              : TmsColors.brandAccentContainer,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
        ),
        child: Text(
          text,
          style:
              (compact
                      ? TmsText.caption1
                      : TmsText.footnoteEmphasized)
                  .copyWith(
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
  final base = task.accumulatedSeconds;
  if (base == null) {
    return null;
  }
  final startedAt = task.segmentStartedAt;
  // 停住的單沒有正在跑的段落，累計秒數本身就是已做秒數；拿它寫成「分:秒」
  // 才和原型的暫停計時一樣精確到秒（`Main.dc.html:3154`）。
  if (startedAt == null) {
    return base;
  }
  final segment = (now ?? deviceNow()).difference(startedAt).inSeconds;
  return base + (segment > 0 ? segment : 0);
}

/// 超時幾分鐘；沒超過預估就回 null。
///
/// 公式照原型的 `overInfo`（`Main.dc.html:3153-3156`）：已做秒數超過預估秒數就
/// 算超時，差額換成分鐘時無條件進位而且至少是 1，所以剛跨過預估的那一秒寫的
/// 是「超時 1 分」而不是 0。首頁那張進行中的卡用的是同一條式子，兩個畫面才不
/// 會對同一張單講出差一分鐘的兩個數字。
///
/// 不分狀態都算：停在一半的單也要看得出它停住前已經超時了。已做秒數跟著計時
/// 同一條走秒公式逐秒重算（見 [_workedSecondsNow]），所以畫面開著不操作，超時
/// 也會隨時間變大；又因為只有跨過整分值才會變，秒數跳動不會讓膠囊忽隱忽現。
/// 走不了秒時退回後端給的已做分鐘。
///
/// 超時在這個畫面上只是提示。算出超時不會呼叫任何後端端點，也不會讓員工
/// 收到推播。
int? _overtimeMinutes(TaskDetail task, {DateTime? now}) {
  final estMin = task.estMin;
  if (estMin == null || estMin <= 0) {
    return null;
  }
  final seconds = _workedSecondsNow(task, now) ?? task.elapsedMinutes * 60;
  final over = seconds - estMin * 60;
  if (over <= 0) {
    return null;
  }
  return math.max(1, (over / 60).ceil());
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
  const TaskTimerHero({
    super.key,
    required this.task,
    this.clock,
    this.onTick,
  });

  final TaskDetail task;

  /// 讀「現在幾點」的方法。平常不給，計時就讀手機的時鐘；widget 測試給一個
  /// 自己說了算的時鐘，才能在不真的等一秒的情況下驗計時有往前跳。
  final DateTime Function()? clock;

  /// 每秒重算一次之後通知擺放的畫面。
  ///
  /// 導覽列捲動後也寫同一串計時，它和這個元件是兄弟節點，自己重畫不會帶到
  /// 它。整頁只留這一支每秒醒來的計時器，導覽列跟著這一下一起重畫；導覽列
  /// 另外掛一支的話，兩串字會在不同的時刻各自進位，同一幀上讀到差一秒。
  final VoidCallback? onTick;

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
          widget.onTick?.call();
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
        // 膠囊比旁邊那行字高，所以它離底線遠一點，兩者的視覺中線才對得上
        // 計時的字身（`Main.dc.html:1172`、`1175`）。
        overtimeMinutes != null
            ? Padding(
                padding: const EdgeInsets.only(bottom: TmsSpacing.sm),
                child: _buildOvertimeChip(overtimeMinutes, onDark: true),
              )
            : Padding(
                padding: const EdgeInsets.only(bottom: TmsSpacing.xs2),
                child: Text(
                  task.estMin == null ? '' : '/ 預估 ${task.estMin} 分',
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.headerMetaOnDark,
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


/// 任務頁開工鈕上的字（`Main.dc.html:3199`）。打掃類講「開始打掃」，其餘類別
/// 講「開始處理」——備品與雜項送的是東西，說「打掃」會對不上員工正在做的事。
String taskStartLabel(TaskDetail task) =>
    task.type == 'cleaning' ? '開始打掃' : '開始處理';

/// 標題區與導覽列上的任務名稱。
///
/// 後端的 `title` 目前帶著房號（例如「302 退房打掃」），而房號在它上面那一行
/// 已經是整頁最大的字，照原樣印會寫成「302 · 302 退房打掃」。房號開頭的那一段
/// 去掉，原型的標題只寫任務名（`Main.dc.html:1193`）。
String taskTitleText(TaskDetail task, String roomNo) {
  final title = task.title ?? '';
  final prefix = '$roomNo ';
  return title.startsWith(prefix) ? title.substring(prefix.length) : title;
}

/// 交辦來源的畫面用詞。對照表出自 `房間交辦/dsl.md` 的「來源」欄，與後端
/// `HandoverEntry.kind` 的 enum 一對一。
String taskHandoverSourceText(String kind) {
  switch (kind) {
    case 'shift_handover':
      return '交班單';
    case 'desk_note':
      return '櫃檯備註';
    case 'requeue_progress':
      return '接手進度';
    case 'manager_note':
      return '主管交代';
    default:
      return kind;
  }
}

/// 一則交辦的出處那一行：來源、誰寫的、幾點寫的，以 ` · ` 串起來。時間只有
/// 交班單這類來源帶得出來，沒有就不佔位。
String taskHandoverMeta(HandoverEntry entry) {
  final at = entry.at;
  return <String>[
    taskHandoverSourceText(entry.kind),
    entry.author,
    if (at != null)
      '${at.hour.toString().padLeft(2, '0')}:'
          '${at.minute.toString().padLeft(2, '0')}',
  ].join(' · ');
}

/// 「這間房的資訊」裡的一列（`Main.dc.html:1288`）：左邊可能有一顆未讀點，接
/// 著標籤，右邊是這一類目前的摘要，列尾一個 chevron。
///
/// 不走 [TmsGroupedRow]：這裡的值是 15pt 而且顏色會隨語意換（有工務單被退回
/// 時轉紅），左邊還多一顆未讀點，和設定列那種「標籤加目前設定值」不是同一種列。
class _InfoRow extends StatelessWidget {
  const _InfoRow({
    required this.label,
    required this.value,
    required this.onTap,
    this.unread = false,
    this.unreadId,
    this.valueColor = TmsColors.quaternaryLabel,
  });

  final String label;

  /// 右側摘要，例如「2 則」或「3 張・2 張未結案」。
  final String value;

  final VoidCallback onTap;

  /// 這一類還有沒看過的東西。未讀在畫面上只有左側那顆點，不寫「未讀」兩個字。
  final bool unread;

  /// 未讀點的 accessibility id，給自動化驗收定位用。
  final String? unreadId;

  /// 右側摘要的字色。
  final Color valueColor;

  @override
  Widget build(BuildContext context) {
    final dot = Container(
      width: TmsSize.infoRowUnreadDot,
      height: TmsSize.infoRowUnreadDot,
      decoration: const BoxDecoration(
        color: TmsColors.brandPrimary,
        shape: BoxShape.circle,
      ),
    );
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
        padding: const EdgeInsets.symmetric(
          horizontal: TmsSpacing.screenEdge,
          vertical: TmsSpacing.groupedRowVertical,
        ),
        color: TmsColors.cardBackground,
        child: Row(
          children: [
            if (unread) ...[
              unreadId == null
                  ? Semantics(label: '未讀', child: dot)
                  : TmsSemantics(id: unreadId!, label: '未讀', child: dot),
              const SizedBox(width: TmsSpacing.sm),
            ],
            Text(label, style: TmsText.body),
            const SizedBox(width: TmsSpacing.sm),
            Expanded(
              child: Text(
                value,
                textAlign: TextAlign.right,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TmsText.subheadline.copyWith(color: valueColor),
              ),
            ),
            const SizedBox(width: TmsSpacing.sm),
            const TmsChevron(),
          ],
        ),
      ),
    );
  }
}

/// SOP 每一列左邊那個 26pt 的圓形勾選框（`Main.dc.html:1314`）。
///
/// 勾起來時整顆填成品牌色、裡面是白勾；沒勾是白底加一圈灰框。用文字打勾代替
/// 的話，點擊目標會縮成一個字那麼小，戴著手套的人點不到。
class _SopCheckBox extends StatelessWidget {
  const _SopCheckBox({required this.checked});

  final bool checked;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: TmsSize.sopCheckBox,
      height: TmsSize.sopCheckBox,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: checked ? TmsColors.brandPrimary : TmsColors.cardBackground,
        border: Border.all(
          color: checked ? TmsColors.brandPrimary : TmsColors.opaqueSeparator,
          width: TmsSize.sopCheckBoxBorder,
        ),
      ),
      child: checked
          ? const TmsMotionView(
              spec: TmsMotion.pop,
              child: CustomPaint(
                size: Size(TmsSize.sopCheckGlyph, TmsSize.sopCheckGlyph),
                painter: _SopCheckGlyphPainter(),
              ),
            )
          : null,
    );
  }
}

/// 勾選框裡那個白勾。
///
/// 自己畫而不取 [CupertinoIcons] 的字形：勾的筆畫粗細由字型決定，調不到設計
/// 定下來的 3，26pt 的圓框裡差一截看得出來。
class _SopCheckGlyphPainter extends CustomPainter {
  const _SopCheckGlyphPainter();

  /// 原型 SVG 的 viewBox 邊長。
  static const double _viewBox = 24;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / _viewBox;
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..color = TmsColors.brandOnPrimary
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..strokeWidth = TmsSize.sopCheckGlyphStroke * scale;
    canvas.save();
    canvas.scale(scale);
    // `Main.dc.html:1315` — M5 12.5l4.5 4.5L19 7.5
    canvas.drawPath(
      Path()
        ..moveTo(5, 12.5)
        ..lineTo(9.5, 17)
        ..lineTo(19, 7.5),
      paint,
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(_SopCheckGlyphPainter oldDelegate) => false;
}
