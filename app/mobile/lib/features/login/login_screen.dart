import 'dart:convert';
import 'dart:io';

import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';

/// 登入 API 的基礎 URL。開發時可用
/// `--dart-define=API_BASE_URL=http://...` 指到實際後端，沒帶這個旗標時
/// 預設打本機 8080。
const String _apiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://localhost:8080',
);

/// 員工登入殼畫面：帳號、密碼、登入按鈕。
///
/// 登入 API 目前只有契約骨架（`specs/truth/contracts` 的
/// `staffAccountsLogin`），request body 欄位名還是沿用「建立員工帳號」那組
/// 欄位（`loginId`/`passwordHash`/`tenantId`…），沒有真正針對登入場景定義
/// 「帳號＋密碼」兩個欄位，也沒有說明密碼要不要在送出前雜湊。這裡先用
/// `account`/`password` 兩個欄位送出，等後端把登入契約補完整後要回來對一次
/// 欄位名（見下方 TODO）。
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _accountController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  bool _submitting = false;
  String? _errorMessage;

  @override
  void dispose() {
    _accountController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _submitting = true;
      _errorMessage = null;
    });

    try {
      final client = HttpClient();
      final uri = Uri.parse('$_apiBaseUrl/staff-accounts/login');
      final request = await client.postUrl(uri);
      request.headers.contentType = ContentType.json;
      // TODO: 查不到正式登入契約的欄位名，先用 account/password；等
      // specs/truth 補上 staffAccountsLogin 的真實 request body 後要回來對齊。
      request.write(jsonEncode({
        'account': _accountController.text,
        'password': _passwordController.text,
      }));

      final response = await request.close();
      await response.transform(utf8.decoder).join();
      client.close();

      if (response.statusCode != 200) {
        setState(() {
          _errorMessage = '帳號或密碼不正確，請再試一次。';
        });
      }
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
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 32),
              const Text(
                '請輸入您的帳號密碼登入',
                style: TextStyle(fontSize: 16),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              TmsSemantics(
                id: 'login.account-field',
                child: CupertinoTextField(
                  controller: _accountController,
                  placeholder: '帳號',
                  padding: const EdgeInsets.all(12),
                ),
              ),
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
                TmsSemantics(
                  id: 'login.error-banner',
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Text(
                      _errorMessage!,
                      style: const TextStyle(color: CupertinoColors.destructiveRed),
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
              TmsSemantics(
                id: 'login.submit-button',
                enabled: !_submitting,
                child: CupertinoButton.filled(
                  onPressed: _submitting ? null : _submit,
                  child: _submitting
                      ? const CupertinoActivityIndicator()
                      : const Text('登入'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
