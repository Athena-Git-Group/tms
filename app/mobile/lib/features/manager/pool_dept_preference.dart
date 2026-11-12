import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// 主管上次在待認領停在哪個部門，只記在這台手機上。
///
/// 主管沒有部門，待認領要看房務還是工務的池由他自己切；切過一次之後下次打開
/// 就停在那一池，重開 App 也一樣。這個選擇不是後端資料：它描述的是「這支手機上
/// 的這個人剛剛在看哪一池」，換一支手機登入就從頭來過，所以只放
/// `SharedPreferences`，不進契約與資料模型。
///
/// 值只有兩個：[housekeeping] 與 [engineering]。沒有紀錄時視為房務：房務的單
/// 比較多也比較急，主管第一次打開先看它。
abstract final class PoolDeptPreference {
  /// `SharedPreferences` 的鍵。iOS 上落成 `NSUserDefaults` 的 `flutter.pool.dept`；
  /// 測試層以 `simctl spawn defaults` 讀寫同一把鍵，鍵名改了那邊就讀不到。
  static const String key = 'pool.dept';

  static const String housekeeping = 'housekeeping';
  static const String engineering = 'engineering';

  static const Set<String> _depts = {housekeeping, engineering};

  /// 最近一次寫入的部門。推播要主管去看某一池時先寫這把鍵再切分頁，已經開著
  /// 的待認領靠這個通道得知要換池；沒人寫過時是 null。
  static final ValueNotifier<String?> changes = ValueNotifier<String?>(null);

  /// 讀這台手機記住的部門。沒有紀錄、或值不是兩個部門之一時回房務，並把房務
  /// 寫回去：畫面停在哪一池，手機上就要有同一筆紀錄，兩邊才不會對不上。
  static Future<String> read() async {
    final prefs = await SharedPreferences.getInstance();
    final stored = prefs.getString(key);
    if (stored != null && _depts.contains(stored)) {
      return stored;
    }
    await prefs.setString(key, housekeeping);
    return housekeeping;
  }

  /// 記住主管停在 [dept]（[housekeeping] 或 [engineering]），並通知在聽的畫面。
  static Future<void> write(String dept) async {
    assert(_depts.contains(dept), '待認領只有房務、工務兩池，收到：$dept');
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(key, dept);
    changes.value = dept;
  }
}
