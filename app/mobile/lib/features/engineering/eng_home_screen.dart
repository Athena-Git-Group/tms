import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';
import '../ticket/repair_labels.dart';
import '../ticket/ticket_screen.dart';
import '../today/my_today_scope.dart';
import '../today/progress_value.dart';
import 'eng_pool_screen.dart';

/// 工務首頁：今日進度三格（手上／今天已完修／急件），以及我手上的工務單。
///
/// 三格的值直接取自 `GET /me/today` 的 engineeringSummary（由 [MyTodayScope]
/// 取一次、整個畫面共用，與房務首頁同一條取用路徑），App 不自己數。
///
/// 手上的單目前只列得出待開始的那些：契約沒有「我的工務單」查詢，能讀到
/// 自己名下單子的只有 `GET /me/today` 的 nextTasks，而它只收 accepted。
/// 維修中與已完修的單因此進不了這個列表——缺的是後端的清單端點，不是這個
/// 畫面的呈現邏輯。
class EngHomeScreen extends StatelessWidget {
  const EngHomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'engHome.screen',
      child: MyTodayScope(
        child: CupertinoPageScaffold(
          navigationBar:
              const CupertinoNavigationBar(middle: Text('工務首頁')),
          child: SafeArea(
            child: ListView(
              children: [
                const _LoadFailureNotice(),
                const _EngineeringProgress(),
                Center(
                  child: CupertinoButton(
                    onPressed: () {
                      Navigator.of(context).push(
                        CupertinoPageRoute<void>(
                          builder: (_) => const EngPoolScreen(),
                        ),
                      );
                    },
                    child: const Text('待認領'),
                  ),
                ),
                const _InHandTickets(),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 今日進度載入失敗時的一行提示。
class _LoadFailureNotice extends StatelessWidget {
  const _LoadFailureNotice();

  @override
  Widget build(BuildContext context) {
    if (MyTodayScope.errorOf(context) == null) {
      return const SizedBox.shrink();
    }
    return const Padding(
      padding: EdgeInsets.all(16),
      child: Text('載入今日進度失敗，請稍後再試。'),
    );
  }
}

/// 今日進度三格。
class _EngineeringProgress extends StatelessWidget {
  const _EngineeringProgress();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    // 房務身分拿到的回應沒有 engineeringSummary，三格當成 0 張畫出來，
    // 而不是整塊消失：這個畫面的主要內容就是這三格，沒有它們等於空白一片。
    final summary = today.engineeringSummary ?? EngineeringSummary.none;
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          _ProgressTile(
            id: 'engHome.progress.in-hand-count',
            caption: '手上',
            value: '${summary.openCount}',
          ),
          _ProgressTile(
            id: 'engHome.progress.repaired-count',
            caption: '今天已完修',
            value: '${summary.completedToday}',
          ),
          _ProgressTile(
            id: 'engHome.progress.urgent-count',
            caption: '急件',
            value: '${summary.urgentCount}',
          ),
        ],
      ),
    );
  }
}

/// 今日進度的一格：數字在上、名稱在下，名稱在掛 id 的節點外面
/// （理由見 [ProgressValue]）。
class _ProgressTile extends StatelessWidget {
  static const TextStyle _valueStyle =
      TextStyle(fontSize: 22, fontWeight: FontWeight.bold);

  const _ProgressTile({
    required this.id,
    required this.caption,
    required this.value,
  });

  final String id;
  final String caption;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        ProgressValue(
          id: id,
          value: value,
          style: _valueStyle,
        ),
        const SizedBox(height: 4),
        Text(caption),
      ],
    );
  }
}

/// 我手上的工務單清單。
///
/// 單卡的內容要逐張打工務單詳情才拿得到，所以這塊自己載自己的：清單載不出來
/// 時上面的三格進度仍然要在。進度是這個畫面的主要內容，不該被清單的失敗拖下水。
class _InHandTickets extends StatefulWidget {
  const _InHandTickets();

  @override
  State<_InHandTickets> createState() => _InHandTicketsState();
}

class _InHandTicketsState extends State<_InHandTickets> {
  /// 已經依哪一份今日進度載過單卡；同一份不重載。
  MyToday? _loadedFrom;
  List<RepairTicketDetail>? _tickets;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final today = MyTodayScope.todayOf(context);
    if (today == null || identical(today, _loadedFrom)) {
      return;
    }
    _loadedFrom = today;
    _load(today);
  }

  Future<void> _load(MyToday today) async {
    final tickets = <RepairTicketDetail>[];
    for (final task in today.nextTasks.where((task) => task.type == 'repair')) {
      try {
        tickets.add(await ApiClient.instance.readRepairTicket(task.taskId));
      } catch (_) {
        continue;
      }
    }
    if (mounted) {
      setState(() {
        _tickets = tickets;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final tickets = _tickets;
    if (tickets == null) {
      return const SizedBox.shrink();
    }
    return Column(
      children: [
        for (final ticket in tickets)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: TmsSemantics(
              id: 'engHome.in-hand.${ticket.ticketNo}.card',
              label:
                  '${ticket.ticketNo} ${ticket.roomNo} ${repairStatusSummaryText(ticket)}',
              child: GestureDetector(
                onTap: () {
                  Navigator.of(context).push(
                    CupertinoPageRoute<void>(
                      builder: (_) => TicketScreen(taskId: ticket.taskId),
                    ),
                  );
                },
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: CupertinoColors.white,
                    border: Border.all(color: CupertinoColors.systemGrey4),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('${ticket.ticketNo} · ${ticket.roomNo}'),
                      Text(repairStatusSummaryText(ticket)),
                    ],
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}
