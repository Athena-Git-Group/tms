import 'package:flutter/widgets.dart';

import '../features/shell/main_shell.dart';
import 'api_client.dart';

Widget landingShellFor({required String role, String? dept}) {
  final kind = managerKindOf(role: role, dept: dept);
  if (kind != null) {
    return ManagerShell(kind: kind);
  }
  if (dept == 'engineering') {
    return const EngineeringShell();
  }
  return const HousekeepingShell();
}

enum ManagerKind {
  housekeeping(['inspection', 'verify'], 'housekeeping'),

  engineering(['verify'], 'engineering');

  const ManagerKind(this.queues, this.poolDept);

  final List<String> queues;

  final String poolDept;

  bool canClaimFrom(String dept) => dept == poolDept;
}

ManagerKind? managerKindOf({required String role, String? dept}) {
  if (role != 'manager') {
    return null;
  }
  return dept == 'engineering'
      ? ManagerKind.engineering
      : ManagerKind.housekeeping;
}

Widget landingShellForLogin(LoginResult session) =>
    landingShellFor(role: session.role, dept: session.dept);
