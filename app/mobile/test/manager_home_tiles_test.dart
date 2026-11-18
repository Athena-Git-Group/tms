import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/landing.dart';
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
}
