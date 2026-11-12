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

class TaskScreen extends StatefulWidget {
  const TaskScreen({super.key, required this.roomNo, this.taskId});

  final String roomNo;

  final String? taskId;

  @override
  State<TaskScreen> createState() => _TaskScreenState();
}

class _TaskScreenState extends State<TaskScreen> {
  late Future<TaskDetail?> _taskFuture;

  bool _completionPendingSync = false;

  bool _repairSheetOpen = false;

  /// Flutter 的 `ModalBarrier` 把自己包在 `BlockSemantics` 裡，用路由推上來的
  /// 覆蓋層會擋掉底下那一頁的 accessibility 節點。這張卡因此疊在這一頁上。
  _CelebrateContent? _celebrate;

  String? _openRepairSheetTaskId;

  bool _repairInterruptsSourceTask = false;

  List<RepairTicketSummary> _repairTickets = const <RepairTicketSummary>[];

  List<SopItem> _sopItems = const <SopItem>[];

  bool _handoverAcked = false;

  bool _teachingDismissed = false;

  bool _askTipSeen = false;

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
        child: Stack(
          children: [
            _buildPage(),
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
            if (_celebrate case final celebrate?)
              Positioned.fill(
                child: CelebrateScreen(
                  roomNo: celebrate.roomNo,
                  subline: celebrate.subline,
                  actualMinutes: celebrate.actualMinutes,
                  estimateMinutes: celebrate.estimateMinutes,
                  todayNth: celebrate.todayNth,
                  nextStep: celebrate.nextStep,
                  queued: celebrate.queued,
                  celebrationEnabled: celebrate.celebrationEnabled,
                  onNextStep: _goToNextStep,
                  onBackHome: () =>
                      Navigator.of(context).popUntil((route) => route.isFirst),
                ),
              ),
          ],
        ),
      ),
    );
  }

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

  void _backToInterruptReasons() {
    final task = _loadedTask;
    _closeRepairSheet();
    if (task != null) {
      _openHoldSheet(task, HoldSheetMode.interrupt);
    }
  }

  Future<void> _start(TaskDetail task) async {
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

  Future<void> _resume(TaskDetail task) async {
    if (ConnectionStatus.instance.isOffline) {
      await showOfflineBlockedAlert(
        context,
        message: offlineCannotResumeMessage,
      );
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
      await _showBusyAlert(e, fallback: '完成沒有成功，請稍後再試');
      return;
    }
    if (!mounted) {
      return;
    }
    if (!sent) {
      setState(() {
        _completionPendingSync = true;
        _celebrate = _showsCelebrate(task)
            ? _offlineCelebrate(task)
            : _celebrate;
      });
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
    if (_showsCelebrate(completed)) {
      setState(() {
        _celebrate = _CelebrateContent(
          roomNo: completed!.roomNo,
          subline: _celebrateSubline(completed),
          actualMinutes: completed.elapsedMinutes,
          estimateMinutes: completed.estMin,
          todayNth: completion?.cleaningDoneToday,
          nextStep: completion?.nextStep,
          celebrationEnabled: completion?.celebrationEnabled ?? true,
        );
      });
    }
  }

  bool _showsCelebrate(TaskDetail task) =>
      task.type == 'cleaning' || task.type == 'amenity';

  _CelebrateContent _offlineCelebrate(TaskDetail task) {
    final today = ApiClient.instance.lastKnownToday;
    final others = (today?.todayTasks ?? const <TodayTask>[])
        .where((t) => t.taskId != task.id)
        .toList();
    final accepted = others.where((t) => t.state == 'accepted').toList()
      ..sort((a, b) {
        final byRedo = b.redoCount.compareTo(a.redoCount);
        if (byRedo != 0) {
          return byRedo;
        }
        final byKind = (a.type == 'cleaning' ? 1 : 0).compareTo(
          b.type == 'cleaning' ? 1 : 0,
        );
        return byKind != 0 ? byKind : a.roomNo.compareTo(b.roomNo);
      });
    const unfinishedOrder = <String>['doing', 'interrupted', 'deferred'];
    final unfinished = others
        .where((t) => unfinishedOrder.contains(t.state))
        .toList()
      ..sort((a, b) {
        final byState = unfinishedOrder
            .indexOf(a.state)
            .compareTo(unfinishedOrder.indexOf(b.state));
        return byState != 0 ? byState : a.roomNo.compareTo(b.roomNo);
      });
    final TaskNextStep? nextStep = switch ((today, accepted, unfinished)) {
      (null, _, _) => null,
      (_, [final next, ...], _) => TaskNextStep(
        kind: TaskNextStepKind.nextTask,
        taskId: next.taskId,
        roomNo: next.roomNo,
      ),
      (_, _, [final next, ...]) => TaskNextStep(
        kind: TaskNextStepKind.unfinishedTask,
        taskId: next.taskId,
        roomNo: next.roomNo,
      ),
      _ => const TaskNextStep(kind: TaskNextStepKind.allClear),
    };
    return _CelebrateContent(
      roomNo: task.roomNo,
      subline: _celebrateSubline(task),
      actualMinutes: task.elapsedMinutes,
      estimateMinutes: task.estMin,
      todayNth: task.type == 'cleaning' && today != null
          ? today.cleaningDone + 1
          : null,
      nextStep: nextStep,
      queued: true,
    );
  }

  Future<void> _goToNextStep(TaskNextStep step) async {
    final navigator = Navigator.of(context);
    if (step.kind == TaskNextStepKind.allClear) {
      navigator.popUntil((route) => route.isFirst);
      MainShellTabRequest.goToPool();
      return;
    }
    final roomNo = step.roomNo;
    final taskId = step.taskId;
    if (roomNo == null || taskId == null) {
      navigator.popUntil((route) => route.isFirst);
      return;
    }
    final TaskDetail next;
    try {
      next = await ApiClient.instance.getTask(taskId);
    } on ApiException {
      if (!mounted) {
        return;
      }
      navigator.popUntil((route) => route.isFirst);
      await _showReassignedAlert();
      return;
    }
    if (!mounted) {
      return;
    }
    navigator.popUntil((route) => route.isFirst);
    final me = ApiClient.instance.session?.staffId;
    if (me != null && next.assigneeId != me) {
      await _showReassignedAlert();
      return;
    }
    navigator.push(
      CupertinoPageRoute<void>(
        builder: (_) => TaskScreen(roomNo: roomNo, taskId: taskId),
      ),
    );
  }

  Future<void> _showReassignedAlert() {
    return showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsAlert(
        message: _reassignedMessage,
        messageId: 'celebrate.reassigned-alert',
        actions: [
          TmsAlertAction(
            label: '知道了',
            id: 'celebrate.reassigned-alert.dismiss-button',
            style: TmsAlertActionStyle.primary,
            onPressed: () => Navigator.of(dialogContext).pop(),
          ),
        ],
      ),
    );
  }

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

  /// 指派不能寫成 `setState(() => _taskFuture = ...)`。箭頭函式會把那個
  /// `Future` 當成回傳值，Flutter 在 debug 版會拒絕回傳 `Future` 的回呼。
  void _showTask(TaskDetail task) {
    setState(() {
      _loadedTask = task;
      _taskFuture = Future<TaskDetail?>.value(task);
    });
  }

  Future<void> _showBusyAlert(ApiException e, {required String fallback}) async {
    if (!mounted) {
      return;
    }
    final message = e.errorMessage ?? fallback;
    return showCupertinoDialog<void>(
      context: context,
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

  static bool _canRepair(TaskDetail task) =>
      task.state != 'cancelled' && task.state != 'pool';

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

    final fabBottom = TmsFab.bottomAboveActionBar(
      hasActionBar: _primaryAction(task) != null,
      bottomSafeAreaInset: MediaQuery.paddingOf(context).bottom,
    );
    final canAsk = task.state != 'cancelled';
    return Column(
      children: [
        Expanded(
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              SingleChildScrollView(
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

  static const double _askTipWidth = 264;

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
      idPrefix: 'task.status-row.hold-reason',
      wrap: (node) {
        final reasonNode = TmsSemantics(
          id: 'task.status-row.hold-reason',
          label: holdReason,
          child: node,
        );
        return repaired
            ? TmsSemantics(id: 'task.resume-banner', child: reasonNode)
            : reasonNode;
      },
    );
  }

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

  Widget _buildAskButton(TaskDetail task) {
    return TmsSemantics(
      id: 'task.ask-button',
      child: TmsFab(
        glyph: TmsFabGlyph.ask,
        semanticsLabel: '查一下：查 SOP 或這間房的記錄',
        onPressed: () {
          setState(() => _askTipSeen = true);
          AskSheet.show(context, taskId: task.id, roomNo: widget.roomNo);
        },
      ),
    );
  }

  Widget _buildActionBar(TaskDetail task) {
    final primary = _primaryAction(task);
    return TmsActionBar(
      hint: _buildUncheckedHint(task),
      secondary: _buildSecondaryButton(task),
      primary: primary == null
          ? null
          : TmsSemantics(
              id: 'task.primary-button',
              child: TmsPillButton(
                label: primary.label,
                onPressed: primary.onPressed,
              ),
            ),
    );
  }

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

  Widget _buildHeader(TaskDetail task) {
    final doing = task.state == 'doing';
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

  String? _timerSubText(TaskDetail task) {
    final held = task.state == 'interrupted';
    final est = task.estMin;
    if (est == null) {
      return held ? '已暫停' : null;
    }
    return '${held ? '已暫停 · ' : ''}預估 $est 分';
  }

  Widget _buildStepTrack(TaskDetail task, {required bool onDark}) {
    final steps = _stepsOf(task);
    final current = _currentStep(task);
    final currentIndex = steps.indexOf(current);
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

  static List<String> _stepsOf(TaskDetail task) {
    if (task.type != 'cleaning') {
      return const <String>['已接', '進行中', '送達'];
    }
    final waived = task.inspectionWaived || task.inspection == 'waived';
    return <String>['已接', '進行中', '完成', waived ? '免查房' : '查房'];
  }

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
        ClipRRect(
          borderRadius: BorderRadius.circular(TmsRadius.pill),
          child: SizedBox(
            height: TmsSpacing.sm,
            child: ColoredBox(
              color: TmsColors.neutralContainer,
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

const String _reassignedMessage = '這一間已經不在你名下了';

class _CelebrateContent {
  const _CelebrateContent({
    required this.roomNo,
    required this.subline,
    required this.actualMinutes,
    required this.estimateMinutes,
    required this.todayNth,
    required this.nextStep,
    this.queued = false,
    this.celebrationEnabled = true,
  });

  final String? roomNo;
  final String subline;
  final int actualMinutes;
  final int? estimateMinutes;
  final int? todayNth;
  final TaskNextStep? nextStep;
  final bool queued;
  final bool celebrationEnabled;
}

String _celebrateSubline(TaskDetail task) {
  if (task.type != 'cleaning') {
    return '住客會收到完成通知';
  }
  final waived = task.inspectionWaived || task.inspection == 'waived';
  return waived ? '免查房，直接完成' : '已通知主管查房';
}

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

int? _workedSecondsNow(TaskDetail task, DateTime? now) {
  final base = task.accumulatedSeconds;
  if (base == null) {
    return null;
  }
  final startedAt = task.segmentStartedAt;
  if (startedAt == null) {
    return base;
  }
  final segment = (now ?? deviceNow()).difference(startedAt).inSeconds;
  return base + (segment > 0 ? segment : 0);
}

int? _overtimeMinutes(TaskDetail task, {DateTime? now}) {
  final estMin = task.estMin;
  if (estMin == null || estMin <= 0) {
    return null;
  }
  final seconds = _workedSecondsNow(task, now) ?? task.elapsedMinutes * 60;
  if (seconds <= estMin * 60) {
    return null;
  }
  return math.max(1, seconds ~/ 60 - estMin);
}

class TaskTimerHero extends StatefulWidget {
  const TaskTimerHero({
    super.key,
    required this.task,
    this.clock,
    this.onTick,
  });

  final TaskDetail task;

  final DateTime Function()? clock;

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


String taskStartLabel(TaskDetail task) =>
    task.type == 'cleaning' ? '開始打掃' : '開始處理';

String taskTitleText(TaskDetail task, String roomNo) {
  final title = task.title ?? '';
  final prefix = '$roomNo ';
  return title.startsWith(prefix) ? title.substring(prefix.length) : title;
}

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

  final String value;

  final VoidCallback onTap;

  final bool unread;

  final String? unreadId;

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

class _SopCheckGlyphPainter extends CustomPainter {
  const _SopCheckGlyphPainter();

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
