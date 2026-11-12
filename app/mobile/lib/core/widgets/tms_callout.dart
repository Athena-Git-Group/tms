import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_grouped_list.dart';

enum TmsCalloutTone {
  error(symbol: TmsColors.systemRed, background: TmsColors.errorContainer),

  warn(
    symbol: TmsColors.systemOrange,
    background: TmsColors.brandAccentContainer,
  ),

  caution(
    symbol: TmsColors.systemOrange,
    background: TmsColors.brandAccentContainer,
  ),

  info(symbol: TmsColors.brandPrimary, background: TmsColors.brandContainer),

  success(
    symbol: TmsColors.systemGreen,
    background: TmsColors.successContainer,
  ),

  neutral(
    symbol: TmsColors.quaternaryLabel,
    background: TmsColors.neutralContainer,
  );

  const TmsCalloutTone({required this.symbol, required this.background});

  final Color symbol;

  final Color background;
}

enum TmsCalloutKind {
  taskReturned(TmsCalloutSurface.task),

  taskCancelled(TmsCalloutSurface.task),

  taskHeld(TmsCalloutSurface.task),

  taskTeaching(TmsCalloutSurface.task),

  taskAwaitingInspection(TmsCalloutSurface.task),

  taskFinished(TmsCalloutSurface.task),

  ticketPaused(TmsCalloutSurface.ticket),

  ticketReworking(TmsCalloutSurface.ticket),

  ticketInPool(TmsCalloutSurface.ticket),

  ticketFixed(TmsCalloutSurface.ticket),

  ticketAwaitingReview(TmsCalloutSurface.ticket),

  ticketRoomNotEnterable(TmsCalloutSurface.ticket),

  ticketUrgent(TmsCalloutSurface.ticket);

  const TmsCalloutKind(this.surface);

  final TmsCalloutSurface surface;
}

enum TmsCalloutSurface {
  task,

  ticket,
}

class TmsCalloutAction {
  const TmsCalloutAction({
    required this.label,
    required this.onPressed,
    this.showsChevron = false,
    this.wrap,
  });

  final String label;
  final VoidCallback onPressed;

  final bool showsChevron;

  final Widget Function(Widget node)? wrap;
}

sealed class TmsCalloutSpec {
  const TmsCalloutSpec({required this.kind, this.wrap});

  final TmsCalloutKind kind;

  final Widget Function(Widget node)? wrap;
}

class TmsStatusCallout extends TmsCalloutSpec {
  const TmsStatusCallout({
    required super.kind,
    required this.tone,
    required this.title,
    required this.body,
    this.reason,
    this.actions = const <TmsCalloutAction>[],
    this.idPrefix,
    super.wrap,
  });

  final TmsCalloutTone tone;

  final String? idPrefix;

  final String title;

  final String body;

  final String? reason;

  final List<TmsCalloutAction> actions;
}

class TmsTeachingCallout extends TmsCalloutSpec {
  const TmsTeachingCallout({
    required this.title,
    required this.body,
    required this.onDismiss,
    super.wrap,
  }) : super(kind: TmsCalloutKind.taskTeaching);

  final String title;
  final String body;

  final VoidCallback onDismiss;
}

class TmsCalloutSlot extends StatelessWidget {
  const TmsCalloutSlot({super.key, required this.candidates});

  final List<TmsCalloutSpec?> candidates;

  static const double _iconGap = TmsSpacing.md;

  static const double _textTopInset = TmsSpacing.xxs;

  static TmsCalloutSpec? highestPriority(List<TmsCalloutSpec?> candidates) {
    TmsCalloutSpec? best;
    for (final candidate in candidates) {
      if (candidate == null) {
        continue;
      }
      assert(
        best == null || best.kind.surface == candidate.kind.surface,
        '同一個位置只能收同一個畫面的候選，兩個畫面的優先序各自獨立。',
      );
      if (best == null || candidate.kind.index < best.kind.index) {
        best = candidate;
      }
    }
    return best;
  }

