import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/main.dart';

void main() {
  testWidgets('App 啟動後顯示登入畫面的員工編號、密碼欄位與登入按鈕',
      (WidgetTester tester) async {
    await tester.pumpWidget(const TmsStaffApp());
    await tester.pump();

    // 沒有記住帳號（共用手機模式）時，登入畫面要有員工編號與密碼兩個輸入欄。
    expect(find.text('員工編號'), findsOneWidget);
    expect(find.text('密碼'), findsOneWidget);
    expect(find.text('登入'), findsWidgets);
  });
}
