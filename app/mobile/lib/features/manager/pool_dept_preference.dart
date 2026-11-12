import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

abstract final class PoolDeptPreference {
  /// shared_preferences 在 iOS 上把這把鍵存成 `NSUserDefaults` 的
  /// `flutter.pool.dept`，測試層以 `simctl spawn defaults` 讀寫的是那個名字。
  static const String key = 'pool.dept';

  static const String housekeeping = 'housekeeping';
  static const String engineering = 'engineering';

  static const Set<String> _depts = {housekeeping, engineering};

  static final ValueNotifier<String?> changes = ValueNotifier<String?>(null);

  static bool isPoolDept(String? dept) => _depts.contains(dept);

  static Future<String> read() async {
    final prefs = await SharedPreferences.getInstance();
    final stored = prefs.getString(key);
    if (stored != null && _depts.contains(stored)) {
      return stored;
    }
    await prefs.setString(key, housekeeping);
    return housekeeping;
  }

  static Future<void> write(String dept) async {
    assert(_depts.contains(dept), '待認領只有房務、工務兩池，收到：$dept');
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(key, dept);
    changes.value = dept;
  }
}
