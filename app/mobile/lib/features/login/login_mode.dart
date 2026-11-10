import '../../core/test_backdoor.dart';

/// 登入畫面的三種模式。
///
/// 模式不是一個布林值。「這支手機記住誰」與「員工這次想不想用那個帳號」是兩件
/// 獨立的事：共用手機永遠沒得選，記住帳號的手機則可以當場切去輸入別人的員工
/// 編號、也可以再切回來。用一個布林值表示的話，「記住阿美但這次要用阿德登入」
/// 和「這支手機誰都沒記住」會縮成同一個狀態，而這兩者的大標、副標與那顆切換鈕
/// 都不一樣。
enum LoginMode {
  /// 這支手機記住了某個帳號，而且員工就要用它登入：顯示身分卡，只要輸入密碼。
  remembered,

  /// 這支手機記住了某個帳號，但員工這次要用別人的帳號：輸入員工編號與密碼，
  /// 底下留一顆切回去的鈕。
  otherAccount,

  /// 共用手機，誰都沒記住：輸入員工編號與密碼，沒有切換對象可言。
  sharedDevice,
}

/// 登入畫面隨模式變動的那幾句話與那顆鈕。
///
/// 三種模式的文案在原型裡是三組寫死的字（`Main.dc.html:4525`、`Main.dc.html:4531`），
/// 散在畫面程式裡就會變成巢狀的三元運算式；要核對哪一句對應哪一種模式，得先在
/// 心裡把條件展開一次。集中在這裡之後，比對原型就是逐行讀。
class LoginCopy {
  const LoginCopy._({
    required this.mode,
    required this.title,
    required this.subtitle,
    required this.switchLabel,
  });

  /// 由「手機記住的帳號」與「員工有沒有按過切換」推出這次要顯示的一整組文案。
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

  /// 切換帳號那顆文字鈕上的字；[LoginMode.sharedDevice] 沒有這顆鈕，值是 null。
  final String? switchLabel;

  bool get showsIdentityCard => mode == LoginMode.remembered;

  /// 要不要畫員工編號欄。身分卡與員工編號欄互斥：有身分卡就代表員工編號已經
  /// 由這支手機記住了。
  bool get showsStaffIdField => mode != LoginMode.remembered;
}
