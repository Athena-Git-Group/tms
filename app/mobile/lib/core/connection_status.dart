import 'package:flutter/foundation.dart';

import 'today_refresh.dart';

import 'api_client.dart';

/// 全 App 共用的連線狀態，以及離線期間還沒送出去的動作。
///
/// <h2>這個檔案要保證什麼</h2>
///
/// 不管員工在哪一個畫面，同一時間看到的連線狀態都是同一個：頂部那一列、被
/// 擋下來的動作、待同步的筆數，三者講的是同一件事。離線時做完的事也不會
/// 不見，連線一恢復就依員工操作的先後順序送給後端，而且同一次操作不管送
/// 幾遍，後端只認列一次。
///
/// <h2>怎麼做到</h2>
///
/// 連線狀態只有 [ConnectionStatus] 這一份，畫面要嘛讀它當下的值、要嘛監聽
/// 它的變動；沒有任何畫面自己判斷連線。離線期間被允許的動作交給
/// [OfflineQueue] 保管，每一筆在進佇列的當下就配一把 `Idempotency-Key`，
/// 再送一次時用的還是同一把。
///
/// 兩者放在同一個檔案，因為它們是同一件事的兩面：佇列要知道現在是不是離線
/// 才知道該不該送，連線狀態要報出待同步筆數才畫得出離線列。拆成兩個檔案時
/// 兩邊得互相 import，讀的人看不出哪一邊才是源頭。
///
/// <h2>連線狀態為什麼不自己偵測網路</h2>
///
/// 依規格，離線與連線異常一律由外部注入（自動化測試的裝置後門），App 不另
/// 做網路偵測。[ConnectionStatus] 是 App 這一側的唯一讀取點，注入端只是它的
/// 寫入者之一；將來真的要接網路偵測，也只是多一個寫入者，畫面一行都不必改。
class ConnectionStatus {
  ConnectionStatus._();

  static final ConnectionStatus instance = ConnectionStatus._();

  final ValueNotifier<bool> _offline = ValueNotifier<bool>(false);

  final ValueNotifier<bool> _unstable = ValueNotifier<bool>(false);

  /// 裝置是否離線。寫成可監聽的值，離線列才能跟著它即時出現與消失，離線
  /// 佇列也才能在它轉回連線的那一刻自動把待同步的動作送出去；用普通欄位的
  /// 話，畫面只會在下一次自己重畫時才發現狀態變了。
  ValueListenable<bool> get offline => _offline;

  /// 裝置仍連著網，但即時推送不可靠。連線異常列與降級輪詢讀這個值。
  ValueListenable<bool> get unstable => _unstable;

  /// 目前待同步的筆數，離線列直接顯示這個值。
  ValueListenable<int> get pendingSyncCount => OfflineQueue.instance.pendingCount;

  bool get isOffline => _offline.value;

  bool get isUnstable => _unstable.value;

  /// 切換離線狀態。由離線轉回連線時，待同步的動作會自己送出去，不必等員工
  /// 再點一次。
  void setOffline(bool offline) {
    if (_offline.value == offline) {
      return;
    }
    _offline.value = offline;
    if (!offline) {
      OfflineQueue.instance.flush();
    }
  }

  void setUnstable(bool unstable) => _unstable.value = unstable;

  /// 測試之間重置狀態，避免前一個測試設定的連線狀態漏到下一個測試。
  void reset() {
    _offline.value = false;
    _unstable.value = false;
  }
}

/// 即時推送不可靠時改用輪詢，這是兩次查詢之間相隔的時間。
///
/// 畫面上的那一列把這個秒數寫給員工看，所以文案要從這個值組出來；各寫各的
/// 會讓畫面承諾 5 秒、實際上卻是別的間隔。
const Duration degradedPollInterval = Duration(seconds: 5);

/// 離線期間仍然被允許的寫入動作，由這個佇列保管到連線恢復為止。
///
/// 送出失敗的那一筆留在佇列最前面，後面的不插隊。「送出去了但回應沒收到」
/// 和「根本沒送到」在手機這一端長得一模一樣；若換一把新鍵再送，後端會把它
/// 當成一次全新的動作而做第二遍，員工就會看到同一間房被完成兩次。
///
/// 全 App 只有一份。離線完成任務與離線開立工務單是同一件事的兩個入口，各做
/// 一套的話，離線列的待同步筆數要把兩份長度加起來才對，送出的順序也會各排
/// 各的，員工實際操作的先後就保不住了。
class OfflineQueue {
  OfflineQueue._();

  static final OfflineQueue instance = OfflineQueue._();

  /// 同一筆動作最多送幾次。試這麼多次還不成，多半不是一下子的連線不穩，
  /// 再試下去只是讓員工多等；留在佇列裡等下一次恢復連線反而比較快。
  static const int _maxAttempts = 5;

  /// 兩次之間隔多久。訂在幾百毫秒這個量級：短到員工不會覺得畫面卡著，長到
  /// 不會在連線真的斷了的時候連打後端五次。
  static const Duration _retryDelay = Duration(milliseconds: 300);

  final List<PendingOperation> _pending = <PendingOperation>[];

  /// 是否正在送佇列裡的動作。
  bool _flushing = false;

  /// 待同步的筆數，一律由佇列長度推出來。另外記一個計數器的話，只要有一條
  /// 路徑漏了加減，畫面上的數字就會跟實際還沒送出的動作對不起來。
  final ValueNotifier<int> pendingCount = ValueNotifier<int>(0);

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
    if (ConnectionStatus.instance.isOffline) {
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
  /// 一次。送失敗的那一筆會原地再試，最多 [_maxAttempts] 次，每次之間隔
  /// [_retryDelay]：剛恢復連線時的失敗多半是連線還不穩，隔一下再送就成了，
  /// 而放著不管的話員工會一直看到待同步，只能靠他自己再斷線重連一次才會
  /// 重試。試滿還是不成就停在那裡等下一次恢復連線，不跳過它繼續送後面的
  /// ——跳過會讓員工的操作順序在後端變成另一個順序。
  ///
  /// 同一時間只跑一輪。恢復連線可能連著觸發兩次（例如員工在網路斷續時來回
  /// 切換），兩輪同時跑會對同一筆動作併發送出。
  Future<void> flush() async {
    if (_flushing) {
      return;
    }
    _flushing = true;
    try {
      while (_pending.isNotEmpty && !ConnectionStatus.instance.isOffline) {
        final operation = _pending.first;
        if (!await _sendWithRetry(operation)) {
          return;
        }
        _pending.remove(operation);
        pendingCount.value = _pending.length;
        // 押在手機上的完成這時才真的進到後端，首頁的間數與主卡要跟著換。
        TodayRefresh.instance.request();
      }
    } finally {
      _flushing = false;
    }
  }

  /// 送出一筆動作，失敗就再試，回傳後端是否終於收下。
  Future<bool> _sendWithRetry(PendingOperation operation) async {
    for (var attempt = 1; attempt <= _maxAttempts; attempt++) {
      try {
        await operation.send(operation.idempotencyKey);
        return true;
      } catch (_) {
        if (attempt == _maxAttempts || ConnectionStatus.instance.isOffline) {
          return false;
        }
        await Future<void>.delayed(_retryDelay);
      }
    }
    return false;
  }

  void _add(PendingOperation operation) {
    _pending.add(operation);
    pendingCount.value = _pending.length;
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
