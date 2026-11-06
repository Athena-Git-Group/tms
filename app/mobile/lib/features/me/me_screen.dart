import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/navigation.dart';
import '../../core/tms_semantics.dart';
import '../login/login_screen.dart';

/// 「我的」畫面（ui-plan 畫面 10）。本輪只承接登出入口與兩個登出攔阻；
/// 推播設定、文字大小、語言、個人資料、遺失物等子頁沒有 feature 承接，
/// 不實作（上游缺口，見 tasks.md）。
///
/// 登出的兩個攔阻（dsl.md「App 顯示登出被攔住」）：
///
/// 1. **離線**：離線狀態讀全 App 共用的 [ConnectionStatus]。離線時登出會讓
///    本機憑證被丟掉而後端無從得知，先攔住。
/// 2. **名下有進行中的任務**：以後端回應為準——用 `GET /me/today` 判斷
///    還有沒有未完成的打掃單（`cleaningDone < cleaningTotal`）或剩餘工時
///    （`remainingMinutes > 0`）。後端沒有「我的進行中任務清單」端點，
///    這兩個統計是目前契約內能拿到的最接近訊號；App 不在本機另記任務狀態。
class MeScreen extends StatelessWidget {
  const MeScreen({super.key});

  Future<void> _attemptLogout(BuildContext context) async {
    if (ConnectionStatus.instance.isOffline) {
      _showBlockedAlert(context, '目前離線，連線後才能登出');
      return;
    }

    bool hasTasksInProgress;
    try {
      final today = await ApiClient.instance.getMyToday();
      hasTasksInProgress =
          today.cleaningDone < today.cleaningTotal || today.remainingMinutes > 0;
    } catch (_) {
      // 查不到權威狀態就不放行登出：寧可多攔一次，也不要讓還掛著單的人
      // 把憑證丟掉。
      if (context.mounted) {
        _showBlockedAlert(context, '目前離線，連線後才能登出');
      }
      return;
    }
    if (!context.mounted) {
      return;
    }
    if (hasTasksInProgress) {
      _showBlockedAlert(context, '還有進行中的任務，請先完成或中斷再登出');
      return;
    }

    // 攔阻都通過才真的登出：丟掉本機憑證，回登入畫面
    // （replaceAllRoutes 清掉返回堆疊，登出後滑不回「我的」）。
    ApiClient.instance = ApiClient();
    replaceAllRoutes(context, const LoginScreen());
  }

  void _showBlockedAlert(BuildContext context, String reason) {
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => TmsSemantics(
        id: 'me.logout-blocked-alert',
        label: '無法登出：$reason',
        child: CupertinoAlertDialog(
          title: const Text('無法登出'),
          content: Text(reason),
          actions: [
            CupertinoDialogAction(
              isDefaultAction: true,
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const TmsSemantics(
                id: 'me.logout-blocked-alert.confirm-button',
                child: Text('知道了'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'me.screen',
      child: CupertinoPageScaffold(
        navigationBar: const CupertinoNavigationBar(middle: Text('我的')),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              TmsSemantics(
                id: 'me.logout-button',
                child: CupertinoButton(
                  onPressed: () => _attemptLogout(context),
                  child: const Text('登出'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
