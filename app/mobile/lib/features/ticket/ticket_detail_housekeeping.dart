import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_callout.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_photo_cell.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_scroll_nav_bar.dart';
import '../../core/widgets/tms_timeline.dart';
import '../manager/manager_ids.dart';
import 'repair_labels.dart';
import 'ticket_last_verify_failure_card.dart';

class TicketDetailHousekeeping extends StatelessWidget {
  const TicketDetailHousekeeping({
    super.key,
    required this.ticket,
    required this.errorMessage,
    required this.onWithdraw,
    required this.onAddPhoto,
    required this.onOpenPhoto,
  });

  final RepairTicketDetail? ticket;

  final String? errorMessage;

  final VoidCallback onWithdraw;
  final VoidCallback onAddPhoto;

  final void Function(int index) onOpenPhoto;

  static const double _scrollThreshold = 4;

  static const double _glassFadeStop = 0.7;

  @override
  Widget build(BuildContext context) {
    final detail = ticket;
    final title = detail == null
        ? '工務單'
        : '${detail.ticketNo} · ${repairCategoryText(detail.category)}';
    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      child: SafeArea(
        bottom: false,
        child: Column(
          children: [
            Expanded(
              child: TmsScrollNavBarScroll(
                threshold: _scrollThreshold,
                builder: (context, scrolled) => Stack(
                  children: [
                    _buildScrollView(detail),
                    Positioned(
                      top: 0,
                      left: 0,
                      right: 0,
                      child: _buildNavBar(context, scrolled, title),
                    ),
                  ],
                ),
              ),
            ),
            if (detail != null) ?_buildActionBar(detail),
          ],
        ),
      ),
    );
  }

  Widget _buildNavBar(BuildContext context, bool scrolled, String title) {
    return TmsScrollNavBar(
      scrolled: scrolled,
      restingBackground: TmsColors.groupedBackground,
      restingForeground: TmsColors.label,
      restingTitle: title,
      scrolledTitle: title,
      glassFadeStop: _glassFadeStop,
      leading: TmsNavBarBackButton(
        style: TmsNavBarButtonStyle.plain,
        onPressed: () => Navigator.of(context).maybePop(),
      ),
    );
  }

  Widget _buildScrollView(RepairTicketDetail? detail) {
    final error = errorMessage;
    final Widget body;
    if (error != null) {
      body = Text(error, style: TmsText.body);
    } else if (detail == null) {
      body = const Center(child: CupertinoActivityIndicator());
    } else {
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: _spaced(_sections(detail)),
      );
    }
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        TmsSize.frozenNavBarHeight + TmsSpacing.md,
        TmsSpacing.screenEdge,
        TmsSpacing.xl2,
      ),
      child: body,
    );
  }

  List<Widget> _spaced(List<Widget> sections) {
    final spaced = <Widget>[];
    for (var i = 0; i < sections.length; i++) {
      if (i > 0) {
        spaced.add(const SizedBox(height: TmsSpacing.md));
      }
      spaced.add(sections[i]);
    }
    return spaced;
  }

  List<Widget> _sections(RepairTicketDetail detail) {
    final summary = _summaryOf(detail);
    final note = _actionNote(detail);
    return [
      TmsSemantics(
        id: 'ticket.status-summary-card',
        label: '${summary.title} ${summary.detail}',
        child: ExcludeSemantics(
          child: TmsStatusSummaryCard(
            glyph: summary.glyph,
            glyphBackground: summary.background,
            title: summary.title,
            detail: summary.detail,
            detailColor: summary.detailColor,
          ),
        ),
      ),
      if (TicketLastVerifyFailureCard.shows(detail))
        TicketLastVerifyFailureCard(ticket: detail),
      _sectionWithHeader('進度', _timeline(detail)),
      ?_photoSection(detail),
      _sectionWithHeader('內容', _infoCard(detail)),
      if (note != null)
        TmsSemantics(
          id: 'ticket.action-note',
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: TmsSpacing.screenEdge,
            ),
            child: Text(note, style: TmsText.footnote.copyWith(height: 1.4)),
          ),
        ),
    ];
  }

  Widget _sectionWithHeader(String header, Widget card) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.screenEdge,
            0,
            TmsSpacing.screenEdge,
            TmsSpacing.xs2,
          ),
          child: Text(header, style: TmsText.footnote),
        ),
        card,
      ],
    );
  }


  ({
    String title,
    String detail,
    TmsStatusSummaryGlyph glyph,
    Color background,
    Color detailColor,
  })
  _summaryOf(RepairTicketDetail detail) {
    final title = repairStatusSummaryText(
      detail,
      audience: RepairSummaryAudience.reporter,
    );
    final who = detail.assigneeName;
    if (repairIsReworking(detail)) {
      return (
        title: title,
        detail: '上一次修完沒有通過驗收，工務正在重修。',
        glyph: TmsStatusSummaryGlyph.wrench,
        background: TmsColors.brandAccent,
        detailColor: TmsColors.secondaryLabel,
      );
    }
    switch (repairCompletionStage(detail)) {
      case RepairCompletionStage.awaitingVerification:
        return (
          title: title,
          detail: who == null ? '工務已回報完修。' : '工務 $who 回報完修。',
          glyph: TmsStatusSummaryGlyph.checkmark,
          background: TmsColors.systemGreen,
          detailColor: TmsColors.secondaryLabel,
        );
      case RepairCompletionStage.closedVerified:
        return (
          title: title,
          detail: '主管驗收通過，這張單已結案。',
          glyph: TmsStatusSummaryGlyph.checkmark,
          background: TmsColors.systemGreen,
          detailColor: TmsColors.secondaryLabel,
        );
      case RepairCompletionStage.closedNoAction:
        return (
          title: title,
          detail: who == null ? '工務到場確認不需要處理。' : '工務 $who 到場確認不需要處理。',
          glyph: TmsStatusSummaryGlyph.checkmark,
          background: TmsColors.quaternaryLabel,
          detailColor: TmsColors.secondaryLabel,
        );
      case RepairCompletionStage.notCompleted:
        break;
    }
    switch (detail.state) {
      case 'cancelled':
        return (
          title: title,
          detail: '工務不會再看到這張單。',
          glyph: TmsStatusSummaryGlyph.cross,
          background: TmsColors.quaternaryLabel,
          detailColor: TmsColors.secondaryLabel,
        );
      case 'pool':
        return (
          title: title,
          detail: detail.lastVerifyFailedAt != null
              ? '上一次修完沒有通過驗收，等工務重新接單。'
              : '工務接單後會開始處理。',
          glyph: TmsStatusSummaryGlyph.clock,
          background: TmsColors.systemOrange,
          detailColor: TmsColors.secondaryLabel,
        );
      case 'accepted':
        return (
          title: title,
          detail: '還沒開始維修。',
          glyph: TmsStatusSummaryGlyph.checkmark,
          background: TmsColors.brandPrimary,
          detailColor: TmsColors.secondaryLabel,
        );
      case 'doing':
        return (
          title: title,
          detail: '修好會通知你。',
          glyph: TmsStatusSummaryGlyph.wrench,
          background: TmsColors.brandPrimary,
          detailColor: TmsColors.secondaryLabel,
        );
      default:
        return (
          title: title,
          detail: '',
          glyph: TmsStatusSummaryGlyph.clock,
          background: TmsColors.quaternaryLabel,
          detailColor: TmsColors.secondaryLabel,
        );
    }
  }

  Widget _timeline(RepairTicketDetail detail) {
    final steps = _timelineSteps(detail);
    return TmsSemantics(
      id: 'ticket.timeline',
      label: [
        for (final step in steps)
          step.note == null ? step.label : '${step.label} ${step.note}',
      ].join('，'),
      child: TmsTimeline(steps: steps),
    );
  }

  List<TmsTimelineStep> _timelineSteps(RepairTicketDetail detail) {
    final state = detail.state;
    final who = detail.assigneeName;
    final createdAt = detail.createdAt;
    final sentTime = createdAt == null ? null : repairClockText(createdAt);
    final current = state == 'pool' ? 0 : repairCurrentStepIndex(detail);
    final stage = repairCompletionStage(detail);

    TmsTimelineStatus statusOf(int index) {
      if (index < current) {
        return TmsTimelineStatus.done;
      }
      return index == current
          ? TmsTimelineStatus.current
          : TmsTimelineStatus.pending;
    }

    final downgrade = detail.urgentDowngradeReason;
    final String firstNote;
    if (detail.isUrgent) {
      firstNote = '系統判定急件（${detail.urgentRule ?? '規則'}），已同步通知主管';
    } else if (downgrade != null) {
      firstNote = '主管改為一般件：$downgrade';
    } else {
      firstNote = '房務 ${detail.reporterNameText} 報修';
    }
    final notes = <String?>[
      firstNote,
      state == 'doing' && who != null ? '工務 $who 維修中' : null,
      stage == RepairCompletionStage.notCompleted || who == null
          ? null
          : '工務 $who',
      switch (stage) {
        RepairCompletionStage.closedVerified =>
          detail.verifiedBy == null ? null : '${detail.verifiedBy}驗收',
        RepairCompletionStage.closedNoAction => '不需處理，直接結案',
        _ => null,
      },
    ];

    if (state == 'cancelled') {
      return [
        TmsTimelineStep(
          label: '已撤回',
          status: TmsTimelineStatus.failed,
          note: firstNote,
          time: sentTime,
        ),
      ];
    }
    return [
      for (var i = 0; i < repairStepLabels.length; i++)
        TmsTimelineStep(
          label: repairStepLabels[i],
          status: statusOf(i),
          note: notes[i],
          time: i == 0 ? sentTime : null,
          wrap: (node) => _stepWrap(node, i, statusOf(i)),
        ),
    ];
  }

  Widget _stepWrap(Widget node, int index, TmsTimelineStatus status) {
    final Widget step = TmsSemantics(
      id: 'ticket.stepper.${index + 1}',
      label: repairStepLabels[index],
      child: node,
    );
    return switch (status) {
      TmsTimelineStatus.current => TmsSemantics(
        id: 'ticket.stepper.${index + 1}--current',
        child: step,
      ),
      TmsTimelineStatus.done => TmsSemantics(
        id: ManagerIds.ticketStepperDone(index + 1),
        child: step,
      ),
      _ => step,
    };
  }

  Widget? _photoSection(RepairTicketDetail detail) {
    final canAdd =
        detail.state != 'done' &&
        detail.state != 'closed' &&
        detail.state != 'cancelled';
    final photos = detail.photos;
    if (photos.isEmpty && !canAdd) {
      return null;
    }
    final head = '照片（${photos.length} 張，含浮水印）';
    return _sectionWithHeader(
      head,
      TmsCard(
        style: TmsCardStyle.inSheet,
        padding: const EdgeInsets.all(TmsSpacing.md),
        child: GridView.count(
          crossAxisCount: 3,
          mainAxisSpacing: TmsSpacing.sm,
          crossAxisSpacing: TmsSpacing.sm,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: [
            for (var i = 0; i < photos.length; i++)
              TmsSemantics(
                id: 'ticket.photo.${i + 1}',
                child: TmsPhotoCell(
                  edge: null,
                  semanticsLabel: '放大 ${detail.ticketNo} 照片 ${i + 1}',
                  onTap: () => onOpenPhoto(i),
                ),
              ),
            if (canAdd)
              TmsSemantics(
                id: 'ticket.photo.add-button',
                child: TmsAddPhotoButton.inGrid(
                  label: '補照片',
                  onTap: onAddPhoto,
                ),
              ),
          ],
        ),
      ),
    );
  }


  Widget _infoCard(RepairTicketDetail detail) {
    final rows = <({String key, String value})>[
      (key: '房號', value: detail.whereText),
      (key: '類別', value: _categoryValue(detail)),
      (key: '說明', value: detail.description ?? '—'),
      (key: '報修人', value: _reporterValue(detail)),
    ];
    return ClipRRect(
      borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
      child: ColoredBox(
        color: TmsColors.cardBackground,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < rows.length; i++)
              _InfoRow(
                label: rows[i].key,
                value: rows[i].value,
                showDivider: i < rows.length - 1,
              ),
          ],
        ),
      ),
    );
  }

  String _categoryValue(RepairTicketDetail detail) {
    final category = repairCategoryText(detail.category);
    if (detail.isUrgent) {
      final rule = detail.urgentRule;
      return rule == null ? '$category（急件）' : '$category（急件 · $rule）';
    }
    if (detail.urgentDowngradeReason != null) {
      return '$category（原為急件，主管已改為一般件）';
    }
    return category;
  }

  String _reporterValue(RepairTicketDetail detail) {
    final createdAt = detail.createdAt;
    return createdAt == null
        ? detail.reporterNameText
        : '${detail.reporterNameText} · ${repairClockText(createdAt)}';
  }

  String? _actionNote(RepairTicketDetail detail) {
    switch (detail.state) {
      case 'pool':
        return '工務接單前可以補照片或撤回；接單後只能補照片。';
      case 'accepted':
      case 'doing':
        return '工務處理中仍可補照片，例如問題變嚴重時。';
      default:
        return null;
    }
  }


  Widget? _buildActionBar(RepairTicketDetail detail) {
    if (detail.state != 'pool') {
      return null;
    }
    return TmsActionBar(
      primary: TmsSemantics(
        id: 'ticket.withdraw-button',
        child: TmsPillButton(
          label: '撤回工務單',
          style: TmsPillButtonStyle.dangerOutlined,
          onPressed: onWithdraw,
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({
    required this.label,
    required this.value,
    required this.showDivider,
  });

  final String label;
  final String value;
  final bool showDivider;

  static const double _labelWidth = 64;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.screenEdge,
        vertical: TmsSpacing.groupedRowVertical,
      ),
      decoration: showDivider
          ? const BoxDecoration(
              border: Border(
                bottom: BorderSide(
                  color: TmsColors.separator,
                  width: TmsSize.hairline,
                ),
              ),
            )
          : null,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: _labelWidth,
            child: Text(
              label,
              style: TmsText.body.copyWith(color: TmsColors.secondaryLabel),
            ),
          ),
          const SizedBox(width: TmsSpacing.md),
          Expanded(child: Text(value, style: TmsText.body)),
        ],
      ),
    );
  }
}
