import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/repair_photo_capture.dart';
import '../../core/system_status.dart';
import '../../core/tms_semantics.dart';
import '../../core/today_refresh.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_photo_action_sheet.dart';
import '../../core/widgets/tms_photo_viewer.dart';
import '../../core/widgets/tms_sheet.dart';
import '../../core/widgets/tms_toast.dart';
import 'repair_labels.dart';
import 'ticket_complete_sheet.dart';
import 'ticket_detail_engineering.dart';
import 'ticket_detail_housekeeping.dart';
import 'ticket_manager_action_bar.dart';
import 'ticket_verify_fail_sheet.dart';
import 'ticket_verify_result_card.dart';
import '../manager/manager_conflict_alert.dart';
import '../manager/manager_ids.dart';

enum TicketView {
  housekeeping,

  engineering,

  manager;

  static TicketView forSession(LoginResult? session) {
    if (session?.role == 'manager') {
      return TicketView.manager;
    }
    return session?.dept == 'engineering'
        ? TicketView.engineering
        : TicketView.housekeeping;
  }
}

/// 使用者是從哪個入口打開這張工務單的。
///
/// 主管驗收通過後要離開還是留下，取決於這個來源：從待驗收清單進來的人正在
/// 一張一張處理，通過後應該回到清單繼續下一張；從推播、通知中心或首頁進來
/// 的人只關心這一張，把他送回清單反而是把他丟到沒要求去的地方。
enum TicketEntry {
  /// 主管的待驗收清單。
  verifyQueue,

  /// 推播、通知中心、首頁等其他入口。
  elsewhere,
}

class TicketScreen extends StatefulWidget {
  const TicketScreen({
    super.key,
    required this.view,
    this.taskId,
    this.entry = TicketEntry.elsewhere,
  });

  final TicketView view;

  final String? taskId;

  final TicketEntry entry;

  @override
  State<TicketScreen> createState() => _TicketScreenState();
}

class _TicketScreenState extends State<TicketScreen> {
  RepairTicketDetail? _ticket;
  String? _error;

  String? _resolutionLabel;

  RepairVerificationOutcome? _verifyOutcome;

  RepairVerificationOutcome? _pendingVerifyOutcome;

  bool _verifyConflicted = false;

  final Map<String, String> _verifyKeys = {};

