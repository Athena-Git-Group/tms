import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/repair_photo_capture.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/today_refresh.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_photo_action_sheet.dart';
import '../../core/widgets/tms_photo_cell.dart';
import '../../core/widgets/tms_segmented_control.dart';
import '../../core/widgets/tms_sheet.dart';
import 'repair_labels.dart';
import 'ticket_screen.dart';

enum _PlaceType { room, area }

enum _Who { me, pool }

class RepairTicketFormSheet extends StatefulWidget {
  const RepairTicketFormSheet({super.key});

  static Future<void> show(BuildContext context) async {
    final ticket = await TmsSheetRoute.show<RepairTicketDetail>(
      context,
      (_) => const RepairTicketFormSheet(),
    );
    if (ticket == null || !context.mounted) {
      return;
    }
    await Navigator.of(context, rootNavigator: true).push(
      CupertinoPageRoute<void>(
        builder: (_) =>
            TicketScreen(view: TicketView.engineering, taskId: ticket.taskId),
      ),
    );
  }

  @override
  State<RepairTicketFormSheet> createState() => _RepairTicketFormSheetState();
}

class _RepairTicketFormSheetState extends State<RepairTicketFormSheet> {
  final TextEditingController _roomController = TextEditingController();
  final TextEditingController _descriptionController = TextEditingController();

  static const int _maxPhotos = 3;

  static const int _maxDescriptionLength = 60;

  static const int _maxRoomNoLength = 4;

  _PlaceType _placeType = _PlaceType.room;
  String? _category;
  _Who _who = _Who.me;
  final List<RepairPhoto> _photos = [];
  bool _submitting = false;
  String? _error;

  List<RoomSummary>? _rooms;
  bool _roomsFailed = false;

  @override
  void initState() {
    super.initState();
    _loadRooms();
  }

