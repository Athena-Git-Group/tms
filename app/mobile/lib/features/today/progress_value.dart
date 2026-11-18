import 'package:flutter/widgets.dart';

import '../../core/tms_semantics.dart';

class ProgressValue extends StatelessWidget {
  const ProgressValue({
    super.key,
    required this.id,
    required this.value,
    this.style,
    this.unit,
    this.unitStyle,
  });

  final String id;

  final String value;

  final TextStyle? style;

  final String? unit;

  final TextStyle? unitStyle;

  @override
  Widget build(BuildContext context) {
    final unit = this.unit;
    return TmsSemantics(
      id: id,
      label: unit == null ? value : '$value $unit',
      child: ExcludeSemantics(
        child: unit == null
            ? Text(value, style: style)
            : Text.rich(
                TextSpan(
                  text: '$value ',
                  style: style,
                  children: [TextSpan(text: unit, style: unitStyle)],
                ),
              ),
      ),
    );
  }
}
