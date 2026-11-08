import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_category_square.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_home_cards.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_tag.dart';
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
///
/// 版型與房務首頁同一套：進度環卡、主卡、白底群組清單三段，共用
/// [TmsProgressRingCard] 與 [TmsHeroCard]。兩個首頁長得一樣，是因為同一位主管
/// 要同時看懂兩邊，而工務與房務的員工也可能互相代班。
class EngHomeScreen extends StatelessWidget {
  const EngHomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'engHome.screen',
      child: MyTodayScope(
        child: CupertinoPageScaffold(
          backgroundColor: TmsColors.groupedBackground,
          navigationBar: const CupertinoNavigationBar(middle: Text('工務首頁')),
          child: SafeArea(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(
                TmsSpacing.screenEdge,
                TmsSpacing.md,
                TmsSpacing.screenEdge,
                TmsSpacing.xxl,
              ),
              children: const [
                // 連線狀態列固定在內容最上方，員工一眼就知道現在是離線還是
                // 連線不穩定，不必等某個操作失敗才發現。
                SystemStatusBar(
                  padding: EdgeInsets.only(bottom: TmsSpacing.md),
                ),
                _LoadFailureNotice(),
                _EngineeringProgress(),
                SizedBox(height: TmsSpacing.lg),
                _FocusCard(),
                SizedBox(height: TmsSpacing.lg),
                _InHandTickets(),
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
      padding: EdgeInsets.only(bottom: TmsSpacing.lg),
      child: Text('載入今日進度失敗，請稍後再試。'),
    );
  }
}

/// 今日進度卡：左邊一個完修完成度的環，右邊手上張數與急件數。
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
    // 環的分母優先用後端排出來的「今天預計要修幾張」（plannedToday）：那是
    // 排程說了算的目標量，一整天不會因為新單進來就變動，環的進度才有意義。
    //
    // 排不出預計量時後端給 null，這時退回「今天已完修＋手上還沒結」相加。
    // 這條退路是畫面自己湊的數字、會隨著新單進來往上跳（完修一張、又進一張
    // 時環甚至會倒退），但它是這份回應唯一還能支撐的分母——分母拿不到就不畫
    // 環的話，這張卡的主要內容會整塊消失，比一個會動的分母更糟。使用者已經
    // 裁決接受這個現況，所以保留退路、而不是等後端補齊才畫。
    final denominator =
        summary.plannedToday ?? (summary.completedToday + summary.openCount);
    final ratio =
        denominator == 0 ? 0.0 : summary.completedToday / denominator;
    return TmsProgressRingCard(
      progress: ratio,
      ringCenter: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              ProgressValue(
                id: 'engHome.progress.repaired-count',
                value: '${summary.completedToday}',
                style: TmsText.displayMetric,
              ),
              Text(
                ' / $denominator',
                style: TmsText.callout.copyWith(
                  fontWeight: FontWeight.w600,
                  color: TmsColors.tertiaryLabel,
                ),
              ),
            ],
          ),
          const SizedBox(height: TmsSpacing.xxs),
          Text(
            '今天已完修',
            style: TmsText.caption1.copyWith(
              fontWeight: FontWeight.w400,
              color: TmsColors.tertiaryLabel,
            ),
          ),
        ],
      ),
      details: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TmsStatBlock(
            value: ProgressValue(
              id: 'engHome.progress.in-hand-count',
              value: '${summary.openCount}',
              style: TmsText.title3,
            ),
            caption: '手上',
          ),
          const SizedBox(height: TmsSpacing.md),
          _UrgentAndMaintenance(summary: summary),
        ],
      ),
    );
  }
}

/// 今日進度卡右欄下半：急件，以及有保養資料時並排的今日保養。
///
/// 兩格並排而不是上下堆，是照原型：急件與今日保養都是「今天還剩什麼」的同一
/// 層資訊，上面的「手上」才是主數字。
class _UrgentAndMaintenance extends StatelessWidget {
  const _UrgentAndMaintenance({required this.summary});

  final EngineeringSummary summary;

  @override
  Widget build(BuildContext context) {
    final urgent = TmsStatBlock(
      value: ProgressValue(
        id: 'engHome.progress.urgent-count',
        value: '${summary.urgentCount}',
        style: TmsText.title3.copyWith(
          // 急件是唯一要把人拉過去的數字，所以只有它會變色；是 0 的時候
          // 沒有人要被拉過去，跟著其餘數字用一般文字色。
          color: summary.urgentCount > 0
              ? TmsColors.onErrorContainer
              : TmsColors.label,
        ),
      ),
      caption: '急件',
    );
    final done = summary.maintenanceDone;
    final total = summary.maintenanceTotal;
    // 兩個欄位同時有值才畫這一格。只有一邊有值的「3 / —」看不出今天保養排了
    // 幾張，不如整格不畫；後端沒有保養維度時兩邊都是 null，也走這條路。
    if (done == null || total == null) {
      return urgent;
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        urgent,
        const SizedBox(width: TmsSpacing.lg),
        TmsStatBlock(
          // 這一格刻意不掛 accessibility id：沒有斷言在讀保養張數，而新增 id
          // 會動到驗收基準。要驗收時再由 ui-plan 統一編號。
          value: Text(
            '$done / $total',
            style: TmsText.title3.copyWith(color: TmsColors.brandAccent),
          ),
          caption: '今日保養',
        ),
      ],
    );
  }
}

