import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

/// Flutter 測試視窗預設是 800x600，`ListView` 不會建出捲到視窗外的列，
/// 所以改用手機的邏輯尺寸。
const Size phoneLogicalSize = Size(402, 874);

/// 把測試視窗設成 [phoneLogicalSize]，測試結束後還原。
void usePhoneSurface(WidgetTester tester) {
  tester.view.physicalSize = phoneLogicalSize;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

/// 關掉動畫再測。畫面上有無限循環的動畫時，`pumpAndSettle` 永遠等不到畫面
/// 靜止，會一直等到逾時。
///
/// 在 `main()` 裡呼叫一次，同一個檔案的每個測試開始前都會關掉動畫，結束後還原。
void turnOffAnimationsForEachTest() {
  setUp(() {
    TestWidgetsFlutterBinding.ensureInitialized()
        .platformDispatcher
        .accessibilityFeaturesTestValue = const FakeAccessibilityFeatures(
      disableAnimations: true,
    );
  });

  tearDown(() {
    TestWidgetsFlutterBinding.ensureInitialized().platformDispatcher
        .clearAccessibilityFeaturesTestValue();
  });
}
