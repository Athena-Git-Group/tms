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

/// 一種完修結果在畫面上的全部說法與照片規則。
typedef RepairResolutionOption = ({
  String key,
  String label,
  String shortLabel,
  String detail,
  String hint,
  bool needsPhoto,
});

/// 完修四選一（契約 CompleteTaskRequest.resolutionType）→ 畫面文字與照片規則。
///
/// 每一個結果都帶一句 [detail]，因為四者的差別不在修好沒有，而在送出之後系統
/// 替員工做了什麼（開追蹤單、改房況、通知誰、要不要主管驗收）。只給「已修好／
/// 暫時處理／無法修復」幾個詞的話，員工得先知道飯店的流程才選得對。
/// [shortLabel] 是報修人摘要卡上的短說法（「已修好 · 等主管驗收」的前半）。
/// [hint] 是送出前的最後一次提醒，講同一件事但換成「按下去會發生什麼」。
/// [needsPhoto] 是這個結果送出前要不要至少一張完修照片：不需處理是到場確認沒
/// 有問題，沒有東西可拍。
const List<RepairResolutionOption> repairResolutions = [
  (
    key: 'fixed',
    label: '已修好，房間可正常使用',
    shortLabel: '已修好',
    detail: '通知報修的人與櫃檯',
    hint: '送出後報修的人與櫃檯會收到通知，房務可以接著做。',
    needsPhoto: true,
  ),
  (
    key: 'temporary',
    label: '暫時處理，需要再追蹤',
    shortLabel: '暫時處理',
    detail: '自動建一張追蹤單，並通知櫃檯與主管',
    hint: '送出後會自動建立追蹤單，櫃檯與主管會收到通知。',
    needsPhoto: true,
  ),
  (
    key: 'cannot_fix',
    label: '無法修復，房間停用（OOO）',
    shortLabel: '無法修復',
    detail: 'PMS 房況改為停用，並通知櫃檯與主管',
    hint: '送出後 PMS 房況改為停用，櫃檯與主管會收到通知。',
    needsPhoto: true,
  ),
  (
    key: 'no_action_needed',
    label: '不需處理',
    shortLabel: '不需處理',
    detail: '到場確認沒有問題，直接結案，不需主管驗收',
    hint: '送出後這張單直接結案，不建追蹤單、不改房況。',
    needsPhoto: false,
  ),
];

/// 完修結果 enum → 對照表裡那一列；認不得或還沒選時是 null。
RepairResolutionOption? repairResolutionOf(String? resolutionType) {
  for (final entry in repairResolutions) {
    if (entry.key == resolutionType) {
      return entry;
    }
  }
  return null;
}

/// 這個完修結果要不要附完修照片。還沒選結果時當作要，送出鈕不會先亮。
bool repairResolutionNeedsPhoto(String? resolutionType) =>
    repairResolutionOf(resolutionType)?.needsPhoto ?? true;

/// 完修面板照片卡的標題，跟著選的結果講照片是不是必填。
String repairCompletionPhotoLabel(String? resolutionType) =>
    repairResolutionNeedsPhoto(resolutionType)
    ? '完修照片（必填，至少 1 張、最多 3 張）'
    : '完修照片（不需處理可以不附照片）';

/// 完修結果 enum → 畫面文字。認不得的值原樣回傳，不猜一個說法。
String repairResolutionText(String resolutionType) =>
    repairResolutionOf(resolutionType)?.label ?? resolutionType;

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
    case 'closed':
      return '已結案';
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

/// 完修之後走到哪一段。
///
/// 步驟條第四步「驗收」完成了沒、狀態摘要卡講哪一句，都看這一個值：兩邊各自
/// 判斷的話，不需處理直接結案這種單就可能一邊說結案、一邊還停在驗收。
enum RepairCompletionStage {
  /// 還沒完修（待認領、待開始、維修中、退回重修中）。
  notCompleted,

  /// 已完修，等主管驗收。
  awaitingVerification,

  /// 主管驗收通過，已結案。
  closedVerified,

  /// 完修回報選不需處理，直接結案、沒有驗收。
  closedNoAction;

  /// 已結案（不論是驗收通過還是不需處理）：第四步「驗收」算完成。
  bool get isClosed => this == closedVerified || this == closedNoAction;
}

/// 這張單完修之後走到哪一段，見 [RepairCompletionStage]。
RepairCompletionStage repairCompletionStage(RepairTicketDetail ticket) {
  switch (ticket.state) {
    case 'done':
      return RepairCompletionStage.awaitingVerification;
    case 'closed':
      return ticket.resolutionType == 'no_action_needed'
          ? RepairCompletionStage.closedNoAction
          : RepairCompletionStage.closedVerified;
    default:
      return RepairCompletionStage.notCompleted;
  }
}

