import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/landing.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/features/manager/manager_home_screen.dart';

void main() {
  test('房務主管依序是待查房、待驗收兩格', () {
    final tiles = ManagerHomeScreen.tilesFor(ManagerKind.housekeeping);
    expect(tiles.map((t) => t.queue), ['inspection', 'verify']);
    expect(tiles.map((t) => t.title), ['待查房', '待驗收']);
  });

  test('工務主管只有待驗收一格，不帶副文', () {
    final tiles = ManagerHomeScreen.tilesFor(ManagerKind.engineering);
    expect(tiles.map((t) => t.queue), ['verify']);
    expect(tiles.single.title, '待驗收');
    expect(tiles.single.hint, isNull);
  });

  test('待查房用品牌色、待驗收用品牌深色', () {
    final tiles = ManagerHomeScreen.tilesFor(ManagerKind.housekeeping);
    expect(tiles.first.countColor, TmsColors.brandPrimary);
    expect(tiles.last.countColor, TmsColors.brandPrimaryDark);
  });

  test('格子數字兩格 44、寬磚 56，而且比區段標題大', () {
    expect(TmsText.displayTileCount.fontSize, 44);
    expect(TmsText.displayTileCountWide.fontSize, 56);
    expect(
      TmsText.displayTileCount.fontSize! > TmsText.title3.fontSize!,
      isTrue,
    );
  });
}
