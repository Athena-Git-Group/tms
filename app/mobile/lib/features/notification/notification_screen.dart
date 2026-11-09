import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/push_gateway.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_notification_kind_icon.dart';

/// 通知中心畫面（ui-plan 畫面 9）。
///
/// 資料來源是 [ApiClient.listNotifications]（`GET /notifications`）：後端只回
/// 依 createdAt 新到舊排好的單一清單，「今天／更早」分組是 App 端以通知的
/// `createdAt` 對照裝置時間的日期切出來的（契約明訂分組屬呈現邏輯）。
///
/// 行為：
/// - 未讀（`readAt == null`）的通知顯示未讀點
///   （`notification.item.{id}.unread-dot`），標題同時加粗；
/// - 點通知項先打 `POST /notifications/{id}/read` 標已讀，再把通知的
///   `target` 交給 [PushGateway.openTarget] 導頁——去處解析不在畫面層
///   另寫第二套對照；
/// - 「全部標為已讀」（`notification.mark-all-read-button`）打
///   `POST /notifications/read-all`，以回傳清單整批刷新；
/// - 一則通知都沒有時顯示空狀態（`notification.empty-state`）。
class NotificationScreen extends StatefulWidget {
  const NotificationScreen({super.key});

  @override
  State<NotificationScreen> createState() => _NotificationScreenState();
}

class _NotificationScreenState extends State<NotificationScreen> {
  List<NotificationItem>? _items;
  bool _loadFailed = false;

