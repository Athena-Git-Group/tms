import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/navigation.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../login/login_screen.dart';

/// 「我的」畫面（ui-plan 畫面 10）：員工在這裡確認自己是用誰的帳號在用這支
/// App、調整通知與顯示、換密碼或語言，以及登出。
///
/// 推播通知、文字大小、變更密碼、語言四個子頁都還沒有畫面，點了只會說「尚未
/// 開放」。列仍然先擺出來，是因為員工要能在這一頁找得到自己的設定在哪一組
/// 底下；等子頁做好，位置不用再重排一次。
///
/// 原型還有一組「工作」磚（交班單、遺失物），這一輪沒做：磚上的兩個數字——
/// 交班單簽收狀態與自己登記的遺失物件數——後端都沒有端點可問，畫一個 0 上去
/// 員工會以為真的是零。
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
          today.cleaningDone < today.cleaningTotal ||
          today.remainingMinutes > 0;
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

  /// 告訴員工這個設定還不能用。四個還沒有子頁的列共用同一段說法，員工才不會
  /// 以為每一列各自壞在不同的地方。
  void _showNotAvailable(BuildContext context, String feature) {
    showCupertinoDialog<void>(
      context: context,
      builder: (dialogContext) => CupertinoAlertDialog(
        title: Text(feature),
        content: const Text('這個設定尚未開放。'),
        actions: [
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('知道了'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'me.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        // 身分卡本身就是這一頁的標題區，上面不再放一行導覽列大標題——員工一
        // 眼要看到的是「我是誰」，不是「這頁叫我的」。
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(
              TmsSpacing.screenEdge,
              TmsSpacing.md,
              TmsSpacing.screenEdge,
              TmsSpacing.xxl,
            ),
            children: [
              // 連線狀態列固定在內容最上方：登出與各項設定都要連得上後端，
              // 員工要先知道現在的連線狀況。
              const SystemStatusBar(
                padding: EdgeInsets.only(bottom: TmsSpacing.md),
              ),
              const _IdentityCard(),
              const SizedBox(height: TmsSpacing.xl),
              TmsGroupedSection(
                header: '通知與顯示',
                children: [
                  TmsGroupedRow(
                    label: '推播通知',
                    onTap: () => _showNotAvailable(context, '推播通知'),
                  ),
                  TmsGroupedRow(
                    label: '文字大小',
                    onTap: () => _showNotAvailable(context, '文字大小'),
                  ),
                ],
              ),
              const SizedBox(height: TmsSpacing.xl),
              TmsGroupedSection(
                header: '帳號',
                children: [
                  TmsGroupedRow(
                    label: '變更密碼',
                    onTap: () => _showNotAvailable(context, '變更密碼'),
                  ),
                  // 介面目前只做得出繁體中文一種，所以這個值寫死；它不是佔
                  // 位字，是現在真正的語言。
                  TmsGroupedRow(
                    label: '語言',
                    value: '繁體中文',
                    onTap: () => _showNotAvailable(context, '語言'),
                  ),
                ],
              ),
              const SizedBox(height: TmsSpacing.xl),
              TmsSemantics(
                id: 'me.logout-button',
                child: TmsCard(
                  padding: EdgeInsets.zero,
                  child: CupertinoButton(
                    padding: EdgeInsets.zero,
                    minimumSize: const Size(
                      double.infinity,
                      TmsSize.rowMinHeight,
                    ),
                    onPressed: () => _attemptLogout(context),
                    child: Text(
                      '登出',
                      style: TmsText.body.copyWith(color: TmsColors.systemRed),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: TmsSpacing.lg),
              const _VersionLabel(),
            ],
          ),
        ),
      ),
    );
  }
}

/// 讓員工確認現在是用誰的帳號在用這支 App：大頭像、姓名、一行部門。
///
/// 三個值都來自登入時後端回的身分（[ApiClient.session]），不另外打一支 API
/// 問「我是誰」。還沒登入時整張卡不出現。
///
/// 原型這張卡另有樓層與班別兩行副標，以及點進個人資料的 chevron。樓層與班別
/// 後端沒有欄位，個人資料頁也還沒有畫面；chevron 畫了卻點不到任何地方，員工
/// 會一直去戳它。
class _IdentityCard extends StatelessWidget {
  const _IdentityCard();

  /// 頭像裡那個字的字級。60pt 的圓裡放一個中文字，26 是看起來填得剛好的大小。
  static const double _initialFontSize = 26;

  @override
  Widget build(BuildContext context) {
    final session = ApiClient.instance.session;
    if (session == null) {
      return const SizedBox.shrink();
    }
    final subtitle = _subtitle(session);
    return TmsCard(
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.screenEdge,
        vertical: TmsSpacing.md2,
      ),
      child: Row(
        children: [
          Container(
            width: TmsSize.avatarLarge,
            height: TmsSize.avatarLarge,
            alignment: Alignment.center,
            decoration: const BoxDecoration(
              color: TmsColors.brandAccentContainer,
              shape: BoxShape.circle,
            ),
            child: Text(
              _initial(session.name),
              style: const TextStyle(
                fontSize: _initialFontSize,
                fontWeight: FontWeight.w700,
                color: TmsColors.brandOnAccentContainer,
              ),
            ),
          ),
          const SizedBox(width: TmsSpacing.md2),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  session.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TmsText.title3.copyWith(fontWeight: FontWeight.w600),
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: TmsSpacing.xxs),
                  Text(
                    subtitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TmsText.subheadline.copyWith(
                      color: TmsColors.secondaryLabel,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// 取姓名的最後一個字放進頭像。中文名字的名比姓更認得出是誰——一個班裡三
  /// 個姓陳的人，頭像全寫「陳」就分不出來了。
  static String _initial(String name) {
    if (name.isEmpty) {
      return '';
    }
    return name.characters.last;
  }

  /// 姓名底下那一行：部門的白話說法。主管與老闆不綁部門，改寫職務。兩者都認
  /// 不出來時回 null，那一行就不畫。
  static String? _subtitle(LoginResult session) {
    const deptLabels = {
      'housekeeping': '房務',
      'engineering': '工務',
      'frontdesk': '櫃檯',
    };
    const roleLabels = {'manager': '主管', 'owner': '老闆'};
    return deptLabels[session.dept] ?? roleLabels[session.role];
  }
}

/// 讓員工回報問題時講得出自己手上這支是哪一版。版本字串跟著
/// `pubspec.yaml` 的 version 走。
class _VersionLabel extends StatelessWidget {
  const _VersionLabel();

  @override
  Widget build(BuildContext context) {
    return Text(
      'TMS 1.0.0',
      textAlign: TextAlign.center,
      style: TmsText.footnote.copyWith(color: TmsColors.quaternaryLabel),
    );
  }
}
