import 'package:flutter/cupertino.dart';

import '../theme.dart';

class TmsStatusAppearance {
  const TmsStatusAppearance({
    required this.text,
    required this.background,
    required this.foreground,
  });

  final String text;
  final Color background;
  final Color foreground;
}

TmsStatusAppearance tmsStatusAppearance({
  required String state,
  String? type,
  String? inspection,
  int redoCount = 0,
}) {
  switch (state) {
    case 'pool':
      return const TmsStatusAppearance(
        text: '待認領',
        background: TmsColors.brandAccentContainer,
        foreground: TmsColors.brandOnAccentContainer,
      );
    case 'accepted':
      if (redoCount > 0) {
        return const TmsStatusAppearance(
          text: '退回重做',
          background: TmsColors.errorContainer,
          foreground: TmsColors.onErrorContainer,
        );
      }
      return const TmsStatusAppearance(
        text: '待開始',
        background: TmsColors.neutralContainer,
        foreground: TmsColors.onNeutralContainer,
      );
    case 'doing':
      return const TmsStatusAppearance(
        text: '進行中',
        background: TmsColors.brandPrimary,
        foreground: TmsColors.brandOnPrimary,
      );
    case 'deferred':
      return const TmsStatusAppearance(
        text: '已延後',
        background: TmsColors.brandAccentContainer,
        foreground: TmsColors.brandOnAccentContainer,
      );
    case 'interrupted':
      return const TmsStatusAppearance(
        text: '已中斷',
        background: TmsColors.otherContainer,
        foreground: TmsColors.onOtherContainer,
      );
    case 'done':
      return TmsStatusAppearance(
        text: type == 'cleaning' ? '待查房' : '已送達',
        background: TmsColors.brandContainer,
        foreground: TmsColors.brandOnContainerStrong,
      );
    case 'inspected':
      return inspection == 'waived'
          ? const TmsStatusAppearance(
              text: '完成 · 免查房',
              background: TmsColors.groupedBackground,
              foreground: TmsColors.onSuccessContainer,
            )
          : const TmsStatusAppearance(
              text: '查房通過',
              background: TmsColors.successContainer,
              foreground: TmsColors.onSuccessContainer,
            );
    case 'cancelled':
      return const TmsStatusAppearance(
        text: '已取消',
        background: TmsColors.neutralContainer,
        foreground: TmsColors.onNeutralContainer,
      );
    default:
      return TmsStatusAppearance(
        text: state,
        background: TmsColors.neutralContainer,
        foreground: TmsColors.onNeutralContainer,
      );
  }
}

class TmsStatusChip extends StatelessWidget {
  const TmsStatusChip.of(this.appearance, {super.key, this.onDark = false});

  factory TmsStatusChip({
    Key? key,
    required String state,
    String? type,
    String? inspection,
    int redoCount = 0,
    bool onDark = false,
  }) {
    return TmsStatusChip.of(
      tmsStatusAppearance(
        state: state,
        type: type,
        inspection: inspection,
        redoCount: redoCount,
      ),
      key: key,
      onDark: onDark,
    );
  }

  final TmsStatusAppearance appearance;

  final bool onDark;

  @override
  Widget build(BuildContext context) {
    final background = onDark
        ? TmsColors.cardBackground
        : appearance.background;
    final foreground = onDark ? TmsColors.brandPrimary : appearance.foreground;
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.sm2,
        vertical: TmsSpacing.xs,
      ),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(TmsRadius.pill),
      ),
      child: Text(
        appearance.text,
        maxLines: 1,
        softWrap: false,
        style: TmsText.caption1.copyWith(color: foreground),
      ),
    );
  }
}
