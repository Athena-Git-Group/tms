import 'package:flutter/widgets.dart';

import '../theme.dart';

/// 標籤的語氣。語氣決定配色，呼叫端不自己挑顏色。
///
/// 語氣是照「員工看到這張單該有什麼反應」分的，不是照資料欄位分：同一個欄位
/// 在不同情況下語氣會變（等待時間平常是中性的，超過 20 分就變成要處理的），
/// 而不同欄位可能是同一個語氣（接手進度與第一段未接提醒都是「留意一下」）。
enum TmsTagTone {
  /// 要員工優先處理的。推播優先通知的單。
  brand,

  /// 對員工有利的資訊。同樓層、離你最近。
  info,

  /// 留意一下，但還不急。接手別人的進度、第一段未接提醒。
  warn,

  /// 要立刻處理的。急件、已通知主管、等太久。
  danger,

  /// 中性事實。等待時間、退池原因。
  neutral,

  /// 中性事實但底色要比 [neutral] 再深一階，疊在淺灰底的卡上才分得出來。
  neutralStrong,
}

/// 一顆小標籤：單卡標籤列上的膠囊。
///
/// 跟狀態標籤（`TmsStatusChip`）的差別在角色而不只是尺寸：狀態標籤講的是這張
/// 單現在是什麼狀態，全 App 同一個狀態只有一種說法與一種顏色；這個標籤講的是
/// 這張單的附加情況（同樓層、接手進度、等多久、急件），同一張單上可以掛好幾
/// 顆，所以它比狀態標籤小一號，排成一列也不會把卡撐開。
class TmsTag extends StatelessWidget {
  const TmsTag({super.key, required this.label, required this.tone});

  final String label;
  final TmsTagTone tone;

  /// 標籤列的間距。標籤之間與換行之間都用這個值。
  static const double gap = TmsSpacing.xs2;

  Color get _background {
    switch (tone) {
      case TmsTagTone.brand:
        return TmsColors.brandPrimary;
      case TmsTagTone.info:
        return TmsColors.brandContainer;
      case TmsTagTone.warn:
        return TmsColors.brandAccentContainer;
      case TmsTagTone.danger:
        return TmsColors.errorContainer;
      case TmsTagTone.neutral:
        return TmsColors.groupedBackground;
      case TmsTagTone.neutralStrong:
        return TmsColors.neutralContainer;
    }
  }

  Color get _foreground {
    switch (tone) {
      case TmsTagTone.brand:
        return TmsColors.brandOnPrimary;
      case TmsTagTone.info:
        return TmsColors.brandOnContainerStrong;
      case TmsTagTone.warn:
        return TmsColors.brandOnAccentContainer;
      case TmsTagTone.danger:
        return TmsColors.onErrorContainer;
      case TmsTagTone.neutral:
      case TmsTagTone.neutralStrong:
        return TmsColors.onNeutralContainer;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.sm, vertical: 3),
      decoration: BoxDecoration(
        color: _background,
        borderRadius: BorderRadius.circular(TmsRadius.pill),
      ),
      child: Text(
        label,
        maxLines: 1,
        softWrap: false,
        style: TmsText.caption1.copyWith(color: _foreground),
      ),
    );
  }
}