/// 誰在看這張單的「現在到哪了」：報修人看狀態摘要卡，處理人與主管看狀態列。
enum RepairSummaryAudience {
  /// 報修人（房務視角）的狀態摘要卡。
  reporter,

  /// 處理人（工務視角）與主管視角的狀態列；兩者用同一句話。
  statusLine,
}

/// 工務單詳情回答「現在到哪了」的那一句，三種視角的對照表只寫在這裡：
///
/// | 狀態 | 狀態列（工務、主管） | 摘要卡（報修人） |
/// | --- | --- | --- |
/// | 已完修、等主管驗收 | 已完修 · 等主管驗收 | {完修結果短說法} · 等主管驗收 |
/// | 退回重修中 | 退回重修 · {判定人} 判定不通過 | 退回重修中 · {處理人} 處理中 |
/// | 已結案（驗收通過） | 已結案 · {驗收人}驗收 | 同左 |
/// | 已結案（不需處理） | 已結案 · 不需處理 | 同左 |
///
/// 各畫面各寫一句的話，同一張單在不同人的手機上會講成不同的事。還沒完修、也
/// 不是退回重修中的單，狀態列講狀態詞；摘要卡帶著工務的名字講，報修的人想知
/// 道的是誰接了。
String repairStatusSummaryText(
  RepairTicketDetail ticket, {
  RepairSummaryAudience audience = RepairSummaryAudience.statusLine,
}) {
  final reporter = audience == RepairSummaryAudience.reporter;
  if (repairIsReworking(ticket)) {
    return reporter
        ? '退回重修中 · ${ticket.assigneeName ?? '工務'} 處理中'
        : '退回重修 · ${ticket.lastVerifyFailedBy ?? '主管'} 判定不通過';
  }
  switch (repairCompletionStage(ticket)) {
    case RepairCompletionStage.awaitingVerification:
      final result = repairResolutionOf(ticket.resolutionType)?.shortLabel;
      return reporter && result != null ? '$result · 等主管驗收' : '已完修 · 等主管驗收';
    case RepairCompletionStage.closedNoAction:
      return '已結案 · 不需處理';
    case RepairCompletionStage.closedVerified:
      final by = ticket.verifiedBy;
      return by == null ? '已結案' : '已結案 · $by驗收';
    case RepairCompletionStage.notCompleted:
      return reporter
          ? _reporterOpenText(ticket)
          : repairStateText(ticket.state);
  }
}

/// 報修人摘要卡上還沒完修的那幾句。
String _reporterOpenText(RepairTicketDetail ticket) {
  final who = ticket.assigneeName;
  switch (ticket.state) {
    case 'pool':
      return '等工務接單';
    case 'accepted':
      return who == null ? '已接單' : '工務 $who 已接單';
    case 'doing':
      return who == null ? '維修中' : '工務 $who 維修中';
    default:
      return repairStateText(ticket.state);
  }
}

/// 工務單詳情的四步步驟條，三種視角同一套步驟名。
const List<String> repairStepLabels = ['待開始', '維修中', '完修', '驗收'];

/// 目前在 [repairStepLabels] 的第幾步（0 起算）。
///
/// 待開始＝已接單（含退回重修），維修中＝維修中，驗收＝已完修等主管驗收；「完
/// 修」沒有可以停留的狀態。已結案（驗收通過或不需處理）四步都走完，回傳步數；
/// 還沒有人接單（待認領）或已撤回時是 -1。完修之後看 [repairCompletionStage]，
/// 第四步完成與摘要卡講「已結案」永遠一起成立。
int repairCurrentStepIndex(RepairTicketDetail ticket) {
  final stage = repairCompletionStage(ticket);
  if (stage.isClosed) {
    return repairStepLabels.length;
  }
  if (stage == RepairCompletionStage.awaitingVerification) {
    return 3;
  }
  switch (ticket.state) {
    case 'accepted':
      return 0;
    case 'doing':
      return 1;
    default:
      return -1;
  }
}

/// 這張單是不是驗收不通過、退回重修中：還在待開始或維修中、有最近一次不通過，
/// 而且還沒再完修（不通過會清空完修時間）。
bool repairIsReworking(RepairTicketDetail ticket) =>
    (ticket.state == 'accepted' || ticket.state == 'doing') &&
    ticket.lastVerifyFailedAt != null &&
    ticket.doneAt == null;
