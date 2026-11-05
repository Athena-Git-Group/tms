import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/push_gateway.dart';
import '../../core/tms_semantics.dart';

/// 通知中心畫面（ui-plan 畫面 9）。
///
/// 資料來源是 [ApiClient.listNotifications]（`GET /notifications`）：後端只回
/// 依 createdAt 新到舊排好的單一清單，「今天／更早」分組是 App 端以通知的
/// `createdAt` 對照裝置時間的日期切出來的（契約明訂分組屬呈現邏輯）。
///
/// 行為：
/// - 未讀（`readAt == null`）的通知顯示未讀藍點
///   （`notification.item.{id}.unread-dot`）；
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
        navigationBar: CupertinoNavigationBar(
          middle: const Text('通知'),
          trailing: TmsSemantics(
            id: 'notification.mark-all-read-button',
            child: CupertinoButton(
              padding: EdgeInsets.zero,
              onPressed: _markAllRead,
              child: const Text('全部已讀'),
            ),
          ),
        ),
        child: SafeArea(child: _buildBody()),
      ),
    );
  }

  Widget _buildBody() {
    if (_loadFailed) {
      return const Center(child: Text('載入失敗，請稍後再試'));
    }
    final items = _items;
    if (items == null) {
      return const Center(child: CupertinoActivityIndicator());
    }
    if (items.isEmpty) {
      return Center(
        child: TmsSemantics(
          id: 'notification.empty-state',
          label: '目前沒有通知',
          child: const Text('目前沒有通知'),
        ),
      );
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

    return ListView(
      children: [
        if (today.isNotEmpty)
          _NotificationGroup(
            groupKey: 'today',
            title: '今天',
            items: today,
            onTap: _open,
          ),
        if (earlier.isNotEmpty)
          _NotificationGroup(
            groupKey: 'earlier',
            title: '更早',
            items: earlier,
            onTap: _open,
          ),
      ],
    );
  }
}

class _NotificationGroup extends StatelessWidget {
  const _NotificationGroup({
    required this.groupKey,
    required this.title,
    required this.items,
    required this.onTap,
  });

  final String groupKey;
  final String title;
  final List<NotificationItem> items;
  final ValueChanged<NotificationItem> onTap;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'notification.group.$groupKey.section',
      // 分組是「容器」：通知項目的節點要留在分組節點底下，測試才能在
      // 分組內數項目順序（dsl 的「以 section 定位該分組，其中的 title…」）。
      explicitChildNodes: true,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            for (final item in items)
              _NotificationRow(item: item, onTap: () => onTap(item)),
          ],
        ),
      ),
    );
  }
}

class _NotificationRow extends StatelessWidget {
  const _NotificationRow({required this.item, required this.onTap});

  final NotificationItem item;
  final VoidCallback onTap;

  /// 未讀藍點的直徑與它到標題的間距。已讀列以同寬的空位補齊，
  /// 讓未讀與已讀列的標題左緣對齊。
  static const double _dotSize = 10;
  static const double _dotGap = 8;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      // 每一列自成一個 semantics 容器：分組只有一列時，Flutter 會把沒有
      // 自己節點的列內容往上收，藍點與標題就不在分組節點底下了；列固定
      // 成節點，分組內的結構才不隨列數改變。
      child: Semantics(
        container: true,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Row(
            children: [
              // 未讀藍點：只有未讀才掛節點，「已讀＝節點不存在」讓測試的
              // 「不存在或不可見」斷言直接成立。
              if (item.isUnread)
                TmsSemantics(
                  id: 'notification.item.${item.id}.unread-dot',
                  label: '未讀',
                  child: Container(
                    width: _dotSize,
                    height: _dotSize,
                    margin: const EdgeInsets.only(right: _dotGap),
                    decoration: const BoxDecoration(
                      color: CupertinoColors.activeBlue,
                      shape: BoxShape.circle,
                    ),
                  ),
                )
              else
                const SizedBox(width: _dotSize + _dotGap),
              Expanded(
                child: TmsSemantics(
                  id: 'notification.item.${item.id}.title',
                  label: item.title,
                  // 文字已放在上面的 label（測試讀 label 做精確比對），再讓
                  // Text 自己的 semantics 合併進來會變成同一段文字重複兩次。
                  child: ExcludeSemantics(child: Text(item.title)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