  /// 遞增的狀態版本。進畫面的初始載入與「全部標為已讀」都會改 [_items]，
  /// 兩者的回應誰先誰後沒有保證：使用者在初始載入還沒回來前就點了全部
  /// 已讀時，較晚到的初始載入帶的是標記前的舊清單，不能讓它把較新的
  /// 已讀狀態蓋回去。每次要改狀態前取號，回應到手時版本已經前進就丟棄。
  int _stateVersion = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final version = ++_stateVersion;
    try {
      final items = await ApiClient.instance.listNotifications();
      if (!mounted || version != _stateVersion) {
        return;
      }
      setState(() {
        _items = items;
        _loadFailed = false;
      });
    } catch (_) {
      if (!mounted || version != _stateVersion) {
        return;
      }
      setState(() {
        _loadFailed = true;
      });
    }
  }

  Future<void> _markAllRead() async {
    final version = ++_stateVersion;
    final items = await ApiClient.instance.markAllNotificationsRead();
    if (!mounted || version != _stateVersion) {
      return;
    }
    setState(() {
      _items = items;
    });
  }

  /// 點一則通知：先標已讀（權威狀態落地），再依 target 導頁。
  Future<void> _open(NotificationItem item) async {
    final version = ++_stateVersion;
    final updated = await ApiClient.instance.markNotificationRead(item.id);
    if (!mounted) {
      return;
    }
    if (version == _stateVersion) {
      setState(() {
        _items = _items
            ?.map((existing) => existing.id == updated.id ? updated : existing)
            .toList();
      });
    }
    await PushGateway.instance.openTarget(item.target);
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'notification.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        // 不用 CupertinoNavigationBar：這一頁的標題是內容的一部分，跟著列表
        // 一起捲（iOS 的 large title 行為），「全部標為已讀」與標題同一列。
        // 底部不讓 SafeArea 收邊：內容要從浮動分頁列底下捲過去，讓開分頁列
        // 的那段留白由捲動內容自己帶。
        child: SafeArea(
          bottom: false,
          child: ListView(
            padding: EdgeInsets.fromLTRB(
              TmsSpacing.screenEdge,
              TmsSpacing.md,
              TmsSpacing.screenEdge,
              TmsSpacing.scrollPadBottom(MediaQuery.paddingOf(context).bottom),
            ),
            children: _buildContent(),
          ),
        ),
      ),
    );
  }

  List<Widget> _buildContent() {
    // 連線狀態列固定在內容最上方：離線時通知清單停在最後一次抓到的那一份，
    // 員工得先知道這一屏可能不是最新的，才不會以為「沒有新通知」。
    const statusBar = SystemStatusBar(
      padding: EdgeInsets.only(bottom: TmsSpacing.md),
    );
    // 「全部標為已讀」只在還有未讀時出現（`Main.dc.html:344`）：全部都讀過之後
    // 這顆按鈕按了什麼也不會變，留著只是讓人以為還有事情沒做。
    final header = _Header(
      onMarkAllRead: _markAllRead,
      showMarkAllRead: _items?.any((item) => item.isUnread) ?? false,
    );
    if (_loadFailed) {
      return [
        statusBar,
        header,
        const SizedBox(height: TmsSpacing.lg),
        const Text('載入失敗，請稍後再試', style: TmsText.subheadline),
      ];
    }
    final items = _items;
    if (items == null) {
      return [
        statusBar,
        header,
        const SizedBox(height: TmsSpacing.xxl),
        const Center(child: CupertinoActivityIndicator()),
      ];
    }
    if (items.isEmpty) {
      return [
        statusBar,
        header,
        const SizedBox(height: TmsSpacing.lg),
        const TmsContentUnavailable(
          id: 'notification.empty-state',
          copy: TmsEmptyStates.notifications,
          minHeight: TmsContentUnavailable.notificationMinHeight,
        ),
      ];
    }

    // 分組以裝置時間的「日期」切：createdAt 落在今天的進「今天」，其餘進
    // 「更早」。後端清單已新到舊，分組內不重排。
    final today = <NotificationItem>[];
    final earlier = <NotificationItem>[];
    final now = DateTime.now();
    for (final item in items) {
      final local = item.createdAt.toLocal();
      final isToday =
          local.year == now.year &&
          local.month == now.month &&
          local.day == now.day;
      (isToday ? today : earlier).add(item);
    }

    return [
      statusBar,
      header,
      const SizedBox(height: TmsSpacing.lg),
      if (today.isNotEmpty) ...[
        _NotificationGroup(
          groupKey: 'today',
          title: '今天',
          items: today,
          onTap: _open,
          now: now,
        ),
        const SizedBox(height: TmsSpacing.lg),
      ],
      if (earlier.isNotEmpty) ...[
        _NotificationGroup(
          groupKey: 'earlier',
          title: '更早',
          items: earlier,
          onTap: _open,
          now: now,
        ),
        const SizedBox(height: TmsSpacing.lg),
      ],
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
        child: Text(
          '有即時動態時，急件不另外推播，只更新即時動態並展開提醒；這裡會保留紀錄。',
          // `Main.dc.html:408` 的 `line-height: 1.5`。這段是整頁最底下的一塊
          // 說明，行距比 [TmsText.footnote] 的 18/13 鬆，才不會讀成一團。
          style: TmsText.footnote.copyWith(height: 1.5),
        ),
      ),
    ];
  }
}

/// 畫面最上方：大標題與右側的「全部標為已讀」，兩者底端對齊。
class _Header extends StatelessWidget {
  const _Header({required this.onMarkAllRead, required this.showMarkAllRead});

  final VoidCallback onMarkAllRead;

  final bool showMarkAllRead;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        const Expanded(child: Text('通知', style: TmsText.largeTitle)),
        if (showMarkAllRead)
          TmsSemantics(
            id: 'notification.mark-all-read-button',
            child: CupertinoButton(
              padding: EdgeInsets.zero,
              minimumSize: const Size(0, TmsSize.minTouchTarget),
              onPressed: onMarkAllRead,
              child: Text(
                '全部標為已讀',
                style: TmsText.body.copyWith(color: TmsColors.brandPrimary),
              ),
            ),
          ),
      ],
    );
  }
}

/// 「今天」或「更早」一整組：小標題加一張白卡，卡裡一則通知一列。
class _NotificationGroup extends StatelessWidget {
  const _NotificationGroup({
    required this.groupKey,
    required this.title,
    required this.items,
    required this.onTap,
    required this.now,
  });

  final String groupKey;
  final String title;
  final List<NotificationItem> items;
  final ValueChanged<NotificationItem> onTap;

