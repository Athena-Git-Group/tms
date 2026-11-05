import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/repair_photo_capture.dart';
import '../../core/tms_semantics.dart';
import 'repair_labels.dart';

/// 工務單詳情（ui-plan 畫面 7，工務視角）。
///
/// 資料來源是 `GET /repair-tickets/{taskId}`；畫面上的狀態摘要卡、四步
/// 步驟條、放回待認領與完修都直接呈現或改寫後端狀態，App 不自己推演狀態。
class TicketScreen extends StatefulWidget {
  const TicketScreen({super.key, this.taskId});

  /// 工務單的任務 id；入口（工務首頁單卡、開工單成功導頁）帶進來。
  /// 房務端入口（任務頁）還沒接資料，null 時只顯示版面占位。
  final String? taskId;

  @override
  State<TicketScreen> createState() => _TicketScreenState();
}

class _TicketScreenState extends State<TicketScreen> {
  /// 步驟條固定四步；目前步驟由後端狀態對照（見 [_currentStepIndex]）。
  static const List<String> _steps = ['待開始', '維修中', '完修', '驗收'];

  RepairTicketDetail? _ticket;
  String? _error;

  /// 本次在這個畫面完修時選的處理結果文字。後端的工務單詳情沒有回傳
  /// resolutionType（只有 resolutionNote，上游缺口已回報），所以「結果為
  /// 暫時處理」這類完修當下的回饋由送出的那一次選擇呈現。
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

  /// 目前在四步中的哪一步；對照 dsl 的狀態對照表（待開始＝已接單、維修中、
  /// 完修＝已完修未驗收、驗收＝已驗收）。待認領等其他狀態沒有「目前步驟」。
  int _currentStepIndex(RepairTicketDetail ticket) {
    switch (ticket.state) {
      case 'accepted':
        return 0;
      case 'doing':
        return 1;
      case 'done':
        return ticket.verifiedBy == null ? 2 : 3;
      default:
        return -1;
    }
  }

  Future<void> _releaseToPool() async {
    final taskId = widget.taskId;
    if (taskId == null) {
      return;
    }
    try {
      final ticket =
          await ApiClient.instance.releaseRepairTicketToPool(taskId);
      if (mounted) {
        setState(() {
          _ticket = ticket;
        });
      }
    } on ApiException catch (e) {
      _showError(e.errorMessage ?? '放回待認領失敗，請稍後再試。');
    } catch (_) {
      _showError('放回待認領失敗，請稍後再試。');
    }
  }

  void _showError(String message) {
    if (!mounted) {
      return;
    }
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => CupertinoAlertDialog(
        content: Text(message),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('知道了'),
          ),
        ],
      ),
    );
  }

  Future<void> _openCompleteSheet() async {
    final submitted = await showCupertinoModalPopup<String>(
      context: context,
      builder: (_) => _CompleteSheet(taskId: widget.taskId!),
    );
    if (submitted != null) {
      setState(() {
        _resolutionLabel = submitted;
      });
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final ticket = _ticket;
    return TmsSemantics(
      id: 'ticket.screen',
      child: CupertinoPageScaffold(
        navigationBar: const CupertinoNavigationBar(middle: Text('工務單詳情')),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              if (_error != null) Text(_error!),
              if (ticket == null && _error == null)
                const Center(child: CupertinoActivityIndicator()),
              if (ticket != null) ..._ticketBody(ticket),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _ticketBody(RepairTicketDetail ticket) {
    final summaryText = repairStatusSummaryText(ticket);
    final currentStep = _currentStepIndex(ticket);
    final resolutionLabel = _resolutionLabel;
    return [
      TmsSemantics(
        id: 'ticket.status-summary-card',
        label: summaryText,
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: CupertinoColors.white,
            border: Border.all(color: CupertinoColors.systemGrey4),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '工務單 ${ticket.ticketNo} · ${ticket.roomNo}',
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Text(summaryText),
            ],
          ),
        ),
      ),
      if (resolutionLabel != null)
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: TmsSemantics(
            id: 'ticket.resolution-label',
            label: '處理結果：$resolutionLabel',
            child: Text('處理結果：$resolutionLabel'),
          ),
        ),
      const SizedBox(height: 16),
      Row(
        children: [
          for (var i = 0; i < _steps.length; i++)
            Expanded(child: _stepTile(i, currentStep)),
        ],
      ),
      const SizedBox(height: 24),
      if (ticket.isPublicArea &&
          (ticket.state == 'accepted' || ticket.state == 'doing'))
        TmsSemantics(
          id: 'ticket.release-to-pool-button',
          child: CupertinoButton(
            onPressed: _releaseToPool,
            child: const Text('放回待認領'),
          ),
        ),
      if (ticket.state == 'doing')
        TmsSemantics(
          id: 'ticket.complete-button',
          child: CupertinoButton.filled(
            onPressed: _openCompleteSheet,
            child: const Text('完修'),
          ),
        ),
    ];
  }

  /// 一步的畫面：外層節點只在「這一步是目前步驟」時掛 `--current` id，
  /// 內層節點（固定 id＋步驟名）給步驟順序的斷言用。
  Widget _stepTile(int index, int currentIndex) {
    final Widget step = TmsSemantics(
      id: 'ticket.stepper.${index + 1}',
      label: _steps[index],
      child: Column(
        children: [
          Container(
            width: 24,
            height: 24,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: index <= currentIndex
                  ? CupertinoColors.activeBlue
                  : CupertinoColors.systemGrey4,
            ),
          ),
          const SizedBox(height: 4),
          Text(_steps[index], style: const TextStyle(fontSize: 12)),
        ],
      ),
    );
    if (index != currentIndex) {
      return step;
    }
    return TmsSemantics(
      id: 'ticket.stepper.${index + 1}--current',
      child: step,
    );
  }
}

