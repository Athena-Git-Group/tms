abstract final class ManagerIds {
  static const String home = 'manager.home';
  static const String homeMine = 'manager.home.mine';

  static const String homeMineEmpty = 'manager.home.mine.empty';

  static String homeTile(String queue) => 'manager.home.tile.$queue';

  static String homeTileCount(String queue) => 'manager.home.tile.$queue.count';

  static const String homeTileVerifyWide = 'manager.home.tile.verify';

  static const String queue = 'manager.queue';
  static const String queueEmpty = 'manager.queue.empty';

  static String queueRow(String key) => 'manager.queue.row.$key';

  static String queueRowWaitingSince(String key) =>
      'manager.queue.row.$key.waiting-since';

  static const String inspect = 'inspect';
  static const String inspectSubmitButton = 'inspect.submit-button';

  static String inspectItem(String key) => 'inspect.item.$key';
  static String inspectItemPassButton(String key) =>
      'inspect.item.$key.pass-button';
  static String inspectItemFailButton(String key) =>
      'inspect.item.$key.fail-button';

  static const String inspectResult = 'inspect.result';
  static const String inspectResultBackButton = 'inspect.result.back-button';

  static const String conflictAlert = 'manager.conflict-alert';

  static const String ticket = 'managerTicket';
  static const String ticketApproveButton = 'managerTicket.approve-button';
  static const String ticketRejectButton = 'managerTicket.reject-button';
  static const String ticketVerifyButton = 'managerTicket.verify-button';
  static const String ticketRejectSheetReasonField =
      'managerTicket.reject-sheet.reason-field';
  static const String ticketRejectSheetSubmitButton =
      'managerTicket.reject-sheet.submit-button';

  static const String ticketPassButton = 'managerTicket.pass-button';
  static const String ticketFailButton = 'managerTicket.fail-button';

  static const String ticketFailSheetReasonField =
      'managerTicket.fail-sheet.reason-field';
  static const String ticketFailSheetSubmitButton =
      'managerTicket.fail-sheet.submit-button';

  static const String ticketResult = 'managerTicket.result';
  static const String ticketResultBackButton =
      'managerTicket.result.back-button';

  static String ticketStepperDone(int step) => 'ticket.stepper.$step--done';

  static String engPoolItemVerificationReturnLabel(String ticketNo) =>
      'engPool.item.$ticketNo.verification-return-label';

  static const String poolDeptSwitch = 'pool.dept-switch';

  static String poolDeptSwitchSide(String dept) => 'pool.dept-switch.$dept';
}
