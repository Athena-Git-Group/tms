import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/repair_photo_capture.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_option_row.dart';
import '../../core/widgets/tms_photo_action_sheet.dart';
import '../../core/widgets/tms_photo_cell.dart';
import '../../core/widgets/tms_sheet.dart';
import 'repair_labels.dart';

/// 完修回報面板（`Main.dc.html:2122`）：拍完修照片、選處理結果、寫說明、送出。
///
/// 送出走 `POST /tasks/{id}/complete`，成功時把選到的處理結果文字回傳給詳情
/// 頁，詳情頁用它補上狀態列裡那句結果——後端的工務單詳情沒有回傳
/// resolutionType，離開這個面板之後就再也問不到員工剛才選了什麼。
class TicketCompleteSheet extends StatefulWidget {
  const TicketCompleteSheet({
    super.key,
    required this.taskId,
    required this.roomNo,
  });

  final String taskId;

  /// 這張單的地點。標題帶著它，員工同時開著好幾張單時才分得出自己在回報哪一
  /// 張；完修照片的浮水印也用它。
  final String roomNo;

  @override
  State<TicketCompleteSheet> createState() => _TicketCompleteSheetState();
}

class _TicketCompleteSheetState extends State<TicketCompleteSheet> {
  /// 契約 CompleteTaskRequest.photoIds 的上下限：1-3 張。
  static const int _maxPhotos = 3;

  /// 處理說明擋在 60 字，是因為它只要寫得下「換了什麼零件」這種一句話的交代；
  /// 真正的過程留給處理歷程，不是靠這一欄撐起來（`Main.dc.html:2190`）。
  static const int _maxNoteLength = 60;

  final List<RepairPhoto> _photos = [];
  final TextEditingController _noteController = TextEditingController();
  String? _selectedResolution;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  /// 已經上傳成功、可以引用進完修回報的照片。
  List<String> get _uploadedPhotoIds => [
    for (final photo in _photos)
      if (photo.photoId != null) photo.photoId!,
  ];

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
      photo.photoId = await captureAndUploadRepairPhoto(purpose: 'completion');
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
        Navigator.of(context).pop(repairResolutionText(resolution));
      }
    } on ApiException catch (e) {
      _failed(e.errorMessage ?? '完修回報失敗，請稍後再試。');
    } catch (_) {
      _failed('完修回報失敗，請稍後再試。');
    }
  }

  void _failed(String message) {
    if (!mounted) {
      return;
    }
    setState(() {
      _submitting = false;
      _error = message;
    });
  }

  /// 置底鈕上的字。上傳還沒結束或有一張失敗時改寫成當下的狀況，否則員工按不
  /// 動又不知道卡在哪（`Main.dc.html:3995`）。
  String get _submitLabel {
    if (_photos.any((photo) => photo.uploadFailed)) {
      return '有照片上傳失敗，點縮圖重試';
    }
    if (_photos.any((photo) => photo.uploading)) {
      return '上傳照片中 ${_uploadedPhotoIds.length} / ${_photos.length}…';
    }
    return '送出完修';
  }

  @override
  Widget build(BuildContext context) {
    final canSubmit =
        _selectedResolution != null && _allPhotosUploaded && !_submitting;
    final hint = _resolutionHint();
    return TmsSemantics(
      id: 'completeSheet.sheet',
      child: TmsSheet(
        title: '完修回報 · ${widget.roomNo}',
        onLeadingTap: () => Navigator.of(context).pop(),
        bottomAction: TmsSemantics(
          id: 'completeSheet.submit-button',
          // 可否送出要顯式標出來：CupertinoButton 的 onPressed 為 null 時，
          // 框架不會把 disabled 曝露到 accessibility tree。
          enabled: canSubmit,
          child: TmsSheetButton(
            label: _submitLabel,
            onPressed: canSubmit ? _submit : null,
            child: _submitting ? const CupertinoActivityIndicator() : null,
          ),
        ),
        children: [
          _photoCard(),
          _resultList(),
          _noteCard(),
          if (hint != null)
            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: TmsSpacing.screenEdge,
              ),
              child: TmsSemantics(
                id: 'completeSheet.result-hint',
                child: Text(
                  hint,
                  style: TmsText.footnote.copyWith(height: 1.4),
                ),
              ),
            ),
          if (_error != null)
            Text(
              _error!,
              style: TmsText.footnote.copyWith(
                color: TmsColors.onErrorContainer,
              ),
            ),
        ],
      ),
    );
  }

  /// 選好結果之後才出現的那一句，講的是「按下送出之後系統會替你做什麼」。
  /// 還沒拍照就不講：那時候送不出去，先講後果只是雜訊（`Main.dc.html:3994`）。
  String? _resolutionHint() {
    final resolution = _selectedResolution;
    if (resolution == null || _photos.isEmpty) {
      return null;
    }
    for (final entry in repairResolutions) {
      if (entry.key == resolution) {
        return entry.hint;
      }
    }
    return null;
  }

  /// 處理結果：一張白卡三列，每列兩行字。
  ///
  /// 不用膠囊而用清單，是因為三個結果的差別寫在副標那一行；膠囊只放得下主標，
  /// 員工得先知道飯店流程才選得對。
  Widget _resultList() {
    return TmsGroupedSection(
      header: '處理結果',
      style: TmsCardStyle.inSheet,
      children: [
        for (final resolution in repairResolutions)
          TmsSemantics(
            id: 'completeSheet.result.${resolution.key}',
            child: TmsOptionRow.result(
              title: Text(resolution.label),
              detail: Text(resolution.detail),
              selected: _selectedResolution == resolution.key,
              onTap: () {
                setState(() {
                  _selectedResolution = resolution.key;
                });
              },
            ),
          ),
      ],
    );
  }

  Widget _noteCard() {
    const label = '處理說明（選填）';
    return TmsCard(
      style: TmsCardStyle.inSheet,
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.screenEdge,
        vertical: TmsSpacing.md,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          const TmsSemantics(
            id: 'completeSheet.note-label',
            label: label,
            child: ExcludeSemantics(
              child: Text(label, style: TmsText.footnoteEmphasized),
            ),
          ),
          const SizedBox(height: TmsSpacing.xs2),
          TmsSemantics(
            id: 'completeSheet.note-field',
            child: CupertinoTextField(
              controller: _noteController,
              placeholder: '例如：更換冷氣濾網、補充冷媒',
              maxLines: 2,
              maxLength: _maxNoteLength,
              padding: EdgeInsets.zero,
              decoration: const BoxDecoration(),
              style: TmsText.callout,
            ),
          ),
        ],
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
