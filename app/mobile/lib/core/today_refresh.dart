import 'package:flutter/foundation.dart';

/// 「今日進度該重查一次了」這件事的廣播點。
///
/// 任務頁、待認領池這些會改動任務狀態的畫面都蓋在分頁外殼上面，取不到外殼
/// 那一層的 `MyTodayScope`（InheritedWidget 只往下傳，推進去的畫面不在它底
/// 下）。沒有這個廣播點的話，員工把一間房做完、返回首頁，首頁讀的還是進場
/// 時取的那一份：主卡仍然寫著那一間「進行中」，已清完的間數也不會動，直到
/// 重開 App 才對得上。
///
/// 改動任務狀態的一方送出成功後呼叫 [request]，掛在畫面樹上的 `MyTodayScope`
/// 會重查 `GET /me/today`。只廣播「資料舊了」，不帶任何內容：新的數字一律由
/// 後端那一支回應決定，App 不自己在本地推算進度。
class TodayRefresh extends ChangeNotifier {
  TodayRefresh._();

  static final TodayRefresh instance = TodayRefresh._();

  /// 通知所有在聽的畫面重查今日進度。
  void request() => notifyListeners();
}