/// 工務首頁的主卡：手上有維修中的單就顯示那一張，否則顯示待開始的第一張。
///
/// 兩張都沒有時整張卡不出現，下方的清單自己會說明手上還有什麼。這張卡刻意不
/// 掛 accessibility id：同一張單在下方清單裡已經有一個掛 id 的節點，這裡再掛
/// 一次會讓同一個 id 在畫面上出現兩個節點。
class _FocusCard extends StatelessWidget {
  const _FocusCard();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    final tickets =
        (today.engineeringSummary ?? EngineeringSummary.none).tickets;
    for (final ticket in tickets) {
      if (ticket.state == 'doing') {
        return _TicketHeroCard(ticket: ticket, doing: true);
      }
    }
    for (final ticket in tickets) {
      if (ticket.state == 'accepted') {
        return _TicketHeroCard(ticket: ticket, doing: false);
      }
    }
    return const SizedBox.shrink();
  }
}

/// 主卡本體：維修中那一張是品牌色底白字，待開始那一張是白底。
class _TicketHeroCard extends StatelessWidget {
  const _TicketHeroCard({required this.ticket, required this.doing});

  final EngineeringTicket ticket;
  final bool doing;

  @override
  Widget build(BuildContext context) {
    final place = ticket.roomNo ?? '公共區域';
    final foreground = doing ? TmsColors.brandOnPrimary : TmsColors.label;
    final subtitleColor = doing
        ? TmsColors.brandContainer
        : TmsColors.secondaryLabel;
    final placeText = Text(
      place,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: TmsText.displayRoomNo.copyWith(color: foreground),
    );
    // 卡右側的計時告訴工務「這張單你已經做了多久」，所以只有維修中那張卡
    // 掛得上：待開始的單還沒開始計時。後端取不到這個數字時給 null，畫面就
    // 不畫這一段——把 null 畫成「0 分」，工務會讀成這張單剛剛才開工。
    final workedMinutes = doing ? ticket.workedMinutes : null;
    return TmsHeroCard(
      onDark: doing,
      eyebrow: doing ? '維修中' : '下一張',
      onTap: () => _openTicket(context, ticket.taskId),
      children: [
        const SizedBox(height: TmsSpacing.md2),
        if (workedMinutes == null)
          placeText
        else
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(child: placeText),
              const SizedBox(width: TmsSpacing.md),
              Padding(
                padding: const EdgeInsets.only(bottom: TmsSpacing.xs),
                child: Text(
                  '$workedMinutes 分',
                  style: TmsText.displayTimer.copyWith(color: foreground),
                ),
              ),
            ],
          ),
        const SizedBox(height: TmsSpacing.xs),
        Text(
          ticket.title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TmsText.subheadline.copyWith(color: subtitleColor),
        ),
        if (ticket.isUrgent && !doing) ...[
          const SizedBox(height: TmsSpacing.sm),
          const TmsTag(label: '急件', tone: TmsTagTone.danger),
        ],
        const SizedBox(height: TmsSpacing.md2),
        TmsPillButton(
          label: doing ? '繼續處理' : '開始維修',
          style: doing ? TmsPillButtonStyle.inverse : TmsPillButtonStyle.filled,
          height: doing ? 48 : 50,
          onPressed: () => _openTicket(context, ticket.taskId),
        ),
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
    if (tickets.isEmpty) {
      return const SizedBox.shrink();
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: TmsSpacing.sm),
          child: Text('我的任務', style: TmsText.title3),
        ),
        TmsGroupedSection(
          children: [
            for (final ticket in tickets) _InHandTicketRow(ticket: ticket),
          ],
        ),
      ],
    );
  }
}

/// 手上的一張單：房號方塊、單號與標題、狀態標籤，點一列進詳情。
class _InHandTicketRow extends StatelessWidget {
  const _InHandTicketRow({required this.ticket});

  final EngineeringTicket ticket;

  @override
  Widget build(BuildContext context) {
    // 契約把 ticketNo 列為必填，但實際回應可能是 null；id 退回用 taskId，
    // 讓單卡至少還是個可指認的節點，而不是整串變成同一個 null。
    final ticketNo = ticket.ticketNo ?? ticket.taskId;
    final place = ticket.roomNo ?? '公共區域';
    final state = repairStateText(ticket.state);
    return TmsSemantics(
      id: 'engHome.in-hand.$ticketNo.card',
      label: '$ticketNo $place $state',
      child: GestureDetector(
        onTap: () => _openTicket(context, ticket.taskId),
        child: Container(
          constraints: const BoxConstraints(minHeight: 64),
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.md2,
            vertical: TmsSpacing.md,
          ),
          color: TmsColors.cardBackground,
          child: ExcludeSemantics(
            child: Row(
              children: [
                TmsRoomSquare(type: 'repair', where: place, size: 48),
                const SizedBox(width: TmsSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        '$ticketNo · $place',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TmsText.headline,
                      ),
                      const SizedBox(height: TmsSpacing.xxs),
                      Text(
                        ticket.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TmsText.subheadline.copyWith(
                          color: TmsColors.secondaryLabel,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: TmsSpacing.sm),
                if (ticket.isUrgent) ...[
                  const TmsTag(label: '急件', tone: TmsTagTone.danger),
                  const SizedBox(width: TmsSpacing.xs2),
                ],
                TmsTag(label: state, tone: TmsTagTone.neutralStrong),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

void _openTicket(BuildContext context, String taskId) {
  Navigator.of(context).push(
    CupertinoPageRoute<void>(builder: (_) => TicketScreen(taskId: taskId)),
  );
}