  @override
  Widget build(BuildContext context) {
    final spec = highestPriority(candidates);
    if (spec == null) {
      return const SizedBox.shrink();
    }
    final Widget node = switch (spec) {
      TmsStatusCallout() => _buildStatus(spec),
      TmsTeachingCallout() => _buildTeaching(spec),
    };
    final wrap = spec.wrap;
    return wrap == null ? node : wrap(node);
  }

  Widget _buildStatus(TmsStatusCallout spec) {
    final reason = spec.reason;
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
      padding: const EdgeInsets.symmetric(
        vertical: TmsSpacing.md,
        horizontal: TmsSpacing.lg,
      ),
      decoration: BoxDecoration(
        color: spec.tone.background,
        borderRadius: BorderRadius.circular(TmsRadius.card),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _ToneIcon(tone: spec.tone),
          const SizedBox(width: _iconGap),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: _textTopInset),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _segment(
                    spec.idPrefix,
                    'title',
                    spec.title,
                    Text(
                      spec.title,
                      style: TmsText.headline,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (reason != null) ...[
                    const SizedBox(height: TmsSpacing.xxs),
                    _segment(
                      spec.idPrefix,
                      'reason',
                      reason,
                      Text(
                        reason,
                        style: _bodyStyle.copyWith(color: TmsColors.label),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                  const SizedBox(height: TmsSpacing.xxs),
                  _segment(
                    spec.idPrefix,
                    'body',
                    spec.body,
                    Text(spec.body, style: _bodyStyle),
                  ),
                  if (spec.actions.isNotEmpty) ...[
                    const SizedBox(height: TmsSpacing.xs2),
                    Wrap(
                      spacing: TmsSpacing.lg,
                      children: [
                        for (final action in spec.actions)
                          _ActionButton(action: action),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTeaching(TmsTeachingCallout spec) {
    return Stack(
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.only(
            left: TmsSpacing.lg,
            top: TmsSpacing.md2,
            bottom: TmsSpacing.md2,
            right: TmsSize.minTouchTarget,
          ),
          decoration: BoxDecoration(
            color: TmsColors.brandContainer,
            borderRadius: BorderRadius.circular(TmsRadius.card),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(
                CupertinoIcons.lightbulb,
                size: TmsSize.calloutTeachingIcon,
                color: TmsColors.brandPrimary,
              ),
              const SizedBox(width: _iconGap),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.only(top: _textTopInset),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(spec.title, style: TmsText.headline),
                      const SizedBox(height: TmsSpacing.xxs),
                      Text(spec.body, style: _bodyStyle),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        Positioned(
          top: 0,
          right: 0,
          child: CupertinoButton(
            padding: EdgeInsets.zero,
            minimumSize: const Size(
              TmsSize.minTouchTarget,
              TmsSize.minTouchTarget,
            ),
            onPressed: spec.onDismiss,
            child: const Icon(
              CupertinoIcons.xmark,
              size: TmsSize.calloutDismissGlyph,
              color: TmsColors.quaternaryLabel,
            ),
          ),
        ),
      ],
    );
  }
}

Widget _segment(String? idPrefix, String segment, String text, Widget node) {
  if (idPrefix == null) {
    return node;
  }
  return TmsSemantics(
    id: '$idPrefix.$segment',
    label: text,
    child: ExcludeSemantics(child: node),
  );
}

const TextStyle _bodyStyle = TextStyle(
  fontFamilyFallback: TmsText.fontFamilyStack,
  fontSize: 15,
  height: 1.4,
  fontWeight: FontWeight.w400,
  color: TmsColors.secondaryLabel,
);

class _ActionButton extends StatelessWidget {
  const _ActionButton({required this.action});

  final TmsCalloutAction action;

  @override
  Widget build(BuildContext context) {
    final style = TmsText.subheadlineEmphasized.copyWith(
      color: TmsColors.brandPrimary,
    );
    final Widget button = CupertinoButton(
      padding: EdgeInsets.zero,
      minimumSize: const Size(0, TmsSize.minTouchTarget),
      onPressed: action.onPressed,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(action.label, style: style),
          if (action.showsChevron) ...[
            const SizedBox(width: TmsSpacing.xs),
            const TmsChevron(color: TmsColors.brandPrimary),
          ],
        ],
      ),
    );
    final wrap = action.wrap;
    return wrap == null ? button : wrap(button);
  }
}

class _ToneIcon extends StatelessWidget {
  const _ToneIcon({required this.tone});

  final TmsCalloutTone tone;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      size: const Size(TmsSize.calloutIcon, TmsSize.calloutIcon),
      painter: _ToneIconPainter(tone: tone),
    );
  }
}

class _ToneIconPainter extends CustomPainter {
  const _ToneIconPainter({required this.tone});

  final TmsCalloutTone tone;

  static const double _viewBox = 24;
  static const double _strokeWidth = 2.4;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width / _viewBox);
    canvas.drawCircle(const Offset(12, 12), 11, Paint()..color = tone.symbol);
    final stroke = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = _strokeWidth
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..color = TmsColors.brandOnPrimary;
    switch (tone) {
      case TmsCalloutTone.error || TmsCalloutTone.caution:
        canvas.drawLine(const Offset(12, 6.8), const Offset(12, 13.2), stroke);
        _drawDot(canvas, const Offset(12, 17.2));
      case TmsCalloutTone.warn:
        canvas.drawLine(
          const Offset(9.8, 8.3),
          const Offset(9.8, 15.7),
          stroke,
        );
        canvas.drawLine(
          const Offset(14.2, 8.3),
          const Offset(14.2, 15.7),
          stroke,
        );
      case TmsCalloutTone.info:
        canvas.drawLine(const Offset(12, 11), const Offset(12, 16.6), stroke);
        _drawDot(canvas, const Offset(12, 7.6));
      case TmsCalloutTone.success:
        canvas.drawPath(
          Path()
            ..moveTo(7.4, 12.4)
            ..lineTo(10.5, 15.5)
            ..lineTo(16.6, 8.9),
          stroke,
        );
      case TmsCalloutTone.neutral:
        canvas.drawLine(
          const Offset(8.6, 8.6),
          const Offset(15.4, 15.4),
          stroke,
        );
        canvas.drawLine(
          const Offset(15.4, 8.6),
          const Offset(8.6, 15.4),
          stroke,
        );
    }
    canvas.restore();
  }

  void _drawDot(Canvas canvas, Offset at) {
    canvas.drawCircle(
      at,
      _strokeWidth / 2,
      Paint()..color = TmsColors.brandOnPrimary,
    );
  }

  @override
  bool shouldRepaint(_ToneIconPainter oldDelegate) => oldDelegate.tone != tone;
}

class TmsQuoteCallout extends StatelessWidget {
  const TmsQuoteCallout({
    super.key,
    required this.title,
    required this.entries,
    this.onAcknowledge,
    this.acknowledgeLabel = '知道了',
  });

  final String title;

  final List<TmsQuoteEntry> entries;

  final VoidCallback? onAcknowledge;

  final String acknowledgeLabel;

  static const double _barInset = TmsSpacing.sm2;

  @override
  Widget build(BuildContext context) {
    final acknowledge = onAcknowledge;
    return Stack(
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.lg2,
            TmsSpacing.md,
            TmsSpacing.md2,
            TmsSpacing.md,
          ),
          decoration: BoxDecoration(
            color: TmsColors.brandOnPrimary,
            borderRadius: BorderRadius.circular(TmsRadius.md),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(
                    CupertinoIcons.bubble_left,
                    size: TmsSize.quoteIcon,
                    color: TmsColors.brandPrimary,
                  ),
                  const SizedBox(width: TmsSpacing.xs2),
                  Expanded(
                    child: Text(
                      title,
                      style: TmsText.subheadlineEmphasized.copyWith(
                        fontWeight: FontWeight.w700,
                        color: TmsColors.brandPrimary,
                      ),
                    ),
                  ),
                  if (acknowledge != null)
                    CupertinoButton(
                      padding: EdgeInsets.zero,
                      minimumSize: const Size(0, TmsSize.minTouchTarget),
                      onPressed: acknowledge,
                      child: Text(
                        acknowledgeLabel,
                        style: TmsText.subheadlineEmphasized.copyWith(
                          color: TmsColors.brandPrimary,
                        ),
                      ),
                    ),
                ],
              ),
              for (final entry in entries) _buildEntry(entry),
            ],
          ),
        ),
        Positioned(
          left: 0,
          top: _barInset,
          bottom: _barInset,
          child: Container(
            width: TmsSize.quoteBar,
            decoration: const BoxDecoration(
              color: TmsColors.brandPrimary,
              borderRadius: BorderRadius.horizontal(
                right: Radius.circular(TmsSize.quoteBar),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildEntry(TmsQuoteEntry entry) {
    final node = Padding(
      padding: const EdgeInsets.only(top: TmsSpacing.sm2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _segment(
            entry.idPrefix,
            'content',
            entry.text,
            Text(entry.text, style: TmsText.body.copyWith(height: 1.4)),
          ),
          const SizedBox(height: TmsSpacing.xxs),
          _segment(
            entry.idPrefix,
            'meta',
            entry.meta,
            Text(entry.meta, style: TmsText.footnote),
          ),
        ],
      ),
    );
    final wrap = entry.wrap;
    return wrap == null ? node : wrap(node);
  }
}

class TmsQuoteEntry {
  const TmsQuoteEntry({
    required this.text,
    required this.meta,
    this.idPrefix,
    this.wrap,
  });

  final String? idPrefix;

  final String text;

  final String meta;

  final Widget Function(Widget node)? wrap;
}

enum TmsStatusSummaryGlyph {
  clock(CupertinoIcons.clock),

  checkmark(CupertinoIcons.checkmark),

  exclamation(CupertinoIcons.exclamationmark),

  cross(CupertinoIcons.xmark),

  pause(CupertinoIcons.pause_solid),

  wrench(CupertinoIcons.wrench),

  offline(CupertinoIcons.wifi_slash);

  const TmsStatusSummaryGlyph(this.icon);

  final IconData icon;
}

class TmsStatusSummaryCard extends StatelessWidget {
  const TmsStatusSummaryCard({
    super.key,
    required this.glyph,
    required this.glyphBackground,
    required this.title,
    required this.detail,
    this.detailColor = TmsColors.secondaryLabel,
  });

  final TmsStatusSummaryGlyph glyph;

  final Color glyphBackground;

  final String title;

  final String detail;

  final Color detailColor;

  static const double _glyphSize = 20;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        vertical: TmsSpacing.md2,
        horizontal: TmsSpacing.lg,
      ),
      decoration: BoxDecoration(
        color: TmsColors.cardBackground,
        borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
      ),
      child: Row(
        children: [
          Container(
            width: TmsSize.statusSummaryIcon,
            height: TmsSize.statusSummaryIcon,
            decoration: BoxDecoration(
              color: glyphBackground,
              shape: BoxShape.circle,
            ),
            alignment: Alignment.center,
            child: Icon(
              glyph.icon,
              size: _glyphSize,
              color: TmsColors.brandOnPrimary,
            ),
          ),
          const SizedBox(width: TmsSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: TmsText.headline),
                const SizedBox(height: TmsSpacing.xxs),
                Text(detail, style: _bodyStyle.copyWith(color: detailColor)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
