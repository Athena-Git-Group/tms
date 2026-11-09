import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/widgets/tms_status_chip.dart';

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

/// 類別 enum → 畫面文字。認不得的值原樣回傳，不猜一個說法。
String repairCategoryText(String category) {
  for (final entry in repairCategories) {
    if (entry.key == category) {
      return entry.label;
    }
  }
  return category;
}

/// 類別 enum → 單卡方塊第二行放得下的短名。
///
/// 方塊的邊長固定在 56pt 以內，「家具／設備」五個字會超出去，所以這一類縮成
/// 「設備」。其餘類別兩個字就放得下，直接沿用 [repairCategoryText] 的說法，
/// 讓方塊上的用語和詳情頁一致。設計稿的對應處在 `Main.dc.html:3825`。
String repairCategoryShortText(String category) {
  return category == 'furniture_equipment'
      ? '設備'
      : repairCategoryText(category);
}

/// 完修三選一（契約 CompleteTaskRequest.resolutionType）→ 畫面文字。
///
/// 每一個結果都帶一句 [detail]，因為三者的差別不在修好沒有，而在送出之後系統
/// 替員工做了什麼（開追蹤單、改房況、通知誰）。只給「已修好／暫時處理／無法
/// 修復」三個詞的話，員工得先知道飯店的流程才選得對。
/// [hint] 是送出前的最後一次提醒，講同一件事但換成「按下去會發生什麼」。
const List<({String key, String label, String detail, String hint})>
repairResolutions = [
  (
    key: 'fixed',
    label: '已修好，房間可正常使用',
    detail: '通知報修的人與櫃檯',
    hint: '送出後報修的人與櫃檯會收到通知，房務可以接著做。',
  ),
  (
    key: 'temporary',
    label: '暫時處理，需要再追蹤',
    detail: '自動建一張追蹤單，並通知櫃檯與主管',
    hint: '送出後會自動建立追蹤單，櫃檯與主管會收到通知。',
  ),
  (
    key: 'cannot_fix',
    label: '無法修復，房間停用（OOO）',
    detail: 'PMS 房況改為停用，並通知櫃檯與主管',
    hint: '送出後 PMS 房況改為停用，櫃檯與主管會收到通知。',
  ),
];

/// 完修結果 enum → 畫面文字。認不得的值原樣回傳，不猜一個說法。
String repairResolutionText(String resolutionType) {
  for (final entry in repairResolutions) {
    if (entry.key == resolutionType) {
      return entry.label;
    }
  }
  return resolutionType;
}

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

/// 工務單狀態 enum → 單卡上那顆狀態標籤的用詞與配色。
///
/// 工務的清單用顏色分狀態，讓一個人掃一眼就知道手上哪張單還沒動、哪張在修。
/// 六種狀態各有一組底色與字色，對照表在 `Main.dc.html:2868`。
///
/// 狀態標籤的用詞在清單上比在詳情頁短一截：這裡的 `accepted` 說「待開始」，
/// 講的是「這張單還沒動工」，和詳情頁狀態摘要卡的「已接單」（講的是「這張
/// 單已經有人負責」，見 [repairStateText]）是同一個狀態的兩種角度。清單與
/// 詳情頁各自以自己那句話驗收，所以不共用一份用詞。
///
/// [isUrgent] 為真且單子還沒動工或已經暫停時，急件與狀態併成一顆紅標籤
/// （`Main.dc.html:3835`）。急件單獨掛一顆標籤的話，一列上會出現兩顆顏色
/// 互搶的標籤，而人要的資訊是「這張急件現在卡在哪」這一句。單子正在修或
/// 已完修時不併，那時候急不急已經不影響接下來要做什麼。
///
/// 契約沒有「已暫停」這個狀態值（見 `shared.yaml` 的 `RepairTicketSummary`），
/// 所以 `paused` 這一列目前不會被後端回傳。
///
/// 認不得的狀態值原樣顯示並配中性色，理由同 [tmsStatusAppearance]。
TmsStatusAppearance repairStatusAppearance({
  required String state,
  bool isUrgent = false,
}) {
  if (isUrgent && (state == 'accepted' || state == 'paused')) {
    return TmsStatusAppearance(
      text: '急件 · ${_repairChipText(state)}',
      background: TmsColors.errorContainer,
      foreground: TmsColors.onErrorContainer,
    );
  }
  switch (state) {
    case 'pending_review':
      return TmsStatusAppearance(
        text: _repairChipText(state),
        background: TmsColors.otherContainer,
        foreground: TmsColors.onOtherContainer,
      );
    case 'pool':
      return TmsStatusAppearance(
        text: _repairChipText(state),
        background: TmsColors.brandContainer,
        foreground: TmsColors.brandOnContainerStrong,
      );
    case 'doing':
      return TmsStatusAppearance(
        text: _repairChipText(state),
        background: TmsColors.brandPrimary,
        foreground: TmsColors.brandOnPrimary,
      );
    case 'paused':
      return TmsStatusAppearance(
        text: _repairChipText(state),
        background: TmsColors.brandAccentContainer,
        foreground: TmsColors.brandOnAccentContainer,
      );
    case 'done':
      return TmsStatusAppearance(
        text: _repairChipText(state),
        background: TmsColors.successContainer,
        foreground: TmsColors.onSuccessContainer,
      );
    default:
      return TmsStatusAppearance(
        text: _repairChipText(state),
        background: TmsColors.neutralContainer,
        foreground: TmsColors.onNeutralContainer,
      );
  }
}

/// 狀態標籤上的字。`accepted` 與 `paused` 以外都和詳情頁同一個說法。
String _repairChipText(String state) {
  switch (state) {
    case 'accepted':
      return '待開始';
    case 'paused':
      return '已暫停';
    default:
      return repairStateText(state);
  }
}

/// 把時刻寫成牆上時鐘那樣的 HH:MM。
///
/// 時間軸與狀態列只回答「這一步幾點發生」，年月日交給別的地方講：同一張單的
/// 四個步驟幾乎都落在同一天，寫出日期只會把那一欄撐寬、把步驟名擠掉。
String repairClockText(DateTime at) {
  final local = at.toLocal();
  final hour = local.hour.toString().padLeft(2, '0');
  final minute = local.minute.toString().padLeft(2, '0');
  return '$hour:$minute';
}

/// 從 [since] 到現在過了幾分鐘，不滿一分鐘算 0。
int repairMinutesSince(DateTime since, {DateTime? now}) {
  final elapsed = (now ?? DateTime.now()).difference(since.toLocal());
  return elapsed.isNegative ? 0 : elapsed.inMinutes;
}

/// 工務單詳情的狀態摘要卡文案：狀態文字之外，已完修的單還要講驗收結果。
///
/// 兩種視角都用同一句話回答「現在到哪了」，房務視角把它放進摘要卡、工務視角
/// 放進狀態列。兩邊各寫一句的話，同一張單在兩個人的手機上會講成不同的事。
String repairStatusSummaryText(RepairTicketDetail ticket) {
  if (ticket.state == 'done') {
    final by = ticket.verifiedBy;
    if (by == null) {
      return '已完修 · 等主管驗收';
    }
    final at = ticket.verifiedAt;
    final parsed = at == null ? null : DateTime.tryParse(at);
    return parsed == null
        ? '已驗收 · $by'
        : '已驗收 · $by ${repairClockText(parsed)}';
  }
  return repairStateText(ticket.state);
}