  /// 算「幾分鐘前」的基準時刻。整頁共用同一個，否則同一屏裡的兩列各按自己
  /// 的 now 去算，相鄰兩則的相對時間可能前後顛倒。
  final DateTime now;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'notification.group.$groupKey.section',
      // 分組是「容器」：通知項目的節點要留在分組節點底下，測試才能在
      // 分組內數項目順序（dsl 的「以 section 定位該分組，其中的 title…」）。
      explicitChildNodes: true,
      child: TmsGroupedSection(
        header: title,
        children: [
          for (final item in items)
            _NotificationRow(item: item, now: now, onTap: () => onTap(item)),
        ],
      ),
    );
  }
}

/// 一則通知：未讀點、圓形分類圖示、標題與時間、內文兩行。
///
/// 未讀同時用兩種方式表達——左側的點，以及標題的字重（未讀 600、已讀 400）。
/// 只靠圓點的話，一屏十幾則通知掃過去分不出哪些還沒看；只靠字重又太細微。
class _NotificationRow extends StatelessWidget {
  const _NotificationRow({
    required this.item,
    required this.now,
    required this.onTap,
  });

  final NotificationItem item;
  final DateTime now;
  final VoidCallback onTap;

  /// 未讀點要對齊標題那一行的中央，而不是整列的中央——內文有兩行時，整列的
  /// 中央會掉到標題底下。這個值是列的上緣到標題中線的距離。
  static const double _dotTopInset = 14;

  /// 標題行高（`Main.dc.html:359` 的 `line-height: 1.35`）。通知列的標題常常
  /// 折成兩行，比 [TmsText.headline] 的 22/17 再鬆一點才不會黏在一起。
  static const double _titleLineHeight = 1.35;

  /// 內文行高（`Main.dc.html:360` 的 `line-height: 1.4`）。
  static const double _bodyLineHeight = 1.4;

