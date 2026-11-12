import 'package:flutter/widgets.dart';

import '../features/shell/main_shell.dart';
import 'api_client.dart';

/// 登入後要進哪一個外殼，三個入口（登入、臨時密碼改完、依推播開首頁）都
/// 只問這一個函式，分流規則不在三處各寫一份。
///
/// 先看角色再看部門：角色是「這個人是誰」、部門是「這個人幫哪一邊做事」，
/// 主管即使被設了部門也還是主管，進主管外殼；不是主管的才看部門，工務進
/// 工務外殼，其餘（房務、櫃檯、沒有部門）進房務外殼。
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

/// 主管的種類。只決定主管看得到哪幾類待辦與「我名下的單」用哪一種卡，不決定
/// 進哪個外殼，也不影響誰能驗收。
enum ManagerKind {
  /// 房務主管：看待查房與待驗收兩類，名下的單用房務首頁的任務卡。
  housekeeping(['inspection', 'verify']),

  /// 工務主管：只看待驗收一類，名下的單用工務首頁的工務單卡；沒有查房入口。
  engineering(['verify']);

  const ManagerKind(this.queues);

  /// 這種主管看得到的待辦類別，依首頁格子的順序排；鍵與後端主管待辦相同。
  /// 首頁畫哪幾格、清單准不准打開，都只看這一份，待查房的入口不在別處另判。
  final List<String> queues;
}

/// 判定主管種類，全 App 只在這裡判一次，再由主管外殼往下傳。
///
/// 角色不是主管時回 null。角色是主管且部門是工務才是工務主管；部門是房務、
/// 其他值或空，一律當房務主管，所以畫面上沒有任何地方會把主管當成員工。
/// 這條口徑與後端決定主管待辦回哪幾類的規則相同。
ManagerKind? managerKindOf({required String role, String? dept}) {
  if (role != 'manager') {
    return null;
  }
  return dept == 'engineering'
      ? ManagerKind.engineering
      : ManagerKind.housekeeping;
}

/// 拿登入回應直接分流的便利版本。
Widget landingShellForLogin(LoginResult session) =>
    landingShellFor(role: session.role, dept: session.dept);
