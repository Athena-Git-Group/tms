import 'package:flutter/cupertino.dart';

import '../../features/pool/pool_widgets.dart';
import '../../features/ticket/repair_labels.dart';
import '../api_client.dart';
import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_category_square.dart';
import 'tms_status_chip.dart';
import 'tms_tag.dart';

/// 手上的工務單卡：左邊地點方塊、中間單的說明與副文、右邊狀態標籤。
///
/// 工務首頁「我的任務」與工務主管首頁「我名下的單」用的都是這一張，兩邊長
/// 得一樣、accessibility id 也一樣（`engHome.in-hand.{單號}.card`），主管看自
/// 己名下的單時跟工務員工看到的是同一張卡。
///
/// 被主管驗收不通過、退回重修中的單，副文換成琥珀「驗收退回」標籤加不通過
/// 的原因，讓處理的人一眼知道這張修過一次沒過；其餘的單副文是來源。
///
/// 整張卡併成一個節點，label 依序是單號、地點、說明、狀態，退回重修中的單
/// 最後再接「驗收退回」，驗收讀卡片時不必理會卡內文字怎麼排。
class TmsRepairTicketRow extends StatelessWidget {
  const TmsRepairTicketRow({
    super.key,
    required this.ticket,
    required this.onTap,
  });

  final EngineeringTicket ticket;
  final VoidCallback onTap;

  /// 退回重修中的單卡上那顆標籤的文字。
  static const String verificationReturnText = '驗收退回';

  @override
  Widget build(BuildContext context) {
    // 契約把 ticketNo 列為必填，但實際回應可能是 null；id 退回用 taskId，
    // 讓單卡至少還是個可指認的節點，而不是整串變成同一個 null。
    final ticketNo = ticket.ticketNo ?? ticket.taskId;
    final place = ticket.roomNo ?? '公共區域';
    final status = repairStatusAppearance(
      state: ticket.state,
      isUrgent: ticket.isUrgent,
    );
    final returnReason = ticket.verificationReturnReason;
    final source = ticket.source;
    final subtitle = returnReason ??
        (source == null ? null : engineeringPoolSourceLabels[source]);
    final label = [
      ticketNo,
      place,
      ticket.title,
      status.text,
      if (returnReason != null) verificationReturnText,
    ].join(' ');
    return TmsSemantics(
      id: 'engHome.in-hand.$ticketNo.card',
      label: label,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          constraints: const BoxConstraints(minHeight: 64),
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.md2,
            vertical: TmsSpacing.md,
          ),
          color: TmsColors.cardBackground,
          child: ExcludeSemantics(
            child: Row(
              children: [
                TmsCategorySquare(
                  where: place,
                  palette: tmsRepairSquarePalette(
                    isUrgent: ticket.isUrgent,
                    isDone: ticket.state == 'done',
                  ),
                  // 「公共區域」四個字用 16pt 會撐破 48pt 的方塊。
                  fontSize: ticket.roomNo == null ? 13 : 16,
                ),
                const SizedBox(width: TmsSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // 列主文是工單標題（`Main.dc.html:959`）：地點已經在左邊
                      // 的方塊上，單號工務平常不用來認單，兩者都串進主文會把
                      // 標題擠到被截斷。
                      Text(
                        ticket.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TmsText.headline,
                      ),
                      if (subtitle != null) ...[
                        const SizedBox(height: TmsSpacing.xxs),
                        Row(
                          children: [
                            if (returnReason != null) ...[
                              const TmsTag(
                                label: verificationReturnText,
                                tone: TmsTagTone.warn,
                              ),
                              const SizedBox(width: TmsTag.gap),
                            ],
                            Expanded(
                              child: Text(
                                subtitle,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TmsText.subheadline.copyWith(
                                  color: TmsColors.secondaryLabel,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: TmsSpacing.sm),
                TmsStatusChip.of(status),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
