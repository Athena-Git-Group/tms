import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/repair_photo_capture.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_photo_action_sheet.dart';
import '../../core/widgets/tms_photo_cell.dart';
import '../../core/widgets/tms_photo_viewer.dart';
import '../../core/widgets/tms_selection_chip.dart';
import '../../core/widgets/tms_step_track.dart';
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

  /// 撤回這張單。後端是唯一的狀態真相，撤回後的狀態直接用它回傳的那一筆。
  Future<void> _withdraw() async {
    await _mutate(() => ApiClient.instance.withdrawRepairTicket(widget.taskId!),
        '撤回失敗，請稍後再試。');
  }

  /// 補資料後重新送審。
  Future<void> _resubmit() async {
    await _mutate(() => ApiClient.instance.resubmitRepairTicket(widget.taskId!),
        '重新送出失敗，請稍後再試。');
  }

  /// 對這張單做一次會改狀態的操作，並以後端回傳的那一筆換掉畫面。
  Future<void> _mutate(
      Future<RepairTicketDetail> Function() action, String failureMessage) async {
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
    final submitted = await showCupertinoModalPopup<String>(
      context: context,
      builder: (_) => _CompleteSheet(
        taskId: widget.taskId!,
        roomNo: _ticket?.roomNo ?? '',
      ),
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
        child: TmsCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '工務單 ${ticket.ticketNo} · ${ticket.roomNo}',
                style: TmsText.headline,
              ),
              const SizedBox(height: TmsSpacing.sm),
              Text(summaryText, style: TmsText.body),
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
      const SizedBox(height: TmsSpacing.md2),
      TmsStepTrack(
        steps: [
          for (var i = 0; i < _steps.length; i++) _step(i, currentStep),
        ],
      ),
      if (ticket.rejectReason != null)
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: TmsSemantics(
            id: 'ticket.reject-reason-label',
            label: '主管未核准：${ticket.rejectReason}',
            child: ExcludeSemantics(
              child: Text('主管未核准：${ticket.rejectReason}'),
            ),
          ),
        ),
      if (ticket.urgentDowngradeReason != null)
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: TmsSemantics(
            id: 'ticket.urgent-downgrade-reason-label',
            label: '已降為一般件：${ticket.urgentDowngradeReason}',
            child: ExcludeSemantics(
              child: Text('已降為一般件：${ticket.urgentDowngradeReason}'),
            ),
          ),
        ),
      const SizedBox(height: TmsSpacing.md),
      ..._photoSection(ticket),
      const SizedBox(height: 24),
      // 撤回只在工務還沒認領前成立（契約 withdrawRepairTicket：pool 與
      // pending_review 才收）。已被認領的 `already_claimed` 直接表現為按鈕
      // 不存在——先顯示再讓員工點了才報 409，是拿錯誤訊息當說明書。
      if (ticket.state == 'pool' || ticket.state == 'pending_review')
        TmsSemantics(
          id: 'ticket.withdraw-button',
          child: CupertinoButton(
            onPressed: _withdraw,
            child: const Text('撤回'),
          ),
        ),
      // 補資料再送出只有主管未核准（rejected）時才有意義，其他狀態不顯示。
      if (ticket.state == 'rejected')
        TmsSemantics(
          id: 'ticket.resubmit-button',
          child: CupertinoButton.filled(
            onPressed: _resubmit,
            child: const Text('補資料再送出'),
          ),
        ),
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

  /// 能不能補照片看的是後端收不收：契約 appendRepairTicketPhotos 在單結案後
  /// 回 409，所以這裡用同一條界線決定要不要畫出「補照片」格，不另外猜一套。
  List<Widget> _photoSection(RepairTicketDetail ticket) {
    final canAdd = ticket.state != 'done' && ticket.state != 'cancelled';
    final photos = ticket.photos;
    if (photos.isEmpty && !canAdd) {
      return const [];
    }
    final head = '照片（${photos.length} 張，含浮水印）';
    return [
      TmsSemantics(
        id: 'ticket.photo.count-label',
        label: head,
        child: Padding(
          padding: const EdgeInsets.only(left: TmsSpacing.screenEdge),
          child: ExcludeSemantics(
            child: Text(head, style: TmsText.footnote),
          ),
        ),
      ),
      const SizedBox(height: TmsSpacing.xs2),
      TmsCard(
        padding: const EdgeInsets.all(TmsSpacing.md),
        child: GridView.count(
          crossAxisCount: 3,
          mainAxisSpacing: TmsSpacing.sm,
          crossAxisSpacing: TmsSpacing.sm,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: [
            for (var i = 0; i < photos.length; i++)
              TmsSemantics(
                id: 'ticket.photo.${i + 1}',
                child: TmsPhotoCell(
                  edge: null,
                  semanticsLabel: '放大 ${ticket.ticketNo} 照片 ${i + 1}',
                  onTap: () => _openViewer(ticket, i),
                ),
              ),
            if (canAdd)
              TmsSemantics(
                id: 'ticket.photo.add-button',
                child: TmsAddPhotoButton.inGrid(
                  label: '補照片',
                  onTap: _appendPhoto,
                ),
              ),
          ],
        ),
      ),
    ];
  }

  void _openViewer(RepairTicketDetail ticket, int index) {
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
      await ApiClient.instance
          .appendRepairTicketPhotos(taskId, photoIds: [photoId]);
      await _load();
    } on ApiException catch (e) {
      _showError(e.errorMessage ?? '補照片失敗，請稍後再試。');
    } catch (_) {
      _showError('補照片失敗，請稍後再試。');
    }
  }

  /// 一步的畫面：外層節點只在「這一步是目前步驟」時掛 `--current` id，
  /// 內層節點（固定 id＋步驟名）給步驟順序的斷言用。
  ///
  /// 還沒有人接單時 [currentIndex] 是 -1，四步就全部是未到——這時步驟條上沒有
  /// 任何一步是目前步驟，`--current` 也不會出現在畫面上。
  TmsStepTrackStep _step(int index, int currentIndex) {
    final TmsStepStatus status;
    if (index < currentIndex) {
      status = TmsStepStatus.done;
    } else if (index == currentIndex) {
      status = TmsStepStatus.current;
    } else {
      status = TmsStepStatus.pending;
    }
    return TmsStepTrackStep(
      label: _steps[index],
      status: status,
      wrap: (node) {
        final Widget step = TmsSemantics(
          id: 'ticket.stepper.${index + 1}',
          label: _steps[index],
          child: node,
        );
        if (status != TmsStepStatus.current) {
          return step;
        }
        return TmsSemantics(
          id: 'ticket.stepper.${index + 1}--current',
          child: step,
        );
      },
    );
  }
}

