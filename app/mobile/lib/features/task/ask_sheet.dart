import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';

/// 查一下面板骨架（ui-plan 畫面 8 的 `askSheet`）：只放版面與
/// accessibility id，不接資料、不實作互動。
///
/// 推薦問題（`ask.suggestion.{index}`）、出處卡（`ask.answer.source-card*`）
/// 與查不到訊息（`ask.not-found-message`）都是依查詢結果出現的元素，
/// 等接上查詢後才掛；骨架先放固定的「問主管」入口。
class AskSheet extends StatelessWidget {
  const AskSheet({super.key});

  /// 任務頁的開啟入口統一走這裡，之後換成有狀態的面板時呼叫端不用動。
  static Future<void> show(BuildContext context) {
    return showCupertinoModalPopup<void>(
      context: context,
      builder: (_) => const AskSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'ask.sheet',
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        color: CupertinoColors.white,
        child: SafeArea(
          top: false,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '查一下',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              const Text('先查房間記錄或工務記錄，查不到再問主管'),
              const SizedBox(height: 16),
              TmsSemantics(
                id: 'ask.ask-manager-button',
                child: CupertinoButton(
                  // 骨架只露出可定位的按鈕；查詢與問主管是後續 feature。
                  onPressed: () {},
                  child: const Text('問主管'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