  TmsToastHandle? _toast;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _toast?.dismiss();
    super.dispose();
  }

  Future<void> _load() async {
    final taskId = widget.taskId;
    if (taskId == null) {
      return;
    }
    try {
      final ticket = await ApiClient.instance.readRepairTicket(taskId);
      if (mounted) {
        setState(() {
          _ticket = ticket;
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = '載入工務單失敗，請稍後再試。';
        });
      }
    }
  }

  Future<void> _confirmWithdraw() async {
    final ticket = _ticket;
    if (ticket == null) {
      return;
    }
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsAlert(
        title: '撤回 ${ticket.ticketNo}？',
        message: '撤回後主管與工務都不會再看到這張單。',
        actions: [
          TmsAlertAction(
            label: '取消',
            onPressed: () => Navigator.of(dialogContext).pop(),
          ),
          TmsAlertAction(
            label: '撤回',
            style: TmsAlertActionStyle.destructive,
            onPressed: () {
              Navigator.of(dialogContext).pop();
              _withdraw();
            },
          ),
        ],
      ),
    );
  }

  Future<void> _withdraw() => _mutate(
    () => ApiClient.instance.withdrawRepairTicket(widget.taskId!),
    '撤回失敗，請稍後再試。',
  );

  Future<void> _releaseToPool() => _mutate(
    () => ApiClient.instance.releaseRepairTicketToPool(widget.taskId!),
    '放回待認領失敗，請稍後再試。',
  );

  Future<void> _mutate(
    Future<RepairTicketDetail> Function() action,
    String failureMessage,
  ) async {
    if (widget.taskId == null) {
      return;
    }
    try {
      final ticket = await action();
      if (mounted) {
        setState(() => _ticket = ticket);
      }
      TodayRefresh.instance.request();
    } on ApiException catch (e) {
      _showError(e.errorMessage ?? failureMessage);
    } catch (_) {
      _showError(failureMessage);
    }
  }

  Future<void> _runThenReload(
    Future<void> Function() action,
    String failureMessage,
  ) async {
    if (widget.taskId == null) {
      return;
    }
    try {
      await action();
    } on ApiException catch (e) {
      _showError(e.errorMessage ?? failureMessage);
      return;
    } catch (_) {
      _showError(failureMessage);
      return;
    }
    TodayRefresh.instance.request();
    await _load();
  }

  Future<void> _claim() => _runThenReload(
    () => ApiClient.instance.claimTask(widget.taskId!),
    '認領失敗，請稍後再試。',
  );

  Future<void> _start() => _runThenReload(
    () => ApiClient.instance.startTask(widget.taskId!),
    '開始維修失敗，請稍後再試。',
  );

  Future<String?> _sendVerify(String result, {String? reason}) async {
    final ticket = _ticket;
    if (ticket == null) {
      return '工務單還沒載入，請稍後再試。';
    }
    final key = _verifyKeys[result] ??= ApiClient.newIdempotencyKey();
    try {
      final outcome = await ApiClient.instance.verifyRepairTicket(
        taskId: ticket.taskId,
        result: result,
        reason: reason,
        idempotencyKey: key,
      );
      _verifyKeys.clear();
      _pendingVerifyOutcome = outcome;
      return null;
    } on ApiException catch (e) {
      if (e.statusCode == 409) {
        _verifyKeys.clear();
        _verifyConflicted = true;
        return null;
      }
      return e.errorMessage ?? '送出失敗，請稍後再試。';
    } catch (_) {
      return '送出失敗，請稍後再試。';
    }
  }

  Future<void> _pass() async {
    if (ConnectionStatus.instance.isOffline) {
      await showOfflineBlockedAlert(context);
      return;
    }
    final error = await _sendVerify('passed');
    if (!mounted) {
      return;
    }
    if (_verifyConflicted) {
      await _showVerifyConflict();
      return;
    }
    if (error != null) {
      _showError(error);
      return;
    }
    final outcome = _pendingVerifyOutcome;
    _pendingVerifyOutcome = null;
    final ticketNo = outcome?.ticketNo ?? _ticket?.ticketNo ?? '';
    if (widget.entry == TicketEntry.verifyQueue) {
      Navigator.of(context).pop(ticketNo);
      return;
    }
    TodayRefresh.instance.request();
    await _load();
    if (!mounted) {
      return;
    }
    _toast?.dismiss();
    _toast = TmsToast.show(
      context,
      '$ticketNo 已結案',
      id: ManagerIds.ticketPassedToast,
    );
  }

  Future<void> _openFailSheet() async {
    final ticket = _ticket;
    if (ticket == null) {
      return;
    }
    await TmsSheetRoute.show<void>(
      context,
      (_) => TicketVerifyFailSheet(
        ticketNo: ticket.ticketNo,
        onSubmit: (reason) => _sendVerify('failed', reason: reason),
      ),
    );
    if (!mounted) {
      return;
    }
    final outcome = _pendingVerifyOutcome;
    _pendingVerifyOutcome = null;
    // 只有不通過才覆蓋驗收結果卡：那張卡的標題寫死「退回重修」，通過的 outcome
    // 一旦走到這裡會變成「通過卻顯示退回」。通過路徑自己會清掉暫存值，這裡再擋一次。
    if (outcome != null && outcome.result == 'failed') {
      setState(() => _verifyOutcome = outcome);
    }
    if (_verifyConflicted) {
      await _showVerifyConflict();
    }
  }

  Future<void> _showVerifyConflict() async {
    _verifyConflicted = false;
    await showManagerConflictAlert(context, message: '可能是另一位主管剛驗收過。');
    if (mounted) {
      Navigator.of(context).maybePop();
    }
  }

  void _showError(String message) {
    if (!mounted) {
      return;
    }
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsAlert(
        message: message,
        actions: [
          TmsAlertAction(
            label: '知道了',
            style: TmsAlertActionStyle.primary,
            onPressed: () => Navigator.of(dialogContext).pop(),
          ),
        ],
      ),
    );
  }

  Future<void> _openCompleteSheet() async {
    final submitted = await TmsSheetRoute.show<String>(
      context,
      (_) => TicketCompleteSheet(
        taskId: widget.taskId!,
        roomNo: _ticket?.roomNo ?? '',
      ),
    );
    if (submitted != null && mounted) {
      setState(() {
        _resolutionLabel = submitted;
      });
      TodayRefresh.instance.request();
      await _load();
    }
  }

  void _openViewer(int index) {
    final ticket = _ticket;
    if (ticket == null) {
      return;
    }
    showTmsPhotoViewer(
      context,
      title: '${ticket.ticketNo} · ${repairCategoryText(ticket.category)}',
      initialIndex: index,
      photos: [
        for (var i = 0; i < ticket.photoCount; i++)
          TmsViewerPhoto(description: '${ticket.ticketNo} 照片 ${i + 1}'),
      ],
    );
  }

  Future<void> _appendPhoto() async {
    final taskId = widget.taskId;
    if (taskId == null) {
      return;
    }
    final source = await showTmsPhotoActionSheet(context);
    if (source == null || !mounted) {
      return;
    }
    try {
      final photoId = await captureAndUploadRepairPhoto(purpose: 'report');
      await ApiClient.instance.appendRepairTicketPhotos(
        taskId,
        photoIds: [photoId],
      );
      await _load();
    } on ApiException catch (e) {
      _showError(e.errorMessage ?? '補照片失敗，請稍後再試。');
    } catch (_) {
      _showError('補照片失敗，請稍後再試。');
    }
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'ticket.screen',
      child: switch (widget.view) {
        TicketView.housekeeping => TicketDetailHousekeeping(
          ticket: _ticket,
          errorMessage: _error,
          onWithdraw: _confirmWithdraw,
          onAddPhoto: _appendPhoto,
          onOpenPhoto: _openViewer,
        ),
        TicketView.engineering => _engineeringLayout(),
        TicketView.manager => _managerLayout(),
      },
    );
  }

  Widget _engineeringLayout({
    bool managerView = false,
    bool showWorkerActions = true,
    Widget? bottomBar,
  }) {
    return TicketDetailEngineering(
      ticket: _ticket,
      errorMessage: _error,
      resolutionLabel: _resolutionLabel,
      managerView: managerView,
      onClaim: _claim,
      onStart: _start,
      onReleaseToPool: _releaseToPool,
      onComplete: _openCompleteSheet,
      onOpenPhoto: _openViewer,
      showWorkerActions: showWorkerActions,
      bottomBar: bottomBar,
    );
  }

  Widget _managerLayout() {
    final outcome = _verifyOutcome;
    if (outcome != null) {
      return TicketVerifyResultCard(
        outcome: outcome,
        onBack: () => Navigator.of(context).maybePop(),
      );
    }
    final ticket = _ticket;
    final verifying =
        ticket != null && TicketManagerActionBar.shows(ticket.state);
    return _engineeringLayout(
      managerView: true,
      showWorkerActions: ticket?.viewerIsAssignee ?? false,
      bottomBar: verifying
          ? TicketManagerActionBar(onPass: _pass, onFail: _openFailSheet)
          : null,
    );
  }
}
