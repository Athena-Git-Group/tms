import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_fab.dart';
import '../../core/widgets/tms_greeting_row.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_home_cards.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_repair_ticket_row.dart';
import '../../core/widgets/tms_status_chip.dart';
import '../../core/widgets/tms_tip_popover.dart';
import '../pool/pool_widgets.dart';
import '../shell/main_shell.dart';
import '../ticket/repair_ticket_form_sheet.dart';
import '../ticket/ticket_screen.dart';
import '../today/my_today_scope.dart';
import '../today/progress_value.dart';

class EngHomeScreen extends StatefulWidget {
  const EngHomeScreen({super.key});

  @override
  State<EngHomeScreen> createState() => _EngHomeScreenState();
}

class _EngHomeScreenState extends State<EngHomeScreen> {
  bool _tipSeen = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MainShellTab.indexOf(context) != MainShellTab.homeIndex) {
      _tipSeen = true;
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.paddingOf(context).bottom;
    final fabBottom = TmsFab.bottomAboveTabBar(bottomInset);
    return TmsSemantics(
      id: 'engHome.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            SafeArea(
              bottom: false,
              child: ListView(
                padding: EdgeInsets.fromLTRB(
                  TmsSpacing.screenEdge,
                  TmsSpacing.md,
                  TmsSpacing.screenEdge,
                  TmsSpacing.scrollPadBottom(bottomInset) +
                      TmsFab.scrollPadClearance,
                ),
                children: const [
                  SystemStatusBar(),
                  _LoadFailureNotice(),
                  _GreetingRow(),
                  SizedBox(height: TmsSpacing.lg),
                  _EngineeringProgress(),
                  SizedBox(height: TmsSpacing.lg),
                  _FocusCard(),
                  SizedBox(height: TmsSpacing.lg),
                  _InHandTickets(),
                ],
              ),
            ),
            Positioned(
              right: TmsFab.sideInset,
              bottom: fabBottom,
              child: TmsSemantics(
                id: 'engHome.create-ticket-fab',
                child: TmsFab(
                  glyph: TmsFabGlyph.plus,
                  semanticsLabel: '開工單',
                  onPressed: () {
                    setState(() => _tipSeen = true);
                    RepairTicketFormSheet.show(context);
                  },
                ),
              ),
            ),
            if (_tipVisible)
              Positioned(
                right: TmsFab.sideInset,
                bottom: TmsTipPopover.bottomAbove(fabBottom, TmsFab.diameter),
                child: TmsTipPopover(
                  title: '在這裡開工單',
                  message: '巡檢時發現問題，拍照就能直接開單。',
                  width: _tipWidth,
                  onDismiss: () => setState(() => _tipSeen = true),
                ),
              ),
          ],
        ),
      ),
    );
  }

  bool get _tipVisible {
    if (_tipSeen) {
      return false;
    }
    return ModalRoute.of(context)?.isCurrent ?? true;
  }

  static const double _tipWidth = 260;
}

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

class _GreetingRow extends StatelessWidget {
  const _GreetingRow();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    final summary = today?.engineeringSummary ?? EngineeringSummary.none;
    final settled =
        today != null && summary.openCount == 0 && summary.completedToday > 0;
    return TmsGreetingRow(
      greeting: settled ? '辛苦了' : '早安',
      name: ApiClient.instance.session?.name,
      shift: today?.shift,
      trailing: '工務',
    );
  }
}

class _EngineeringProgress extends StatelessWidget {
  const _EngineeringProgress();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    final summary = today.engineeringSummary ?? EngineeringSummary.none;
    final denominator =
        summary.plannedToday ?? (summary.completedToday + summary.openCount);
    final ratio = denominator == 0 ? 0.0 : summary.completedToday / denominator;
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
          const SizedBox(height: TmsSpacing.xs),
          Text(
            '張已完修',
            style: TmsText.caption1.copyWith(
              fontWeight: FontWeight.w400,
              color: TmsColors.secondaryLabel,
            ),
          ),
        ],
      ),
      details: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TmsStatBlock(
            value: Row(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                ProgressValue(
                  id: 'engHome.progress.in-hand-count',
                  value: '${summary.openCount}',
                  style: TmsText.title3,
                ),
                const Text(' 張工單', style: TmsText.footnoteMedium),
              ],
            ),
            caption: '手上還有',
          ),
          const SizedBox(height: TmsSpacing.md),
          _UrgentAndMaintenance(summary: summary),
        ],
      ),
    );
  }
}

class _UrgentAndMaintenance extends StatelessWidget {
  const _UrgentAndMaintenance({required this.summary});

  final EngineeringSummary summary;

  @override
  Widget build(BuildContext context) {
    final urgent = TmsStatBlock(
      value: ProgressValue(
        id: 'engHome.progress.urgent-count',
        value: '${summary.urgentCount}',
        style: TmsText.displayStat.copyWith(
          color: summary.urgentCount > 0
              ? TmsColors.onErrorContainer
              : TmsColors.label,
        ),
      ),
      caption: '急件',
    );
    final done = summary.maintenanceDone;
    final total = summary.maintenanceTotal;
    if (done == null || total == null) {
      return urgent;
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        urgent,
        const SizedBox(width: TmsSpacing.lg),
        TmsStatBlock(
          value: Text(
            '$done / $total',
            style: TmsText.displayStat.copyWith(color: TmsColors.brandAccent),
          ),
          caption: '今日保養',
        ),
      ],
    );
  }
}

