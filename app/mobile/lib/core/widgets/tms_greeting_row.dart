import 'package:flutter/cupertino.dart';

import '../theme.dart';

class TmsGreetingRow extends StatelessWidget {
  const TmsGreetingRow({
    super.key,
    required this.greeting,
    this.name,
    this.shift,
    this.trailing,
    this.date,
  });

  final String greeting;

  final String? name;

  final String? shift;

  final String? trailing;

  final DateTime? date;

  static const TextStyle _shiftStyle = TextStyle(
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  static String dateText(DateTime now) {
    const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
    return '週${weekdays[now.weekday - 1]} ${now.month}/${now.day}';
  }

  static String shiftText(String shift) {
    switch (shift) {
      case 'morning':
        return '早班';
      case 'afternoon':
        return '晚班';
      default:
        return shift;
    }
  }

  @override
  Widget build(BuildContext context) {
    final name = this.name;
    final shift = this.shift;
    final trailing = this.trailing;
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text.rich(
                TextSpan(
                  text: dateText(date ?? DateTime.now()),
                  children: [
                    if (shift != null && shift.isNotEmpty) ...[
                      const TextSpan(text: ' · '),
                      TextSpan(text: shiftText(shift), style: _shiftStyle),
                    ],
                    if (trailing != null && trailing.isNotEmpty)
                      TextSpan(text: ' · $trailing'),
                  ],
                ),
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
              const SizedBox(height: TmsSpacing.xxs),
              Text(
                name == null || name.isEmpty ? greeting : '$greeting，$name',
                style: TmsText.title1,
              ),
            ],
          ),
        ),
        const SizedBox(width: TmsSpacing.md),
        Container(
          width: TmsSize.minTouchTarget,
          height: TmsSize.minTouchTarget,
          alignment: Alignment.center,
          decoration: const BoxDecoration(
            color: TmsColors.brandAccentContainer,
            shape: BoxShape.circle,
          ),
          child: name == null || name.isEmpty
              ? null
              : Text(
                  name.substring(name.length - 1),
                  style: TmsText.headline.copyWith(
                    fontWeight: FontWeight.w800,
                    color: TmsColors.brandOnAccentContainer,
                  ),
                ),
        ),
      ],
    );
  }
}
