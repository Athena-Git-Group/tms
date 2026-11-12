import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/repair_photo_capture.dart';
import '../../core/test_backdoor.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_option_row.dart';
import '../../core/widgets/tms_photo_action_sheet.dart';
import '../../core/widgets/tms_photo_cell.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_sheet.dart';
import '../ticket/repair_labels.dart';

class RepairSheet extends StatefulWidget {
  const RepairSheet({
    super.key,
    required this.roomNo,
    required this.sourceTaskId,
    required this.onClose,
    this.interruptSourceTask = false,
    this.onBack,
  });

  final String roomNo;

  final String? sourceTaskId;

  final VoidCallback onClose;

  final bool interruptSourceTask;

  final VoidCallback? onBack;

  @override
  State<RepairSheet> createState() => _RepairSheetState();
}

class _SubmitAction {
  const _SubmitAction({
    required this.label,
    required this.enabled,
    required this.style,
  });

  final String label;
  final bool enabled;
  final TmsPillButtonStyle style;
}

class _RepairSheetState extends State<RepairSheet> {
  static const int _maxPhotos = 3;

  static const int _maxDescriptionLength = 60;

  final List<RepairPhoto> _photos = [];

  final TextEditingController _descriptionController = TextEditingController();

  String? _category;

  RepairTicketPrecheck? _precheck;

  bool _submitting = false;

  bool _photoLimitHit = false;

