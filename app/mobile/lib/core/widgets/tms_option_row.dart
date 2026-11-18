import 'package:flutter/widgets.dart';

import '../theme.dart';

class TmsOptionRow extends StatelessWidget {
  const TmsOptionRow.reason({
    super.key,
    required this.title,
    required this.onTap,
  }) : subtitle = null,
       selected = false,
       _minHeight = 52,
       _verticalPadding = 0,
       _subtitleStyle = null,
       _subtitleGap = 0,
       _checkGap = TmsSpacing.sm;

  const TmsOptionRow.category({
    super.key,
    required this.title,
    required this.selected,
    required this.onTap,
  }) : subtitle = null,
       _minHeight = 44,
       _verticalPadding = 0,
       _subtitleStyle = null,
       _subtitleGap = 0,
       _checkGap = TmsSpacing.sm;

  const TmsOptionRow.suggestion({
    super.key,
    required this.title,
    required Widget scope,
    required this.onTap,
  }) : subtitle = scope,
       selected = false,
       _minHeight = 56,
       _verticalPadding = TmsSpacing.sm2,
       _subtitleStyle = TmsText.caption1Regular,
       _subtitleGap = TmsSpacing.xxs,
       _checkGap = 0;

  const TmsOptionRow.result({
    super.key,
    required this.title,
    required Widget detail,
    required this.selected,
    required this.onTap,
  }) : subtitle = detail,
       _minHeight = 52,
       _verticalPadding = TmsSpacing.sm,
       _subtitleStyle = TmsText.footnote,
       _subtitleGap = 1,
       _checkGap = TmsSpacing.sm2;

  final Widget title;

  final Widget? subtitle;

  final bool selected;

  final VoidCallback? onTap;

  final double _minHeight;
  final double _verticalPadding;
  final TextStyle? _subtitleStyle;
  final double _subtitleGap;
  final double _checkGap;

  @override
  Widget build(BuildContext context) {
    final sub = subtitle;
    final subStyle = _subtitleStyle;
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        constraints: BoxConstraints(minHeight: _minHeight),
        padding: EdgeInsets.symmetric(
          horizontal: TmsSpacing.screenEdge,
          vertical: _verticalPadding,
        ),
        color: TmsColors.cardBackground,
        child: Row(
          children: [
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DefaultTextStyle(style: TmsText.body, child: title),
                  if (sub != null && subStyle != null) ...[
                    SizedBox(height: _subtitleGap),
                    DefaultTextStyle(style: subStyle, child: sub),
                  ],
                ],
              ),
            ),
            if (selected) ...[
              SizedBox(width: _checkGap),
              const TmsOptionCheck(),
            ],
          ],
        ),
      ),
    );
  }
}

class TmsOptionCheck extends StatelessWidget {
  const TmsOptionCheck({super.key});

  @override
  Widget build(BuildContext context) {
    return const CustomPaint(
      size: Size(TmsSize.optionCheck, TmsSize.optionCheck),
      painter: _CheckPainter(),
    );
  }
}

class _CheckPainter extends CustomPainter {
  const _CheckPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / 24;
    final path = Path()
      ..moveTo(5 * scale, 12.5 * scale)
      ..lineTo(9.5 * scale, 17 * scale)
      ..lineTo(19 * scale, 7.5 * scale);
    canvas.drawPath(
      path,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = TmsSize.optionCheckStroke
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..color = TmsColors.brandPrimary,
    );
  }

  @override
  bool shouldRepaint(_CheckPainter oldDelegate) => false;
}
