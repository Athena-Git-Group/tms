import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/widgets/tms_status_chip.dart';

const List<({String key, String label})> repairCategories = [
  (key: 'air_conditioning', label: '冷氣'),
  (key: 'plumbing_electrical', label: '水電'),
  (key: 'furniture_equipment', label: '家具／設備'),
  (key: 'door_lock', label: '門鎖'),
  (key: 'other', label: '其他'),
];

String repairCategoryText(String category) {
  for (final entry in repairCategories) {
    if (entry.key == category) {
      return entry.label;
    }
  }
  return category;
}

String repairCategoryShortText(String category) {
  return category == 'furniture_equipment'
      ? '設備'
      : repairCategoryText(category);
}

typedef RepairResolutionOption = ({
  String key,
  String label,
  String shortLabel,
  String detail,
  String hint,
  bool needsPhoto,
});

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

RepairResolutionOption? repairResolutionOf(String? resolutionType) {
  for (final entry in repairResolutions) {
    if (entry.key == resolutionType) {
      return entry;
    }
  }
  return null;
}

bool repairResolutionNeedsPhoto(String? resolutionType) =>
    repairResolutionOf(resolutionType)?.needsPhoto ?? true;

String repairCompletionPhotoLabel(String? resolutionType) =>
    repairResolutionNeedsPhoto(resolutionType)
    ? '完修照片（必填，至少 1 張、最多 3 張）'
    : '完修照片（不需處理可以不附照片）';

String repairResolutionText(String resolutionType) =>
    repairResolutionOf(resolutionType)?.label ?? resolutionType;

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

String repairClockText(DateTime at) {
  final local = at.toLocal();
  final hour = local.hour.toString().padLeft(2, '0');
  final minute = local.minute.toString().padLeft(2, '0');
  return '$hour:$minute';
}

int repairMinutesSince(DateTime since, {DateTime? now}) {
  final elapsed = (now ?? DateTime.now()).difference(since.toLocal());
  return elapsed.isNegative ? 0 : elapsed.inMinutes;
}

enum RepairCompletionStage {
  notCompleted,

  awaitingVerification,

  closedVerified,

  closedNoAction;

  bool get isClosed => this == closedVerified || this == closedNoAction;
}

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

enum RepairSummaryAudience {
  reporter,

  statusLine,
}

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

const List<String> repairStepLabels = ['待開始', '維修中', '完修', '驗收'];

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

bool repairIsReworking(RepairTicketDetail ticket) =>
    (ticket.state == 'accepted' || ticket.state == 'doing') &&
    ticket.lastVerifyFailedAt != null &&
    ticket.doneAt == null;
