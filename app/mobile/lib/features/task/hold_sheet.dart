import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_option_row.dart';
import '../../core/widgets/tms_sheet.dart';

/// 員工暫時做不下去時，用這個面板說明原因，交接的人與主管才看得到任務為什麼停住。
///
/// 延後與中斷是同一套互動：點一個預設原因就直接送出。預設原因都不適用時選
/// 「其他原因」，面板換成第二步的說明欄，說明要 1 到 60 字才送得出去。兩者的
/// 差別只有標題、預設原因清單與送出時呼叫的端點，都收在 [HoldSheetModeCopy]。
///
/// 說明的字數上限是後端自己把關的，超過時後端拒收整筆操作。面板這層的字數
/// 提示與送出鈕禁用，是為了讓員工在按下去之前就知道寫太長了，不是權威判定。
enum HoldSheetMode {
  /// 還沒開工的任務先放著，之後再回來做。
  defer,

  /// 做到一半被打斷，任務暫停並停止計時。
  interrupt,
}

/// 兩種用途的全部差異都收在這裡：面板標題、預設原因清單、送出走哪個端點。
/// 面板本體不再逐處分支，新增用途時也只要補這個 extension 的分支。
extension HoldSheetModeCopy on HoldSheetMode {
  /// 面板標題用的名稱，也是置底鈕「確定延後」「確定中斷」的後半。
  String get title => switch (this) {
        HoldSheetMode.defer => '延後',
        HoldSheetMode.interrupt => '中斷',
      };

  /// 第一步的面板標題（`Main.dc.html:4928`）。
  ///
  /// 寫成問句而不是「延後原因」，是因為這一步整面只有一張原因清單。標題要說
  /// 的是「現在請你回答什麼」，名詞片語只說得出「這裡放了什麼」。
  String get question => switch (this) {
        HoldSheetMode.defer => '為什麼要延後？',
        HoldSheetMode.interrupt => '為什麼要中斷？',
      };

  /// 各自的預設原因。
  ///
  /// 這些文字同時是送到後端的原因內容，也是測試用來指認選項的依據，改動要連同
  /// 畫面規格一起改，不在這裡自行增刪。
  List<String> get presetReasons => switch (this) {
        HoldSheetMode.defer => const ['請勿打擾', '住客還在房內', '先處理別的單'],
        HoldSheetMode.interrupt => const [
            '缺備品，去拿',
            '需要工務協助',
            '住客回房',
            '臨時被叫去支援',
          ],
      };

  /// 送出這筆原因，回傳後端認定的新任務狀態。
  Future<TaskDetail> submit(String taskId, String reason) => switch (this) {
        HoldSheetMode.defer =>
          ApiClient.instance.deferTask(taskId, reason),
        HoldSheetMode.interrupt =>
          ApiClient.instance.interruptTask(taskId, reason),
      };
}

/// 這個選項本身不是原因文字，選它代表員工要自己填一段說明。
const String _otherReasonOption = '其他原因';

/// 中斷清單裡唯一不是「說明一段原因」的選項：選它要開一張工務單。
const String _needsMaintenanceOption = '需要工務協助';

/// 說明文字的字數上限，與後端對原因長度的限制同值。
const int _maxReasonLength = 60;

/// 面板收起來時交給任務頁的結論。
///
/// 任務頁對兩種結局要做的事完全不同：一種是把畫面換成後端回的新任務狀態，
/// 另一種是接著開工務單表單。兩者共用一個可空欄位的話，任務頁每次都得先判
/// 斷這次拿到的是哪一種，而判斷錯的那一次會把表單該開的時候當成已送出。
sealed class HoldSheetResult {
  const HoldSheetResult();
}

/// 原因已經送出，[task] 是後端認定的新任務狀態。
class HoldSheetSubmitted extends HoldSheetResult {
  const HoldSheetSubmitted(this.task);

  final TaskDetail task;
}

/// 員工在中斷清單選了「需要工務協助」：這一步不送原因，改開工務單表單
/// （`Main.dc.html:4954`）。表單送出時才會連同把原任務轉為已中斷。
class HoldSheetNeedsMaintenance extends HoldSheetResult {
  const HoldSheetNeedsMaintenance();
}

/// 面板裡的第幾步。
///
/// 選「其他原因」是整個面板換成第二步，不是在清單下面展開一張卡
/// （`Main.dc.html:1553`）：標題、左上鈕與置底鈕三樣東西都跟著換，而清單會
/// 消失。寫成展開的話，這三樣各自都得記住自己現在該長什麼樣。
enum _HoldStep {
  /// 預設原因清單。點一列就送出，所以這一步沒有置底鈕。
  reasons,

  /// 自填說明。只有這一步有置底的「確定延後／確定中斷」。
  other,
}

class HoldSheet extends StatefulWidget {
  const HoldSheet({super.key, required this.mode, required this.taskId});

  final HoldSheetMode mode;

