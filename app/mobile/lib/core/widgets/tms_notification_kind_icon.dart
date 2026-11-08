import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 通知中心一則通知的分類。七類各有自己的底色與符號，員工掃一屏通知時先看
/// 顏色就知道哪幾則是急的、哪幾則只是紀錄，不必逐則讀標題。
///
/// 七類的顏色刻意跟全 App 的狀態語氣共用同一組色票：急件與錯誤都是紅、超時
/// 與警告都是橘、完成與成功都是綠。通知中心自己另挑一組顏色的話，同一件事在
/// 通知裡是一個顏色、點進去的單上是另一個顏色，員工得把兩套顏色各記一次。
///
/// [memo] 與 [newRequest] 共用主色是故意的：兩者都是「有人把事情交到你這裡」，
/// 差別只在交的是一句話還是一張單，用符號區分就夠，不必再多一個顏色。
enum TmsNotificationKind {
  /// 急件。
  urgent(
    background: TmsColors.systemRed,
    glyph: CupertinoIcons.bolt_fill,
  ),

  /// 等太久沒人處理的提醒。
  timeout(
    background: TmsColors.systemOrange,
    glyph: CupertinoIcons.timer,
  ),

  /// 做完了：查房通過、工務完修。
  done(
    background: TmsColors.systemGreen,
    glyph: CupertinoIcons.checkmark_alt,
  ),

  /// 有人在單上留話。
  memo(
    background: TmsColors.brandPrimary,
    glyph: CupertinoIcons.bubble_left_fill,
  ),

  /// 池裡來了新的單。
  newRequest(
    background: TmsColors.brandPrimary,
    glyph: CupertinoIcons.tray_fill,
  ),

  /// 保養派單。
  maintenance(
    background: TmsColors.maintenanceIcon,
    glyph: CupertinoIcons.calendar,
  ),

  /// 以上都不是的一般通知。分不出類別時也用這一類，畫面不會因為多了一種沒
  /// 見過的通知就空一塊。
  info(
    background: TmsColors.quaternaryLabel,
    glyph: CupertinoIcons.bell_fill,
  );

  const TmsNotificationKind({required this.background, required this.glyph});

  /// 圓形底的顏色。符號一律是白的，所以這七個值都是配得起白字的深色。
  final Color background;

  /// 圓形底上的符號。
  final IconData glyph;
}

/// 通知列左側的圓形分類圖示：彩色圓底加一個白色符號。
class TmsNotificationKindIcon extends StatelessWidget {
  const TmsNotificationKindIcon({super.key, required this.kind});

  final TmsNotificationKind kind;

  /// 圓形底上的符號邊長。
  static const double _glyphSize = 18;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: TmsSize.notificationKindIcon,
      height: TmsSize.notificationKindIcon,
      decoration: BoxDecoration(
        color: kind.background,
        shape: BoxShape.circle,
      ),
      alignment: Alignment.center,
      child: Icon(
        kind.glyph,
        size: _glyphSize,
        color: TmsColors.brandOnPrimary,
      ),
    );
  }
}
