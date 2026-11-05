import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';

/// 報修面板骨架（ui-plan 畫面 6 的 `repairSheet`，即工務單表單）：只放
/// 版面與 accessibility id，不接資料、不實作互動。
///
/// 照片欄位標題與加入按鈕是 dsl 驗收點（`repair.photo.count-label`／
/// `repair.photo.add-button`）；重複開單提示（`repair.duplicate-ticket.*`）、
/// 急件判定卡（`repair.urgent-judgement.*`）與照片縮圖（`repair.photo.{index}.*`）
/// 都是依資料出現的元素，等接上開單前檢查與相機後才掛。
class RepairSheet extends StatelessWidget {
  const RepairSheet({super.key});

  /// 任務頁的開啟入口統一走這裡，之後換成有狀態的面板時呼叫端不用動。
  static Future<void> show(BuildContext context) {
    return showCupertinoModalPopup<void>(
      context: context,
      builder: (_) => const RepairSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'repair.sheet',
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
                '報修',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              const Text('問題類別'),
              const SizedBox(height: 16),
              const TmsSemantics(
                id: 'repair.photo.count-label',
                child: Text('照片（至少 1 張、最多 3 張）'),
              ),
              const SizedBox(height: 8),
              TmsSemantics(
                id: 'repair.photo.add-button',
                child: CupertinoButton(
                  // 骨架只露出可定位的按鈕；拍照／選相簿是後續 feature。
                  onPressed: () {},
                  child: const Text('加入照片'),
                ),
              ),
              const SizedBox(height: 16),
              TmsSemantics(
                id: 'repair.submit-button',
                // 沒照片時送出鈕禁用是既定規則，骨架就照禁用狀態曝露，
                // 並經 TmsSemantics 顯式標 enabled: false（CupertinoButton
                // 的 onPressed 為 null 時框架不會自動曝露 disabled）。
                enabled: false,
                child: const CupertinoButton.filled(
                  onPressed: null,
                  child: Text('送出'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
