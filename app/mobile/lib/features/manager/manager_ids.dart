/// 主管端畫面的 accessibility id，集中一份。
///
/// 三份 DSL 點名的 id 都在這裡：主管工作台（主管首頁、清單、查房、查房結果）、
/// 工務單（主管動作列與退回原因面板）、待認領（部門切換）。命名規則同全 App：
/// dot 分段＋kebab-case；帶參數的 id 用函式組字串，鍵與房號照後端給的值，不轉換。
abstract final class ManagerIds {
  // 主管首頁
  static const String home = 'manager.home';
  static const String homeMine = 'manager.home.mine';

  /// 「我名下的單」沒有單時的說明。
  static const String homeMineEmpty = 'manager.home.mine.empty';

  /// 三格磚；[queue] 是 inspection、review、verify。
  static String homeTile(String queue) => 'manager.home.tile.$queue';

  /// 三格磚上的數字。
  static String homeTileCount(String queue) => 'manager.home.tile.$queue.count';

  /// 工務主管首頁唯一的一條待驗收寬磚。id 與房務主管的待驗收格相同，
  /// 「點待驗收」「待驗收格顯示幾張」兩種主管共用同一個定位。
  static const String homeTileVerifyWide = 'manager.home.tile.verify';

  // 三類清單
  static const String queue = 'manager.queue';
  static const String queueEmpty = 'manager.queue.empty';

  /// 清單列；[key] 是房號，工務單列則是單號。
  static String queueRow(String key) => 'manager.queue.row.$key';

  /// 清單列右欄的開始等主管時間。
  static String queueRowWaitingSince(String key) =>
      'manager.queue.row.$key.waiting-since';

  // 查房畫面
  static const String inspect = 'inspect';
  static const String inspectSubmitButton = 'inspect.submit-button';

  /// 查房項目列；[key] 是 sop_items.item_key。
  static String inspectItem(String key) => 'inspect.item.$key';
  static String inspectItemPassButton(String key) =>
      'inspect.item.$key.pass-button';
  static String inspectItemFailButton(String key) =>
      'inspect.item.$key.fail-button';

  // 查房結果
  static const String inspectResult = 'inspect.result';
  static const String inspectResultBackButton = 'inspect.result.back-button';

  /// 主管動作送出時單已被別人處理（409 `state_conflict`）的提示對話框；
  /// 查房送出與工務單四個動作共用這一個。
  static const String conflictAlert = 'manager.conflict-alert';

  // 工務單詳情的主管動作
  static const String ticket = 'managerTicket';
  static const String ticketApproveButton = 'managerTicket.approve-button';
  static const String ticketRejectButton = 'managerTicket.reject-button';
  static const String ticketVerifyButton = 'managerTicket.verify-button';
  static const String ticketRejectSheetReasonField =
      'managerTicket.reject-sheet.reason-field';
  static const String ticketRejectSheetSubmitButton =
      'managerTicket.reject-sheet.submit-button';

  // 工務單詳情的驗收：描邊「不通過」在左、實心「通過」在右
  static const String ticketPassButton = 'managerTicket.pass-button';
  static const String ticketFailButton = 'managerTicket.fail-button';

  /// 按「不通過」升起的原因面板：原因輸入框與送出鈕。
  static const String ticketFailSheetReasonField =
      'managerTicket.fail-sheet.reason-field';
  static const String ticketFailSheetSubmitButton =
      'managerTicket.fail-sheet.submit-button';

  /// 不通過送出後的結果卡，說明單去了哪裡；回清單鈕回到待驗收清單。
  static const String ticketResult = 'managerTicket.result';
  static const String ticketResultBackButton =
      'managerTicket.result.back-button';

  /// 工務單詳情步驟條第 [step] 步（1 起算）已完成的標記；第四步「驗收」只在
  /// 單結案時完成。與既有的 `ticket.stepper.N`、`ticket.stepper.N--current`
  /// 同一套命名。
  static String ticketStepperDone(int step) => 'ticket.stepper.$step--done';

  /// 工務待認領單卡上的「驗收退回」標籤；[ticketNo] 是 W- 單號。
  static String engPoolItemVerificationReturnLabel(String ticketNo) =>
      'engPool.item.$ticketNo.verification-return-label';

  // 主管待認領的部門切換
  static const String poolDeptSwitch = 'pool.dept-switch';

  /// 部門切換上的一側；[dept] 是 housekeeping 或 engineering。
  static String poolDeptSwitchSide(String dept) => 'pool.dept-switch.$dept';
}