  @override
  void dispose() {
    _roomController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _loadRooms() async {
    try {
      final rooms = await ApiClient.instance.listRooms();
      if (mounted) {
        setState(() => _rooms = rooms);
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _rooms = const [];
          _roomsFailed = true;
        });
      }
    }
  }

  String? get _roomId {
    final rooms = _rooms;
    final roomNo = _roomController.text.trim();
    if (rooms == null || roomNo.isEmpty) {
      return null;
    }
    for (final room in rooms) {
      if (room.roomNo == roomNo) {
        return room.id;
      }
    }
    return null;
  }

  bool get _placeReady => _placeType == _PlaceType.area || _roomId != null;

  List<String> get _uploadedPhotoIds => [
    for (final photo in _photos) ?photo.photoId,
  ];

  bool get _anyPhotoFailed => _photos.any((photo) => photo.uploadFailed);

  bool get _anyPhotoUploading => _photos.any((photo) => photo.uploading);

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
        roomNo: _placeType == _PlaceType.area
            ? PoolItem.publicAreaRoomNo
            : _roomController.text,
        takenAt: DateTime.now(),
      ),
    );
    setState(() {
      _photos.add(photo);
      _error = null;
    });
    await _upload(photo);
  }

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
    if (category == null || !_canSubmit) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    final isArea = _placeType == _PlaceType.area;
    try {
      final ticket = await ApiClient.instance.createRepairTicket(
        roomId: isArea ? null : _roomId,
        isPublicArea: isArea,
        category: category,
        description: _descriptionController.text.trim(),
        photoIds: _uploadedPhotoIds,
        assigneeId: isArea && _who == _Who.pool
            ? null
            : ApiClient.instance.session?.staffId,
      );
      TodayRefresh.instance.request();
      if (mounted) {
        Navigator.of(context).pop(ticket);
      }
    } on ApiException catch (e) {
      _failed(e.errorMessage ?? '開工單失敗，請稍後再試。');
    } catch (_) {
      _failed('開工單失敗，請稍後再試。');
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

  bool get _canSubmit =>
      _placeReady &&
      _category != null &&
      _photos.isNotEmpty &&
      _uploadedPhotoIds.length == _photos.length &&
      !_submitting;

  String get _submitLabel {
    if (_anyPhotoFailed) {
      return '有照片上傳失敗，點縮圖重試';
    }
    if (_anyPhotoUploading) {
      return '上傳照片中 ${_uploadedPhotoIds.length} / ${_photos.length}…';
    }
    return '建立工單';
  }

  @override
  Widget build(BuildContext context) {
    final isRoom = _placeType == _PlaceType.room;
    return TmsSemantics(
      id: 'repair.create-sheet',
      child: TmsSheet(
        title: '開工單',
        onLeadingTap: () => Navigator.of(context).pop(),
        bottomAction: TmsSemantics(
          id: 'repair.submit-button',
          enabled: _canSubmit,
          child: TmsSheetButton(
            label: _submitLabel,
            onPressed: _canSubmit ? _submit : null,
            child: _submitting
                ? const CupertinoActivityIndicator(
                    color: TmsColors.brandOnPrimary,
                  )
                : null,
          ),
        ),
        children: [
          _placeTypeControl(),
          if (isRoom) _roomCard(),
          if (isRoom && _roomsFailed) _roomsFailedNote(),
          _photoCard(),
          if (_photos.isEmpty) _needPhotoNote(),
          _photoNote(),
          _categoryChips(),
          _descriptionCard(),
          if (isRoom) _roomOnlyNote() else _whoControl(),
          if (_error != null) _errorNote(_error!),
        ],
      ),
    );
  }

  Widget _placeTypeControl() {
    return TmsSegmentedControl<_PlaceType>(
      value: _placeType,
      onChanged: (value) {
        setState(() {
          _placeType = value;
          _roomController.clear();
          _error = null;
        });
      },
      segments: [
        TmsSegment(
          value: _PlaceType.room,
          label: '客房',
          wrap: (node) =>
              TmsSemantics(id: 'repair.room-type-toggle', child: node),
        ),
        TmsSegment(
          value: _PlaceType.area,
          label: '公共區域',
          wrap: (node) =>
              TmsSemantics(id: 'repair.public-area-toggle', child: node),
        ),
      ],
    );
  }

  Widget _roomCard() {
    return TmsCard(
      style: TmsCardStyle.inSheet,
      padding: const EdgeInsets.symmetric(
        vertical: TmsSpacing.sm,
        horizontal: TmsSpacing.lg,
      ),
      child: Row(
        children: [
          const Text('房號', style: TmsText.headline, maxLines: 1),
          const SizedBox(width: TmsSpacing.md),
          Expanded(
            child: TmsSemantics(
              id: 'repair.room-field',
              child: CupertinoTextField(
                controller: _roomController,
                placeholder: '例如 418',
                keyboardType: TextInputType.number,
                maxLength: _maxRoomNoLength,
                padding: EdgeInsets.zero,
                decoration: const BoxDecoration(),
                style: TmsText.title3,
                onChanged: (_) => setState(() => _error = null),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _roomsFailedNote() {
    return const Padding(
      padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      child: TmsSemantics(
        id: 'repair.rooms-load-failure-note',
        child: Text('房間清單載入失敗，現在只開得出公共區域的單。', style: TmsText.footnote),
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
              if (_photos.length < _maxPhotos)
                TmsSemantics(
                  id: 'repair.photo.add-button',
                  child: TmsAddPhotoButton(label: '加入照片', onTap: _addPhoto),
                ),
            ],
          ),
        ],
      ),
    );
  }

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

  Widget _needPhotoNote() {
    return const Padding(
      padding: EdgeInsets.symmetric(horizontal: TmsSpacing.xs),
      child: TmsSemantics(
        id: 'repair.photo.required-note',
        child: Text(
          '至少 1 張照片才能建立，事後追蹤、統計與責任歸屬都用得到。沒有相機權限時，可以從相簿選擇。',
          style: TmsText.footnote,
        ),
      ),
    );
  }

  Widget _photoNote() {
    return ValueListenableBuilder<bool>(
      valueListenable: ConnectionStatus.instance.offline,
      builder: (context, offline, _) {
        final stamp = _placeType == _PlaceType.room ? '房號' : '區域';
        final text = [
          if (offline) '目前離線：照片會先存在手機，恢復連線後自動上傳。',
          if (_photos.isNotEmpty) '照片已加上浮水印（$stamp · 拍攝時間）並壓縮（長邊 1600px、JPEG）。',
        ].join();
        if (text.isEmpty) {
          return const SizedBox.shrink();
        }
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.xs),
          child: Text(text, style: TmsText.footnote),
        );
      },
    );
  }

  Widget _categoryChips() {
    return _LabelledBlock(
      label: '類別',
      child: Wrap(
        spacing: TmsSpacing.sm,
        runSpacing: TmsSpacing.sm,
        children: [
          for (final category in repairCategories)
            TmsSemantics(
              id: 'repair.category.${category.key}.chip',
              child: _FormChip(
                label: category.label,
                selected: _category == category.key,
                onTap: () => setState(() => _category = category.key),
              ),
            ),
        ],
      ),
    );
  }

  Widget _descriptionCard() {
    return TmsCard(
      style: TmsCardStyle.inSheet,
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.lg,
        TmsSpacing.md,
        TmsSpacing.lg,
        TmsSpacing.md,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('狀況說明', style: TmsText.footnoteEmphasized),
          const SizedBox(height: TmsSpacing.xs2),
          TmsSemantics(
            id: 'repair.description-field',
            child: CupertinoTextField(
              controller: _descriptionController,
              placeholder: '例如：自動門開關有異音',
              maxLines: 2,
              minLines: 2,
              maxLength: _maxDescriptionLength,
              padding: EdgeInsets.zero,
              decoration: const BoxDecoration(),
              style: TmsText.callout,
            ),
          ),
        ],
      ),
    );
  }

  Widget _roomOnlyNote() {
    return const Padding(
      padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      child: TmsSemantics(
        id: 'repair.room-only-note',
        child: Text('客房的單由開單的人自己處理，不經主管審核、也不放進待認領。', style: TmsText.footnote),
      ),
    );
  }

  Widget _whoControl() {
    return _LabelledBlock(
      label: '誰來處理',
      footnote: '工務自己開的單不用主管審核。公共區域的單可以放進待認領，其他工務同仁也接得到。',
      child: TmsSegmentedControl<_Who>(
        value: _who,
        onChanged: (value) => setState(() => _who = value),
        segments: [
          TmsSegment(
            value: _Who.me,
            label: '我自己處理',
            wrap: (node) =>
                TmsSemantics(id: 'repair.who.me.segment', child: node),
          ),
          TmsSegment(
            value: _Who.pool,
            label: '放進待認領',
            wrap: (node) =>
                TmsSemantics(id: 'repair.who.pool.segment', child: node),
          ),
        ],
      ),
    );
  }

  Widget _errorNote(String message) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      child: TmsSemantics(
        id: 'repair.create-failure-note',
        child: Text(
          message,
          style: TmsText.footnote.copyWith(color: TmsColors.systemRed),
        ),
      ),
    );
  }
}

