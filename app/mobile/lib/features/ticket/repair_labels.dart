import '../../core/api_client.dart';

/// 工務單在畫面上的用語對照，集中一份讓詳情、完修面板與開工單表單顯示
/// 同一套中文；權威值一律是契約的 enum，不縮寫。

/// 類別 enum（契約 RepairCategory）→ 畫面文字；開工單表單的類別 chip 依
/// 這份順序排列。
const List<({String key, String label})> repairCategories = [
  (key: 'air_conditioning', label: '冷氣'),
  (key: 'plumbing_electrical', label: '水電'),
  (key: 'furniture_equipment', label: '家具／設備'),
  (key: 'door_lock', label: '門鎖'),
  (key: 'other', label: '其他'),
];

/// 完修三選一（契約 CompleteTaskRequest.resolutionType）→ 畫面文字。
const List<({String key, String label})> repairResolutions = [
  (key: 'fixed', label: '已修好'),
  (key: 'temporary', label: '暫時處理'),
  (key: 'cannot_fix', label: '無法修復'),
];

/// 工務單狀態 enum → 畫面文字。認不得的值原樣回傳，不猜一個說法。
///
/// 已完修之後是否已驗收不是獨立狀態值（看 verifiedBy 是否有值，契約
/// RepairTicketSummary.state 的說明），所以 done 在這裡只到「已完修」；
/// 要連驗收一起講的是 [repairStatusSummaryText]。
String repairStateText(String state) {
  switch (state) {
    case 'pending_review':
      return '等主管審核';
    case 'rejected':
      return '主管未核准';
    case 'pool':
      return '待認領';
    case 'accepted':
      return '已接單';
    case 'doing':
      return '維修中';
    case 'done':
      return '已完修';
    case 'cancelled':
      return '已撤回';
    default:
      return state;
  }
}

/// 工務單詳情的狀態摘要卡文案：狀態文字之外，已完修的單還要講驗收結果。
String repairStatusSummaryText(RepairTicketDetail ticket) {
  if (ticket.state == 'done') {
    return ticket.verifiedBy == null
        ? '已完修 · 等主管驗收'
        : '已驗收 · ${ticket.verifiedBy}';
  }
  return repairStateText(ticket.state);
}
