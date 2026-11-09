import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/test_backdoor.dart';
import 'package:tms_staff_app/core/widgets/tms_alert.dart';
import 'package:tms_staff_app/features/login/login_screen.dart';
import 'package:tms_staff_app/features/login/login_widgets.dart';

/// 驗登入頁的三種模式各自顯示什麼，以及語言鈕、眼睛鈕與送出鈕的啟用條件。
///
/// 三種模式的差別只在幾句話與有沒有某一列，肉眼比對最容易看漏；模式一旦判錯，
/// 員工會在共用手機上看到別人的名字，或是在自己的手機上被要求重打員工編號。
void main() {
  tearDown(() {
    TestHooks.instance.reset();
  });

  Future<void> pumpLogin(WidgetTester tester, {String? remembers}) async {
    TestHooks.instance.rememberedAccount.value = remembers == null
        ? null
        : RememberedAccount(loginId: remembers, name: '阿美');
    await tester.pumpWidget(const CupertinoApp(home: LoginScreen()));
    await tester.pumpAndSettle();
  }

  testWidgets('記住帳號時顯示身分卡與歡迎詞，沒有員工編號欄（Main.dc.html:2340、4525）', (tester) async {
    await pumpLogin(tester, remembers: 'H-0532');

    expect(find.text('歡迎回來，阿美'), findsOneWidget);
    expect(find.text('輸入密碼就能開始今天的班。'), findsOneWidget);
    expect(find.widgetWithText(LoginIdentityRow, '阿美'), findsOneWidget);
    expect(find.widgetWithText(LoginIdentityRow, 'H-0532'), findsOneWidget);
    // 頭像取名字的最後一個字（Main.dc.html:4504 的 acct.initial）。
    expect(find.text('美'), findsOneWidget);
    expect(find.text('員工編號'), findsNothing);
    expect(find.text('不是阿美？用其他帳號登入'), findsOneWidget);
  });

  testWidgets('按切換帳號改成輸入員工編號，再按一次切回來（Main.dc.html:2369、4532）', (tester) async {
    await pumpLogin(tester, remembers: 'H-0532');

    await tester.tap(find.text('不是阿美？用其他帳號登入'));
    await tester.pumpAndSettle();

    expect(find.text('登入'), findsWidgets);
    expect(find.text('輸入員工編號和密碼。員工編號在識別證上。'), findsOneWidget);
    expect(find.text('員工編號'), findsOneWidget);
    expect(find.byType(LoginIdentityRow), findsNothing);
    expect(find.text('用 阿美（H-0532）登入'), findsOneWidget);

    await tester.tap(find.text('用 阿美（H-0532）登入'));
    await tester.pumpAndSettle();

    expect(find.text('歡迎回來，阿美'), findsOneWidget);
  });

  testWidgets('共用手機用自己的副標，而且沒有切換帳號鈕（Main.dc.html:4525、4531）', (tester) async {
    await pumpLogin(tester);

    expect(find.text('這是共用手機，請用自己的員工編號登入。員工編號在識別證上。'), findsOneWidget);
    expect(find.text('員工編號'), findsOneWidget);
    expect(
      find.bySemanticsIdentifier('login.switch-account-button'),
      findsNothing,
    );
  });

  testWidgets('兩欄都空時送出鈕按不下去，兩欄都有字才能按（Main.dc.html:4509）', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpLogin(tester);

    CupertinoButton submit() => tester.widget<CupertinoButton>(
      find.descendant(
        of: find.bySemanticsIdentifier('login.submit-button'),
        matching: find.byType(CupertinoButton),
      ),
    );

    expect(submit().onPressed, isNull);

    await tester.enterText(
      find.bySemanticsIdentifier('login.password-field'),
      'Passw0rd!',
    );
    await tester.pump();
    // 共用手機模式還缺員工編號，光有密碼送不出去。
    expect(submit().onPressed, isNull);

    await tester.enterText(
      find.bySemanticsIdentifier('login.shared-device.staff-id-field'),
      'h-0532',
    );
    await tester.pump();
    expect(submit().onPressed, isNotNull);

    // 員工編號一律轉大寫（Main.dc.html:2353）。
    expect(find.text('H-0532'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('眼睛鈕切換密碼要不要遮起來（Main.dc.html:2361）', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpLogin(tester, remembers: 'H-0532');

    CupertinoTextField passwordField() => tester.widget<CupertinoTextField>(
      find.descendant(
        of: find.bySemanticsIdentifier('login.password-field'),
        matching: find.byType(CupertinoTextField),
      ),
    );

    expect(passwordField().obscureText, isTrue);

    await tester.tap(
      find.bySemanticsIdentifier('login.password-visibility-button'),
    );
    await tester.pumpAndSettle();

    expect(passwordField().obscureText, isFalse);

    handle.dispose();
  });

  testWidgets('忘記密碼走 TmsAlert，文案照原型（Main.dc.html:4534）', (tester) async {
    await pumpLogin(tester);

    await tester.tap(find.text('忘記密碼？'));
    await tester.pumpAndSettle();

    expect(find.byType(TmsAlert), findsOneWidget);
    expect(find.byType(CupertinoAlertDialog), findsNothing);
    expect(find.text('忘記密碼'), findsOneWidget);
    expect(find.text('請主管在後台重設。重設後你會拿到臨時密碼，用它登入時會直接要求設定新密碼。'), findsOneWidget);
    expect(find.text('好'), findsOneWidget);
  });

  testWidgets('語言面板列出五種語言，選完語言鈕跟著換（Main.dc.html:2374、4595）', (tester) async {
    await pumpLogin(tester);

    expect(find.text('繁體中文'), findsOneWidget);

    await tester.tap(find.bySemanticsIdentifier('login.language-button'));
    await tester.pumpAndSettle();

    expect(find.text('語言'), findsOneWidget);
    expect(find.text('完成'), findsOneWidget);
    for (final native in <String>[
      '繁體中文',
      '简体中文',
      'English',
      '日本語',
      'Tiếng Việt',
    ]) {
      expect(find.text(native), findsWidgets, reason: native);
    }

    await tester.tap(find.text('日本語'));
    await tester.pumpAndSettle();

    expect(find.text('日本語'), findsOneWidget);
    expect(find.text('繁體中文'), findsNothing);
  });
}
