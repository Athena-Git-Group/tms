import 'package:flutter/cupertino.dart';

import '../../core/test_backdoor.dart';
import '../../core/tms_semantics.dart';

/// 任務頁骨架畫面：顯示單一房號的任務內容。
///
/// 資料來源是 [TestHooks.seedTasks] 這份測試後門灌進來的 fixture，真後端
/// 接線前的過渡做法；見 `core/test_backdoor.dart` 檔頭說明。
///
/// 當任務狀態是「已中斷」且停住原因含「工務已修好」時，顯示恢復提示列
/// （`task.resume-banner`）——這是 Rule「工務單完修的推播提示可以回去
/// 恢復原任務」要驗的畫面狀態。
class TaskScreen extends StatelessWidget {
  const TaskScreen({super.key, required this.roomNo});

  final String roomNo;

  SeedTask? _findTask() {
    for (final task in TestHooks.instance.seedTasks) {
      if (task.roomNo == roomNo) {
        return task;
      }
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final task = _findTask();
    final showResumeBanner = task != null &&
        task.status == '已中斷' &&
        (task.pausedReason?.contains('工務已修好') ?? false);

    return TmsSemantics(
      id: 'task.screen',
      child: CupertinoPageScaffold(
        navigationBar: CupertinoNavigationBar(middle: Text('$roomNo 的任務頁')),
        child: SafeArea(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (showResumeBanner)
                TmsSemantics(
                  id: 'task.resume-banner',
                  child: Container(
                    width: double.infinity,
                    margin: const EdgeInsets.all(16),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: const Color(0xFFE6F7EC),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            '$roomNo 號房的工務已修好，您可以恢復原任務了',
                          ),
                        ),
                        TmsSemantics(
                          id: 'task.status-row.resume-button',
                          child: CupertinoButton(
                            // 骨架畫面先只露出可點的按鈕；呼叫後端把任務從
                            // 「已中斷」轉回「進行中」是後續功能。
                            onPressed: () {},
                            child: const Text('恢復任務'),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  task == null ? '找不到這個房號的任務' : '內容：${task.content}',
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
