import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';

/// 完成慶祝骨架畫面（ui-plan 畫面 4）：只放版面與 accessibility id，
/// 不接資料、不實作互動。
///
/// 標題文字（`celebrate.title`）是 dsl 驗收點；離線完成時的「已離線暫存」
/// 提示是狀態相關元素，等接上同步狀態後才掛。
class CelebrateScreen extends StatelessWidget {
  const CelebrateScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'celebrate.screen',
      child: CupertinoPageScaffold(
        child: SafeArea(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const TmsSemantics(
                  id: 'celebrate.title',
                  child: Text(
                    '這間做完了！',
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(height: 24),
                TmsSemantics(
                  id: 'celebrate.back-home-button',
                  child: CupertinoButton.filled(
                    onPressed: () {
                      // 回到導航堆疊最底的首頁；慶祝頁之後的「下一間卡」
                      // 呈現是首頁 feature 的事，這裡只負責返回。
                      Navigator.of(context).popUntil((route) => route.isFirst);
                    },
                    child: const Text('回到首頁'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
