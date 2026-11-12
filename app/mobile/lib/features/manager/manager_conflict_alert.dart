import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_alert.dart';
import 'manager_ids.dart';

/// 主管動作送出時後端回 409 `state_conflict`（這張單已被別人處理）時彈的提示。
///
/// 查房送出與工務單的核准、退回、驗收共用這一個：說法、按鈕與 accessibility id
/// （[ManagerIds.conflictAlert]）都一樣，主管不管在哪一頁撞到都看到同一句話。
/// 按「知道了」關掉後 Future 才完成，呼叫端接著退回清單；清單的重取由開那一頁
/// 的那一端負責。
///
/// [message] 是補充說明，依動作不同換說法（查房是「另一位主管剛查過這間房」）。
Future<void> showManagerConflictAlert(
  BuildContext context, {
  String message = '可能是另一位主管剛處理過，或這張單被改派了。',
}) {
  return showCupertinoDialog<void>(
    context: context,
    builder: (dialogContext) => TmsSemantics(
      id: ManagerIds.conflictAlert,
      // 對話框層顯式帶標題，測試定位到這個 id 就能讀到「這張單已被處理」。
      label: '這張單已被處理',
      explicitChildNodes: true,
      child: TmsAlert(
        title: '這張單已被處理',
        message: message,
        actions: [
          TmsAlertAction(
            label: '知道了',
            style: TmsAlertActionStyle.primary,
            onPressed: () => Navigator.of(dialogContext).pop(),
          ),
        ],
      ),
    ),
  );
}
