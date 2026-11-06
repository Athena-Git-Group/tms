import 'package:flutter/foundation.dart';

import 'api_client.dart';
import 'test_backdoor.dart';

/// 手機離線期間仍然被允許的寫入動作，由這個佇列保管到連線恢復為止。
///
/// <h2>這個類別要保證什麼</h2>
///
/// 員工在離線時做完的事不會不見：動作先留在手機上，畫面標示待同步，連線
/// 一恢復就依員工操作的先後順序送給後端。而且同一次操作不管送幾遍，後端
/// 只會認列一次。
///
/// <h2>怎麼做到</h2>
///
/// 每一筆動作在進佇列的當下就配一把 `Idempotency-Key`，這把鍵跟著它一輩子，
/// 再送一次時用的還是同一把。送出失敗的那一筆留在佇列最前面，後面的不插隊。
///
/// 「送出去了但回應沒收到」和「根本沒送到」在手機這一端長得一模一樣。若換
/// 一把新鍵再送，後端會把它當成一次全新的動作而做第二遍，員工就會看到同一
/// 間房被完成兩次。
///
/// <h2>為什麼全 App 只有一份</h2>
///
/// 離線完成任務與離線開立工務單是同一件事的兩個入口，兩邊共用這一份佇列。
/// 各做一套的話，離線列的待同步筆數要把兩份長度加起來才對，送出的順序也會
/// 各排各的，員工實際操作的先後就保不住了。
class OfflineQueue {
  OfflineQueue._() {
    TestHooks.instance.forceOffline.addListener(_onOfflineChanged);
  }

  static final OfflineQueue instance = OfflineQueue._();

  final List<PendingOperation> _pending = <PendingOperation>[];

  /// 目前待同步的筆數，離線列直接顯示這個值。
  ///
  /// 這個值一律由佇列長度推出來。另外記一個計數器的話，只要有一條路徑漏了
  /// 加減，畫面上的數字就會跟實際還沒送出的動作對不起來。
  final ValueNotifier<int> pendingCount = ValueNotifier<int>(0);

  /// 目前是否離線。離線判定以測試注入的開關為準，App 不另做網路偵測。
  bool get isOffline => TestHooks.instance.forceOffline.value;

  /// 送出一個寫入動作，離線時改為留在手機上。
  ///
  /// 回傳值告訴呼叫端後端是否已經收到：true 代表送出去了，呼叫端照正常流程
  /// 往下走；false 代表這筆還在佇列裡，呼叫端要留在原地標示待同步，既不能當
  /// 成失敗、也不能當成成功。
  ///
  /// [kind] 是這筆動作的種類，例如 `complete`、`repair-ticket`，只給待同步的
  /// 呈現與排障用，不會送到後端。
  Future<bool> submit({
    required String kind,
    required Future<void> Function(String idempotencyKey) send,
  }) async {
    final operation = PendingOperation(
      kind: kind,
      idempotencyKey: ApiClient.newIdempotencyKey(),
      send: send,
    );
    if (isOffline) {
      _add(operation);
      return false;
    }
    await send(operation.idempotencyKey);
    return true;
  }

  /// 把一筆動作直接放進佇列，不嘗試送出。
  ///
  /// 給需要「手機上已經累積了幾筆待同步」當起始狀態的呼叫端用。產品流程一律
  /// 走 [submit]，它才會在連線正常時把動作直接送出去。
  void enqueue(PendingOperation operation) => _add(operation);

  /// 把還沒送出的動作交給後端，直到佇列清空或再次離線。
  ///
  /// 每一筆都用它自己那把鍵，所以就算其中某筆其實已經送到過，後端也只認列
  /// 一次。某一筆送失敗就停在那裡等下一次，不跳過它繼續送後面的：失敗多半是
  /// 連線又斷了，而跳過會讓員工的操作順序在後端變成另一個順序。
  Future<void> flush() async {
    while (_pending.isNotEmpty && !isOffline) {
      final operation = _pending.first;
      try {
        await operation.send(operation.idempotencyKey);
      } catch (_) {
        return;
      }
      _pending.remove(operation);
      pendingCount.value = _pending.length;
    }
  }

  void _add(PendingOperation operation) {
    _pending.add(operation);
    pendingCount.value = _pending.length;
  }

  /// 離線開關由 true 轉回 false 的那一刻就是連線恢復，待同步的動作要自己送
  /// 出去，不必等員工再點一次。
  void _onOfflineChanged() {
    if (!isOffline) {
      flush();
    }
  }
}

/// 佇列裡的一筆動作：種類、它專屬的那把鍵，以及真正送出去的那一步。
class PendingOperation {
  PendingOperation({
    required this.kind,
    required this.idempotencyKey,
    required this.send,
  });

  final String kind;

  final String idempotencyKey;

  /// 送出這筆動作。[OfflineQueue] 再送一次時會把同一把鍵傳進來，所以這個函式
  /// 必須用參數收到的鍵，不可以自己另外產生一把。
  final Future<void> Function(String idempotencyKey) send;
}
