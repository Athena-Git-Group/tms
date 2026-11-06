import 'package:flutter/cupertino.dart';

import 'connection_status.dart';
import 'theme.dart';
import 'tms_semantics.dart';

/// 離線時唯一還能做的事是把手上進行中的那間房做完，其餘動作被擋下來時都用
/// 這一句話告訴員工原因。
///
/// 每個被擋的入口各寫一句的話，同一個限制會出現四種說法，員工得自己猜這些
/// 是不是同一回事。
const String offlineOnlyCompleteMessage = '目前離線，只能完成進行中的單';

/// 離線完成任務或離線開立工務單之後，畫面用這一句告訴員工東西還在手機上。
const String pendingSyncMessage = '已離線暫存，連線後自動同步';

/// 連線不穩定時畫面頂部那一列的文字。秒數直接取自降級輪詢的間隔，畫面承諾
/// 的時間就不會跟實際重查的時間各走各的。
final String connectionIssueMessage =
    '資料可能延遲，每 ${degradedPollInterval.inSeconds} 秒自動更新';

/// 連線不穩定那一列的標題。狀態列的第一行講「現在是什麼狀況」，第二行才講
/// 「所以會怎樣」；只留一行的話員工得自己從「資料可能延遲」反推連線出問題了。
const String connectionIssueTitle = '即時連線異常';

/// 離線那一列的標題。
const String offlineTitle = '目前離線';

/// 離線那一列的說明：離線時還能做什麼，一句話講完。
const String offlineDescription = '只能完成進行中的單';

/// 畫面頂部的連線狀態列，離線與連線不穩定兩種情況各顯示一列。
///
/// 這兩種狀態是跨畫面的，每個畫面各自判斷與各自排版的話，同一個狀態在不同
/// 畫面上會長得不一樣，而且新畫面很容易漏掉其中一種。需要呈現連線狀態的畫
/// 面把這個元件放在內容最上方即可。
///
/// 連線正常時這個元件不佔任何高度。
class SystemStatusBar extends StatelessWidget {
  const SystemStatusBar({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<bool>(
      valueListenable: ConnectionStatus.instance.offline,
      builder: (context, offline, _) {
        if (offline) {
          return const _OfflineBar();
        }
        return ValueListenableBuilder<bool>(
          valueListenable: ConnectionStatus.instance.unstable,
          builder: (context, unstable, _) {
            if (!unstable) {
              return const SizedBox.shrink();
            }
            return _StatusStrip(
              id: 'system.connection-issue-bar',
              icon: CupertinoIcons.exclamationmark_triangle,
              iconColor: TmsColors.systemOrange,
              title: connectionIssueTitle,
              description: connectionIssueMessage,
              tint: TmsColors.cardBackground,
              titleColor: TmsColors.label,
              descriptionColor: TmsColors.secondaryLabel,
            );
          },
        );
      },
    );
  }
}

/// 離線列：左邊是「目前離線」，右邊是還沒送出去的筆數。
///
/// 兩段文字各自掛一個 accessibility id，因為驗收要分別讀得到文案與數字；
/// 合成一個節點時兩者會被接成一串，讀的一方得自己剝字串才拿得到數字。
class _OfflineBar extends StatelessWidget {
  const _OfflineBar();

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<int>(
      valueListenable: ConnectionStatus.instance.pendingSyncCount,
      builder: (context, pendingCount, _) {
        return _StatusBarFrame(
          tint: TmsColors.offlineBarBackground,
          icon: CupertinoIcons.wifi_slash,
          iconColor: TmsColors.offlineBarLabel,
          title: TmsSemantics(
            id: 'system.offline-bar',
            label: offlineTitle,
            child: ExcludeSemantics(
              child: Text(
                offlineTitle,
                style: TmsText.subheadlineEmphasized.copyWith(
                  color: TmsColors.offlineBarLabel,
                ),
              ),
            ),
          ),
          description: Text(
            offlineDescription,
            style: TmsText.footnote.copyWith(
              color: TmsColors.offlineBarSecondaryLabel,
            ),
          ),
          trailing: TmsSemantics(
            id: 'system.offline-bar.pending-count',
            label: '$pendingCount',
            child: ExcludeSemantics(
              child: Text(
                '$pendingCount 筆待同步',
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.offlineBarSecondaryLabel,
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

/// 白底的狀態列（目前只有「即時連線異常」用）：標題講狀況、說明講影響，左邊
/// 一個圖示標語氣。
class _StatusStrip extends StatelessWidget {
  const _StatusStrip({
    required this.id,
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.description,
    required this.tint,
    required this.titleColor,
    required this.descriptionColor,
  });

  final String id;
  final IconData icon;
  final Color iconColor;
  final String title;

  /// 整列的 accessibility label 就是這一句：驗收讀的是「所以會怎樣」，不是
  /// 標題那四個字。
  final String description;
  final Color tint;
  final Color titleColor;
  final Color descriptionColor;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      label: description,
      child: ExcludeSemantics(
        child: _StatusBarFrame(
          tint: tint,
          icon: icon,
          iconColor: iconColor,
          title: Text(
            title,
            style: TmsText.subheadlineEmphasized.copyWith(color: titleColor),
          ),
          description: Text(
            description,
            style: TmsText.footnote.copyWith(color: descriptionColor),
          ),
        ),
      ),
    );
  }
}

/// 連線狀態列共用的版型：圓角方塊、左圖示、中間兩行文字、右側補充。
///
/// 離線與連線異常兩列只差底色、圖示與文字，版型抽在這裡，兩列才不會隨著各自
/// 改動慢慢長成兩個高度不一樣的東西——它們出現在同一個位置，差一點點就看得
/// 出來。
class _StatusBarFrame extends StatelessWidget {
  const _StatusBarFrame({
    required this.tint,
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.description,
    this.trailing,
  });

  final Color tint;
  final IconData icon;
  final Color iconColor;
  final Widget title;
  final Widget description;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.lg,
        vertical: TmsSpacing.sm2,
      ),
      decoration: BoxDecoration(
        color: tint,
        borderRadius: BorderRadius.circular(TmsRadius.card),
      ),
      child: Row(
        children: [
          Icon(icon, size: TmsSize.statusIcon, color: iconColor),
          const SizedBox(width: TmsSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                title,
                const SizedBox(height: TmsSpacing.xxs),
                description,
              ],
            ),
          ),
          if (trailing != null) ...[
            const SizedBox(width: TmsSpacing.md),
            trailing!,
          ],
        ],
      ),
    );
  }
}

/// 離線時員工點了不能做的動作，用這個提示告訴他為什麼沒有反應。
///
/// 呼叫端必須在呼叫後端之前就先問 [ConnectionStatus.isOffline] 並改走這裡，不要
/// 讓請求送出去再靠失敗回應補救：離線時那個請求根本到不了後端，員工只會看到
/// 轉圈到逾時。
Future<void> showOfflineBlockedAlert(BuildContext context) {
  return showCupertinoDialog<void>(
    context: context,
    builder: (dialogContext) => TmsSemantics(
      id: 'system.offline-blocked-alert',
      label: offlineOnlyCompleteMessage,
      child: CupertinoAlertDialog(
        content: const ExcludeSemantics(child: Text(offlineOnlyCompleteMessage)),
        actions: [
          TmsSemantics(
            id: 'system.offline-blocked-alert.dismiss-button',
            child: CupertinoDialogAction(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('知道了'),
            ),
          ),
        ],
      ),
    ),
  );
}
