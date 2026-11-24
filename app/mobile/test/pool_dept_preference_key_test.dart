import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/features/manager/pool_dept_preference.dart';

void main() {
  test('待認領部門偏好的鍵名與端對端測試直接讀寫的名字一致', () {
    // 端對端測試不經過 App，直接用 `simctl spawn defaults` 讀寫 NSUserDefaults
    // 的 `flutter.pool.dept`：
    // app/bdd/src/test/java/com/athena/app/bdd/steps/common/CommonGivenSteps.java
    // 的 POOL_DEPT_PREFERENCE_KEY。改這裡要一起改那邊。
    expect(PoolDeptPreference.key, 'pool.dept');
  });
}
