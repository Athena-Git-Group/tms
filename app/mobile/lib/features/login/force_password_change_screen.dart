import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/landing.dart';
import '../../core/navigation.dart';
import '../../core/theme.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import 'login_widgets.dart';
import 'password_rules.dart';

class ForcePasswordChangeScreen extends StatefulWidget {
  const ForcePasswordChangeScreen({
    super.key,
    required this.loginId,
    required this.temporaryPassword,
  });

  final String loginId;

  final String temporaryPassword;

  @override
  State<ForcePasswordChangeScreen> createState() =>
      _ForcePasswordChangeScreenState();
}

class _ForcePasswordChangeScreenState extends State<ForcePasswordChangeScreen> {
  final TextEditingController _newPasswordController = TextEditingController();
  final TextEditingController _confirmPasswordController =
      TextEditingController();

  bool _newPasswordObscured = true;
  bool _confirmPasswordObscured = true;

  String? _errorMessage;

  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _newPasswordController.addListener(_onFieldEdited);
    _confirmPasswordController.addListener(_onFieldEdited);
  }

  @override
  void dispose() {
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  void _onFieldEdited() {
    setState(() {
      _errorMessage = null;
    });
  }

  NewPasswordCheck get _check => NewPasswordCheck.of(
    newPassword: _newPasswordController.text,
    confirmPassword: _confirmPasswordController.text,
    temporaryPassword: widget.temporaryPassword,
    loginId: widget.loginId,
  );

  void _confirmThenSubmit() {
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsSemantics(
        id: 'login.force-password-change.confirm-alert',
        child: TmsAlert(
          title: '設定新密碼？',
          message: '這支手機會保持登入；其他裝置（例如櫃檯 iPad）上的這個帳號會被登出。',
          actions: <TmsAlertAction>[
            TmsAlertAction(
              id: 'login.force-password-change.confirm-alert.cancel-button',
              label: '取消',
              onPressed: () => Navigator.of(dialogContext).pop(),
            ),
            TmsAlertAction(
              id: 'login.force-password-change.confirm-alert.confirm-button',
              label: '設定',
              style: TmsAlertActionStyle.primary,
              onPressed: () {
                Navigator.of(dialogContext).pop();
                _submit();
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _submit() async {
    setState(() {
      _submitting = true;
      _errorMessage = null;
    });
    try {
      final result = await ApiClient.instance.changePassword(
        newPassword: _newPasswordController.text,
      );
      if (!mounted) {
        return;
      }
      replaceAllRoutes(context, landingShellForLogin(result));
      return;
    } on ApiException catch (e) {
      setState(() {
        _errorMessage = e.errorMessage ?? '目前無法完成設定，請稍後再試。';
      });
    } catch (_) {
      setState(() {
        _errorMessage = '無法連線到伺服器，請確認網路後再試一次。';
      });
    } finally {
      if (mounted) {
        setState(() {
          _submitting = false;
        });
      }
    }
  }

  Widget _passwordFields(NewPasswordCheck check) {
    return LoginFormCard(
      children: <Widget>[
        LoginFieldRow(
          label: '新密碼',
          field: TmsSemantics(
            id: 'login.force-password-change.new-password-field',
            child: LoginTextField(
              controller: _newPasswordController,
              obscureText: _newPasswordObscured,
              maxLength: 32,
              autofillHints: const <String>[AutofillHints.newPassword],
            ),
          ),
          trailing: LoginPasswordVisibilityButton(
            semanticsId:
                'login.force-password-change.new-password-visibility-button',
            obscured: _newPasswordObscured,
            onPressed: () => setState(() {
              _newPasswordObscured = !_newPasswordObscured;
            }),
          ),
        ),
        LoginFieldRow(
          label: '確認密碼',
          hasError: check.showsMismatch,
          field: TmsSemantics(
            id: 'login.force-password-change.confirm-password-field',
            child: LoginTextField(
              controller: _confirmPasswordController,
              obscureText: _confirmPasswordObscured,
              maxLength: 32,
              autofillHints: const <String>[AutofillHints.newPassword],
            ),
          ),
          trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              if (check.confirmMatches) ...<Widget>[
                const SizedBox(width: TmsSpacing.sm),
                const LoginCheckBadge(size: 20, strokeWidth: 2.2),
              ],
              LoginPasswordVisibilityButton(
                semanticsId: 'login.force-password-change.confirm-password-visibility-button',
                obscured: _confirmPasswordObscured,
                onPressed: () => setState(() {
                  _confirmPasswordObscured = !_confirmPasswordObscured;
                }),
              ),
            ],
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final check = _check;
    final errorMessage = _errorMessage;
    final ready = check.readyToSubmit && !_submitting;
    final formBlock = <Widget>[
      _passwordFields(check),
      if (check.showsMismatch)
        const LoginErrorText(
          semanticsId: 'login.force-password-change.mismatch-banner',
          message: '兩次輸入的新密碼不一樣',
        ),
      if (errorMessage != null)
        LoginErrorText(
          semanticsId: 'login.force-password-change.error-banner',
          message: errorMessage,
        ),
      PasswordRuleChecklist(
        semanticsId: 'login.force-password-change.password-rules',
        rules: check.rules,
      ),
    ];
    final spacedFormBlock = <Widget>[];
    for (final block in formBlock) {
      if (spacedFormBlock.isNotEmpty) {
        spacedFormBlock.add(const SizedBox(height: TmsSpacing.sm));
      }
      spacedFormBlock.add(block);
    }

    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      navigationBar: const CupertinoNavigationBar(
        middle: Text('設定新密碼'),
        automaticallyImplyLeading: false,
      ),
      child: SafeArea(
        bottom: false,
        child: Column(
          children: <Widget>[
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(
                  TmsSpacing.screenEdge,
                  TmsSpacing.md,
                  TmsSpacing.screenEdge,
                  TmsSpacing.xl2,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    const LoginNoticeCard(
                      semanticsId: 'login.force-password-change.notice',
                      iconColor: TmsColors.systemOrange,
                      title: '請先設定新密碼',
                      body: '你用的是主管發的臨時密碼。設定新密碼後才能繼續使用。',
                    ),
                    const SizedBox(height: TmsSpacing.lg2),
                    ...spacedFormBlock,
                  ],
                ),
              ),
            ),
            TmsFormActionBar(
              children: <Widget>[
                TmsSemantics(
                  id: 'login.force-password-change.submit-button',
                  enabled: ready,
                  child: TmsPillButton(
                    label: '設定新密碼',
                    glow: TmsPillGlow.alt,
                    onPressed: ready ? _confirmThenSubmit : null,
                    child: _submitting
                        ? const CupertinoActivityIndicator(
                            color: TmsColors.brandOnPrimary,
                          )
                        : null,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
