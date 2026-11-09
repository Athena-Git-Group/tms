import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/navigation.dart';
import '../../core/theme.dart';
import '../../core/widgets/tms_alert.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/test_backdoor.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../shell/main_shell.dart';
import 'force_password_change_screen.dart';
import 'login_language.dart';
import 'login_mode.dart';
import 'login_widgets.dart';

/// 員工登入畫面（ui-plan 畫面 11）。
///
/// 畫面有三種模式，差別在於這支手機記住了誰、以及員工這次要不要用那個帳號，
/// 判斷規則與各模式的文案都在 [LoginCopy]。這支手機記住誰由
/// [TestHooks.rememberedAccount] 決定並隨其變化即時切換——目前唯一的寫入端是
/// 測試後門，正式的「記住我」持久化還沒有 feature 承接，一般建置永遠是共用
/// 手機模式。
///
/// 這一頁沒有導覽列，整頁是蓋在 App 之上的一層，因為登出之後員工沒有別的地方
/// 可去，留一條返回路徑只會讓人以為按得回去。
///
/// 送出走 [ApiClient.login]（`POST /staff-accounts/login`）。錯誤、鎖定、
/// 臨時密碼的判斷全部以後端回應為準：固定錯誤文案直接取後端 ApiError 的
/// `message`，App 不自己累計失敗次數、不自己算鎖定到幾點；
/// `mustChangePassword=true` 時進強制改密畫面（[ForcePasswordChangeScreen]）。
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _staffIdController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  bool _submitting = false;

  /// 員工有沒有按過「用其他帳號登入」。
  bool _switchedToOtherAccount = false;

  /// 密碼現在是不是遮著的。
  bool _passwordObscured = true;

  /// 語言鈕上顯示的語言。
  ///
  /// 選了之後只有這顆鈕與面板上的勾會跟著動，介面文字不會換成那個語言：
  /// 多語系本身還沒有 feature 承接，整個 App 的字目前只有繁體中文一份。
  LoginLanguage _language = LoginLanguage.traditionalChinese;

  /// 後端回的錯誤句（401 credentials_invalid 等），顯示在 `login.error-banner`。
  String? _errorMessage;

  /// 後端是否回報這個帳號正被鎖住（429 account_locked）。
  /// 鎖住時整張表單淡化不可點，送出鈕停用。
  bool _lockedOut = false;

  @override
  void initState() {
    super.initState();
    // 送出鈕的啟用條件看的是兩個欄位現在有沒有字，每打一個字都要重算一次。
    _staffIdController.addListener(_onFieldEdited);
    _passwordController.addListener(_onFieldEdited);
  }

  @override
  void dispose() {
    _staffIdController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  /// 員工一動欄位就把上一次的錯誤收掉。錯誤訊息留著的話，紅字與紅色的欄位
  /// 名稱會停在新打的字旁邊，看起來像是這次打的也錯了。
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
      // 置底操作列自己吃掉底部安全區，外面再包一層就會留兩倍白。
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
        const LoginLockoutCard(
          semanticsId: 'login.lockout-banner',
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

  /// 表單那一段：輸入卡、錯誤紅字、切換帳號鈕。
  ///
  /// 鎖定時整段淡化而且點不動，連欄位都打不進字。只停用送出鈕的話，員工會
  /// 把密碼重打一次才發現按不下去，而他要做的其實是等十五分鐘或去找主管。
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
                // 兩個欄位各帶一把 key：共用手機模式有兩列、記住帳號模式只有
                // 一列，沒有 key 時 Flutter 會把舊的第一列（員工編號）重用成
                // 新的第一列（密碼），accessibility id 跟著留在舊的那個，
                // 驗收就定位不到密碼欄。
                key: const ValueKey('login.staff-id-row'),
                label: '員工編號',
                hasError: hasError,
                field: TmsSemantics(
                  id: 'login.shared-device.staff-id-field',
                  child: LoginTextField(
                    controller: _staffIdController,
                    // 不設長度上限、不強制大寫。原型的員工編號是 H-0532
                    // 這種格式（6 碼大寫），所以它寫 maxLength 12 並轉大寫；
                    // 這套系統沒有那個形狀：契約的 loginId 沒有任何長度或
                    // 大小寫限制，種子帳號是 hk1，而 app/bdd 的 fixture 是
                    // 「fx-」加 12 碼共 15 字。照原型設限會把合法帳號擋在外面，
                    // 而後端 findByTenantIdAndLoginId 又是精確比對，結果是登不進去。
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
            // 表單置底列的送出鈕在原型裡用另一組光暈。
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

  /// 送得出去的條件：沒在送、沒被鎖、密碼有字，而且需要員工編號的模式下
  /// 編號也有字。條件不足時按鈕是灰的且按不下去，員工不必送出一次才知道
  /// 自己漏了一格。
  bool _isReadyToSubmit(LoginCopy copy) {
    if (_submitting || _lockedOut || _passwordController.text.isEmpty) {
      return false;
    }
    return !copy.showsStaffIdField || _staffIdController.text.isNotEmpty;
  }

  /// 在「用記住的帳號」與「輸入別人的員工編號」之間切換。
  ///
  /// 切換會把兩個欄位與上一次的錯誤一起清掉：切過去是要登另一個人的帳號，
  /// 留著前一個人打到一半的密碼只會被連錯次數算進新的帳號。
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

  /// 離線時連送都不送。登入一定要問過後端，先送出再等逾時只是讓員工多站著
  /// 等一輪，而他要做的是走回有訊號的地方。
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
              style: TmsAlertActionStyle.primary,
              onPressed: () => Navigator.of(dialogContext).pop(),
            ),
          ],
        ),
      ),
    );
  }

  /// 忘記密碼沒有自助流程：重設密碼是主管在後台做的事，App 這邊沒有對應的
  /// 端點，所以告訴員工該找誰、以及重設之後會發生什麼，而不是導到一個做不了
  /// 事的畫面。
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
              style: TmsAlertActionStyle.primary,
              onPressed: () => Navigator.of(dialogContext).pop(),
            ),
          ],
        ),
      ),
    );
  }
}
