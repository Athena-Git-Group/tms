import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../ticket/repair_labels.dart';
import '../ticket/ticket_screen.dart';
import '../today/my_today_scope.dart';
import '../today/progress_value.dart';

/// 工務首頁：今日進度三格（手上／今天已完修／急件），以及我手上的工務單。
///
/// 三格的值直接取自 `GET /me/today` 的 engineeringSummary（由 [MyTodayScope]
/// 取一次、整個畫面共用，與房務首頁同一條取用路徑），App 不自己數。
///
/// 手上的單同樣來自 engineeringSummary：後端的 tickets 已經收齊本人手上
/// （已接單／維修中）與今天完修的單，也已經排好（急件在前、未結案在已完修
/// 之前），所以這個畫面依序呈現就好，不自己查、不自己排。
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
/// 內容全部來自今日進度的 engineeringSummary.tickets，與上面三格同一份回應，
/// 不必逐張再打工務單詳情：清單與三格因此一定講同一件事，也不會有一半的單
/// 載得出來、一半載不出來。
class _InHandTickets extends StatelessWidget {
  const _InHandTickets();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    final tickets =
        (today.engineeringSummary ?? EngineeringSummary.none).tickets;
    return Column(
      children: [
        for (final ticket in tickets) _InHandTicketCard(ticket: ticket),
      ],
    );
  }
}

/// 手上的一張單：單號、房號（公共區域的單沒有房號）與狀態，點一下進詳情。
class _InHandTicketCard extends StatelessWidget {
  const _InHandTicketCard({required this.ticket});

  final EngineeringTicket ticket;

  @override
  Widget build(BuildContext context) {
    // 契約把 ticketNo 列為必填，但實際回應可能是 null；id 退回用 taskId，
    // 讓單卡至少還是個可指認的節點，而不是整串變成同一個 null。
    final ticketNo = ticket.ticketNo ?? ticket.taskId;
    final place = ticket.roomNo ?? '公共區域';
    final state = repairStateText(ticket.state);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: TmsSemantics(
        id: 'engHome.in-hand.$ticketNo.card',
        label: '$ticketNo $place $state',
        child: TmsCard(
          padding: const EdgeInsets.all(TmsSpacing.md),
          onTap: () {
            Navigator.of(context).push(
              CupertinoPageRoute<void>(
                builder: (_) => TicketScreen(taskId: ticket.taskId),
              ),
            );
          },
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('$ticketNo · $place', style: TmsText.headline),
              Text(
                state,
                style: TmsText.subheadline
                    .copyWith(color: TmsColors.secondaryLabel),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