class _LabelledBlock extends StatelessWidget {
  const _LabelledBlock({
    required this.label,
    required this.child,
    this.footnote,
  });

  final String label;
  final Widget child;

  final String? footnote;

  @override
  Widget build(BuildContext context) {
    final note = footnote;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.screenEdge,
            0,
            TmsSpacing.screenEdge,
            TmsSpacing.xs2,
          ),
          child: Text(label, style: TmsText.footnote),
        ),
        child,
        if (note != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(
              TmsSpacing.screenEdge,
              TmsSpacing.xs2,
              TmsSpacing.screenEdge,
              0,
            ),
            child: Text(note, style: TmsText.footnote),
          ),
      ],
    );
  }
}

class _FormChip extends StatelessWidget {
  const _FormChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  static const double _height = 40;

  static const double _horizontalPadding = 14;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      selected: selected,
      button: true,
      child: GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: Container(
          constraints: const BoxConstraints(minHeight: _height),
          padding: const EdgeInsets.symmetric(horizontal: _horizontalPadding),
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: selected ? TmsColors.brandPrimary : TmsColors.cardBackground,
            borderRadius: BorderRadius.circular(TmsRadius.pill),
            border: selected
                ? null
                : Border.all(color: TmsColors.opaqueSeparator),
          ),
          child: Text(
            label,
            style: TmsText.subheadlineEmphasized.copyWith(
              color: selected ? TmsColors.brandOnPrimary : TmsColors.label,
            ),
          ),
        ),
      ),
    );
  }
}
