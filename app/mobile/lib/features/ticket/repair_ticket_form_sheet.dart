import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/repair_photo_capture.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_photo_action_sheet.dart';
import '../../core/widgets/tms_photo_cell.dart';
import '../../core/widgets/tms_segmented_control.dart';
import '../../core/widgets/tms_sheet.dart';
import 'repair_labels.dart';
import 'ticket_screen.dart';

/// 這張單要開給客房還是公共區域（`Main.dc.html:4002`）。
///
/// 兩者不是同一個欄位的兩個值，而是兩條互斥的開單路徑：客房要帶房間 id、公共
/// 區域帶 `isPublicArea`，契約明寫兩者同時帶會被擋下。用一個 enum 表示，就不
/// 會出現「房號填了一半又切到公共區域」這種兩邊都有值的狀態。
enum _PlaceType { room, area }

/// 公共區域的單開完之後誰接手（`Main.dc.html:4007`）。
enum _Who { me, pool }

/// 工務自己開一張工務單的底部面板（`Main.dc.html:2197`）。
///
/// 工務巡場時發現問題，從工務首頁右下角的浮動按鈕升起這個面板開單，不必先有
/// 一張房務任務。它蓋在首頁上而不是推走首頁，因為關掉它之後員工要接著看剛剛
/// 在看的那一屏，首頁重新載入一次的話那一屏就回不來了。
///
/// ## 客房與公共區域是兩條路
///
/// 客房要把員工打的房號換成房間 id 才送得出去，所以面板一開就取一次房間主檔
/// （`GET /rooms`），打進去的房號對不上主檔就不讓送。對不上時後端只會回一個
/// 422，員工看到的是「開工單失敗」而不是「沒有這個房號」。公共區域沒有房號，
/// 契約也只有一個 `isPublicArea` 布林值，所以沒有東西要查。
///
/// ## 原型的區域選擇沒有落地
///
/// 原型在公共區域那一段有一排區域膠囊（大廳、健身房…），但契約的開單請求只有
/// `isPublicArea`，沒有任何欄位放得下選到的區域，`/tenant-configs` 也沒有區域
/// 清單。選了存不住，所以整段不顯示，缺口回交上游。
class RepairTicketFormSheet extends StatefulWidget {
  const RepairTicketFormSheet({super.key});

  /// 升起這個面板，開成了就接著打開那張單的詳情。
  ///
  /// 導頁放在這裡而不是面板內部：開完要去哪裡是叫它的畫面在決定，而且面板自己
  /// pop 之後就沒有可用的 context 了。
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

  /// 契約 CreateRepairTicketRequest.photoIds 的上下限：1-3 張。
  static const int _maxPhotos = 3;

  /// 狀況說明的字數上限（`Main.dc.html:2286`）。
  static const int _maxDescriptionLength = 60;

  /// 房號欄的字數上限。`Main.dc.html:2207` — maxLength 4
  static const int _maxRoomNoLength = 4;

  _PlaceType _placeType = _PlaceType.room;
  String? _category;
  _Who _who = _Who.me;
  final List<RepairPhoto> _photos = [];
  bool _submitting = false;
  String? _error;

  /// 房間主檔。還沒回來是 null，取失敗是空清單——兩者都讓客房送不出去，但只有
  /// 後者要在畫面上說一句，否則員工會以為是自己的房號打錯。
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

  /// 員工打的房號對應的房間 id；主檔還沒回來或房號不存在時是 null。
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

  /// 地點填齊了沒有（`Main.dc.html:4000` 的 `okPlace`）。
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
        // 客房的單一律由開單的人自己處理（`Main.dc.html:4008`），那一段沒有
        // 「放進待認領」可選，所以客房不看 `_who`。
        assigneeId: isArea && _who == _Who.pool
            ? null
            : ApiClient.instance.session?.staffId,
      );
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

  /// 置底鈕此刻的文案（`Main.dc.html:4010`）。
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
          // 可否送出要顯式標出來：CupertinoButton 的 onPressed 為 null 時，
          // 框架不會把 disabled 曝露到 accessibility tree。
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

  /// 地點類型分段控制。切過去就把另一條路的輸入清掉，不留一個看不見的舊值。
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

  /// 房號：一張白卡，左邊欄名、右邊一個大字輸入框。
  ///
  /// 數字鍵盤配 20pt 粗體：這個欄位只收三到四位數，而員工多半戴著手套單手打。
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

  /// 照片區：一張白卡，上面一行標題與張數，底下是照片格與「加入照片」格。
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

  /// 一張照片的縮圖。序號從 1 算起，與 dsl 的句型一致。
  ///
  /// 這個面板加一張就傳一張，所以同一列裡每一格的上傳狀態各自獨立。
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

  /// 一張都還沒拍時，把「為什麼非拍不可」與「沒有相機權限怎麼辦」寫在送出鈕
  /// 上方。只說「送出鈕不能按」的話，員工會以為是當掉了。
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

  /// 照片與離線的處理說明。兩段都沒有時整行不出現。
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

  /// 類別：一排膠囊，不是清單。
  ///
  /// 和房務報修面板相反（那裡是清單）。開工單的員工是工務自己，五個類別是他
  /// 每天在用的詞，認得出來不必逐列讀；膠囊排成兩行也只佔清單三分之一的高度，
  /// 下面的狀況說明與誰來處理才不用捲才看得到。
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

  /// 狀況說明：工務自己寫一句話，接手與驗收的人不用看照片猜。
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

  /// 誰來處理：只有公共區域的單有這個選擇，因為只有它放得進待認領。
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

/// 面板裡「一行小標配一塊內容」的組合（`Main.dc.html:2276`）。
class _LabelledBlock extends StatelessWidget {
  const _LabelledBlock({
    required this.label,
    required this.child,
    this.footnote,
  });

  final String label;
  final Widget child;

  /// 內容底下那一行說明。
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

/// 開工單面板的膠囊（`Main.dc.html:2279`）。
///
/// 不走 [TmsSelectionChip]：那一顆沒選中時是灰底無框，這裡的原型是白底加一圈
/// 灰框，高度也是 40 不是 44。那一顆同時用在待認領的篩選磚與房務報修，動它會
/// 連帶動到那些畫面；這裡只有類別一處要用，所以放在用它的檔案裡。
class _FormChip extends StatelessWidget {
  const _FormChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  /// `Main.dc.html:2279` — min-height 40px
  static const double _height = 40;

  /// `Main.dc.html:2279` — padding 0 14px
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
