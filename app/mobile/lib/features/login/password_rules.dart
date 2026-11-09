import 'package:flutter/foundation.dart';

/// 新密碼檢查清單上的一條規則。
@immutable
class PasswordRule {
  const PasswordRule({required this.label, required this.satisfied});

  /// 顯示在清單上的句子，例如「至少 8 碼」。
  final String label;

  /// 現在打的新密碼符不符合這一條。符合時那一列打勾變綠。
  final bool satisfied;
}

/// 讓設定新密碼的畫面在員工打字的當下就告訴他還差什麼，不必送出一次才知道。
///
/// 這整組判斷只是輸入提示。准不准換成這組密碼由後端決定，
/// `POST /staff-accounts/change-password` 自己會擋掉空白與「新密碼和目前密碼
/// 相同」。所以這裡算出來的結果只拿來打勾、顯示紅字、決定送出鈕要不要亮；
/// 它不解讀後端的回應，也不會在送出前把請求換成自己編的錯誤訊息。後端沒擋、
/// 這裡卻擋下來的話，員工會看到一個後台怎麼改都解不開的畫面。
///
/// 四條規則的內容與判斷方式照設計原型（`Main.dc.html:4617-4622`）。
@immutable
class NewPasswordCheck {
  const NewPasswordCheck._({
    required this.rules,
    required this.confirmMatches,
    required this.showsMismatch,
  });

  /// [temporaryPassword] 是員工這次登入時打的那組臨時密碼，[loginId] 是他的
  /// 員工編號；兩者都只用來比對，不會被送出。
  factory NewPasswordCheck.of({
    required String newPassword,
    required String confirmPassword,
    required String temporaryPassword,
    required String loginId,
  }) {
    final normalizedId = _normalize(loginId);
    final normalizedNew = _normalize(newPassword);
    final rules = <PasswordRule>[
      PasswordRule(label: '至少 8 碼', satisfied: newPassword.length >= 8),
      PasswordRule(
        label: '英文和數字都要有',
        satisfied:
            RegExp(r'[A-Za-z]').hasMatch(newPassword) &&
            RegExp(r'\d').hasMatch(newPassword),
      ),
      PasswordRule(
        label: '和臨時密碼不同',
        satisfied: newPassword.isNotEmpty && newPassword != temporaryPassword,
      ),
      PasswordRule(
        label: '不能是員工編號',
        satisfied:
            newPassword.isNotEmpty &&
            normalizedNew != normalizedId &&
            normalizedNew != normalizedId.replaceFirst(RegExp(r'^[a-z]'), ''),
      ),
    ];
    final matches =
        confirmPassword.isNotEmpty && confirmPassword == newPassword;
    return NewPasswordCheck._(
      rules: rules,
      confirmMatches: matches,
      // 打到比新密碼還長還是不一樣，才算打錯。短於新密碼時員工可能只是還沒
      // 打完，那時就跳紅字等於每個人每次都要先看一次錯誤訊息。
      showsMismatch:
          confirmPassword.isNotEmpty &&
          confirmPassword.length >= newPassword.length &&
          !matches,
    );
  }

  final List<PasswordRule> rules;

  /// 兩次輸入的新密碼一樣，而且不是兩格都空的。確認欄右側的綠勾看這個值。
  final bool confirmMatches;

  /// 要不要在卡片下方顯示「兩次輸入的新密碼不一樣」。
  final bool showsMismatch;

  bool get allRulesSatisfied => rules.every((rule) => rule.satisfied);

  /// 送出鈕要不要亮。
  bool get readyToSubmit => allRulesSatisfied && confirmMatches;

  /// 比對員工編號前先把大小寫與分隔符號抹平：識別證上印的是 `H-0532`，員工
  /// 可能打成 `h0532`，那仍然是拿同一組編號當密碼。
  static String _normalize(String value) =>
      value.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '');
}
