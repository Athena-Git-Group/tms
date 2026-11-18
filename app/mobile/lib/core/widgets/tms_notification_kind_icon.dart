import 'package:flutter/cupertino.dart';

import '../theme.dart';

enum TmsNotificationKind {
  urgent(
    background: TmsColors.systemRed,
    glyph: CupertinoIcons.bolt_fill,
  ),

  timeout(
    background: TmsColors.systemOrange,
    glyph: CupertinoIcons.timer,
  ),

  done(
    background: TmsColors.systemGreen,
    glyph: CupertinoIcons.checkmark_alt,
  ),

  memo(
    background: TmsColors.brandPrimary,
    glyph: CupertinoIcons.bubble_left_fill,
  ),

  newRequest(
    background: TmsColors.brandPrimary,
    glyph: CupertinoIcons.tray,
  ),

  maintenance(
    background: TmsColors.maintenanceIcon,
    glyph: CupertinoIcons.calendar,
  ),

  info(
    background: TmsColors.quaternaryLabel,
    glyph: CupertinoIcons.bell_fill,
  );

  const TmsNotificationKind({required this.background, required this.glyph});

  final Color background;

  final IconData glyph;
}

class TmsNotificationKindIcon extends StatelessWidget {
  const TmsNotificationKindIcon({super.key, required this.kind});

  final TmsNotificationKind kind;

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