  @override
  void initState() {
    super.initState();
    _descriptionController.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _pickCategory(String categoryKey) async {
    setState(() {
      _category = categoryKey;
      _precheck = null;
    });
    final sourceTaskId = widget.sourceTaskId;
    if (sourceTaskId == null) {
      return;
    }
    try {
      final precheck = await ApiClient.instance.precheckRepairTicket(
        sourceTaskId: sourceTaskId,
        category: categoryKey,
      );
      if (mounted) {
        setState(() => _precheck = precheck);
      }
    } catch (_) {
    }
  }

  Future<void> _addPhoto() async {
    if (_photos.length >= _maxPhotos) {
      setState(() => _photoLimitHit = true);
      return;
    }
    final source = await showTmsPhotoActionSheet(context);
    if (source == null || !mounted) {
      return;
    }
    setState(() {
      _photos.add(RepairPhoto(
        watermark: repairPhotoWatermark(
          roomNo: widget.roomNo,
          takenAt: deviceNow(),
        ),
      ));
    });
  }

  void _removePhoto(int index) {
    setState(() {
      _photos.removeAt(index);
      _photoLimitHit = false;
    });
  }

  Future<void> _uploadPhotos({bool rethrowFailure = false}) async {
    for (var i = 0; i < _photos.length; i++) {
      final photo = _photos[i];
      if (photo.photoId != null) {
        continue;
      }
      try {
        photo.photoId = await captureAndUploadRepairPhoto(
          purpose: 'report',
          photoIndex: i + 1,
        );
        photo.uploadFailed = false;
      } catch (_) {
        photo.uploadFailed = true;
        if (rethrowFailure) {
          rethrow;
        }
      }
    }
    if (mounted) {
      setState(() {});
    }
  }

  Future<void> _retryPhoto(int index) async {
    final photo = _photos[index];
    try {
      photo.photoId = await captureAndUploadRepairPhoto(
        purpose: 'report',
        photoIndex: index + 1,
      );
      photo.uploadFailed = false;
    } catch (_) {
      photo.uploadFailed = true;
    }
    if (mounted) {
      setState(() {});
    }
  }

  Future<void> _submit() async {
    final category = _category;
    if (category == null || _photos.isEmpty || _submitting) {
      return;
    }
    setState(() => _submitting = true);

    if (!ConnectionStatus.instance.isOffline) {
      await _uploadPhotos();
      if (_photos.any((photo) => photo.uploadFailed)) {
        setState(() => _submitting = false);
        return;
      }
    }

    final description = _descriptionController.text.trim();
    bool sent;
    try {
      sent = await OfflineQueue.instance.submit(
        kind: 'repair-ticket',
        send: (idempotencyKey) async {
          await _uploadPhotos(rethrowFailure: true);
          await ApiClient.instance.createRepairTicket(
            sourceTaskId: widget.sourceTaskId,
            category: category,
            description: description,
            photoIds: [
              for (final photo in _photos) photo.photoId!,
            ],
            interruptSourceTask: widget.interruptSourceTask,
            idempotencyKey: idempotencyKey,
          );
        },
      );
    } catch (_) {
      setState(() => _submitting = false);
      return;
    }

    if (!mounted) {
      return;
    }
    if (!sent) {
      setState(() => _submitting = false);
      return;
    }
    widget.onClose();
  }

  bool get _urgent => _precheck?.urgent ?? false;

  _SubmitAction get _submitAction {
    final failed = _photos.any((photo) => photo.uploadFailed);
    if (failed) {
      return const _SubmitAction(
        label: '有照片上傳失敗，點縮圖重試',
        enabled: false,
        style: TmsPillButtonStyle.filled,
      );
    }
    if (_submitting) {
      final uploaded = _photos.where((photo) => photo.photoId != null).length;
      return _SubmitAction(
        label: '上傳照片中 $uploaded / ${_photos.length}…',
        enabled: false,
        style: TmsPillButtonStyle.filled,
      );
    }
    final urgent = _urgent;
    return _SubmitAction(
      label: urgent ? '送出急件（直接給工務）' : '送出給主管審核',
      enabled: _category != null && _photos.isNotEmpty,
      style: urgent
          ? TmsPillButtonStyle.urgentFilled
          : TmsPillButtonStyle.filled,
    );
  }

  @override
  Widget build(BuildContext context) {
    final action = _submitAction;
    final onBack = widget.onBack;
    final precheck = _precheck;
    return TmsSemantics(
      id: 'repair.sheet',
      child: TmsSheet(
        title: '報修',
        leading:
            onBack == null ? TmsSheetLeading.close : TmsSheetLeading.back,
        onLeadingTap: onBack ?? widget.onClose,
        bottomAction: TmsSemantics(
          id: 'repair.submit-button',
          enabled: action.enabled,
          child: TmsSheetButton(
            label: action.label,
            style: action.style,
            onPressed: action.enabled ? _submit : null,
          ),
        ),
        children: [
          _categoryList(),
          if (precheck?.duplicate != null)
            _duplicateCallout(precheck!.duplicate!),
          _descriptionCard(),
          if (precheck != null) _urgentJudgementBlock(precheck),
          if (_category == null) _urgentPendingNote(),
          _photoCard(),
          if (_photos.isEmpty) _needPhotoNote(),
          _photoNote(),
        ],
      ),
    );
  }

  Widget _descriptionCard() {
    return TmsCard(
      style: TmsCardStyle.inSheet,
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.md2,
        TmsSpacing.md,
        TmsSpacing.md2,
        TmsSpacing.md,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('狀況說明（選填）', style: TmsText.footnoteEmphasized),
          const SizedBox(height: TmsSpacing.sm),
          TmsSemantics(
            id: 'repair.description-field',
            child: CupertinoTextField(
              controller: _descriptionController,
              placeholder: '例如：冷氣開了不冷，出風口有滴水',
              maxLines: 2,
              minLines: 2,
              maxLength: _maxDescriptionLength,
              padding: EdgeInsets.zero,
              decoration: const BoxDecoration(),
              style: TmsText.callout,
            ),
          ),
          const SizedBox(height: TmsSpacing.sm),
          TmsSemantics(
            id: 'repair.description-char-count',
            child: Text(
              '${_descriptionController.text.length} / $_maxDescriptionLength',
              textAlign: TextAlign.right,
              style: TmsText.caption1.copyWith(
                color: TmsColors.secondaryLabel,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _photoCard() {
    const label = '照片（必填，至少 1 張、最多 3 張）';
    return TmsCard(
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
                  id: 'repair.photo.count-label',
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
              if (!_photoLimitHit)
                TmsSemantics(
                  id: 'repair.photo.add-button',
                  child: TmsAddPhotoButton(
                    label: '加入照片',
                    onTap: _addPhoto,
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _needPhotoNote() {
    return const Padding(
      padding: EdgeInsets.symmetric(horizontal: TmsSpacing.xs),
      child: TmsSemantics(
        id: 'repair.photo.required-note',
        child: Text(
          '至少 1 張照片才能送出，方便主管與工務判斷狀況。沒有相機權限時，可以從相簿選擇。',
          style: TmsText.footnote,
        ),
      ),
    );
  }

  Widget _photoNote() {
    return ValueListenableBuilder<bool>(
      valueListenable: ConnectionStatus.instance.offline,
      builder: (context, offline, _) {
        final text = [
          if (offline) '目前離線：工務單與照片會先存在手機，恢復連線後自動上傳、送出。',
          if (_photos.isNotEmpty)
            '照片已加上浮水印（房號 · 拍攝時間）並壓縮（長邊 1600px、JPEG）。',
        ].join();
        if (text.isEmpty) {
          return const SizedBox.shrink();
        }
        final note = Padding(
          padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.xs),
          child: Text(text, style: TmsText.footnote),
        );
        if (!offline) {
          return note;
        }
        return TmsSemantics(
          id: 'ticket.summary.offline-queued-label',
          label: text,
          child: ExcludeSemantics(child: note),
        );
      },
    );
  }

  Widget _categoryList() {
    return TmsGroupedSection(
      header: '問題類別',
      style: TmsCardStyle.inSheet,
      trailingDivider: true,
      children: [
        for (final category in repairCategories)
          TmsSemantics(
            id: 'repair.category.${category.key}.chip',
            child: TmsOptionRow.category(
              title: Text(category.label),
              selected: _category == category.key,
              onTap: () => _pickCategory(category.key),
            ),
          ),
      ],
    );
  }

  Widget _duplicateCallout(RepairTicketSummary duplicate) {
    final text = '已有 ${duplicate.ticketNo} '
        '${repairCategoryText(duplicate.category)}'
        '（${repairStateText(duplicate.state)}）';
    return TmsCard(
      style: TmsCardStyle.inSheet,
      padding: const EdgeInsets.symmetric(
        vertical: TmsSpacing.md,
        horizontal: TmsSpacing.lg,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            CupertinoIcons.info_circle_fill,
            size: TmsSize.calloutIcon,
            color: TmsColors.brandPrimary,
          ),
          const SizedBox(width: TmsSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                TmsSemantics(
                  id: 'repair.duplicate-ticket.banner',
                  label: text,
                  child: ExcludeSemantics(
                    child: Text(text, style: TmsText.subheadlineEmphasized),
                  ),
                ),
                const SizedBox(height: TmsSpacing.xxs),
                const Text(
                  '同一個問題不用再開新單，補照片到原本的單，主管與工務都看得到。',
                  style: TmsText.footnote,
                ),
                TmsSemantics(
                  id: 'repair.duplicate-ticket.add-photo-button',
                  child: CupertinoButton(
                    padding: EdgeInsets.zero,
                    minimumSize: const Size(0, TmsSize.minTouchTarget),
                    alignment: Alignment.centerLeft,
                    onPressed: () => _appendPhotosToDuplicate(duplicate),
                    child: Text(
                      '補充照片到這張單',
                      style: TmsText.subheadlineEmphasized.copyWith(
                        color: TmsColors.brandPrimary,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _appendPhotosToDuplicate(RepairTicketSummary duplicate) async {
    await _uploadPhotos();
    final photoIds = [
      for (final photo in _photos)
        if (photo.photoId != null) photo.photoId!,
    ];
    if (photoIds.isEmpty) {
      return;
    }
    try {
      await ApiClient.instance
          .appendRepairTicketPhotos(duplicate.taskId, photoIds: photoIds);
      widget.onClose();
    } catch (_) {
    }
  }

  Widget _urgentPendingNote() {
    return const Padding(
      padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      child: TmsSemantics(
        id: 'repair.urgent-judgement.pending-note',
        child: Text('選好類別後，系統會判定是不是急件。', style: TmsText.footnote),
      ),
    );
  }

  Widget _urgentJudgementBlock(RepairTicketPrecheck precheck) {
    final urgent = precheck.urgent;
    final rule = precheck.urgentRule;
    final headline = urgent
        ? (rule == null ? '系統判定：急件' : '系統判定：急件 · $rule')
        : '系統判定：一般件';
    const note = '急件由系統依飯店設定的規則判定，房務不能更改。判錯時由主管修正。';
    const urgentBody = '送出後直接給工務，並同步通知主管。';
    return TmsSemantics(
      id: 'repair.urgent-judgement.card',
      label: urgent ? '$headline\n$urgentBody\n$note' : '$headline\n$note',
      explicitChildNodes: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          TmsCard(
            style: TmsCardStyle.inSheet,
            backgroundColor:
                urgent ? TmsColors.errorContainer : TmsColors.cardBackground,
            padding: const EdgeInsets.symmetric(
              vertical: TmsSpacing.md,
              horizontal: TmsSpacing.lg,
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: TmsSpacing.xxxs),
                  child: Icon(
                    urgent
                        ? CupertinoIcons.bolt_fill
                        : CupertinoIcons.info_circle_fill,
                    size: _judgementIconSize,
                    color: urgent
                        ? TmsColors.systemRed
                        : TmsColors.quaternaryLabel,
                  ),
                ),
                const SizedBox(width: TmsSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      ExcludeSemantics(
                        child: Text(headline, style: TmsText.subheadlineBold),
                      ),
                      if (urgent) ...[
                        const SizedBox(height: TmsSpacing.xxs),
                        const ExcludeSemantics(
                          child: Text(urgentBody, style: TmsText.footnote),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: TmsSpacing.xs2),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
            child: ExcludeSemantics(
              child: Text(note, style: TmsText.footnote),
            ),
          ),
        ],
      ),
    );
  }

  static const double _judgementIconSize = 22;

  Widget _thumbnail(int index) {
    final photo = _photos[index];
    final statusText = photo.uploadFailed
        ? '上傳失敗'
        : (photo.photoId == null ? '待上傳' : '已上傳');
    final TmsPhotoUpload upload;
    if (photo.uploadFailed) {
      upload = const TmsPhotoFailed();
    } else if (photo.photoId != null) {
      upload = const TmsPhotoUploaded();
    } else if (_submitting) {
      upload = const TmsPhotoUploading(0);
    } else {
      upload = const TmsPhotoIdle();
    }
    return TmsSemantics(
      id: 'repair.photo.${index + 1}.upload-status',
      label: statusText,
      child: TmsSemantics(
        id: 'repair.photo.${index + 1}.watermark-label',
        label: photo.watermark,
        child: _retryWrapper(
          index,
          failed: photo.uploadFailed,
          child: TmsPhotoCell(
            upload: upload,
            onRemove: _submitting ? null : () => _removePhoto(index),
            onRetry: () => _retryPhoto(index),
          ),
        ),
      ),
    );
  }

  Widget _retryWrapper(
    int index, {
    required bool failed,
    required Widget child,
  }) {
    if (!failed) {
      return child;
    }
    return TmsSemantics(
      id: 'repair.photo.${index + 1}.retry-button',
      child: child,
    );
  }
}
