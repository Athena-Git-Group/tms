import 'dart:async';

import 'package:tms_staff_app/core/repair_photo_capture.dart';

/// 單元測試沒有相機也沒有相簿外掛，跟自動化測試版一樣改送固定的測試照片。
Future<void> testExecutable(FutureOr<void> Function() testMain) async {
  repairPhotoPicker = testBuildRepairPhotoPicker;
  await testMain();
}