  /// 要被延後或中斷的任務。
  final String taskId;

  /// 任務頁開啟面板的唯一入口。
  ///
  /// 員工沒有送出就關掉面板時回傳 null，任務頁什麼都不必做。
  static Future<HoldSheetResult?> show(
    BuildContext context, {
    required HoldSheetMode mode,
    required String taskId,
  }) {
    return TmsSheetRoute.show<HoldSheetResult>(
      context,
      (_) => HoldSheet(mode: mode, taskId: taskId),
    );
  }

  @override
  State<HoldSheet> createState() => _HoldSheetState();
}

class _HoldSheetState extends State<HoldSheet> {
  _HoldStep _step = _HoldStep.reasons;

  final TextEditingController _noteController = TextEditingController();

  @override
  void initState() {
    super.initState();
    // 字數提示要跟著每一個字更新，所以監聽欄位本身，不等到送出才讀一次。
    _noteController.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  /// 自填說明目前可不可以送出。
  ///
  /// 這個值要經由 [TmsSemantics] 顯式傳出去。Flutter 的按鈕在沒有 onPressed 時
  /// 不會把「不可用」寫進 accessibility 樹，少了這一層，自動化測試讀到的按鈕
  /// 永遠是可用的，字數超過仍然驗不出禁用。
  bool get _canSubmitNote {
    final note = _noteController.text.trim();
    return note.isNotEmpty && note.length <= _maxReasonLength;
  }

  /// 點了一列預設原因。
  ///
  /// 「其他原因」與「需要工務協助」不是原因文字，是往下一步走的入口；其餘每
  /// 一列的文字就是要送出去的原因，點下去直接送（`Main.dc.html:4954`）。
  void _pickReason(String option) {
    switch (option) {
      case _otherReasonOption:
        setState(() => _step = _HoldStep.other);
      case _needsMaintenanceOption:
        Navigator.of(context).pop(const HoldSheetNeedsMaintenance());
      default:
        _submit(option);
    }
  }

  Future<void> _submit(String reason) async {
    // 離線時延後與中斷都擋在這裡，不送請求。這兩個動作會把單子留在手機以外
    // 的地方等別人接手，後端不知情的話，主管與同仁看到的仍然是一筆沒人動過
    // 的任務。
    if (ConnectionStatus.instance.isOffline) {
      await showOfflineBlockedAlert(context);
      return;
    }
    final updated = await widget.mode.submit(widget.taskId, reason);
    if (!mounted) {
      return;
    }
    Navigator.of(context).pop(HoldSheetSubmitted(updated));
  }

  @override
  Widget build(BuildContext context) {
    final onList = _step == _HoldStep.reasons;
    return TmsSemantics(
      id: 'hold.sheet',
      explicitChildNodes: true,
      child: TmsSheet(
        title: onList ? widget.mode.question : _otherReasonOption,
        leading: onList ? TmsSheetLeading.close : TmsSheetLeading.back,
        onLeadingTap: onList
            ? () => Navigator.of(context).pop()
            : () => setState(() => _step = _HoldStep.reasons),
        bottomAction: onList
            ? null
            : TmsSemantics(
                id: 'hold.confirm-button',
                enabled: _canSubmitNote,
                child: TmsSheetButton(
                  label: '確定${widget.mode.title}',
                  onPressed: _canSubmitNote
                      ? () => _submit(_noteController.text.trim())
                      : null,
                ),
              ),
        children: onList ? [_reasonList()] : [_noteCard()],
      ),
    );
  }

  Widget _reasonList() {
    final options = [...widget.mode.presetReasons, _otherReasonOption];
    return TmsGroupedSection(
      style: TmsCardStyle.inSheet,
      trailingDivider: true,
      children: [
        for (final option in options)
          TmsSemantics(
            id: 'hold.reason.$option',
            child: TmsOptionRow.reason(
              title: Text(option),
              onTap: () => _pickReason(option),
            ),
          ),
      ],
    );
  }

  Widget _noteCard() {
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
          const Text('請簡單說明原因', style: TmsText.footnoteEmphasized),
          const SizedBox(height: TmsSpacing.sm),
          TmsSemantics(
            id: 'hold.other-reason-field',
            child: CupertinoTextField(
              controller: _noteController,
              placeholder: '例如：住客說下午 2 點後再來打掃',
              maxLines: 3,
              minLines: 3,
              padding: EdgeInsets.zero,
              decoration: const BoxDecoration(),
              style: TmsText.callout,
            ),
          ),
          const SizedBox(height: TmsSpacing.sm),
          // 不另外指定 label：文字本身就是子節點的 label，顯式再給一次
          // 會被接成兩份，讀值變成同一段文字出現兩遍。
          TmsSemantics(
            id: 'hold.char-count',
            child: Text(
              _charCountText,
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

  /// 欄位下方的即時字數，格式是「目前字數 / 上限」。
  String get _charCountText =>
      '${_noteController.text.length} / $_maxReasonLength';
}
