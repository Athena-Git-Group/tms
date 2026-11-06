import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';

/// 今日進度的共用取用路徑：房務首頁與工務首頁的數字都從這一份 `GET /me/today`
/// 的回應來。
///
/// 怎麼做到：把這個元件放在畫面樹的上層，它在建立時取一次今日進度並記住結果，
/// 底下任何一個元件用 [todayOf] 讀同一份資料、用 [errorOf] 讀取失敗的原因。
/// 資料還沒回來時 [todayOf] 回 null，讀的一方自己決定這段期間要畫什麼。
///
/// 不這樣做的話，同一個畫面上每個要用今日進度的區塊都得自己打一次端點：
/// 兩次回應之間資料可能已經變了，畫面上就會同時出現兩組對不起來的數字，
/// 而且「數字從哪裡來」會散在各個區塊裡，改動其中一處時很難發現其他處沒跟著改。
class MyTodayScope extends StatefulWidget {
  const MyTodayScope({super.key, required this.child});

  final Widget child;

  /// 這個子樹共用的今日進度；還在載入或載入失敗時回 null。
  static MyToday? todayOf(BuildContext context) => _dataOf(context)?.today;

  /// 載入失敗的原因；成功或還在載入時回 null。
  static Object? errorOf(BuildContext context) => _dataOf(context)?.error;

  static _MyTodayData? _dataOf(BuildContext context) {
    final data = context.dependOnInheritedWidgetOfExactType<_MyTodayData>();
    assert(data != null, '要讀今日進度的元件必須放在 MyTodayScope 底下');
    return data;
  }

  @override
  State<MyTodayScope> createState() => _MyTodayScopeState();
}

class _MyTodayScopeState extends State<MyTodayScope> {
  MyToday? _today;
  Object? _error;
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    _load();
    ConnectionStatus.instance.unstable.addListener(_syncPolling);
    _syncPolling();
  }

  @override
  void dispose() {
    ConnectionStatus.instance.unstable.removeListener(_syncPolling);
    _pollTimer?.cancel();
    super.dispose();
  }

  /// 即時推送不可靠時，畫面改成每隔固定時間自己重查一次。
  ///
  /// 連線正常時不開這個計時器：多數時間推送是可靠的，固定輪詢只是讓手機
  /// 多耗電、後端多收一批沒有必要的查詢。
  void _syncPolling() {
    _pollTimer?.cancel();
    if (!ConnectionStatus.instance.isUnstable) {
      _pollTimer = null;
      return;
    }
    _pollTimer = Timer.periodic(degradedPollInterval, (_) => _load());
  }

  Future<void> _load() async {
    final MyToday today;
    try {
      today = await ApiClient.instance.getMyToday();
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = error;
        });
      }
      return;
    }
    if (mounted) {
      setState(() {
        _today = today;
        _error = null;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return _MyTodayData(today: _today, error: _error, child: widget.child);
  }
}

class _MyTodayData extends InheritedWidget {
  const _MyTodayData({
    required this.today,
    required this.error,
    required super.child,
  });

  final MyToday? today;
  final Object? error;

  @override
  bool updateShouldNotify(_MyTodayData oldWidget) =>
      today != oldWidget.today || error != oldWidget.error;
}
