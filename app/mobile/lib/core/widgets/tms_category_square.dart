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

/// 待認領單卡最左邊那個 56pt 的方塊：上面是房號或區域、下面是類別兩個字。
///
/// 這是單卡上唯一的彩色區塊，員工在列表上是先看顏色分類、再看房號找位置，所
/// 以兩件事疊在同一個方塊上：顏色回答「這是哪一類」，數字回答「在哪裡」。
///
/// [where] 是房號或區域名。字級跟著長度縮：房號通常兩三碼，用 18；「公共區
/// 域」這種四個字的區域名用 18 會爆出方塊，所以往下降一級。
class TmsRoomSquare extends StatelessWidget {
  const TmsRoomSquare({
    super.key,
    required this.type,
    required this.where,
    this.size = 56,
  });

  final String type;
  final String where;
  final double size;

  double get _whereFontSize {
    if (where.length <= 3) {
      return 18;
    }
    return where.length <= 4 ? 13 : 11;
  }

  @override
  Widget build(BuildContext context) {
    final appearance = tmsCategoryAppearance(type);
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: appearance.background,
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
              color: appearance.foreground,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            appearance.label,
            maxLines: 1,
            softWrap: false,
            style: TmsText.caption2.copyWith(color: appearance.foreground),
          ),
        ],
      ),
    );
  }
}
