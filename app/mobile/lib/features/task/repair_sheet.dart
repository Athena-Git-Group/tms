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
import '../../core/widgets/tms_selection_chip.dart';
import '../../core/widgets/tms_sheet.dart';
import '../ticket/repair_labels.dart';

/// 報修面板（ui-plan 畫面 6 的 `repairSheet`，即工務單表單）：房務在任務頁
/// 點報修圖示進來，選類別、拍 1–3 張照片，送出一張工務單。
///
/// <h2>為什麼是貼在任務頁上的面板，不是蓋住整頁的 modal</h2>
///
/// 報修途中員工仍要看得到原任務目前的狀態（照片上傳失敗時工務單不算送出，
/// 那一間還是進行中），離線時也要看得到頂部的離線列與待同步筆數。Flutter 的
/// modal 路由會把底下整頁的 semantics 擋掉，那些資訊就連畫面上都讀不到了，
/// 所以這個面板是任務頁自己的一層，不另開路由。
///
/// <h2>浮水印為什麼要另外掛一個節點</h2>
///
/// 照片的浮水印（房號與拍攝時間）除了畫進圖片，還要在縮圖上渲染一個帶同樣
/// 字串的可及節點（`repair.photo.{index}.watermark-label`）：浮水印若只燒進
/// 像素，自動化測試讀不到那段文字，也就沒有任何機制保證它真的被打上去。
class RepairSheet extends StatefulWidget {
  const RepairSheet({
    super.key,
    required this.roomNo,
    required this.sourceTaskId,
    required this.onClose,
  });

  /// 這張單要報修的房號；浮水印與開單都用它。
  final String roomNo;

  /// 報修是從哪一筆原任務進來的（契約 CreateRepairTicketRequest.sourceTaskId）。
  final String? sourceTaskId;

  /// 送出成功或員工取消時收起面板。
  final VoidCallback onClose;

  @override
  State<RepairSheet> createState() => _RepairSheetState();
}

class _RepairSheetState extends State<RepairSheet> {
  /// 契約 CreateRepairTicketRequest.photoIds 的上下限：1–3 張。
  static const int _maxPhotos = 3;

  final List<RepairPhoto> _photos = [];

  String? _category;

  /// 開單前檢查的結果：同房同類別還沒結案的那張單，以及系統的急件判定。
  /// 兩者都由後端算，App 只呈現。
  RepairTicketPrecheck? _precheck;

  /// 「中斷 → 需要工務協助」模式：送出的同時把原任務轉為已中斷。
  bool _interruptSourceTask = false;

  bool _submitting = false;

  /// 這張單已經留在手機的待送出佇列裡。
  bool _queuedOffline = false;

  /// 員工已經試過加第 4 張。加入按鈕要到這一刻才收起來（ui-plan 畫面 6 的
  /// 失敗回饋「第 4 張 → 加入按鈕消失」）：張數還在上限內就先留著按鈕，
  /// 員工才不會在沒有任何動作的情況下看著按鈕自己不見。
  bool _photoLimitHit = false;

  /// 選類別時向後端要開單前檢查。判定與重複提示都是後端的結論，選到別的
  /// 類別就再問一次——同一間房不同類別的重複單與急件規則本來就不同。
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
      // 檢查失敗不擋開單：重複提示與急件判定都是提示性的，後端在真正建立
      // 工務單時會再判一次。
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

  /// 把還沒上傳成功的照片逐張送上去，並把每一張的結果記在它自己身上。
  ///
  /// 已經有照片 id 的不重送：送出失敗後員工只該重試失敗的那一張，整批重傳
  /// 會讓已經上傳好的照片在後端多出一份。
  ///
  /// [rethrowFailure] 給離線佇列重送用：那條路徑要的是「這一筆還沒送成」的
  /// 訊號，好把動作留在佇列裡下次再來。
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

  /// 只重試這一張。其他照片的上傳結果原封不動。
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

  /// 送出這張工務單。
  ///
  /// 只有每張照片都上傳成功、而且裝置在線上時，才會真的送開單請求；有任何一張
  /// 沒上傳成功就停在面板上讓員工重試那一張，後端不會收到半成品的單。離線時
  /// 整個動作（上傳照片＋開單）交給 [OfflineQueue]，它在連線恢復時用同一把
  /// `Idempotency-Key` 送出去，所以網路不穩重送幾次，後端也只會有一張單。
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

