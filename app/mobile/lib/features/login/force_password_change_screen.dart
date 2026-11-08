import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/navigation.dart';
import '../../core/theme.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/tms_semantics.dart';
import '../shell/main_shell.dart';
import 'login_widgets.dart';

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
/// （新密碼與目前密碼相同等），App 不自行判斷密碼強度：長度與字元組成的門檻
/// truth 尚未定義，發明一套會和後端對不上。
class ForcePasswordChangeScreen extends StatefulWidget {
  const ForcePasswordChangeScreen({super.key});

  @override
  State<ForcePasswordChangeScreen> createState() =>
      _ForcePasswordChangeScreenState();
}

class _ForcePasswordChangeScreenState extends State<ForcePasswordChangeScreen> {
  final TextEditingController _newPasswordController = TextEditingController();
  final TextEditingController _confirmPasswordController =
      TextEditingController();

  String? _errorMessage;

  bool _submitting = false;

  @override
  void dispose() {
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_newPasswordController.text.isEmpty ||
        _newPasswordController.text != _confirmPasswordController.text) {
      setState(() {
        _errorMessage = '兩次輸入的新密碼不一致，請重新輸入。';
      });
      return;
    }
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
      // 改密完成就離開登入流程，首頁依部門分流（與登入成功後同一套判斷）。
      replaceAllRoutes(
        context,
        result.dept == 'engineering'
            ? const EngineeringShell()
            : const HousekeepingShell(),
      );
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

  /// 兩格新密碼欄：新密碼與再輸入一次。
  Widget _buildPasswordFields() {
    return LoginFormCard(
      children: [
        LoginFieldRow(
          label: '新密碼',
          field: TmsSemantics(
            id: 'login.force-password-change.new-password-field',
            child: LoginTextField(
              controller: _newPasswordController,
              obscureText: true,
            ),
          ),
        ),
        LoginFieldRow(
          label: '再次輸入新密碼',
          field: TmsSemantics(
            id: 'login.force-password-change.confirm-password-field',
            child: LoginTextField(
              controller: _confirmPasswordController,
              obscureText: true,
            ),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      navigationBar: const CupertinoNavigationBar(
        middle: Text('設定新密碼'),
        automaticallyImplyLeading: false,
      ),
      child: SafeArea(
        child: Column(
          children: [
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
                  children: [
                    const LoginBrandHeader(
                      title: '設定新密碼',
                      subtitle: '您目前使用的是臨時密碼，請先設定新密碼才能繼續使用。',
                    ),
                    const SizedBox(height: TmsSpacing.lg),
                    _buildPasswordFields(),
                    if (_errorMessage != null) ...[
                      const SizedBox(height: TmsSpacing.sm),
                      LoginErrorText(message: _errorMessage!),
                    ],
                  ],
                ),
              ),
            ),
            LoginActionBar(
              children: [
                TmsSemantics(
                  id: 'login.force-password-change.submit-button',
                  enabled: !_submitting,
                  child: TmsPillButton(
                    label: '確認',
                    onPressed: _submitting ? null : _submit,
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
