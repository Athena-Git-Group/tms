import 'package:flutter/widgets.dart';

import '../theme.dart';

class TmsSquarePalette {
  const TmsSquarePalette({required this.background, required this.foreground});

  final Color background;
  final Color foreground;
}

class TmsCategoryAppearance {
  const TmsCategoryAppearance({required this.label, required this.palette});

  final String label;
  final TmsSquarePalette palette;
}

TmsCategoryAppearance tmsCategoryAppearance(String type) {
  switch (type) {
    case 'cleaning':
      return const TmsCategoryAppearance(
        label: '打掃',
        palette: TmsSquarePalette(
          background: TmsColors.brandContainer,
          foreground: TmsColors.brandPrimary,
        ),
      );
    case 'amenity':
      return const TmsCategoryAppearance(
        label: '備品',
        palette: TmsSquarePalette(
          background: TmsColors.brandAccentContainer,
          foreground: TmsColors.brandOnAccentContainer,
        ),
      );
    default:
      return const TmsCategoryAppearance(
        label: '其他',
        palette: TmsSquarePalette(
          background: TmsColors.otherContainer,
          foreground: TmsColors.onOtherContainer,
        ),
      );
  }
}

TmsSquarePalette tmsRepairSquarePalette({
  required bool isUrgent,
  required bool isDone,
}) {
  if (isDone) {
    return const TmsSquarePalette(
      background: TmsColors.groupedBackground,
      foreground: TmsColors.secondaryLabel,
    );
  }
  if (isUrgent) {
    return const TmsSquarePalette(
      background: TmsColors.errorContainer,
      foreground: TmsColors.onErrorContainer,
    );
  }
  return const TmsSquarePalette(
    background: TmsColors.brandContainer,
    foreground: TmsColors.brandPrimary,
  );
}

class TmsCategorySquare extends StatelessWidget {
  const TmsCategorySquare({
    super.key,
    required this.where,
    required this.palette,
    this.fontSize = 16,
  });

  final String where;
  final TmsSquarePalette palette;
  final double fontSize;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 48,
      height: 48,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: palette.background,
        borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
      ),
      child: Text(
        where,
        maxLines: 1,
        softWrap: false,
        style: TextStyle(
          fontSize: fontSize,
          fontWeight: FontWeight.w700,
          fontFeatures: TmsText.tabularNumbers,
          color: palette.foreground,
        ),
      ),
    );
  }
}

class TmsRoomSquare extends StatelessWidget {
  const TmsRoomSquare({
    super.key,
    required this.where,
    required this.label,
    required this.palette,
  });

  final String where;

  final String label;

  final TmsSquarePalette palette;

  double get _whereFontSize {
    if (where.length <= 3) {
      return 18;
    }
    return where.length <= 4 ? 13 : 11;
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 56,
      height: 56,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: palette.background,
        borderRadius: BorderRadius.circular(TmsRadius.card),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            where,
            maxLines: 1,
            softWrap: false,
            style: TextStyle(
              fontSize: _whereFontSize,
              height: 1.1,
              fontWeight: FontWeight.w800,
              fontFeatures: TmsText.tabularNumbers,
              color: palette.foreground,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            label,
            maxLines: 1,
            softWrap: false,
            style: TmsText.caption2.copyWith(color: palette.foreground),
          ),
        ],
      ),
    );
  }
}
