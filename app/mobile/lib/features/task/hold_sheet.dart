import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';

/// 員工暫時做不下去時，用這個面板說明原因，交接的人與主管才看得到任務為什麼停住。
///
/// 延後與中斷是同一套互動：點一個預設原因把它選起來，再點確認送出；預設原因
/// 都不適用時選「其他原因」，多出一個說明欄，說明要 1 到 60 字才送得出去。
/// 兩者的差別只有預設原因清單與送出時呼叫的端點。
///
/// 說明的字數上限是後端自己把關的，超過時後端拒收整筆操作。面板這層的字數
/// 提示與送出鈕禁用，是為了讓員工在按下去之前就知道寫太長了，不是權威判定。
enum HoldSheetMode {
  /// 還沒開工的任務先放著，之後再回來做。
  defer,

  /// 做到一半被打斷，任務暫停並停止計時。
  interrupt,
}

/// 這個選項本身不是原因文字，選它代表員工要自己填一段說明。
const String _otherReasonOption = '其他原因';

/// 說明文字的字數上限，與後端對原因長度的限制同值。
const int _maxReasonLength = 60;

class HoldSheet extends StatefulWidget {
  const HoldSheet({super.key, required this.mode, required this.taskId});

  final HoldSheetMode mode;

  /// 要被延後或中斷的任務。
  final String taskId;

  /// 任務頁開啟面板的唯一入口。
  ///
  /// 送出成功時回傳後端認定的新任務狀態，讓任務頁以後端的說法更新畫面，而不是
  /// 自己猜任務變成什麼狀態；員工沒有送出就關掉面板時回傳 null。
  static Future<TaskDetail?> show(
    BuildContext context, {
    required HoldSheetMode mode,
    required String taskId,
  }) {
    return showCupertinoModalPopup<TaskDetail>(
      context: context,
      builder: (_) => HoldSheet(mode: mode, taskId: taskId),
    );
  }

  /// 兩種用途各自的預設原因。
  ///
  /// 這些文字同時是送到後端的原因內容，也是測試用來指認選項的依據，改動要連同
  /// 畫面規格一起改，不在這裡自行增刪。
  static List<String> presetReasons(HoldSheetMode mode) {
    return switch (mode) {
      HoldSheetMode.defer => const ['請勿打擾', '住客還在房內', '先處理別的單'],
      HoldSheetMode.interrupt => const [
          '缺備品，去拿',
          '需要工務協助',
          '住客回房',
          '臨時被叫去支援',
        ],
    };
  }

  @override
  State<HoldSheet> createState() => _HoldSheetState();
}

class _HoldSheetState extends State<HoldSheet> {
  /// 目前選中的那一列：預設原因的文字，或 [_otherReasonOption]。
  String? _selectedOption;

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

  bool get _isOtherSelected => _selectedOption == _otherReasonOption;

  /// 真正要送給後端的原因文字；員工還沒選任何一列時沒有東西可送，回 null。
  String? get _reasonToSubmit {
    if (_selectedOption == null) {
      return null;
    }
    return _isOtherSelected ? _noteController.text.trim() : _selectedOption;
  }

  /// 送出鈕目前可不可以按。
  ///
  /// 這個值要經由 [TmsSemantics] 顯式傳出去。Flutter 的按鈕在沒有 onPressed 時
  /// 不會把「不可用」寫進 accessibility 樹，少了這一層，自動化測試讀到的按鈕
  /// 永遠是可用的，字數超過仍然驗不出禁用。
  bool get _canSubmit {
    final reason = _reasonToSubmit;
    if (reason == null) {
      return false;
    }
    return reason.isNotEmpty && reason.length <= _maxReasonLength;
  }

  Future<void> _submit() async {
    final reason = _reasonToSubmit;
    if (reason == null) {
      return;
    }
    final updated = switch (widget.mode) {
      HoldSheetMode.defer =>
        await ApiClient.instance.deferTask(widget.taskId, reason),
      HoldSheetMode.interrupt =>
        await ApiClient.instance.interruptTask(widget.taskId, reason),
    };
    if (!mounted) {
      return;
    }
    Navigator.of(context).pop(updated);
  }

  @override
  Widget build(BuildContext context) {
    final title = widget.mode == HoldSheetMode.defer ? '延後' : '中斷';
    final options = [
      ...HoldSheet.presetReasons(widget.mode),
      _otherReasonOption,
    ];

    return TmsSemantics(
      id: 'hold.sheet',
      explicitChildNodes: true,
      child: Container(
        width: double.infinity,
        // 底部要把鍵盤佔掉的高度讓出來。面板貼在畫面最下緣，鍵盤升起時會整個
        // 蓋住說明欄與送出鈕，員工既看不到自己打了什麼，也按不到確認。
        padding: EdgeInsets.fromLTRB(
          16,
          16,
          16,
          16 + MediaQuery.viewInsetsOf(context).bottom,
        ),
        color: CupertinoColors.white,
        child: SafeArea(
          top: false,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '$title原因',
                style:
                    const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              for (final option in options)
                TmsSemantics(
                  id: 'hold.reason.$option',
                  child: CupertinoButton(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    onPressed: () => setState(() => _selectedOption = option),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        option,
                        style: TextStyle(
                          fontWeight: _selectedOption == option
                              ? FontWeight.bold
                              : FontWeight.normal,
                        ),
                      ),
                    ),
                  ),
                ),
              if (_isOtherSelected) ...[
                TmsSemantics(
                  id: 'hold.other-reason-field',
                  child: CupertinoTextField(
                    controller: _noteController,
                    placeholder: '請簡單說明',
                    maxLines: 2,
                  ),
                ),
                const SizedBox(height: 4),
                // 不另外指定 label：文字本身就是子節點的 label，顯式再給一次
                // 會被接成兩份，讀值變成同一段文字出現兩遍。
                TmsSemantics(
                  id: 'hold.char-count',
                  child: Text(_charCountText),
                ),
              ],
              const SizedBox(height: 16),
              TmsSemantics(
                id: 'hold.confirm-button',
                enabled: _canSubmit,
                child: CupertinoButton.filled(
                  onPressed: _canSubmit ? _submit : null,
                  child: const Text('確認'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 欄位下方的即時字數，格式是「目前字數 / 上限」。
  String get _charCountText =>
      '${_noteController.text.length} / $_maxReasonLength';
}
