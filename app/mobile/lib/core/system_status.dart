import 'package:flutter/cupertino.dart';

import 'offline_queue.dart';
import 'test_backdoor.dart';
import 'tms_semantics.dart';

/// 離線時唯一還能做的事是把手上進行中的那間房做完，其餘動作被擋下來時都用
/// 這一句話告訴員工原因。
///
/// 每個被擋的入口各寫一句的話，同一個限制會出現四種說法，員工得自己猜這些
/// 是不是同一回事。
const String offlineOnlyCompleteMessage = '目前離線，只能完成進行中的單';

/// 離線完成任務或離線開立工務單之後，畫面用這一句告訴員工東西還在手機上。
const String pendingSyncMessage = '已離線暫存，連線後自動同步';

/// 連線不穩定時畫面頂部那一列的文字。
const String connectionIssueMessage = '資料可能延遲，每 5 秒自動更新';

/// 即時推送不可靠時改用輪詢，這是兩次查詢之間相隔的時間。
///
/// 畫面上的那一列把這個秒數寫給員工看，所以兩邊要同一個值；各寫各的會讓
/// 畫面承諾 5 秒、實際上卻是別的間隔。
const Duration degradedPollInterval = Duration(seconds: 5);

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
      valueListenable: TestHooks.instance.forceOffline,
      builder: (context, offline, _) {
        if (offline) {
          return const _OfflineBar();
        }
        return ValueListenableBuilder<bool>(
          valueListenable: TestHooks.instance.flakyConnection,
          builder: (context, flaky, _) {
            if (!flaky) {
              return const SizedBox.shrink();
            }
            return const _StatusStrip(
              id: 'system.connection-issue-bar',
              text: connectionIssueMessage,
              tint: Color(0xFFFFF3CD),
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
      valueListenable: OfflineQueue.instance.pendingCount,
      builder: (context, pendingCount, _) {
        return Container(
          width: double.infinity,
          color: const Color(0xFFE5E5EA),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const TmsSemantics(
                id: 'system.offline-bar',
                label: '目前離線',
                child: ExcludeSemantics(child: Text('目前離線')),
              ),
              TmsSemantics(
                id: 'system.offline-bar.pending-count',
                label: '$pendingCount',
                child: ExcludeSemantics(child: Text('$pendingCount 筆待同步')),
              ),
            ],
          ),
        );
      },
    );
  }
}

/// 一整列的狀態提示，整列讀出來剛好是那一句話。
class _StatusStrip extends StatelessWidget {
  const _StatusStrip({required this.id, required this.text, required this.tint});

  final String id;
  final String text;
  final Color tint;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      label: text,
      child: Container(
        width: double.infinity,
        color: tint,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: ExcludeSemantics(child: Text(text)),
      ),
    );
  }
}

/// 離線時員工點了不能做的動作，用這個提示告訴他為什麼沒有反應。
///
/// 呼叫端必須在呼叫後端之前就先問 [OfflineQueue.isOffline] 並改走這裡，不要
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
