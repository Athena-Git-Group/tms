import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/navigation.dart';
import '../../core/theme.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/test_backdoor.dart';
import '../../core/tms_semantics.dart';
import '../shell/main_shell.dart';
import 'force_password_change_screen.dart';
import 'login_widgets.dart';

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
    final loginId = remembered != null
        ? remembered.loginId
        : _staffIdController.text;
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
        landing = const EngineeringShell();
      } else {
        landing = const HousekeepingShell();
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
      backgroundColor: TmsColors.groupedBackground,
      navigationBar: const CupertinoNavigationBar(middle: Text('登入')),
      child: SafeArea(
        child: ValueListenableBuilder<RememberedAccount?>(
          valueListenable: TestHooks.instance.rememberedAccount,
          builder: (context, remembered, _) {
            final lockedOut = _lockoutRemainingMinutes != null;
            return Column(
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
                        _buildHeader(remembered),
                        const SizedBox(height: TmsSpacing.lg),
                        if (lockedOut) ...[
                          LoginLockoutCard(
                            semanticsId: 'login.lockout-banner',
                            title: '暫時無法登入',
                            body: _lockoutMessage,
                          ),
                          const SizedBox(height: TmsSpacing.lg),
                        ],
                        LoginFormCard(
                          children: [
                            if (remembered == null)
                              LoginFieldRow(
                                // 兩個欄位各帶一把 key：共用手機模式有兩列、
                                // 記住帳號模式只有一列，沒有 key 時 Flutter 會
                                // 把舊的第一列（員工編號）重用成新的第一列
                                // （密碼），accessibility id 跟著留在舊的那個，
                                // 驗收就定位不到密碼欄。
                                key: const ValueKey('login.staff-id-row'),
                                label: '員工編號',
                                field: TmsSemantics(
                                  id: 'login.shared-device.staff-id-field',
                                  child: LoginTextField(
                                    controller: _staffIdController,
                                  ),
                                ),
                              ),
                            LoginFieldRow(
                              key: const ValueKey('login.password-row'),
                              label: '密碼',
                              field: TmsSemantics(
                                id: 'login.password-field',
                                child: LoginTextField(
                                  controller: _passwordController,
                                  obscureText: true,
                                ),
                              ),
                            ),
                          ],
                        ),
                        if (_errorMessage != null) ...[
                          const SizedBox(height: TmsSpacing.sm),
                          LoginErrorText(
                            semanticsId: 'login.error-banner',
                            message: _errorMessage!,
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                LoginActionBar(
                  children: [
                    TmsSemantics(
                      id: 'login.submit-button',
                      enabled: !_submitting && !lockedOut,
                      child: TmsPillButton(
                        label: '登入',
                        onPressed: _submitting || lockedOut
                            ? null
                            : () => _submit(remembered),
                        child: _submitting
                            ? const CupertinoActivityIndicator(
                                color: TmsColors.brandOnPrimary,
                              )
                            : null,
                      ),
                    ),
                    CupertinoButton(
                      padding: EdgeInsets.zero,
                      minimumSize: const Size(
                        double.infinity,
                        TmsSize.minTouchTarget,
                      ),
                      onPressed: () => _showForgotPassword(context),
                      child: Text(
                        '忘記密碼？',
                        style: TmsText.subheadline.copyWith(
                          color: TmsColors.brandPrimary,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  /// 鎖定橫幅上那一句話。剩餘分鐘數直接用後端回的值，App 不自己算鎖到幾點。
  String get _lockoutMessage => '帳號已鎖定，請於 $_lockoutRemainingMinutes 分鐘後再試';

  /// 招牌區：記住帳號時直接叫員工的名字，共用手機時只講這支手機要輸入什麼。
  Widget _buildHeader(RememberedAccount? remembered) {
    if (remembered == null) {
      return const LoginBrandHeader(title: '登入', subtitle: '請輸入您的員工編號與密碼登入');
    }
    return TmsSemantics(
      id: 'login.remembered-identity.card',
      label: '${remembered.name}，請輸入您的密碼登入',
      child: ExcludeSemantics(
        child: LoginBrandHeader(title: remembered.name, subtitle: '請輸入您的密碼登入'),
      ),
    );
  }

  /// 忘記密碼目前沒有自助流程：重設密碼是主管在後台做的事，App 這邊沒有對應
  /// 的端點，所以告訴員工該找誰，而不是導到一個做不了事的畫面。
  void _showForgotPassword(BuildContext context) {
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => CupertinoAlertDialog(
        title: const Text('忘記密碼？'),
        content: const Text('請找主管在後台重設密碼。'),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('知道了'),
          ),
        ],
      ),
    );
  }
}
