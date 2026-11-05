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

/// 工務單詳情的狀態摘要卡文案，對照後端工務單狀態：已完修之後是否已驗收
/// 不是獨立狀態值，看 verifiedBy 是否有值（契約 RepairTicketSummary.state
/// 的說明）。
String repairStatusSummaryText(RepairTicketDetail ticket) {
  switch (ticket.state) {
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
      return ticket.verifiedBy == null
          ? '已完修 · 等主管驗收'
          : '已驗收 · ${ticket.verifiedBy}';
    case 'cancelled':
      return '已撤回';
    default:
      return ticket.state;
  }
}
