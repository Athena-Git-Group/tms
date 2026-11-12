import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/landing.dart';
import 'package:tms_staff_app/features/manager/manager_home_screen.dart';

/// 主管首頁的格子集合跟著主管種類走：房務主管兩格，工務主管只有一條待驗收
/// 寬磚、沒有副文，也沒有待查房與待審核。
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
}
