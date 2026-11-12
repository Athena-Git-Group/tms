import 'package:flutter/widgets.dart';

import '../theme.dart';

class TmsSegment<T> {
  const TmsSegment({
    required this.value,
    required this.label,
    this.id,
    this.wrap,
  });

  final T value;

  final String label;

  final String? id;

  final Widget Function(Widget node)? wrap;
}

class TmsSegmentedControl<T> extends StatelessWidget {
  const TmsSegmentedControl({
    super.key,
    required this.segments,
    required this.value,
    required this.onChanged,
  });

  final List<TmsSegment<T>> segments;

  final T value;

  final ValueChanged<T> onChanged;

  static const double trackPadding = 2;

  static const double trackRadius = 9;

  static const double segmentHeight = 32;

  static const double segmentRadius = 7;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(trackPadding),
      decoration: BoxDecoration(
        color: TmsColors.segmentedTrack,
        borderRadius: BorderRadius.circular(trackRadius),
      ),
      child: Row(
        children: [
          for (final segment in segments)
            Expanded(child: _segment(segment, segment.value == value)),
        ],
      ),
    );
  }

  Widget _segment(TmsSegment<T> segment, bool selected) {
    final Widget node = GestureDetector(
      onTap: selected ? null : () => onChanged(segment.value),
      behavior: HitTestBehavior.opaque,
      child: Container(
        constraints: const BoxConstraints(minHeight: segmentHeight),
        alignment: Alignment.center,
        decoration: selected
            ? BoxDecoration(
                color: TmsColors.cardBackground,
                borderRadius: BorderRadius.circular(segmentRadius),
                boxShadow: const <BoxShadow>[
                  BoxShadow(
                    color: TmsColors.segmentedThumbShadow,
                    offset: Offset(0, 1),
                    blurRadius: 3,
                  ),
                ],
              )
            : null,
        child: Text(
          segment.label,
          style: (selected ? TmsText.footnoteEmphasized : TmsText.footnote)
              .copyWith(color: TmsColors.label),
        ),
      ),
    );
    final wrap = segment.wrap;
    final id = segment.id;
    return Semantics(
      identifier: id,
      container: id != null,
      selected: selected,
      button: true,
      child: wrap == null ? node : wrap(node),
    );
  }
}
