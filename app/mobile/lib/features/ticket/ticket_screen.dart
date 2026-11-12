import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/repair_photo_capture.dart';
import '../../core/tms_semantics.dart';
import '../../core/today_refresh.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_photo_action_sheet.dart';
import '../../core/widgets/tms_photo_viewer.dart';
import '../../core/widgets/tms_sheet.dart';
import 'repair_labels.dart';
import 'ticket_complete_sheet.dart';
import 'ticket_detail_engineering.dart';
import 'ticket_detail_housekeeping.dart';
import 'ticket_manager_action_bar.dart';
import 'ticket_verify_fail_sheet.dart';
import 'ticket_verify_result_card.dart';
import '../manager/manager_conflict_alert.dart';

/// 工務單詳情要給誰看。
///
/// 同一張單對報修的人、修的人與驗收的主管是三件不同的事，所以這個值由入口點
/// 明講，不讓畫面自己猜；每一種視角畫成哪一版只在 [TicketScreen] 決定。推播
/// 與通知中心是唯一事前不知道收件人身分的入口，那裡用 [TicketView.forSession]。
enum TicketView {
  /// 報修的人看自己送出的單。
  housekeeping,

  /// 工務看自己要修的單。
  engineering,

  /// 主管從待驗收清單、首頁「我名下的單」、推播或通知中心點開的單；通過與不通
  /// 過只在這個視角出現。
  manager;

  /// 依登入帳號決定視角：主管不分部門一律是主管視角（工務主管也是），其餘
  /// 依部門，和原型「同一個 App 只依部門切換」一致（`Main.dc.html:1909`）。
  static TicketView forSession(LoginResult? session) {
    if (session?.role == 'manager') {
      return TicketView.manager;
    }
    return session?.dept == 'engineering'
        ? TicketView.engineering
        : TicketView.housekeeping;
  }
}

/// 工務單詳情（ui-plan 畫面 7）。
///
/// 這一層只做三件事：跟後端要這張單、把會改狀態的操作送出去、再把後端回傳的
/// 那一筆交給對應視角去畫。狀態一律以後端回傳的為準，App 不自己推演。
class TicketScreen extends StatefulWidget {
  const TicketScreen({super.key, required this.view, this.taskId});

  final TicketView view;

  /// 工務單的任務 id；入口（任務頁、工務首頁單卡、開工單成功導頁、推播）帶
  /// 進來。null 時只顯示版面占位。
  final String? taskId;

  @override
  State<TicketScreen> createState() => _TicketScreenState();
}

class _TicketScreenState extends State<TicketScreen> {
  RepairTicketDetail? _ticket;
  String? _error;

  /// 本次在這個畫面完修時選的處理結果文字；由工務視角寫進狀態列。
  String? _resolutionLabel;

  /// 驗收不通過送出成功後後端回的結果；有值時整頁換成驗收結果卡。
  RepairVerificationOutcome? _verifyOutcome;

  /// 這次驗收撞上別人已處理（409 `state_conflict`）；等面板收起再提示。
  bool _verifyConflicted = false;

  /// 通過與不通過各自的 Idempotency-Key。同一個動作重送（沒收到回應再按一
  /// 次）帶同一把，後端才認得出是同一次驗收；兩種結果是兩件事，不共用。
  final Map<String, String> _verifyKeys = {};

  @override
  void initState() {
    super.initState();
    _load();
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

  /// 撤回這張單。按下去先問一次，因為撤回之後主管與工務就再也看不到它，員工
  /// 沒有辦法自己把單救回來（`Main.dc.html:4813`）。
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
      // 這張單的狀態變了，首頁那一份今日進度（手上張數、我的任務）因此變舊了。
      TodayRefresh.instance.request();
    } on ApiException catch (e) {
      _showError(e.errorMessage ?? failureMessage);
    } catch (_) {
      _showError(failureMessage);
    }
  }

  /// 認領與開始維修走的是任務 API，它們不回傳工務單詳情，所以做完重新讀一次。
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
    // 這張單的狀態變了，首頁那一份今日進度因此變舊了。
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

  /// 送出這次驗收。成功或撞單都回 null（結果記在狀態上），其他失敗回一句給
  /// 人看的話。
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
      if (mounted && outcome.result == 'failed') {
        setState(() => _verifyOutcome = outcome);
      }
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

  /// 按通過：直接送出，成功後回到開這一頁的清單（清單由那一端重取）。
  Future<void> _pass() async {
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
    Navigator.of(context).maybePop();
  }

  /// 按不通過：升起原因面板；面板收起後才處理撞單，對話框不會疊在面板上。
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
    if (mounted && _verifyConflicted) {
      await _showVerifyConflict();
    }
  }

  /// 單已被別的主管驗收或判不通過：說一聲，回清單。
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
      // 完修的單從首頁的進行中變成已完修、已完修張數也要加一。
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
    bool showWorkerActions = true,
    Widget? bottomBar,
  }) {
    return TicketDetailEngineering(
      ticket: _ticket,
      errorMessage: _error,
      resolutionLabel: _resolutionLabel,
      onClaim: _claim,
      onStart: _start,
      onReleaseToPool: _releaseToPool,
      onComplete: _openCompleteSheet,
      onOpenPhoto: _openViewer,
      showWorkerActions: showWorkerActions,
      bottomBar: bottomBar,
    );
  }

  /// 主管視角：工務版面，只換底部動作列。已完修時是通過與不通過；其他狀態只有
  /// 打開自己名下的單才給處理人的動作。不通過送出後整頁換成結果卡。
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
      showWorkerActions: ticket?.viewerIsAssignee ?? false,
      bottomBar: verifying
          ? TicketManagerActionBar(onPass: _pass, onFail: _openFailSheet)
          : null,
    );
  }
}
