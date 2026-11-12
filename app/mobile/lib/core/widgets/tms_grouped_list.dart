import 'package:flutter/widgets.dart';

import '../theme.dart';
import 'tms_card.dart';
import 'tms_toggle.dart';

class TmsGroupedSection extends StatelessWidget {
  const TmsGroupedSection({
    super.key,
    this.header,
    this.style = TmsCardStyle.grouped,
    this.trailingDivider = false,
    required this.children,
  });

  final String? header;

  final TmsCardStyle style;

  final bool trailingDivider;

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    const divider = SizedBox(
      height: TmsSize.hairline,
      child: ColoredBox(color: TmsColors.separator),
    );
    final rows = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i > 0) {
        rows.add(divider);
      }
      rows.add(children[i]);
    }
    if (trailingDivider && children.isNotEmpty) {
      rows.add(divider);
    }
    final radius = style == TmsCardStyle.inSheet
        ? TmsRadius.cardInSheet
        : TmsRadius.card;
    final group = TmsCard(
      style: style,
      padding: EdgeInsets.zero,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: Column(mainAxisSize: MainAxisSize.min, children: rows),
      ),
    );
    if (header == null) {
      return group;
    }
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
          child: Text(header!, style: TmsText.footnote),
        ),
        group,
      ],
    );
  }
}

class TmsGroupedRow extends StatelessWidget {
  const TmsGroupedRow({
    super.key,
    required this.label,
    this.value,
    this.showChevron = true,
    this.onTap,
  });

  final String label;

  final String? value;

  final bool showChevron;

  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final row = Container(
      constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.screenEdge,
        vertical: TmsSpacing.groupedRowVertical,
      ),
      color: TmsColors.cardBackground,
      child: Row(
        children: [
          Text(label, style: TmsText.body),
          const SizedBox(width: TmsSpacing.sm),
          Expanded(
            child: Text(
              value ?? '',
              textAlign: TextAlign.right,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TmsText.body.copyWith(color: TmsColors.quaternaryLabel),
            ),
          ),
          if (showChevron) ...[
            const SizedBox(width: TmsSpacing.sm),
            const TmsChevron(),
          ],
        ],
      ),
    );
    if (onTap == null) {
      return row;
    }
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: row,
    );
  }
}

class TmsChevron extends StatelessWidget {
  const TmsChevron({super.key, this.color = TmsColors.chevron});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      size: const Size(TmsSize.chevronWidth, TmsSize.chevronHeight),
      painter: _ChevronPainter(color: color),
    );
  }
}

class _ChevronPainter extends CustomPainter {
  const _ChevronPainter({required this.color});

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final path = Path()
      ..moveTo(1.5, 1.5)
      ..lineTo(6.5, 6.5)
      ..lineTo(1.5, 11.5);
    canvas.drawPath(
      path,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = TmsSize.chevronStroke
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..color = color,
    );
  }

  @override
  bool shouldRepaint(_ChevronPainter oldDelegate) => oldDelegate.color != color;
}

class TmsGroupedToggleRow extends StatelessWidget {
  const TmsGroupedToggleRow({
    super.key,
    required this.label,
    required this.value,
    required this.onChanged,
    this.toggleId,
  });

  final String label;

  final bool value;

  final ValueChanged<bool> onChanged;

  final String? toggleId;

  static const double _verticalPadding = 6;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.screenEdge,
        vertical: _verticalPadding,
      ),
      color: TmsColors.cardBackground,
      child: Row(
        children: [
          Expanded(child: Text(label, style: TmsText.body)),
          const SizedBox(width: TmsSpacing.sm),
          TmsToggle(
            value: value,
            onChanged: onChanged,
            label: label,
            id: toggleId,
          ),
        ],
      ),
    );
  }
}