/// 完修回報面板（雛形的 `completeSheet`）：拍完修照片、選完修三選一、送出。
/// 送出走 `POST /tasks/{id}/complete`，成功時把選到的處理結果文字回傳給
/// 詳情頁顯示。
class _CompleteSheet extends StatefulWidget {
  const _CompleteSheet({required this.taskId, required this.roomNo});

  final String taskId;

  /// 這張單的房號；完修照片的浮水印用它。
  final String roomNo;

  @override
  State<_CompleteSheet> createState() => _CompleteSheetState();
}

class _CompleteSheetState extends State<_CompleteSheet> {
  /// 契約 CompleteTaskRequest.photoIds 的上下限：1-3 張。
  static const int _maxPhotos = 3;

  final List<RepairPhoto> _photos = [];
  String? _selectedResolution;
  bool _submitting = false;
  String? _error;

  /// 已經上傳成功、可以引用進完修回報的照片。
  List<String> get _uploadedPhotoIds =>
      [for (final photo in _photos) if (photo.photoId != null) photo.photoId!];

  bool get _allPhotosUploaded =>
      _photos.isNotEmpty && _uploadedPhotoIds.length == _photos.length;

  Future<void> _addPhoto() async {
    if (_photos.length >= _maxPhotos) {
      return;
    }
    final source = await showTmsPhotoActionSheet(context);
    if (source == null || !mounted) {
      return;
    }
    final photo = RepairPhoto(
      watermark: repairPhotoWatermark(
        roomNo: widget.roomNo,
        takenAt: DateTime.now(),
      ),
    );
    setState(() {
      _photos.add(photo);
      _error = null;
    });
    await _upload(photo);
  }

