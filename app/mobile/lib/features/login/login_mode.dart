import '../../core/test_backdoor.dart';

enum LoginMode {
  remembered,

  otherAccount,

  sharedDevice,
}

class LoginCopy {
  const LoginCopy._({
    required this.mode,
    required this.title,
    required this.subtitle,
    required this.switchLabel,
  });

  factory LoginCopy.of({
    required RememberedAccount? remembered,
    required bool switchedToOtherAccount,
  }) {
    if (remembered == null) {
      return const LoginCopy._(
        mode: LoginMode.sharedDevice,
        title: '登入',
        subtitle: '這是共用手機，請用自己的員工編號登入。員工編號在識別證上。',
        switchLabel: null,
      );
    }
    if (switchedToOtherAccount) {
      return LoginCopy._(
        mode: LoginMode.otherAccount,
        title: '登入',
        subtitle: '輸入員工編號和密碼。員工編號在識別證上。',
        switchLabel: '用 ${remembered.name}（${remembered.loginId}）登入',
      );
    }
    return LoginCopy._(
      mode: LoginMode.remembered,
      title: '歡迎回來，${remembered.name}',
      subtitle: '輸入密碼就能開始今天的班。',
      switchLabel: '不是${remembered.name}？用其他帳號登入',
    );
  }

  final LoginMode mode;

  final String title;

  final String subtitle;

  final String? switchLabel;

  bool get showsIdentityCard => mode == LoginMode.remembered;

  bool get showsStaffIdField => mode != LoginMode.remembered;
}
