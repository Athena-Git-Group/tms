import 'package:flutter/cupertino.dart';

import '../../core/error_banner.dart';
import '../../core/tms_semantics.dart';

/// 臨時密碼登入後的強制改密畫面。
///
/// 規格要求「App 進入強制改密畫面，完成前無法使用其他畫面」（dsl.md）。
/// 做法是兩層都擋：登入畫面把這裡設成唯一的 route（replaceAllRoutes，
/// 清掉返回堆疊）——沒有上一頁可返回、也沒有 Tab bar，返回手勢自然無處
/// 可去；完成改密前後端（BearerTokenAuthFilter）也會把這組憑證對其他
/// API 的呼叫一律擋下（403 must_change_password）。
///
/// 送出「設定新密碼」目前是 App 端上游缺口：契約（`specs/truth/contracts/`）
/// 與後端都還沒有改密端點（`BearerTokenAuthFilter` 註明「改密 API 本身不在
/// 本輪範圍」），App 不得自創端點呼叫，所以按下確認後只能停在本畫面等
/// 上游補齊；對應場景「阿美設定新密碼後進入首頁」在端點落地前無法轉綠。
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

  @override
  void dispose() {
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  void _submit() {
    if (_newPasswordController.text.isEmpty ||
        _newPasswordController.text != _confirmPasswordController.text) {
      setState(() {
        _errorMessage = '兩次輸入的新密碼不一致，請重新輸入。';
      });
      return;
    }
    // 上游缺口：改密端點尚未存在（見類別 Javadoc），這裡還沒有可以呼叫的
    // API。端點補齊後：呼叫改密 → 成功後 replaceAllRoutes 進首頁。
    setState(() {
      _errorMessage = '目前無法完成設定，請稍後再試。';
    });
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      navigationBar: const CupertinoNavigationBar(
        middle: Text('設定新密碼'),
        automaticallyImplyLeading: false,
      ),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 24),
              const Text(
                '您目前使用的是臨時密碼，請先設定新密碼才能繼續使用。',
                style: TextStyle(fontSize: 15),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              TmsSemantics(
                id: 'login.force-password-change.new-password-field',
                child: CupertinoTextField(
                  controller: _newPasswordController,
                  placeholder: '新密碼',
                  obscureText: true,
                  padding: const EdgeInsets.all(12),
                ),
              ),
              const SizedBox(height: 12),
              TmsSemantics(
                id: 'login.force-password-change.confirm-password-field',
                child: CupertinoTextField(
                  controller: _confirmPasswordController,
                  placeholder: '再次輸入新密碼',
                  obscureText: true,
                  padding: const EdgeInsets.all(12),
                ),
              ),
              const SizedBox(height: 20),
              if (_errorMessage != null) ErrorBanner(message: _errorMessage!),
              TmsSemantics(
                id: 'login.force-password-change.submit-button',
                child: CupertinoButton.filled(
                  onPressed: _submit,
                  child: const Text('確認'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
