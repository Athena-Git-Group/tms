import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/error_banner.dart';
import '../../core/navigation.dart';
import '../../core/test_backdoor.dart';
import '../../core/tms_semantics.dart';
import '../engineering/eng_home_screen.dart';
import '../home/home_screen.dart';
import 'force_password_change_screen.dart';

/// 員工登入畫面（ui-plan 畫面 11），兩種情境：
///
/// - **記住帳號**：這支手機固定給同一人用，畫面顯示身分卡
///   （`login.remembered-identity.card`），只需輸入密碼；
/// - **共用手機**：沒有記住任何帳號，需輸入員工編號
///   （`login.shared-device.staff-id-field`）＋密碼。
///
/// 模式由 [TestHooks.rememberedAccount] 決定並隨其變化即時切換——目前唯一的
/// 寫入端是測試後門，正式的「記住我」持久化還沒有 feature 承接，一般建置
/// 永遠是共用手機模式。
///
/// 送出走 [ApiClient.login]（`POST /staff-accounts/login`）。錯誤、鎖定、
/// 臨時密碼的判斷全部以後端回應為準：固定錯誤文案與鎖定剩餘分鐘直接取
/// 後端 ApiError 的 `message`／`context.remainingMinutes`，App 不自己算
/// 鎖定時間、不在本機累計失敗次數；`mustChangePassword=true` 時進強制改密
/// 畫面（[ForcePasswordChangeScreen]）。
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _staffIdController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  bool _submitting = false;

  /// 後端回的錯誤句（401 credentials_invalid 等），顯示在 `login.error-banner`。
  String? _errorMessage;

  /// 帳號鎖定剩餘分鐘（429 account_locked 的 `context.remainingMinutes`）。
  /// 非 null 時顯示 `login.lockout-banner` 並禁用登入按鈕。
  int? _lockoutRemainingMinutes;

  @override
  void dispose() {
    _staffIdController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit(RememberedAccount? remembered) async {
    final loginId =
        remembered != null ? remembered.loginId : _staffIdController.text;
    setState(() {
      _submitting = true;
      _errorMessage = null;
      _lockoutRemainingMinutes = null;
    });

    try {
      final result = await ApiClient.instance.login(
        loginId: loginId,
        password: _passwordController.text,
      );
      if (!mounted) {
        return;
      }
      // 登入成功即離開登入流程（見 replaceAllRoutes 的返回堆疊保證）。
      // 首頁依部門分流（ui-plan：同一 App 用部門切換呈現工務視角）：
      // 工務落在工務首頁，其他部門維持房務首頁。
      final Widget landing;
      if (result.mustChangePassword) {
        landing = const ForcePasswordChangeScreen();
      } else if (result.dept == 'engineering') {
        landing = const EngHomeScreen();
      } else {
        landing = const HomeScreen();
      }
      replaceAllRoutes(context, landing);
      return;
    } on ApiException catch (e) {
      final remainingMinutes = e.lockoutRemainingMinutes;
      setState(() {
        if (remainingMinutes != null) {
          _lockoutRemainingMinutes = remainingMinutes;
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
      navigationBar: const CupertinoNavigationBar(
        middle: Text('登入'),
      ),
      child: SafeArea(
        child: ValueListenableBuilder<RememberedAccount?>(
          valueListenable: TestHooks.instance.rememberedAccount,
          builder: (context, remembered, _) {
            final lockedOut = _lockoutRemainingMinutes != null;
            return SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const SizedBox(height: 32),
                  if (remembered != null)
                    TmsSemantics(
                      id: 'login.remembered-identity.card',
                      label: '${remembered.name}，請輸入您的密碼登入',
                      child: Column(
                        children: [
                          Text(
                            remembered.name,
                            style: const TextStyle(
                              fontSize: 22,
                              fontWeight: FontWeight.w600,
                            ),
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 8),
                          const Text(
                            '請輸入您的密碼登入',
                            style: TextStyle(fontSize: 15),
                            textAlign: TextAlign.center,
                          ),
                        ],
                      ),
                    )
                  else ...[
                    const Text(
                      '請輸入您的員工編號與密碼登入',
                      style: TextStyle(fontSize: 16),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 24),
                    TmsSemantics(
                      id: 'login.shared-device.staff-id-field',
                      child: CupertinoTextField(
                        controller: _staffIdController,
                        placeholder: '員工編號',
                        padding: const EdgeInsets.all(12),
                      ),
                    ),
                  ],
                  const SizedBox(height: 12),
                  TmsSemantics(
                    id: 'login.password-field',
                    child: CupertinoTextField(
                      controller: _passwordController,
                      placeholder: '密碼',
                      obscureText: true,
                      padding: const EdgeInsets.all(12),
                    ),
                  ),
                  const SizedBox(height: 20),
                  if (_errorMessage != null)
                    ErrorBanner(
                      semanticsId: 'login.error-banner',
                      message: _errorMessage!,
                    ),
                  if (lockedOut)
                    ErrorBanner(
                      semanticsId: 'login.lockout-banner',
                      message: '帳號已鎖定，請於 $_lockoutRemainingMinutes 分鐘後再試',
                    ),
                  TmsSemantics(
                    id: 'login.submit-button',
                    enabled: !_submitting && !lockedOut,
                    child: CupertinoButton.filled(
                      onPressed: _submitting || lockedOut
                          ? null
                          : () => _submit(remembered),
                      child: _submitting
                          ? const CupertinoActivityIndicator()
                          : const Text('登入'),
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}
