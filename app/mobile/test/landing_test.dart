import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/landing.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';

void main() {
  group('landingShellFor', () {
    test('主管一律進主管外殼，不看有沒有部門', () {
      expect(landingShellFor(role: 'manager'), isA<ManagerShell>());
      expect(
        landingShellFor(role: 'manager', dept: 'housekeeping'),
        isA<ManagerShell>(),
      );
      expect(
        landingShellFor(role: 'manager', dept: 'engineering'),
        isA<ManagerShell>(),
      );
    });

    test('主管外殼帶著分流時判好的主管種類，部門是工務才是工務主管', () {
      Matcher shellOf(ManagerKind kind) =>
          isA<ManagerShell>().having((s) => s.kind, 'kind', kind);
      expect(
        landingShellFor(role: 'manager'),
        shellOf(ManagerKind.housekeeping),
      );
      expect(
        landingShellFor(role: 'manager', dept: 'housekeeping'),
        shellOf(ManagerKind.housekeeping),
      );
      expect(
        landingShellFor(role: 'manager', dept: 'engineering'),
        shellOf(ManagerKind.engineering),
      );
    });

    test('不是主管時，工務部門進工務外殼', () {
      expect(
        landingShellFor(role: 'engineering', dept: 'engineering'),
        isA<EngineeringShell>(),
      );
    });

    test('其餘（房務、櫃檯、沒有部門）進房務外殼', () {
      expect(
        landingShellFor(role: 'housekeeping', dept: 'housekeeping'),
        isA<HousekeepingShell>(),
      );
      expect(
        landingShellFor(role: 'front_desk', dept: 'front_desk'),
        isA<HousekeepingShell>(),
      );
      expect(landingShellFor(role: 'housekeeping'), isA<HousekeepingShell>());
    });

    test('登入回應直接分流，用的是回應裡的 role 與 dept', () {
      const session = LoginResult(
        token: 't',
        expiresAt: '2026-01-01T00:00:00Z',
        staffId: 's',
        name: '李主管',
        role: 'manager',
        dept: 'housekeeping',
        mustChangePassword: false,
      );
      expect(landingShellForLogin(session), isA<ManagerShell>());
    });
  });
}
