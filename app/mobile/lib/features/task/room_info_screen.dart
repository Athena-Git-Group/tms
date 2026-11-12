import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_scroll_nav_bar.dart';
import '../ticket/repair_labels.dart';
import '../ticket/ticket_screen.dart';
import 'task_screen.dart';

class _RoomInfoScaffold extends StatelessWidget {
  const _RoomInfoScaffold({required this.title, required this.children});

  final String title;

  final List<Widget> children;

  static const double _contentGap = TmsSpacing.md;

  @override
  Widget build(BuildContext context) {
    final padding = MediaQuery.paddingOf(context);
    final spaced = <Widget>[];
    for (final child in children) {
      if (spaced.isNotEmpty) {
        spaced.add(const SizedBox(height: TmsSpacing.md));
      }
      spaced.add(child);
    }
    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      child: TmsScrollNavBarScroll(
        threshold: TmsSpacing.xs,
        builder: (context, scrolled) => Stack(
          children: [
            SingleChildScrollView(
              padding: EdgeInsets.fromLTRB(
                TmsSpacing.screenEdge,
                padding.top + TmsSize.frozenNavBarHeight + _contentGap,
                TmsSpacing.screenEdge,
                (padding.bottom > TmsSpacing.screenEdge
                        ? padding.bottom
                        : TmsSpacing.screenEdge) +
                    TmsSpacing.screenEdge,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: spaced,
              ),
            ),
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: TmsScrollNavBar(
                scrolled: scrolled,
                topInset: padding.top,
                restingBackground: TmsColors.groupedBackground,
                restingForeground: TmsColors.label,
                restingTitle: title,
                scrolledTitle: title,
                leading: TmsNavBarBackButton(
                  onPressed: () => Navigator.of(context).maybePop(),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class HandoverListScreen extends StatelessWidget {
  const HandoverListScreen({
    super.key,
    required this.roomNo,
    required this.entries,
  });

  final String roomNo;

  final List<HandoverEntry> entries;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'room-info.handovers.screen',
      child: _RoomInfoScaffold(
        title: '交辦 · $roomNo',
        children: [
          TmsGroupedSection(
            style: TmsCardStyle.inSheet,
            children: [
              for (var i = 0; i < entries.length; i++)
                _HandoverRow(index: i, entry: entries[i]),
            ],
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
            child: Text(
              '主管交代、接手進度、交班單與櫃檯備註，同一間房的都收在這裡。',
              style: TmsText.footnote,
            ),
          ),
        ],
      ),
    );
  }
}

class _HandoverRow extends StatelessWidget {
  const _HandoverRow({required this.index, required this.entry});

  final int index;
  final HandoverEntry entry;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: TmsColors.cardBackground,
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        TmsSpacing.groupedRowVertical,
        TmsSpacing.screenEdge,
        TmsSpacing.groupedRowVertical,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TmsSemantics(
            id: 'room-info.handover.$index.content',
            label: entry.content,
            child: ExcludeSemantics(
              child: Text(
                entry.content,
                style: TmsText.body.copyWith(height: 1.45),
              ),
            ),
          ),
          const SizedBox(height: TmsSpacing.xs),
          TmsSemantics(
            id: 'room-info.handover.$index.meta',
            label: taskHandoverMeta(entry),
            child: ExcludeSemantics(
              child: Text(taskHandoverMeta(entry), style: TmsText.footnote),
            ),
          ),
        ],
      ),
    );
  }
}

class RepairTicketListScreen extends StatelessWidget {
  const RepairTicketListScreen({
    super.key,
    required this.roomNo,
    required this.tickets,
  });

  final String roomNo;

  final List<RepairTicketSummary> tickets;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'room-info.repair-tickets.screen',
      child: _RoomInfoScaffold(
        title: '工務單 · $roomNo',
        children: [
          TmsGroupedSection(
            style: TmsCardStyle.inSheet,
            children: [
              for (final ticket in tickets)
                _RepairTicketRow(
                  ticket: ticket,
                  onTap: () => Navigator.of(context).push(
                    CupertinoPageRoute<void>(
                      builder: (_) => TicketScreen(
                        view: TicketView.housekeeping,
                        taskId: ticket.taskId,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _RepairTicketRow extends StatelessWidget {
  const _RepairTicketRow({required this.ticket, required this.onTap});

  final RepairTicketSummary ticket;
  final VoidCallback onTap;

  static const EdgeInsets _urgentBadgePadding = EdgeInsets.symmetric(
    horizontal: TmsSpacing.xs2,
    vertical: 1,
  );

  @override
  Widget build(BuildContext context) {
    final rejected = ticket.state == 'rejected';
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: TmsSemantics(
        id: 'room-info.repair-ticket.${ticket.ticketNo}.row',
        child: Container(
          color: TmsColors.cardBackground,
          constraints: const BoxConstraints(minHeight: 56),
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.screenEdge,
            vertical: TmsSpacing.sm2,
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            '${ticket.ticketNo} · '
                            '${repairCategoryText(ticket.category)}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TmsText.body,
                          ),
                        ),
                        if (ticket.isUrgent) ...[
                          const SizedBox(width: TmsSpacing.xs2),
                          Container(
                            padding: _urgentBadgePadding,
                            decoration: BoxDecoration(
                              color: TmsColors.errorContainer,
                              borderRadius: BorderRadius.circular(
                                TmsSpacing.xs2,
                              ),
                            ),
                            child: Text(
                              '急件',
                              style: TmsText.caption1.copyWith(
                                color: TmsColors.onErrorContainer,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: TmsSpacing.xxs),
                    Text(
                      repairStateText(ticket.state),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TmsText.subheadline.copyWith(
                        color: rejected
                            ? TmsColors.onErrorContainer
                            : TmsColors.secondaryLabel,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: TmsSpacing.md),
              const TmsChevron(),
            ],
          ),
        ),
      ),
    );
  }
}
