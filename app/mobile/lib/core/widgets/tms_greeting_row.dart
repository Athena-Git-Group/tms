import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 兩個首頁最上面那一塊：日期列在上、問候語在下，右邊一顆圓形頭像。
///
/// 日期列在上、問候語在下（`Main.dc.html:128-129`、`859-860`）：員工一早開 App
/// 第一眼要確認的是「今天是哪一班、顧哪裡」，這兩件事錯了後面整天的任務都對不
/// 上，問候語則是讀完這一行之後的招呼。
///
/// 房務與工務共用同一個版型，只有日期列最後那一段不同（房務接負責樓層、工務
/// 接「工務」），所以那一段由呼叫端給。
class TmsGreetingRow extends StatelessWidget {
  const TmsGreetingRow({
    super.key,
    required this.greeting,
    this.name,
    this.shift,
    this.trailing,
    this.date,
  });

  /// 招呼語本身（「早安」「辛苦了」）。[name] 有值時會接成「早安，阿德」。
  final String greeting;

  /// 登入身分的姓名。頭像裡放的是姓名末字；沒有姓名時頭像留空色塊，問候語
  /// 也只留招呼，不補一個稱呼。
  final String? name;

  /// 班別鍵（租戶設定 `shift_times` 的 `morning`／`afternoon`）。沒排班或後端
  /// 還沒給這個欄位時為 null，日期列就略過班別那一段。
  final String? shift;

  /// 日期列最後那一段：房務是負責樓層、工務是部門名。null 時整段不出現。
  final String? trailing;

  /// 今天的日期；不給就取當下。測試要釘住星期時才傳。
  final DateTime? date;

  /// 日期列裡班別那一段的字重與顏色（`Main.dc.html:128`）。整列是 15pt 灰字，
  /// 只有班別加粗轉黑，員工掃一眼就能從日期列裡先讀到班別。
  static const TextStyle _shiftStyle = TextStyle(
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  /// `週一 10/19`（`Main.dc.html:128`）。星期在前，員工排班是按星期記的。
  static String dateText(DateTime now) {
    const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
    return '週${weekdays[now.weekday - 1]} ${now.month}/${now.day}';
  }

  /// 班別的白話說法。
  ///
  /// 後端回的是租戶設定 `shift_times` 的鍵，不是中文名稱——系統裡沒有任何地方
  /// 存班別的中文說法。翻成白話在這裡做，跟來源、工務單類別走同一套做法：
  /// enum 值不露在畫面上。認不得的鍵原樣顯示，不猜一個說法。
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
