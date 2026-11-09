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
import '../../core/widgets/tms_selection_chip.dart';
import '../../core/widgets/tms_sheet.dart';
import 'repair_labels.dart';
import 'ticket_screen.dart';

/// 工務自行開工單的表單（工務待認領的「開工單」入口）。
///
/// 走契約 createRepairTicket 的「不經過任何房務任務」路徑：不帶
/// sourceTaskId，改帶公共區域或客房其中之一。急件由系統判定，表單沒有
/// 急件欄位。送出成功後導到新建那張單的詳情。
///
/// accessibility id 沿用房務報修面板的 `repair.` 前綴（兩個入口是同一張
/// 表單的兩種進法），差別只在這裡不預先帶入任何原任務。
class RepairTicketFormScreen extends StatefulWidget {
  const RepairTicketFormScreen({super.key});

  @override
  State<RepairTicketFormScreen> createState() => _RepairTicketFormScreenState();
}

class _RepairTicketFormScreenState extends State<RepairTicketFormScreen> {
  final TextEditingController _roomController = TextEditingController();

  /// 契約 CreateRepairTicketRequest.photoIds 的上下限：1-3 張。
  static const int _maxPhotos = 3;

  bool _isPublicArea = false;
  String? _category;
  final List<RepairPhoto> _photos = [];
  bool _submitting = false;
  String? _error;

  /// 已經上傳成功、可以引用進開單請求的照片。
  List<String> get _uploadedPhotoIds =>
      [for (final photo in _photos) if (photo.photoId != null) photo.photoId!];

  bool get _allPhotosUploaded =>
      _photos.isNotEmpty && _uploadedPhotoIds.length == _photos.length;

  @override
  void dispose() {
    _roomController.dispose();
    super.dispose();
  }

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
        roomNo: _isPublicArea ? '公共區域' : _roomController.text,
        takenAt: DateTime.now(),
      ),
    );
    setState(() {
      _photos.add(photo);
      _error = null;
    });
    await _upload(photo);
  }

  /// 把這一張送上去，並把結果記在它自己身上。
  ///
  /// 逐張記錄而不是整批記一個成功或失敗，員工才能只重傳失敗的那一張。整批
  /// 重傳會讓已經傳好的照片在後端多出一份。
  Future<void> _upload(RepairPhoto photo) async {
    setState(() => photo.uploading = true);
    try {
      photo.photoId = await captureAndUploadRepairPhoto(purpose: 'report');
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
    final category = _category;
    if (category == null || !_allPhotosUploaded || _submitting) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final ticket = await ApiClient.instance.createRepairTicket(
        isPublicArea: _isPublicArea,
        category: category,
        photoIds: _uploadedPhotoIds,
      );
      if (mounted) {
        Navigator.of(context).pushReplacement(
          CupertinoPageRoute<void>(
            builder: (_) => TicketScreen(
              view: TicketView.engineering,
              taskId: ticket.taskId,
            ),
          ),
        );
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = e.errorMessage ?? '開工單失敗，請稍後再試。';
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = '開工單失敗，請稍後再試。';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final canSubmit = _category != null && _allPhotosUploaded && !_submitting;
    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      navigationBar: const CupertinoNavigationBar(middle: Text('開工單')),
      child: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(TmsSpacing.screenEdge),
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: TmsSemantics(
                id: 'repair.public-area-toggle',
                child: TmsSelectionChip(
                  label: '公共區域',
                  selected: _isPublicArea,
                  onTap: () {
                    setState(() {
                      _isPublicArea = true;
                      _roomController.clear();
                    });
                  },
                ),
              ),
            ),
            const SizedBox(height: TmsSpacing.md),
            TmsSemantics(
              id: 'repair.room-field',
              child: CupertinoTextField(
                controller: _roomController,
                placeholder: '房號',
                padding: const EdgeInsets.all(TmsSpacing.md),
                onTap: () {
                  setState(() {
                    _isPublicArea = false;
                  });
                },
              ),
            ),
            const SizedBox(height: TmsSpacing.lg),
            TmsGroupedSection(
              header: '問題類別',
              children: [
                for (final category in repairCategories)
                  TmsSemantics(
                    id: 'repair.category.${category.key}.chip',
                    child: TmsOptionRow.category(
                      title: Text(category.label),
                      selected: _category == category.key,
                      onTap: () {
                        setState(() {
                          _category = category.key;
                        });
                      },
                    ),
                  ),
              ],
            ),
            const SizedBox(height: TmsSpacing.lg),
            _photoCard(),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: TmsSpacing.sm),
                child: Text(
                  _error!,
                  style: TmsText.subheadline
                      .copyWith(color: TmsColors.systemRed),
                ),
              ),
            const SizedBox(height: TmsSpacing.lg),
            TmsSemantics(
              id: 'repair.submit-button',
              enabled: canSubmit,
              child: TmsSheetButton(
                label: '送出',
                onPressed: canSubmit ? _submit : null,
                child: _submitting
                    ? const CupertinoActivityIndicator(
                        color: TmsColors.brandOnPrimary,
                      )
                    : null,
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 照片區：與報修面板同一張白卡、同一種照片格。工務自己開單時照片是直接上傳
  /// 的（沒有待上傳這一段），所以每一格都是已完成態。
  Widget _photoCard() {
    return TmsCard(
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
              Expanded(
                child: TmsSemantics(
                  id: 'repair.photo.count-label',
                  label: '已拍 ${_photos.length} 張 · 至少 1 張、最多 3 張',
                  child: ExcludeSemantics(
                    child: Text(
                      '已拍 ${_photos.length} 張',
                      style: TmsText.footnoteEmphasized,
                    ),
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
                  id: 'repair.photo.add-button',
                  child: TmsAddPhotoButton(
                    label: '拍照片',
                    onTap: _addPhoto,
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }

  /// 一張照片的縮圖。序號從 1 算起，與 dsl 的句型一致。
  ///
  /// 這個畫面加一張就傳一張，所以同一列裡每一格的上傳狀態各自獨立。
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
      id: 'repair.photo.${index + 1}.upload-status',
      label: statusText,
      child: TmsSemantics(
        id: 'repair.photo.${index + 1}.watermark-label',
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