  @override
  Widget build(BuildContext context) {
    final body = item.body;
    final titleStyle = (item.isUnread ? TmsText.headline : TmsText.body)
        .copyWith(height: _titleLineHeight);
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      // 每一列自成一個 semantics 容器：分組只有一列時，Flutter 會把沒有
      // 自己節點的列內容往上收，未讀點與標題就不在分組節點底下了；列固定
      // 成節點，分組內的結構才不隨列數改變。
      child: Semantics(
        container: true,
        child: Container(
          color: TmsColors.cardBackground,
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.md,
            TmsSpacing.md,
            TmsSpacing.screenEdge,
            TmsSpacing.md,
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 未讀點：只有未讀才掛節點，「已讀＝節點不存在」讓測試的
              // 「不存在或不可見」斷言直接成立。已讀列留同寬的空位，未讀與
              // 已讀兩種列的分類圖示左緣才對得齊。
              SizedBox(
                width: TmsSize.unreadDot,
                child: item.isUnread
                    ? Padding(
                        padding: const EdgeInsets.only(top: _dotTopInset),
                        child: TmsSemantics(
                          id: 'notification.item.${item.id}.unread-dot',
                          label: '未讀',
                          child: Container(
                            width: TmsSize.unreadDot,
                            height: TmsSize.unreadDot,
                            decoration: const BoxDecoration(
                              color: TmsColors.brandPrimary,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ),
                      )
                    : null,
              ),
              const SizedBox(width: TmsSpacing.md),
              TmsNotificationKindIcon(kind: _kindOf(item)),
              const SizedBox(width: TmsSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      // 標題 17 與時間 13 以文字基線對齊（`Main.dc.html:359`）。
                      // 靠頂端對齊的話，兩段字級不同的文字上緣齊、底下各自
                      // 參差，時間看起來會浮在標題上方。
                      crossAxisAlignment: CrossAxisAlignment.baseline,
                      textBaseline: TextBaseline.alphabetic,
                      children: [
                        Expanded(
                          child: TmsSemantics(
                            id: 'notification.item.${item.id}.title',
                            label: item.title,
                            // 文字已放在上面的 label（測試讀 label 做精確比
                            // 對），再讓 Text 自己的 semantics 合併進來會變成
                            // 同一段文字重複兩次。
                            child: ExcludeSemantics(
                              child: Text(item.title, style: titleStyle),
                            ),
                          ),
                        ),
                        const SizedBox(width: TmsSpacing.sm),
                        Text(
                          relativeTimeLabel(item.createdAt, now),
                          style: TmsText.footnote.copyWith(
                            color: TmsColors.quaternaryLabel,
                          ),
                        ),
                      ],
                    ),
                    if (body != null && body.isNotEmpty) ...[
                      const SizedBox(height: TmsSpacing.xxs),
                      Text(
                        body,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TmsText.subheadline.copyWith(
                          color: TmsColors.secondaryLabel,
                          height: _bodyLineHeight,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 一則通知屬於哪一類，決定左側圓形圖示的顏色與符號。
///
/// 判斷只看通知的 `event`（`shared.yaml` 的 `PushPayload.event`，十七種事件
/// 值），不看標題：標題是給人讀的文案，改一個字就讓整批通知換顏色太脆弱。
///
/// 十七種事件收斂成七類的規則是「收件人看到這一則要做什麼」：
///
/// - 急件（紅）：`repair_urgent`——要立刻有人去。
/// - 超時（橘）：`unclaimed_reminder_staff`、`unclaimed_reminder_manager`、
///   `shift_end_still_interrupted`——單卡著沒人處理，再不動就拖下去。
/// - 完成（綠）：`inspection_passed`、`repair_completed`——事情做完了。
/// - 新單（藍・收件匣）：`pool_new_request`、`reassigned_to_me`、
///   `leave_takeover`、`inspection_failed`、`spot_check_failed`——一張單進到
///   你手上或你的池子裡，接下來要做它。查房退回與抽查未過也在這裡：房間退回
///   重做，對收件人就是「這張單又回來了」。
/// - 交辦（藍・對話框）：`kb_reply`、`kb_unhelpful`——有人在問答裡留了話，
///   要讀不要做。
/// - 保養（紫）：目前十七種事件沒有保養派單專屬的事件值，保養單進池走的是
///   `pool_new_request`；後端補上保養事件之前，這一類不會出現在通知中心。
/// - 一般（灰）：`task_cancelled`、`reassigned_away`、`shift_end_returned`、
///   `guest_withdrew`——單已經不在你手上，純告知；以及沒見過的事件值。分不出
///   類別時畫成一般通知，比硬塞進一個看起來很急的分類好。
TmsNotificationKind _kindOf(NotificationItem item) {
  switch (item.event) {
    case 'repair_urgent':
      return TmsNotificationKind.urgent;
    case 'unclaimed_reminder_staff':
    case 'unclaimed_reminder_manager':
    case 'shift_end_still_interrupted':
      return TmsNotificationKind.timeout;
    case 'inspection_passed':
    case 'repair_completed':
      return TmsNotificationKind.done;
    case 'pool_new_request':
    case 'reassigned_to_me':
    case 'leave_takeover':
    case 'inspection_failed':
    case 'spot_check_failed':
      return TmsNotificationKind.newRequest;
    case 'kb_reply':
    case 'kb_unhelpful':
      return TmsNotificationKind.memo;
    default:
      return TmsNotificationKind.info;
  }
}

/// 把通知時間寫成員工看得懂的相對說法：一分鐘內「剛剛」，一小時內數分鐘，
/// 一天內數小時，更早一律「昨天 HH:mm」（`Main.dc.html:4553`）。
///
/// 絕對時刻對當班的人沒有用——她要知道的是「這則是剛剛才來的，還是上一班
/// 留下的」。一天以上不再改寫成日期：這一頁是當班看的流水紀錄，知道「不是
/// 今天的」就夠，確切哪一天點進去那張單上看。
String relativeTimeLabel(DateTime createdAt, DateTime now) {
  final local = createdAt.toLocal();
  final minutes = now.difference(local).inMinutes;
  if (minutes < 1) {
    return '剛剛';
  }
  if (minutes < 60) {
    return '$minutes 分鐘前';
  }
  if (minutes < 60 * 24) {
    return '${minutes ~/ 60} 小時前';
  }
  final hour = local.hour.toString().padLeft(2, '0');
  final minute = local.minute.toString().padLeft(2, '0');
  return '昨天 $hour:$minute';
}
