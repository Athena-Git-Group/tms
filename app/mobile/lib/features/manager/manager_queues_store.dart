import 'package:flutter/foundation.dart';

import '../../core/api_client.dart';

/// 主管首頁與主管清單共用的那一份 `GET /me/manager-queues` 回應。
///
/// 格子數字與點進去的清單必須出自同一份回應，兩邊才對得起來；所以回應只存
/// 這裡一份，首頁與清單都聽 [value]，要重取也只走 [refresh] 這一個入口。之後
/// 主管在清單上處理完一張單，呼叫一次 [refresh]，首頁格子與清單會一起換成
/// 新的數字，不會出現清單少了一列、首頁還是舊數字的情形。
///
/// 還沒取到或取失敗時 [value] 是 null（取失敗時保留上一次成功的回應）。
class ManagerQueuesStore extends ValueNotifier<ManagerQueues?> {
  ManagerQueuesStore() : super(null);

  /// 重取一次主管待辦並通知在聽的畫面。取失敗時不動目前的值，畫面維持原樣。
  Future<void> refresh() async {
    final ManagerQueues queues;
    try {
      queues = await ApiClient.instance.getManagerQueues();
    } catch (_) {
      return;
    }
    value = queues;
  }

  /// 某一類的數字；[queue] 是 inspection 或 verify。還沒取到時回 null。
  int? countOf(String queue) {
    final counts = value?.counts;
    if (counts == null) {
      return null;
    }
    return queue == 'inspection' ? counts.inspection : counts.verify;
  }
}