  /// 把這一張送上去，並把結果記在它自己身上，讓員工只重傳失敗的那一張。
  Future<void> _upload(RepairPhoto photo) async {
    setState(() => photo.uploading = true);
    try {
      photo.photoId =
          await captureAndUploadRepairPhoto(purpose: 'completion');
      photo.uploadFailed = false;
    } catch (_) {
      photo.uploadFailed = true;
    }
    photo.uploading = false;
    if (mounted) {
      setState(() {});
    }
  }

  void _removePhoto(int index) {
    setState(() {
      _photos.removeAt(index);
      _error = null;
    });
  }

  Future<void> _submit() async {
    final resolution = _selectedResolution;
    if (resolution == null || !_allPhotosUploaded || _submitting) {
      return;
    }
    setState(() {
      _submitting = true;
    });
    try {
      await ApiClient.instance.completeTask(
        widget.taskId,
        photoIds: _uploadedPhotoIds,
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
        _selectedResolution != null && _allPhotosUploaded && !_submitting;
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
            _photoCard(),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              children: [
                for (final resolution in repairResolutions)
                  TmsSemantics(
                    id: 'completeSheet.result.${resolution.key}',
                    child: TmsSelectionChip(
                      label: resolution.label,
                      selected: _selectedResolution == resolution.key,
                      onTap: () {
                        setState(() {
                          _selectedResolution = resolution.key;
                        });
                      },
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

  /// 一張照片都還沒有時，卡片下面多一行說明，告訴員工沒有相機權限時還可以
  /// 從相簿挑。不說的話，相機被關掉的人會以為自己回報不了完修。
  Widget _photoCard() {
    const label = '完修照片（必填，至少 1 張、最多 3 張）';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        TmsCard(
          style: TmsCardStyle.inSheet,
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.md2,
            TmsSpacing.md,
            TmsSpacing.md2,
            TmsSpacing.md,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.baseline,
                textBaseline: TextBaseline.alphabetic,
                children: [
                  const Expanded(
                    child: TmsSemantics(
                      id: 'completeSheet.photo.count-label',
                      label: label,
                      child: ExcludeSemantics(
                        child: Text(label, style: TmsText.footnoteEmphasized),
                      ),
                    ),
                  ),
                  Text(
                    '${_photos.length} / $_maxPhotos',
                    style: TmsText.caption1.copyWith(
                      fontWeight: FontWeight.w400,
                      color: TmsColors.secondaryLabel,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: TmsSpacing.sm2),
              Wrap(
                spacing: TmsSpacing.sm,
                runSpacing: TmsSpacing.sm,
                children: [
                  for (var i = 0; i < _photos.length; i++) _thumbnail(i),
                  if (_photos.length < _maxPhotos)
                    TmsSemantics(
                      id: 'completeSheet.photo.add-button',
                      child: TmsAddPhotoButton(
                        label: '加入照片',
                        onTap: _addPhoto,
                      ),
                    ),
                ],
              ),
            ],
          ),
        ),
        if (_photos.isEmpty)
          const Padding(
            padding: EdgeInsets.only(
              top: TmsSpacing.sm,
              left: TmsSpacing.xs,
              right: TmsSpacing.xs,
            ),
            child: Text(
              '修繕完成必須拍照。沒有相機權限時，可以從相簿選擇。',
              style: TmsText.footnote,
            ),
          ),
      ],
    );
  }

  /// 一張完修照片的縮圖。序號從 1 算起，與 dsl 的句型一致。
  Widget _thumbnail(int index) {
    final photo = _photos[index];
    final statusText = photo.uploadFailed
        ? '上傳失敗'
        : (photo.photoId == null ? '上傳中' : '已上傳');
    final TmsPhotoUpload upload;
    if (photo.uploadFailed) {
      upload = const TmsPhotoFailed();
    } else if (photo.photoId != null) {
      upload = const TmsPhotoUploaded();
    } else {
      upload = const TmsPhotoUploading(0);
    }
    return TmsSemantics(
      id: 'completeSheet.photo.${index + 1}.upload-status',
      label: statusText,
      child: TmsSemantics(
        id: 'completeSheet.photo.${index + 1}.watermark-label',
        label: photo.watermark,
        child: TmsPhotoCell(
          upload: upload,
          onRemove: photo.uploading ? null : () => _removePhoto(index),
          onRetry: () => _upload(photo),
        ),
      ),
    );
  }
}
