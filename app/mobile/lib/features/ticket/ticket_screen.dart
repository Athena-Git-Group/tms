import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/repair_photo_capture.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_photo_action_sheet.dart';
import '../../core/widgets/tms_photo_viewer.dart';
import '../../core/widgets/tms_sheet.dart';
import 'repair_labels.dart';
import 'ticket_complete_sheet.dart';
import 'ticket_detail_engineering.dart';
import 'ticket_detail_housekeeping.dart';

/// 工務單詳情要給誰看。
///
/// 同一張單對報修的人與修的人是兩件不同的事，兩邊的版面也完全不同
/// （`Main.dc.html:1746` 與 `1911`），所以這個值由入口點明講，不讓畫面自己猜。
/// 推播是唯一事前不知道收件人身分的入口，那裡依登入帳號的部門決定。
enum TicketView {
  /// 報修的人看自己送出的單。
  housekeeping,

  /// 工務看自己要修的單。
  engineering,
}

/// 工務單詳情（ui-plan 畫面 7）。
///
/// 這一層只做三件事：跟後端要這張單、把會改狀態的操作送出去、再把後端回傳的
/// 那一筆交給對應視角去畫。狀態一律以後端回傳的為準，App 不自己推演。
class TicketScreen extends StatefulWidget {
  const TicketScreen({super.key, required this.view, this.taskId});

  /// 這一頁要畫成哪一種視角。
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

  Future<void> _resubmit() => _mutate(
    () => ApiClient.instance.resubmitRepairTicket(widget.taskId!),
    '重新送出失敗，請稍後再試。',
  );

  Future<void> _releaseToPool() => _mutate(
    () => ApiClient.instance.releaseRepairTicketToPool(widget.taskId!),
    '放回待認領失敗，請稍後再試。',
  );

  /// 對這張單做一次會改狀態的操作，並以後端回傳的那一筆換掉畫面。
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
        for (var i = 0; i < ticket.photos.length; i++)
          TmsViewerPhoto(description: '${ticket.ticketNo} 照片 ${i + 1}'),
      ],
    );
  }

  /// 補一張照片到這張單上。
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
          onResubmit: _resubmit,
          onAddPhoto: _appendPhoto,
          onOpenPhoto: _openViewer,
        ),
        TicketView.engineering => TicketDetailEngineering(
          ticket: _ticket,
          errorMessage: _error,
          resolutionLabel: _resolutionLabel,
          onClaim: _claim,
          onStart: _start,
          onReleaseToPool: _releaseToPool,
          onComplete: _openCompleteSheet,
          onOpenPhoto: _openViewer,
        ),
      },
    );
  }
}
