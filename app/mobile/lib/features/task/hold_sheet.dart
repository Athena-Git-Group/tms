import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';

/// 延後／中斷面板骨架（ui-plan 畫面 5 的 `holdSheet`）：只放版面與
/// accessibility id，不接資料、不實作互動。
///
/// 原因選項、「其他」的 60 字說明與送出行為是後續 feature；骨架先以
/// 占位文字呈現版面。
class HoldSheet extends StatelessWidget {
  const HoldSheet({super.key});

  /// 任務頁的開啟入口統一走這裡，之後換成有狀態的面板時呼叫端不用動。
  static Future<void> show(BuildContext context) {
    return showCupertinoModalPopup<void>(
      context: context,
      builder: (_) => const HoldSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'hold.sheet',
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
                '延後／中斷',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              const Text('請選擇原因'),
              const SizedBox(height: 16),
              TmsSemantics(
                id: 'hold.confirm-button',
                child: CupertinoButton.filled(
                  // 骨架只露出可定位的按鈕；選原因與送出是後續 feature。
                  onPressed: () {},
                  child: const Text('確認'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
