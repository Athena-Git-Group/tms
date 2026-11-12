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
import '../../core/widgets/tms_step_track.dart';
import '../manager/manager_ids.dart';
import 'repair_labels.dart';
import 'ticket_last_verify_failure_card.dart';

class TicketDetailEngineering extends StatelessWidget {
  const TicketDetailEngineering({
    super.key,
    required this.ticket,
    required this.errorMessage,
    required this.resolutionLabel,
    required this.onClaim,
    required this.onStart,
    required this.onReleaseToPool,
    required this.onComplete,
    required this.onOpenPhoto,
    this.showWorkerActions = true,
    this.bottomBar,
  });

  final RepairTicketDetail? ticket;

  final String? errorMessage;

  final String? resolutionLabel;

  final VoidCallback onClaim;
  final VoidCallback onStart;
  final VoidCallback onReleaseToPool;
  final VoidCallback onComplete;
  final void Function(int index) onOpenPhoto;

  final bool showWorkerActions;

  final Widget? bottomBar;

  @override
  Widget build(BuildContext context) {
    final detail = ticket;
    final onDark = detail?.state == 'doing';
    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      child: SafeArea(
        bottom: false,
        child: Column(
          children: [
            Expanded(
              child: TmsScrollNavBarScroll(
                builder: (context, scrolled) => Stack(
                  children: [
                    _buildScrollView(detail, onDark: onDark),
                    Positioned(
                      top: 0,
                      left: 0,
                      right: 0,
                      child: _buildNavBar(
                        context,
                        detail,
                        scrolled: scrolled,
                        onDark: onDark,
                      ),
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

  Widget _buildNavBar(
    BuildContext context,
    RepairTicketDetail? detail, {
    required bool scrolled,
    required bool onDark,
  }) {
    final where = detail?.whereText;
    return TmsSemantics(
      id: 'ticket.nav',
      explicitChildNodes: true,
      child: TmsScrollNavBar(
        scrolled: scrolled,
        restingBackground: onDark
            ? TmsColors.brandPrimary
            : TmsColors.cardBackground,
        restingForeground: onDark ? TmsColors.brandOnPrimary : TmsColors.label,
        restingTitle: onDark && where != null ? '$where · 工務單' : '工務單',
        scrolledTitle: where == null ? '工務單' : '$where · 工務單',
        leading: TmsNavBarBackButton(
          onPressed: () => Navigator.of(context).maybePop(),
        ),
      ),
    );
  }

  Widget _buildScrollView(RepairTicketDetail? detail, {required bool onDark}) {
    final error = errorMessage;
    if (error != null || detail == null) {
      return SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(
          TmsSpacing.screenEdge,
          TmsSize.frozenNavBarHeight + TmsSpacing.md,
          TmsSpacing.screenEdge,
          TmsSpacing.xl2,
        ),
        child: error != null
            ? Text(error, style: TmsText.body)
            : const Center(child: CupertinoActivityIndicator()),
      );
    }
    return SingleChildScrollView(
      padding: const EdgeInsets.only(top: TmsSize.frozenNavBarHeight),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildHero(detail, onDark: onDark),
          Padding(
            padding: const EdgeInsets.fromLTRB(
              TmsSpacing.screenEdge,
              TmsSpacing.md2,
              TmsSpacing.screenEdge,
              TmsSpacing.xl2,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: _spaced(_sections(detail)),
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _spaced(List<Widget> sections) {
    final spaced = <Widget>[];
    for (var i = 0; i < sections.length; i++) {
      if (i > 0) {
        spaced.add(const SizedBox(height: TmsSpacing.md2));
      }
      spaced.add(sections[i]);
    }
    return spaced;
  }

  Widget _buildHero(RepairTicketDetail detail, {required bool onDark}) {
    final foreground = onDark ? TmsColors.brandOnPrimary : TmsColors.label;
    final secondary = onDark
        ? TmsColors.brandContainer
        : TmsColors.secondaryLabel;
    return Container(
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.xl,
        TmsSpacing.sm,
        TmsSpacing.xl,
        TmsSpacing.xl,
      ),
      decoration: BoxDecoration(
        color: onDark ? TmsColors.brandPrimary : TmsColors.cardBackground,
        borderRadius: const BorderRadius.only(
          bottomLeft: Radius.circular(TmsRadius.xl),
          bottomRight: Radius.circular(TmsRadius.xl),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TmsSemantics(
            id: 'ticket.hero.where',
            label: detail.whereText,
            child: ExcludeSemantics(
              child: Text(
                detail.whereText,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style:
                    (detail.isPublicArea ? TmsText.title1 : TmsText.displayHero)
                        .copyWith(
                          color: foreground,
                          fontWeight: FontWeight.w800,
                        ),
              ),
            ),
          ),
          const SizedBox(height: TmsSpacing.xs2),
          Text(
            _heroTitle(detail),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TmsText.headline.copyWith(color: foreground),
          ),
          const SizedBox(height: TmsSpacing.xxs),
          Text(
            _heroMeta(detail),
            style: TmsText.subheadline.copyWith(color: secondary),
          ),
          Padding(
            padding: const EdgeInsets.only(top: TmsSpacing.md2),
            child: _buildStepTrack(detail, onDark: onDark),
          ),
        ],
      ),
    );
  }

  String _heroTitle(RepairTicketDetail detail) {
    final description = detail.description;
    if (description == null || description.isEmpty) {
      return repairCategoryText(detail.category);
    }
    return description;
  }

  String _heroMeta(RepairTicketDetail detail) {
    final createdAt = detail.createdAt;
    return [
      '${detail.reporterNameText} 報修',
      repairCategoryText(detail.category),
      if (createdAt != null) '${repairMinutesSince(createdAt)} 分鐘前',
    ].join(' · ');
  }

  Widget _buildStepTrack(RepairTicketDetail detail, {required bool onDark}) {
    final currentIndex = repairCurrentStepIndex(detail);
    return TmsSemantics(
      id: 'ticket.step-track',
      explicitChildNodes: true,
      child: TmsStepTrack(
        onDark: onDark,
        steps: [
          for (var i = 0; i < repairStepLabels.length; i++)
            _trackStep(i, currentIndex),
        ],
      ),
    );
  }

  TmsStepTrackStep _trackStep(int index, int currentIndex) {
    final TmsStepStatus status;
    if (index < currentIndex) {
      status = TmsStepStatus.done;
    } else if (index == currentIndex) {
      status = TmsStepStatus.current;
    } else {
      status = TmsStepStatus.pending;
    }
    return TmsStepTrackStep(
      label: repairStepLabels[index],
      status: status,
      wrap: (node) {
        final Widget step = TmsSemantics(
          id: 'ticket.stepper.${index + 1}',
          label: repairStepLabels[index],
          child: node,
        );
        return switch (status) {
          TmsStepStatus.current => TmsSemantics(
            id: 'ticket.stepper.${index + 1}--current',
            child: step,
          ),
          TmsStepStatus.done => TmsSemantics(
            id: ManagerIds.ticketStepperDone(index + 1),
            child: step,
          ),
          _ => step,
        };
      },
    );
  }

  List<Widget> _sections(RepairTicketDetail detail) {
    return [
      _buildCallouts(detail),
      if (TicketLastVerifyFailureCard.shows(detail))
        TicketLastVerifyFailureCard(ticket: detail),
      ?_photoSection(detail),
      ?_completionPhotoSection(detail),
      _sectionWithHeader('這張單的資訊', _infoCard(detail)),
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

  Widget _buildCallouts(RepairTicketDetail detail) {
    return TmsCalloutSlot(
      candidates: [
        _fixedCallout(detail),
        ?_reworkingCallout(detail),
        if (detail.state == 'pool')
          TmsStatusCallout(
            kind: TmsCalloutKind.ticketInPool,
            tone: TmsCalloutTone.info,
            title: repairStateText(detail.state),
            body: '還沒有人認領這張單，其他工務同仁都接得到。',
            idPrefix: 'ticket.status-summary-card',
            wrap: (node) => _summaryWrap(node, repairStateText(detail.state)),
          ),
        if (detail.state == 'pending_review')
          const TmsStatusCallout(
            kind: TmsCalloutKind.ticketAwaitingReview,
            tone: TmsCalloutTone.info,
            title: '等主管審核',
            body: '房務報修的一般件，主管核准後才會進待認領。現在先讓你知道。',
          ),
        if (detail.isUrgent)
          TmsStatusCallout(
            kind: TmsCalloutKind.ticketUrgent,
            tone: TmsCalloutTone.error,
            title: detail.urgentRule == null
                ? '急件'
                : '急件 · ${detail.urgentRule}',
            body: '系統依飯店設定的規則判定，已同步通知主管。',
          ),
      ],
    );
  }

  TmsCalloutSpec? _fixedCallout(RepairTicketDetail detail) {
    if (repairCompletionStage(detail) == RepairCompletionStage.notCompleted) {
      return null;
    }
    final title = repairStatusSummaryText(detail);
    final resolutionType = detail.resolutionType;
    final result =
        resolutionLabel ??
        (resolutionType == null ? null : repairResolutionText(resolutionType));
    final parts = <String>[
      if (result != null) '$result。',
      if (detail.resolutionNote != null) '說明：${detail.resolutionNote}。',
    ];
    final fallback = detail.state == 'done' ? '等主管驗收後這張單就結案。' : '這張單已結案。';
    return TmsStatusCallout(
      kind: TmsCalloutKind.ticketFixed,
      tone: TmsCalloutTone.success,
      title: title,
      body: parts.isEmpty ? fallback : parts.join(''),
      wrap: (node) => _summaryWrap(
        TmsSemantics(
          id: 'ticket.resolution-label',
          label: parts.join(''),
          child: node,
        ),
        title,
      ),
    );
  }

  TmsCalloutSpec? _reworkingCallout(RepairTicketDetail detail) {
    if (!repairIsReworking(detail)) {
      return null;
    }
    final title = repairStatusSummaryText(detail);
    final assignee = detail.assigneeName;
    return TmsStatusCallout(
      kind: TmsCalloutKind.ticketReworking,
      tone: TmsCalloutTone.warn,
      title: title,
      body: assignee == null ? '等工務重新接單。' : '工務 $assignee 重修中。',
      wrap: (node) => _summaryWrap(node, title),
    );
  }

  Widget _summaryWrap(Widget node, String title) {
    return TmsSemantics(
      id: 'ticket.status-summary-card',
      label: title,
      child: node,
    );
  }

  Widget? _photoSection(RepairTicketDetail detail) {
    final count = detail.photoCount;
    if (count == 0) {
      return null;
    }
    return _sectionWithHeader(
      '報修照片（$count 張，含浮水印）',
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
            for (var i = 0; i < count; i++)
              TmsSemantics(
                id: 'ticket.photo.${i + 1}',
                child: TmsPhotoCell(
                  edge: null,
                  semanticsLabel: '放大報修照片 ${i + 1}',
                  onTap: () => onOpenPhoto(i),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget? _completionPhotoSection(RepairTicketDetail detail) {
    final photos = detail.completionPhotos;
    if (repairCompletionStage(detail) == RepairCompletionStage.notCompleted ||
        photos.isEmpty) {
      return null;
    }
    return _sectionWithHeader(
      '完修照片（${photos.length} 張）',
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
                id: 'ticket.completion-photo.${i + 1}',
                child: TmsPhotoCell(
                  edge: null,
                  semanticsLabel: '完修照片 ${i + 1}',
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _infoCard(RepairTicketDetail detail) {
    final rows = <({String key, String value})>[
      (key: '地點', value: detail.whereText),
      (key: '類別', value: repairCategoryText(detail.category)),
      (key: '報修人', value: detail.reporterNameText),
      (key: '負責人', value: detail.assigneeName ?? '等工務認領'),
    ];
    return ClipRRect(
      borderRadius: BorderRadius.circular(TmsRadius.card),
      child: ColoredBox(
        color: TmsColors.cardBackground,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < rows.length; i++)
              _TicketInfoRow(
                label: rows[i].key,
                value: rows[i].value,
                showDivider: i < rows.length - 1,
              ),
          ],
        ),
      ),
    );
  }

  Widget? _buildActionBar(RepairTicketDetail detail) {
    final override = bottomBar;
    if (override != null) {
      return override;
    }
    if (!showWorkerActions) {
      return null;
    }
    final secondary = _secondaryAction(detail);
    final primary = _primaryAction(detail);
    if (secondary == null && primary == null) {
      return null;
    }
    return TmsActionBar(secondary: secondary, primary: primary);
  }

  Widget? _secondaryAction(RepairTicketDetail detail) {
    if (detail.isPublicArea &&
        (detail.state == 'accepted' || detail.state == 'doing')) {
      return TmsSemantics(
        id: 'ticket.release-to-pool-button',
        child: TmsPillButton(
          label: '放回待認領',
          style: TmsPillButtonStyle.outlined,
          onPressed: onReleaseToPool,
        ),
      );
    }
    return null;
  }

  Widget? _primaryAction(RepairTicketDetail detail) {
    switch (detail.state) {
      case 'pool':
        return TmsSemantics(
          id: 'ticket.claim-button',
          child: TmsPillButton(
            label: '認領這單',
            glow: TmsPillGlow.alt,
            onPressed: onClaim,
          ),
        );
      case 'accepted':
        return TmsSemantics(
          id: 'ticket.start-button',
          child: TmsPillButton(
            label: '開始維修',
            glow: TmsPillGlow.alt,
            onPressed: onStart,
          ),
        );
      case 'doing':
        return TmsSemantics(
          id: 'ticket.complete-button',
          child: TmsPillButton(
            label: '完修回報',
            glow: TmsPillGlow.alt,
            onPressed: onComplete,
          ),
        );
      default:
        return null;
    }
  }
}

class _TicketInfoRow extends StatelessWidget {
  const _TicketInfoRow({
    required this.label,
    required this.value,
    required this.showDivider,
  });

  final String label;
  final String value;
  final bool showDivider;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
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
        children: [
          Text(label, style: TmsText.body),
          const SizedBox(width: TmsSpacing.md),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: TmsText.subheadline.copyWith(
                color: TmsColors.secondaryLabel,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