class _FocusCard extends StatelessWidget {
  const _FocusCard();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    final summary = today.engineeringSummary ?? EngineeringSummary.none;
    for (final ticket in summary.tickets) {
      if (ticket.state == 'doing') {
        return _TicketHeroCard(ticket: ticket, doing: true);
      }
    }
    for (final ticket in summary.tickets) {
      if (ticket.state == 'accepted') {
        return _TicketHeroCard(ticket: ticket, doing: false);
      }
    }
    return TmsContentUnavailable.inCard(
      id: 'engHome.empty-state',
      copy: TmsEmptyStates.engineeringHome(
        doneCount: summary.completedToday,
        poolHasItems: today.poolBadgeCount > 0,
      ),
    );
  }
}

class _TicketHeroCard extends StatelessWidget {
  const _TicketHeroCard({required this.ticket, required this.doing});

  final EngineeringTicket ticket;
  final bool doing;

  static const double _titleDrop = 5;

  static const double _ornamentGutter = 80;

  static const double _playIconSize = 18;

  @override
  Widget build(BuildContext context) {
    final place = ticket.roomNo ?? '公共區域';
    final foreground = doing ? TmsColors.brandOnPrimary : TmsColors.label;
    final placeStyle =
        (ticket.roomNo == null
                ? TmsText.displayAreaName
                : TmsText.displayRoomNo)
            .copyWith(color: foreground);
    return doing
        ? _buildDoing(context, place, placeStyle)
        : _buildNext(context, place, placeStyle);
  }

  Widget _buildDoing(BuildContext context, String place, TextStyle placeStyle) {
    final workedMinutes = ticket.workedMinutes;
    return TmsHeroCard(
      onDark: true,
      eyebrow: '維修中',
      onTap: () => _openTicket(context, ticket.taskId),
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              place,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: placeStyle,
            ),
            const SizedBox(width: TmsSpacing.md),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(bottom: _titleDrop),
                child: Text(
                  ticket.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.brandContainer,
                  ),
                ),
              ),
            ),
            if (workedMinutes != null) ...[
              const SizedBox(width: TmsSpacing.md),
              Text(
                '$workedMinutes 分',
                style: TmsText.displayTimerCompact.copyWith(
                  color: TmsColors.brandOnPrimary,
                ),
              ),
            ],
          ],
        ),
        TmsPillButton(
          label: '繼續處理',
          style: TmsPillButtonStyle.inverse,
          height: 48,
          onPressed: () => _openTicket(context, ticket.taskId),
        ),
      ],
    );
  }

  Widget _buildNext(BuildContext context, String place, TextStyle placeStyle) {
    final buttonLabel = '開始維修 $place';
    final source = ticket.source;
    final sourceText = source == null
        ? null
        : engineeringPoolSourceLabels[source];
    return TmsHeroCard(
      eyebrow: '下一張（急件優先）',
      ornamentIcon: CupertinoIcons.wrench,
      onTap: () => _openTicket(context, ticket.taskId),
      children: [
        Padding(
          padding: const EdgeInsets.only(right: _ornamentGutter),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                place,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: placeStyle,
              ),
              const SizedBox(height: TmsSpacing.xs),
              Text(
                sourceText == null
                    ? ticket.title
                    : '${ticket.title} · $sourceText',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TmsText.subheadline,
              ),
            ],
          ),
        ),
        if (ticket.isUrgent)
          const TmsStatusChip.of(
            TmsStatusAppearance(
              text: '急件',
              background: TmsColors.errorContainer,
              foreground: TmsColors.onErrorContainer,
            ),
          ),
        TmsPillButton(
          label: buttonLabel,
          height: 50,
          onPressed: () => _openTicket(context, ticket.taskId),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                CupertinoIcons.play_fill,
                size: _playIconSize,
                color: TmsColors.brandOnPrimary,
              ),
              const SizedBox(width: TmsSpacing.sm),
              Flexible(
                child: Text(
                  buttonLabel,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TmsText.headline.copyWith(
                    color: TmsColors.brandOnPrimary,
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

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
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.xs,
            0,
            TmsSpacing.xs,
            TmsSpacing.sm,
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('我的任務', style: TmsText.title3),
              Text(
                '依緊急程度排序',
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
            ],
          ),
        ),
        TmsGroupedSection(
          children: [
            for (final ticket in tickets)
              TmsRepairTicketRow(
                ticket: ticket,
                onTap: () => _openTicket(context, ticket.taskId),
              ),
          ],
        ),
      ],
    );
  }
}

void _openTicket(BuildContext context, String taskId) {
  Navigator.of(context).push(
    CupertinoPageRoute<void>(
      builder: (_) =>
          TicketScreen(view: TicketView.engineering, taskId: taskId),
    ),
  );
}
