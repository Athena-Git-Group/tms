import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/push_gateway.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_grouped_list.dart';

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
        // 一起捲（iOS 的 large title 行為），「全部已讀」與標題同一列。
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(
              TmsSpacing.screenEdge,
              TmsSpacing.md,
              TmsSpacing.screenEdge,
              TmsSpacing.xxl,
            ),
            children: _buildContent(),
          ),
        ),
      ),
    );
  }

  List<Widget> _buildContent() {
    final header = _Header(onMarkAllRead: _markAllRead);
    if (_loadFailed) {
      return [
        header,
        const SizedBox(height: TmsSpacing.lg),
        const Text('載入失敗，請稍後再試', style: TmsText.subheadline),
      ];
    }
    final items = _items;
    if (items == null) {
      return [
        header,
        const SizedBox(height: TmsSpacing.xxl),
        const Center(child: CupertinoActivityIndicator()),
      ];
    }
    if (items.isEmpty) {
      return [
        header,
        const SizedBox(height: TmsSpacing.lg),
        const _EmptyState(),
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
      const Padding(
        padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
        child: Text(
          '有即時動態時，急件不另外推播，只更新即時動態並展開提醒；這裡會保留紀錄。',
          style: TmsText.footnote,
        ),
      ),
    ];
  }
}

/// 畫面最上方：大標題與右側的「全部已讀」，兩者底端對齊。
class _Header extends StatelessWidget {
  const _Header({required this.onMarkAllRead});

  final VoidCallback onMarkAllRead;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        const Expanded(child: Text('通知', style: TmsText.largeTitle)),
        TmsSemantics(
          id: 'notification.mark-all-read-button',
          child: CupertinoButton(
            padding: EdgeInsets.zero,
            minimumSize: const Size(0, TmsSize.minTouchTarget),
            onPressed: onMarkAllRead,
            child: Text(
              '全部已讀',
              style: TmsText.body.copyWith(color: TmsColors.brandPrimary),
            ),
          ),
        ),
      ],
    );
  }
}

/// 一則通知都沒有時的畫面（iOS 的 ContentUnavailableView）：不包卡片，在剩下
/// 的空間裡置中一個描邊圖示、一行標題、一行說明。
///
/// 整塊的文字顯式寫在 [TmsSemantics] 的 label 上，底下的視覺再把自己的
/// semantics 關掉：定位與讀值因此落在同一個節點，讀出來的也剛好是一句完整的
/// 話，而不是同一段文字被 Flutter 合併兩次。
class _EmptyState extends StatelessWidget {
  const _EmptyState();

  /// 空狀態要把剩下的一屏撐開，內容才會落在視覺中央而不是黏在標題底下。
  static const double _minHeight = 460;

  /// 置中型空狀態的左右內距比畫面邊距再寬一些，說明文才不會長到兩邊見底。
  static const double _horizontalPadding = 32;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'notification.empty-state',
      label: '目前沒有通知。派單、急件與退回都會出現在這裡。',
      child: ExcludeSemantics(
        child: Container(
          constraints: const BoxConstraints(minHeight: _minHeight),
          padding: const EdgeInsets.fromLTRB(
            _horizontalPadding,
            TmsSpacing.xl2,
            _horizontalPadding,
            TmsSpacing.xl2,
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(
                CupertinoIcons.bell,
                size: TmsSize.emptyStateIcon,
                color: TmsColors.quaternaryLabel,
              ),
              const SizedBox(height: TmsSpacing.md2),
              const Text(
                '目前沒有通知',
                textAlign: TextAlign.center,
                style: TmsText.title2,
              ),
              const SizedBox(height: TmsSpacing.xs2),
              Text(
                '派單、急件與退回都會出現在這裡。',
                textAlign: TextAlign.center,
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
            ],
          ),
        ),
      ),
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

  @override
  Widget build(BuildContext context) {
    final body = item.body;
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
              const _KindIcon(),
              const SizedBox(width: TmsSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: TmsSemantics(
                            id: 'notification.item.${item.id}.title',
                            label: item.title,
                            // 文字已放在上面的 label（測試讀 label 做精確比
                            // 對），再讓 Text 自己的 semantics 合併進來會變成
                            // 同一段文字重複兩次。
                            child: ExcludeSemantics(
                              child: Text(
                                item.title,
                                style: item.isUnread
                                    ? TmsText.headline
                                    : TmsText.body,
                              ),
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

/// 通知的圓形分類圖示。
///
/// 原型把通知分七類，各有自己的底色與符號（急件紅、超時橘、完成綠、交辦與
/// 新單品牌色、保養紫、一般灰）。後端的通知契約
/// （`specs/truth/contracts/components/schemas/通知中心.yaml`）目前沒有
/// `kind` 欄位，App 無從分辨是哪一類，所以一律畫原型自己的 fallback：灰底
/// 鈴鐺。等契約補上 kind，這裡換成七色對照表即可，版面尺寸不用動。
class _KindIcon extends StatelessWidget {
  const _KindIcon();

  /// 圓形底上的符號邊長。
  static const double _glyphSize = 18;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: TmsSize.notificationKindIcon,
      height: TmsSize.notificationKindIcon,
      decoration: const BoxDecoration(
        color: TmsColors.quaternaryLabel,
        shape: BoxShape.circle,
      ),
      alignment: Alignment.center,
      child: const Icon(
        CupertinoIcons.bell_fill,
        size: _glyphSize,
        color: TmsColors.brandOnPrimary,
      ),
    );
  }
}

/// 把通知時間寫成員工看得懂的相對說法：一分鐘內「剛剛」，一小時內數分鐘，
/// 一天內數小時，昨天寫「昨天 HH:mm」，更早寫日期。
///
/// 絕對時刻對當班的人沒有用——她要知道的是「這則是剛剛才來的，還是上一班
/// 留下的」。
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
  if (minutes < 60 * 48) {
    final hour = local.hour.toString().padLeft(2, '0');
    final minute = local.minute.toString().padLeft(2, '0');
    return '昨天 $hour:$minute';
  }
  return '${local.month}/${local.day}';
}
