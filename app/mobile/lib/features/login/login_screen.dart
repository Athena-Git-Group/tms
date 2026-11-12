import 'dart:async' show unawaited;

import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/landing.dart';
import '../../core/navigation.dart';
import '../../core/push_token_service.dart';
import '../../core/theme.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/test_backdoor.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import 'force_password_change_screen.dart';
import 'login_language.dart';
import 'login_mode.dart';
import 'login_widgets.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _staffIdController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  bool _submitting = false;

  bool _switchedToOtherAccount = false;

  bool _passwordObscured = true;

  LoginLanguage _language = LoginLanguage.traditionalChinese;

  String? _errorMessage;

  bool _lockedOut = false;

  @override
  void initState() {
    super.initState();
    _staffIdController.addListener(_onFieldEdited);
    _passwordController.addListener(_onFieldEdited);
  }

  @override
  void dispose() {
    _staffIdController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _onFieldEdited() {
    setState(() {
      _errorMessage = null;
    });
  }

  Future<void> _submit(LoginCopy copy, RememberedAccount? remembered) async {
    if (ConnectionStatus.instance.isOffline) {
      _showOfflineAlert(context);
      return;
    }
    final loginId = copy.showsStaffIdField
        ? _staffIdController.text
        : remembered!.loginId;
    setState(() {
      _submitting = true;
      _errorMessage = null;
      _lockedOut = false;
    });

    try {
      final result = await ApiClient.instance.login(
        loginId: loginId,
        password: _passwordController.text,
      );
      if (!mounted) {
        return;
      }
      unawaited(PushTokenService.instance?.start());
      final Widget landing;
      if (result.mustChangePassword) {
        landing = ForcePasswordChangeScreen(
          loginId: loginId,
          temporaryPassword: _passwordController.text,
        );
      } else {
        landing = landingShellForLogin(result);
      }
      replaceAllRoutes(context, landing);
      return;
    } on ApiException catch (e) {
      setState(() {
        if (e.lockoutRemainingMinutes != null) {
          _lockedOut = true;
        } else {
          _errorMessage = e.errorMessage ?? '登入失敗，請再試一次。';
        }
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

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      child: SafeArea(
        bottom: false,
        child: ValueListenableBuilder<RememberedAccount?>(
          valueListenable: TestHooks.instance.rememberedAccount,
          builder: (context, remembered, _) {
            final copy = LoginCopy.of(
              remembered: remembered,
              switchedToOtherAccount: _switchedToOtherAccount,
            );
            return Column(
              children: <Widget>[
                Align(
                  alignment: Alignment.centerRight,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: TmsSpacing.sm,
                    ),
                    child: LoginLanguageButton(
                      current: _language,
                      onPressed: _pickLanguage,
                    ),
                  ),
                ),
                Expanded(child: _scrollArea(copy, remembered)),
                _actionBar(copy, remembered),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _scrollArea(LoginCopy copy, RememberedAccount? remembered) {
    final blocks = <Widget>[
      LoginBrandHeader(title: copy.title, subtitle: copy.subtitle),
      if (_lockedOut)
        const LoginNoticeCard(
          semanticsId: 'login.lockout-banner',
          iconColor: TmsColors.systemRed,
          title: '暫時無法登入',
          body: '連續輸錯 5 次，請 15 分鐘後再試，或請主管在後台重設密碼。',
        ),
      _form(copy, remembered),
    ];
    final spaced = <Widget>[];
    for (final block in blocks) {
      if (spaced.isNotEmpty) {
        spaced.add(const SizedBox(height: TmsSpacing.xl2));
      }
      spaced.add(block);
    }
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        TmsSpacing.md,
        TmsSpacing.screenEdge,
        TmsSpacing.xl2,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: spaced,
      ),
    );
  }

  Widget _form(LoginCopy copy, RememberedAccount? remembered) {
    final hasError = _errorMessage != null;
    final switchLabel = copy.switchLabel;
    final form = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        LoginFormCard(
          children: <Widget>[
            if (copy.showsIdentityCard)
              TmsSemantics(
                key: const ValueKey('login.identity-row'),
                id: 'login.remembered-identity.card',
                label: '${remembered!.name}，${remembered.loginId}',
                child: ExcludeSemantics(
                  child: LoginIdentityRow(
                    name: remembered.name,
                    subtitle: remembered.loginId,
                  ),
                ),
              ),
            if (copy.showsStaffIdField)
              LoginFieldRow(
                key: const ValueKey('login.staff-id-row'),
                label: '員工編號',
                hasError: hasError,
                field: TmsSemantics(
                  id: 'login.shared-device.staff-id-field',
                  child: LoginTextField(
                    controller: _staffIdController,
                    uppercase: false,
                    autofillHints: const <String>[AutofillHints.username],
                  ),
                ),
              ),
            LoginFieldRow(
              key: const ValueKey('login.password-row'),
              label: '密碼',
              hasError: hasError,
              field: TmsSemantics(
                id: 'login.password-field',
                child: LoginTextField(
                  controller: _passwordController,
                  obscureText: _passwordObscured,
                  maxLength: 32,
                  autofillHints: const <String>[AutofillHints.password],
                ),
              ),
              trailing: LoginPasswordVisibilityButton(
                semanticsId: 'login.password-visibility-button',
                obscured: _passwordObscured,
                onPressed: () => setState(() {
                  _passwordObscured = !_passwordObscured;
                }),
              ),
            ),
          ],
        ),
        if (hasError) ...<Widget>[
          const SizedBox(height: TmsSpacing.sm),
          LoginErrorText(
            semanticsId: 'login.error-banner',
            message: _errorMessage!,
          ),
        ],
        if (switchLabel != null) ...<Widget>[
          const SizedBox(height: TmsSpacing.sm),
          Align(
            alignment: Alignment.centerLeft,
            child: TmsSemantics(
              id: 'login.switch-account-button',
              label: switchLabel,
              child: ExcludeSemantics(
                child: CupertinoButton(
                  padding: const EdgeInsets.symmetric(
                    horizontal: TmsSpacing.screenEdge,
                  ),
                  minimumSize: const Size(0, TmsSize.minTouchTarget),
                  onPressed: _switchAccount,
                  child: Text(
                    switchLabel,
                    style: TmsText.subheadline.copyWith(
                      color: TmsColors.brandPrimary,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ],
    );
    if (!_lockedOut) {
      return form;
    }
    return IgnorePointer(child: Opacity(opacity: 0.4, child: form));
  }

  Widget _actionBar(LoginCopy copy, RememberedAccount? remembered) {
    final ready = _isReadyToSubmit(copy);
    return TmsFormActionBar(
      children: <Widget>[
        TmsSemantics(
          id: 'login.submit-button',
          enabled: ready,
          child: TmsPillButton(
            label: '登入',
            glow: TmsPillGlow.alt,
            onPressed: ready ? () => _submit(copy, remembered) : null,
            child: _submitting
                ? const CupertinoActivityIndicator(
                    color: TmsColors.brandOnPrimary,
                  )
                : null,
          ),
        ),
        CupertinoButton(
          padding: EdgeInsets.zero,
          minimumSize: const Size(double.infinity, TmsSize.minTouchTarget),
          onPressed: () => _showForgotPassword(context),
          child: Text(
            '忘記密碼？',
            style: TmsText.subheadline.copyWith(color: TmsColors.brandPrimary),
          ),
        ),
      ],
    );
  }

  bool _isReadyToSubmit(LoginCopy copy) {
    if (_submitting || _lockedOut || _passwordController.text.isEmpty) {
      return false;
    }
    return !copy.showsStaffIdField || _staffIdController.text.isNotEmpty;
  }

  void _switchAccount() {
    setState(() {
      _switchedToOtherAccount = !_switchedToOtherAccount;
      _staffIdController.clear();
      _passwordController.clear();
      _errorMessage = null;
      _passwordObscured = true;
    });
  }

  Future<void> _pickLanguage() async {
    final picked = await LoginLanguageSheet.show(context, current: _language);
    if (picked != null && mounted) {
      setState(() {
        _language = picked;
      });
    }
  }

  void _showOfflineAlert(BuildContext context) {
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsSemantics(
        id: 'login.offline-alert',
        child: TmsAlert(
          title: '要連線才能登入',
          message: '目前離線。恢復連線後再試一次。',
          actions: <TmsAlertAction>[
            TmsAlertAction(
              label: '好',
              id: 'login.forgot-password-alert.dismiss-button',
              style: TmsAlertActionStyle.primary,
              onPressed: () => Navigator.of(dialogContext).pop(),
            ),
          ],
        ),
      ),
    );
  }

  void _showForgotPassword(BuildContext context) {
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsSemantics(
        id: 'login.forgot-password-alert',
        child: TmsAlert(
          title: '忘記密碼',
          message: '請主管在後台重設。重設後你會拿到臨時密碼，用它登入時會直接要求設定新密碼。',
          actions: <TmsAlertAction>[
            TmsAlertAction(
              label: '好',
              id: 'login.forgot-password-alert.dismiss-button',
              style: TmsAlertActionStyle.primary,
              onPressed: () => Navigator.of(dialogContext).pop(),
            ),
          ],
        ),
      ),
    );
  }
}
