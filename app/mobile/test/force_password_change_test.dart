import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/widgets/tms_alert.dart';
import 'package:tms_staff_app/features/login/force_password_change_screen.dart';
import 'package:tms_staff_app/features/login/login_widgets.dart';
import 'package:tms_staff_app/features/login/password_rules.dart';

/// 驗強制改密頁的四條規則清單、相符綠勾、眼睛鈕與送出前的確認對話框。
///
/// 這一頁是員工拿到臨時密碼後唯一能走的路，走不過去就整個 App 都進不去。規則
/// 清單的打勾與送出鈕的啟用條件連動，算錯一條就會讓一組後端其實會收的密碼按
/// 不下去。
void main() {
  NewPasswordCheck check(String newPassword, [String confirmPassword = '']) =>
      NewPasswordCheck.of(
        newPassword: newPassword,
        confirmPassword: confirmPassword,
        temporaryPassword: 'temp-0000',
        loginId: 'H-0532',
      );

  group('密碼規則（Main.dc.html:4617-4622）', () {
    test('四條規則的順序與文案照原型', () {
      expect(check('').rules.map((rule) => rule.label).toList(), <String>[
        '至少 8 碼',
        '英文和數字都要有',
        '和臨時密碼不同',
        '不能是員工編號',
      ]);
    });

    test('空白時四條全都沒符合', () {
      expect(check('').rules.map((rule) => rule.satisfied).toList(), <bool>[
        false,
        false,
        false,
        false,
      ]);
    });

    test('七碼不夠，第八碼補上就符合第一條', () {
      expect(check('Abc1234').rules.first.satisfied, isFalse);
      expect(check('Abc12345').rules.first.satisfied, isTrue);
    });

    test('只有英文或只有數字都不算第二條符合', () {
      expect(check('Abcdefghij').rules[1].satisfied, isFalse);
      expect(check('1234567890').rules[1].satisfied, isFalse);
      expect(check('Abcdefg1').rules[1].satisfied, isTrue);
    });

    test('打的就是臨時密碼時第三條不符合', () {
      expect(check('temp-0000').rules[2].satisfied, isFalse);
      expect(check('temp-0001').rules[2].satisfied, isTrue);
    });

    test('員工編號當密碼時第三條以外那一條不符合，大小寫與分隔符號都算同一組', () {
      expect(check('H-0532').rules[3].satisfied, isFalse);
      expect(check('h0532').rules[3].satisfied, isFalse);
      // 原型連去掉開頭字母的 0532 也擋（Main.dc.html:4621）。
      expect(check('0532').rules[3].satisfied, isFalse);
      expect(check('Passw0rd!').rules[3].satisfied, isTrue);
    });

    test('兩次相符才算，兩格都空不算', () {
      expect(check('Passw0rd!', 'Passw0rd!').confirmMatches, isTrue);
      expect(check('', '').confirmMatches, isFalse);
    });

    test('確認欄還短於新密碼時先不報錯，打到一樣長還不一樣才報', () {
      expect(check('Passw0rd!', 'Pass').showsMismatch, isFalse);
      expect(check('Passw0rd!', 'Passw0rd?').showsMismatch, isTrue);
      expect(check('Passw0rd!', 'Passw0rd!').showsMismatch, isFalse);
    });

    test('四條全過又兩次相符才送得出去', () {
      expect(check('Passw0rd!', 'Passw0rd!').readyToSubmit, isTrue);
      expect(check('Passw0rd!', 'Passw0rd?').readyToSubmit, isFalse);
      expect(check('abcdefgh', 'abcdefgh').readyToSubmit, isFalse);
    });
  });

  Future<void> pumpScreen(WidgetTester tester) async {
    await tester.pumpWidget(
      const CupertinoApp(
        home: ForcePasswordChangeScreen(
          loginId: 'H-0532',
          temporaryPassword: 'temp-0000',
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('頂部是橘色鎖頭的提示卡，不是品牌招牌（Main.dc.html:708-717）', (tester) async {
    await pumpScreen(tester);

    expect(find.byType(LoginNoticeCard), findsOneWidget);
    expect(find.byType(LoginBrandHeader), findsNothing);
    expect(find.text('請先設定新密碼'), findsOneWidget);
    expect(find.text('你用的是主管發的臨時密碼。設定新密碼後才能繼續使用。'), findsOneWidget);
  });

  testWidgets('兩欄的標籤是新密碼與確認密碼，置底鈕是設定新密碼（Main.dc.html:4652、4655）', (
    tester,
  ) async {
    await pumpScreen(tester);

    expect(find.text('新密碼'), findsOneWidget);
    expect(find.text('確認密碼'), findsOneWidget);
    expect(find.text('再次輸入新密碼'), findsNothing);
    expect(find.text('設定新密碼'), findsWidgets);
    expect(find.text('確認'), findsNothing);
  });

  testWidgets('規則一條一列，符合的那一條換成綠色打勾（Main.dc.html:757-766）', (tester) async {
    await pumpScreen(tester);

    expect(find.text('至少 8 碼'), findsOneWidget);
    expect(find.text('英文和數字都要有'), findsOneWidget);
    expect(find.text('和臨時密碼不同'), findsOneWidget);
    expect(find.text('不能是員工編號'), findsOneWidget);
    expect(find.byType(LoginCheckBadge), findsNothing);
    expect(find.byType(LoginUncheckedMark), findsNWidgets(4));

    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.new-password-field',
      ),
      'Passw0rd!',
    );
    await tester.pump();

    expect(find.byType(LoginCheckBadge), findsNWidgets(4));
    expect(find.byType(LoginUncheckedMark), findsNothing);
  });

  testWidgets('兩次相符時確認欄右側多一顆綠勾（Main.dc.html:745）', (tester) async {
    await pumpScreen(tester);

    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.new-password-field',
      ),
      'Passw0rd!',
    );
    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.confirm-password-field',
      ),
      'Passw0rd!',
    );
    await tester.pump();

    // 四條規則各一顆，確認欄再一顆。
    expect(find.byType(LoginCheckBadge), findsNWidgets(5));
  });

  testWidgets('打到一樣長還不一樣就即時顯示紅字（Main.dc.html:754、4629）', (tester) async {
    await pumpScreen(tester);

    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.new-password-field',
      ),
      'Passw0rd!',
    );
    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.confirm-password-field',
      ),
      'Pass',
    );
    await tester.pump();
    expect(find.text('兩次輸入的新密碼不一樣'), findsNothing);

    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.confirm-password-field',
      ),
      'Passw0rd?',
    );
    await tester.pump();
    expect(find.text('兩次輸入的新密碼不一樣'), findsOneWidget);
  });

  testWidgets('規則沒全過按不下去，全過又相符才亮（Main.dc.html:4623、4654）', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester);

    CupertinoButton submit() => tester.widget<CupertinoButton>(
      find.descendant(
        of: find.bySemanticsIdentifier(
          'login.force-password-change.submit-button',
        ),
        matching: find.byType(CupertinoButton),
      ),
    );

    expect(submit().onPressed, isNull);

    // 只有英文、長度也不夠，兩條規則沒過。
    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.new-password-field',
      ),
      'abcdef',
    );
    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.confirm-password-field',
      ),
      'abcdef',
    );
    await tester.pump();
    expect(submit().onPressed, isNull);

    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.new-password-field',
      ),
      'Passw0rd!',
    );
    await tester.pump();
    // 新密碼改了，確認欄還停在舊的字，兩次不相符。
    expect(submit().onPressed, isNull);

    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.confirm-password-field',
      ),
      'Passw0rd!',
    );
    await tester.pump();
    expect(submit().onPressed, isNotNull);

    handle.dispose();
  });

  testWidgets('兩欄各有一顆眼睛鈕，按下去就不遮了（Main.dc.html:737、747）', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester);

    CupertinoTextField field(String id) => tester.widget<CupertinoTextField>(
      find.descendant(
        of: find.bySemanticsIdentifier(id),
        matching: find.byType(CupertinoTextField),
      ),
    );

    expect(
      field('login.force-password-change.new-password-field').obscureText,
      isTrue,
    );
    expect(
      field('login.force-password-change.confirm-password-field').obscureText,
      isTrue,
    );

    await tester.tap(
      find.bySemanticsIdentifier(
        'login.force-password-change.new-password-visibility-button',
      ),
    );
    await tester.pumpAndSettle();

    expect(
      field('login.force-password-change.new-password-field').obscureText,
      isFalse,
    );
    expect(
      field('login.force-password-change.confirm-password-field').obscureText,
      isTrue,
    );

    handle.dispose();
  });

  testWidgets('送出前先跳確認對話框，取消就不送（Main.dc.html:4642-4644）', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester);

    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.new-password-field',
      ),
      'Passw0rd!',
    );
    await tester.enterText(
      find.bySemanticsIdentifier(
        'login.force-password-change.confirm-password-field',
      ),
      'Passw0rd!',
    );
    await tester.pump();

    await tester.tap(
      find.bySemanticsIdentifier('login.force-password-change.submit-button'),
    );
    await tester.pumpAndSettle();

    expect(find.byType(TmsAlert), findsOneWidget);
    expect(find.byType(CupertinoAlertDialog), findsNothing);
    expect(find.text('設定新密碼？'), findsOneWidget);
    expect(find.text('這支手機會保持登入；其他裝置（例如櫃檯 iPad）上的這個帳號會被登出。'), findsOneWidget);
    expect(find.text('取消'), findsOneWidget);
    expect(find.text('設定'), findsOneWidget);

    await tester.tap(
      find.bySemanticsIdentifier(
        'login.force-password-change.confirm-alert.cancel-button',
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(TmsAlert), findsNothing);

    handle.dispose();
  });
}