    bool sent;
    try {
      sent = await OfflineQueue.instance.submit(
        kind: 'repair-ticket',
        send: (idempotencyKey) async {
          await _uploadPhotos(rethrowFailure: true);
          await ApiClient.instance.createRepairTicket(
            sourceTaskId: widget.sourceTaskId,
            category: category,
            photoIds: [
              for (final photo in _photos) photo.photoId!,
            ],
            interruptSourceTask: _interruptSourceTask,
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
      setState(() {
        _submitting = false;
        _queuedOffline = true;
      });
      return;
    }
    widget.onClose();
  }

  @override
  Widget build(BuildContext context) {
    final canSubmit =
        _category != null && _photos.isNotEmpty && !_submitting;
    return TmsSemantics(
      id: 'repair.sheet',
      child: TmsSheet(
        title: '報修',
        onLeadingTap: widget.onClose,
        bottomAction: TmsSemantics(
          id: 'repair.submit-button',
          // 可否送出要顯式標出來：CupertinoButton 的 onPressed 為
          // null 時，框架不會把 disabled 曝露到 accessibility tree。
          enabled: canSubmit,
          child: TmsSheetButton(
            label: '送出',
            onPressed: canSubmit ? _submit : null,
          ),
        ),
        children: [
          _categoryList(),
          if (_precheck?.duplicate != null)
            _duplicateCallout(_precheck!.duplicate!),
          if (_precheck != null) _urgentJudgementCard(_precheck!),
          _photoCard(),
          TmsSemantics(
            id: 'repair.interrupt-source-task-toggle',
            child: Align(
              alignment: Alignment.centerLeft,
              child: TmsSelectionChip(
                label: '中斷 · 需要工務協助',
                selected: _interruptSourceTask,
                onTap: () => setState(
                  () => _interruptSourceTask = !_interruptSourceTask,
                ),
              ),
            ),
          ),
          if (_queuedOffline) _offlineQueuedLabel(),
        ],
      ),
    );
  }

  /// 照片區：一張白卡，上面一行標題與張數，底下是照片格與「加入照片」格。
  Widget _photoCard() {
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
                  label: '照片（至少 1 張、最多 3 張）',
                  child: ExcludeSemantics(
                    child: Text(
                      '照片（至少 1 張、最多 3 張）',
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

  /// 問題類別：一組分組列表，選中的那一列尾端打勾。原型在這裡用的是清單不是
  /// 膠囊——類別有五種，膠囊排成兩行之後，哪一顆被選起來要靠顏色分辨；清單一列
  /// 一個，選中的那一列右邊直接有勾。
  Widget _categoryList() {
    return TmsGroupedSection(
      header: '問題類別',
      style: TmsCardStyle.inSheet,
      children: [
        for (final category in repairCategories)
          TmsSemantics(
            id: 'repair.category.${category.key}.chip',
            child: TmsOptionRow(
              title: Text(category.label),
              selected: _category == category.key,
              onTap: () => _pickCategory(category.key),
            ),
          ),
      ],
    );
  }

  /// 重複提示：同房同類別已經有一張沒結案的單時，先告訴員工那張單的單號與
  /// 目前狀態，並給他一條「把照片補到那張單」的路，而不是再開一張重複的。
  ///
  /// 用白卡加一顆資訊圓圖示，不用紅底——這不是錯誤，是一個「其實有更省事的
  /// 做法」的提醒；紅底會讓員工以為自己做錯了什麼。
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
            size: 26,
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
                      '補充照片到 ${duplicate.ticketNo}',
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

  /// 把這次拍的照片補到既有那張單上（契約 appendRepairTicketPhotos）。
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
      // 補照片失敗時留在面板上，員工可以再試一次。
    }
  }

  /// 急件判定卡：只呈現後端算出來的判定，並把「這是系統依飯店設定規則判定、
  /// 房務不能更改」寫在卡上。卡上刻意沒有任何可點的切換元件——判定改不改得了
  /// 不能只靠一句說明，畫面上根本不給入口才算數。
  Widget _urgentJudgementCard(RepairTicketPrecheck precheck) {
    final headline =
        precheck.urgent ? '系統判定：急件' : '系統判定：一般件';
    final rule = precheck.urgentRule;
    final note = '急件判定依飯店設定的規則判定，房務不能更改，只能由主管事後降級。';
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: TmsSemantics(
        id: 'repair.urgent-judgement.card',
        label: rule == null ? '$headline\n$note' : '$headline · $rule\n$note',
        explicitChildNodes: true,
        child: TmsCard(
          style: TmsCardStyle.inSheet,
          padding: const EdgeInsets.all(TmsSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ExcludeSemantics(child: Text(headline, style: TmsText.headline)),
              if (rule != null)
                TmsSemantics(
                  id: 'repair.urgent-judgement.reason',
                  label: rule,
                  child: ExcludeSemantics(child: Text(rule)),
                ),
              ExcludeSemantics(
                child: Text(
                  note,
                  style: TmsText.footnote
                      .copyWith(color: TmsColors.secondaryLabel),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 待送出標示。它跟著離線佇列的長度走：佇列清空的那一刻標示就該消失，
  /// 另記一個旗標的話，送出去之後畫面還會繼續說這張單沒送出。
  Widget _offlineQueuedLabel() {
    return ValueListenableBuilder<int>(
      valueListenable: ConnectionStatus.instance.pendingSyncCount,
      builder: (context, pendingCount, _) {
        if (pendingCount == 0) {
          return const SizedBox.shrink();
        }
        return Padding(
          padding: const EdgeInsets.only(top: 8),
          child: TmsSemantics(
            id: 'ticket.summary.offline-queued-label',
            label: '待送出：工務單與照片已保存在手機',
            child: const ExcludeSemantics(
              child: Text(
                '待送出：工務單與照片已保存在手機',
                style: TmsText.footnote,
              ),
            ),
          ),
        );
      },
    );
  }

  /// 一張照片的縮圖。序號從 1 算起，與 dsl 的句型一致。
  ///
  /// 上傳狀態與浮水印都不畫成文字，改成兩層可及節點掛在同一格上。原型的格子
  /// 下面沒有任何文字，狀態靠遮罩的長相表達，而浮水印是燒進像素的；自動化
  /// 測試與讀螢幕的人讀不到像素，所以那兩段文字仍要有節點。
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

  /// 上傳失敗時整格就是重試鈕，所以重試的可及節點只在失敗時存在。平常也掛著
  /// 的話，自動化測試會在一張都沒失敗的畫面上找到三顆重試鈕。
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