/// 完修回報面板（雛形的 `completeSheet`）：拍完修照片、選完修三選一、送出。
/// 送出走 `POST /tasks/{id}/complete`，成功時把選到的處理結果文字回傳給
/// 詳情頁顯示。
class _CompleteSheet extends StatefulWidget {
  const _CompleteSheet({required this.taskId});

  final String taskId;

  @override
  State<_CompleteSheet> createState() => _CompleteSheetState();
}

class _CompleteSheetState extends State<_CompleteSheet> {
  final List<String> _photoIds = [];
  String? _selectedResolution;
  bool _submitting = false;
  String? _error;

  Future<void> _addPhoto() async {
    try {
      final photoId =
          await captureAndUploadRepairPhoto(purpose: 'completion');
      if (mounted) {
        setState(() {
          _photoIds.add(photoId);
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = '照片上傳失敗，請再試一次。';
        });
      }
    }
  }

  Future<void> _submit() async {
    final resolution = _selectedResolution;
    if (resolution == null || _photoIds.isEmpty || _submitting) {
      return;
    }
    setState(() {
      _submitting = true;
    });
    try {
      await ApiClient.instance.completeTask(
        widget.taskId,
        photoIds: _photoIds,
        resolutionType: resolution,
      );
      if (mounted) {
        final label = repairResolutions
            .firstWhere((entry) => entry.key == resolution)
            .label;
        Navigator.of(context).pop(label);
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = e.errorMessage ?? '完修回報失敗，請稍後再試。';
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = '完修回報失敗，請稍後再試。';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final canSubmit =
        _selectedResolution != null && _photoIds.isNotEmpty && !_submitting;
    return Container(
      color: CupertinoColors.systemBackground,
      padding: const EdgeInsets.all(16),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('完修回報',
                style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            Row(
              children: [
                TmsSemantics(
                  id: 'completeSheet.photo.add-button',
                  child: CupertinoButton(
                    onPressed: _addPhoto,
                    child: const Text('拍完修照片'),
                  ),
                ),
                Text('已拍 ${_photoIds.length} 張'),
              ],
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              children: [
                for (final resolution in repairResolutions)
                  TmsSemantics(
                    id: 'completeSheet.result.${resolution.key}',
                    child: GestureDetector(
                      onTap: () {
                        setState(() {
                          _selectedResolution = resolution.key;
                        });
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 8),
                        decoration: BoxDecoration(
                          color: _selectedResolution == resolution.key
                              ? CupertinoColors.activeBlue
                              : CupertinoColors.systemGrey5,
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Text(
                          resolution.label,
                          style: TextStyle(
                            color: _selectedResolution == resolution.key
                                ? CupertinoColors.white
                                : CupertinoColors.black,
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  _error!,
                  style:
                      const TextStyle(color: CupertinoColors.destructiveRed),
                ),
              ),
            const SizedBox(height: 16),
            TmsSemantics(
              id: 'completeSheet.submit-button',
              enabled: canSubmit,
              child: CupertinoButton.filled(
                onPressed: canSubmit ? _submit : null,
                child: _submitting
                    ? const CupertinoActivityIndicator()
                    : const Text('送出'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
