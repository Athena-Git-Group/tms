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

/// 臨時密碼登入後的強制改密畫面。
///
/// 規格要求「App 進入強制改密畫面，完成前無法使用其他畫面」（dsl.md）。
/// 做法是兩層都擋：登入畫面把這裡設成唯一的 route（replaceAllRoutes，
/// 清掉返回堆疊）——沒有上一頁可返回、也沒有 Tab bar，返回手勢自然無處
/// 可去；完成改密前後端（BearerTokenAuthFilter）也會把這組憑證對其他
/// API 的呼叫一律擋下（403 must_change_password）。
///
/// 送出走 [ApiClient.changePassword]（`POST /staff-accounts/change-password`）：
/// 強制改密情境的身分已由登入換得的 token 證明，不帶 currentPassword。成功時
/// 後端換發一組新的 JWT（舊的立即失效），client 換上新權杖後以 replaceAllRoutes
/// 進首頁——登入流程到此結束，返回堆疊一併清掉。失敗時直接顯示後端回的訊息
/// （新密碼與目前密碼相同等）。
///
/// 畫面上的四條規則清單（[NewPasswordCheck]）只是輸入提示，不是這裡的判準：
/// 長度與字元組成的門檻 truth 尚未定義，App 發明一套會和後端對不上，所以能不能
/// 換成這組密碼一律以後端的回應為準。
class ForcePasswordChangeScreen extends StatefulWidget {
  const ForcePasswordChangeScreen({
    super.key,
    required this.loginId,
    required this.temporaryPassword,
  });

  /// 員工這次登入用的員工編號，用來檢查「不能是員工編號」那一條。
  final String loginId;

  /// 員工這次登入打的那組臨時密碼，用來檢查「和臨時密碼不同」那一條。
  /// 只留在畫面上比對用，不會被送出，改密完成離開畫面就跟著消失。
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
    // 規則清單、相符的綠勾與送出鈕的狀態都跟著現在打了什麼走，每打一個字都要
    // 重算一次。
    _newPasswordController.addListener(_onFieldEdited);
    _confirmPasswordController.addListener(_onFieldEdited);
  }

  @override
  void dispose() {
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  /// 員工一動欄位就把上一次後端回的訊息收掉。留著的話，紅字會停在新打的字
  /// 旁邊，看起來像是這次打的也被拒絕了。
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

  /// 送出前先問一次，因為按下去會把這個帳號在其他裝置上的登入全部踢掉。櫃檯
  /// 的 iPad 正在用同一個帳號時，員工要先知道自己會讓它登出。
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
    // 卡片、紅字與規則清單之間 8，提示卡與這一整段之間 18
    // （Main.dc.html:709、:722、:723）。
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
      // 置底操作列自己吃掉底部安全區，外面再包一層就會留兩倍白。
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
                    // 表單置底列的送出鈕在原型裡用另一組光暈。
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
