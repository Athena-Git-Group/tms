import 'package:flutter/foundation.dart';

@immutable
class PasswordRule {
  const PasswordRule({required this.label, required this.satisfied});

  final String label;

  final bool satisfied;
}

@immutable
class NewPasswordCheck {
  const NewPasswordCheck._({
    required this.rules,
    required this.confirmMatches,
    required this.showsMismatch,
  });

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
      showsMismatch:
          confirmPassword.isNotEmpty &&
          confirmPassword.length >= newPassword.length &&
          !matches,
    );
  }

  final List<PasswordRule> rules;

  final bool confirmMatches;

  final bool showsMismatch;

  bool get allRulesSatisfied => rules.every((rule) => rule.satisfied);

  bool get readyToSubmit => allRulesSatisfied && confirmMatches;

  static String _normalize(String value) =>
      value.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '');
}
