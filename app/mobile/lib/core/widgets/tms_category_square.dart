import 'package:flutter/widgets.dart';

import '../theme.dart';

/// 方塊的底色與字色。
///
/// 兩個顏色一定成對換，拆成兩個參數傳就有機會配錯，例如淺藍底配深紅字。
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

/// 把契約的任務類型換算成房務任務方塊的短名與配色。
///
/// 同一種任務在哪個畫面上都是同一個顏色，員工掃一眼就能認出「這是打掃、那是
/// 送備品」，不必先讀字。
///
/// [type] 收契約的任務類型值（`cleaning`、`amenity`、`repair`、`other`）。
/// 維修與其他兩種共用同一組顏色，因為現場把它們當同一類處理；認不得的值一併
/// 歸到這一組，而不是讓方塊空著。
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

/// 工務單方塊的配色（`Main.dc.html:3823`）。
///
/// 工務側的顏色回答的不是「這是哪一類」，而是「這張單急不急、修好了沒」：
/// 急件紅、已完修灰、其餘品牌色。工務一天之內要先挑出急件處理，拿類別上色的
/// 話整個清單會是同一片顏色，急件在列表上完全看不出來。類別改由方塊第二行的
/// 短名交代。
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

/// 任務列最左邊那個 48pt 的方塊，裡面是地點。
///
/// 方塊上的字回答「在哪裡」，顏色回答「這是哪一類」或「急不急」——兩件事疊在
/// 同一個方塊上，列表才能在一眼之內同時讀到位置與分類。
///
/// [where] 是房號或區域名。房號兩三碼用預設的 16，區域名長，呼叫端傳較小的
/// [fontSize]（`Main.dc.html:3835` 的 `listSize`）。
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

/// 待認領單卡最左邊那個 56pt 的方塊：上面是房號或區域，下面是類別短名。
///
/// 這是單卡上唯一的彩色區塊，員工在列表上是先看顏色、再看房號找位置，所以兩
/// 件事疊在同一個方塊上：顏色回答「這一類還是急件」，數字回答「在哪裡」。
///
/// [where] 是房號或區域名。字級跟著長度縮：房號通常兩三碼，用 18；「公共區
/// 域」這種四個字的區域名用 18 會爆出方塊，所以往下降一級。
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
