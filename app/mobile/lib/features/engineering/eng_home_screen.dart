import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/tms_semantics.dart';
import '../ticket/repair_labels.dart';
import '../ticket/ticket_screen.dart';
import 'eng_pool_screen.dart';

/// 工務首頁（ui-plan 畫面 13 的 `engHome`）。
///
/// 今日進度三格（手上／今天已完修／急件）直接取 `GET /me/today` 的
/// engineeringSummary，App 不自己數。下方列出我手上的工務單，點單卡進
/// 工務單詳情。
///
/// 手上的單目前只列得出待開始的那些：契約沒有「我的工務單」查詢，能讀到
/// 自己名下單子的只有 `GET /me/today` 的 nextTasks，而它只收 accepted。
/// 維修中與已完修的單因此進不了這個列表——缺的是後端的清單端點，不是這個
/// 畫面的呈現邏輯。
class EngHomeScreen extends StatefulWidget {
  const EngHomeScreen({super.key});

  @override
  State<EngHomeScreen> createState() => _EngHomeScreenState();
}

class _EngHomeScreenState extends State<EngHomeScreen> {
  EngineeringSummary? _summary;
  List<RepairTicketDetail>? _tickets;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final MyToday today;
    try {
      today = await ApiClient.instance.getMyToday();
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = '載入今日進度失敗，請稍後再試。';
        });
      }
      return;
    }
    if (mounted) {
      setState(() {
        _summary = today.engineeringSummary ??
            const EngineeringSummary(
                openCount: 0, completedToday: 0, urgentCount: 0);
        _error = null;
      });
    }

    // 單卡列表載不出來不該讓三格進度跟著消失：進度是這個畫面的主要內容，
    // 兩者各自獨立處理失敗。
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
    final summary = _summary;
    final tickets = _tickets;
    return TmsSemantics(
      id: 'engHome.screen',
      child: CupertinoPageScaffold(
        navigationBar: const CupertinoNavigationBar(middle: Text('工務首頁')),
        child: SafeArea(
          child: ListView(
            children: [
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(_error!),
                ),
              if (summary != null)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      _ProgressTile(
                        id: 'engHome.progress.in-hand-count',
                        label: '手上',
                        count: summary.openCount,
                      ),
                      _ProgressTile(
                        id: 'engHome.progress.repaired-count',
                        label: '今天已完修',
                        count: summary.completedToday,
                      ),
                      _ProgressTile(
                        id: 'engHome.progress.urgent-count',
                        label: '急件',
                        count: summary.urgentCount,
                      ),
                    ],
                  ),
                ),
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
              if (tickets != null)
                for (final ticket in tickets)
                  Padding(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                    child: TmsSemantics(
                      id: 'engHome.in-hand.${ticket.ticketNo}.card',
                      label:
                          '${ticket.ticketNo} ${ticket.roomNo} ${repairStatusSummaryText(ticket)}',
                      child: GestureDetector(
                        onTap: () {
                          Navigator.of(context).push(
                            CupertinoPageRoute<void>(
                              builder: (_) =>
                                  TicketScreen(taskId: ticket.taskId),
                            ),
                          );
                        },
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: CupertinoColors.white,
                            border:
                                Border.all(color: CupertinoColors.systemGrey4),
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
          ),
        ),
      ),
    );
  }
}

class _ProgressTile extends StatelessWidget {
  const _ProgressTile({
    required this.id,
    required this.label,
    required this.count,
  });

  final String id;
  final String label;
  final int count;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        TmsSemantics(
          id: id,
          label: '$count',
          // 數字已經由上面的 label 曝露；不擋掉 Text 自己的 semantics 的話，
          // 讀到的會是 label 與子節點文字接在一起的「2\n2」。
          child: ExcludeSemantics(
            child: Text(
              '$count',
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
            ),
          ),
        ),
        const SizedBox(height: 4),
        Text(label),
      ],
    );
  }
}
