import 'package:flutter/widgets.dart';

import '../theme.dart';

/// 任務類別在畫面上的顏色與短名。
class TmsCategoryAppearance {
  const TmsCategoryAppearance({
    required this.label,
    required this.background,
    required this.foreground,
  });

  final String label;
  final Color background;
  final Color foreground;
}

/// 把契約的任務類型換算成類別方塊的短名與配色。
///
/// 房務與工務兩側的任務列都用這一份，同一種任務在哪個畫面上都是同一個顏色，
/// 員工掃一眼就能認出「這是打掃、那是送備品」，不必先讀標題。
///
/// [type] 收契約的任務類型值（`cleaning`、`amenity`、`repair`、`other`）。
/// 維修與其他兩種共用同一組顏色，因為現場把它們當同一類處理；認不得的值一併
/// 歸到這一組，而不是讓方塊空著。
TmsCategoryAppearance tmsCategoryAppearance(String type) {
  switch (type) {
    case 'cleaning':
      return const TmsCategoryAppearance(
        label: '打掃',
        background: TmsColors.brandContainer,
        foreground: TmsColors.brandPrimary,
      );
    case 'amenity':
      return const TmsCategoryAppearance(
        label: '備品',
        background: TmsColors.brandAccentContainer,
        foreground: TmsColors.brandOnAccentContainer,
      );
    default:
      return const TmsCategoryAppearance(
        label: '其他',
        background: TmsColors.otherContainer,
        foreground: TmsColors.onOtherContainer,
      );
  }
}

/// 任務列最左邊那個帶顏色的方塊，裡面是類別的兩個字。
///
/// 預設邊長 48，是首頁任務列的尺寸；待認領單卡的方塊較大，由呼叫端傳
/// [size] 指定。
class TmsCategorySquare extends StatelessWidget {
  const TmsCategorySquare({super.key, required this.type, this.size = 48});

  final String type;
  final double size;

  @override
  Widget build(BuildContext context) {
    final appearance = tmsCategoryAppearance(type);
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: appearance.background,
        borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
      ),
      child: Text(
        appearance.label,
        style: TmsText.callout.copyWith(
          fontWeight: FontWeight.w700,
          color: appearance.foreground,
        ),
      ),
    );
  }
}
