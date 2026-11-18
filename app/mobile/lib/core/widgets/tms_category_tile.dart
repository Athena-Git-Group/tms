import 'package:flutter/cupertino.dart';

import '../theme.dart';

class TmsCountBadge extends StatelessWidget {
  const TmsCountBadge({super.key, required this.count}) : _onTabBar = false;

  const TmsCountBadge.tabBar({super.key, required this.count})
    : _onTabBar = true;

  final int count;

  final bool _onTabBar;

  @override
  Widget build(BuildContext context) {
    final diameter = _onTabBar ? 18.0 : 20.0;
    return Container(
      constraints: BoxConstraints(minWidth: diameter),
      height: diameter,
      padding: EdgeInsets.symmetric(horizontal: _onTabBar ? 5 : TmsSpacing.xs2),
      alignment: Alignment.center,
      decoration: const BoxDecoration(
        color: TmsColors.systemRed,
        shape: BoxShape.rectangle,
        borderRadius: BorderRadius.all(Radius.circular(TmsRadius.pill)),
      ),
      child: Text(
        '$count',
        maxLines: 1,
        softWrap: false,
        style: (_onTabBar ? TmsText.caption2 : TmsText.caption1).copyWith(
          color: TmsColors.brandOnPrimary,
        ),
      ),
    );
  }
}

class TmsCategoryTile extends StatelessWidget {
  const TmsCategoryTile({
    super.key,
    required this.label,
    required this.iconBackground,
    required this.iconForeground,
    required this.selected,
    required this.onTap,
    this.icon,
    this.iconChild,
    this.badge,
    this.unselectedBackground,
  }) : assert(
         (icon == null) != (iconChild == null),
         'icon 與 iconChild 只能給一個',
       );

  final String label;

  final IconData? icon;

  final Widget? iconChild;

  final Color iconBackground;
  final Color iconForeground;
  final bool selected;
  final VoidCallback onTap;

  final Color? unselectedBackground;

  static const Color translucentBackground = Color(0x8CFFFFFF);

  final Widget? badge;

  static const double gap = TmsSpacing.sm2;

  @override
  Widget build(BuildContext context) {
    final tile = Container(
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.xs,
        TmsSpacing.sm2,
        TmsSpacing.xs,
        TmsSpacing.sm,
      ),
      decoration: BoxDecoration(
        color: selected ? TmsColors.cardBackground : unselectedBackground,
        borderRadius: BorderRadius.circular(TmsRadius.tile),
        boxShadow: selected
            ? const [BoxShadow(color: TmsColors.brandPrimary, spreadRadius: 2)]
            : null,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 44,
            height: 44,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: iconBackground,
              borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
            ),
            child:
                iconChild ?? Icon(icon, size: 24, color: iconForeground),
          ),
          const SizedBox(height: TmsSpacing.xs2),
          Text(
            label,
            maxLines: 1,
            softWrap: false,
            style: TmsText.footnoteEmphasized.copyWith(color: TmsColors.labelSoft),
          ),
        ],
      ),
    );
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: badge == null
          ? tile
          : Stack(
              fit: StackFit.passthrough,
              clipBehavior: Clip.none,
              children: [
                tile,
                Positioned(top: -4, right: 2, child: badge!),
              ],
            ),
    );
  }
}
