import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import 'repair_labels.dart';

/// 狀態列下方的琥珀提示卡：這張單最近一次驗收不通過的原因、判定人與時間。
///
/// 只講最近一次，不列歷次。單還沒結案、而且被判過不通過時才出現：退回重修中
/// 看得到為什麼被退，重修完再等驗收時也還看得到上一次的原因；結案之後就不再
/// 提。報修人、處理人與主管三種視角都用這一張。
class TicketLastVerifyFailureCard extends StatelessWidget {
  const TicketLastVerifyFailureCard({super.key, required this.ticket});

  final RepairTicketDetail ticket;

  /// 這張單現在該不該出現這張卡。
  static bool shows(RepairTicketDetail ticket) =>
      ticket.lastVerifyFailedReason != null && ticket.state != 'closed';

  @override
  Widget build(BuildContext context) {
    final reason = ticket.lastVerifyFailedReason ?? '';
    final by = ticket.lastVerifyFailedBy ?? '';
    final at = DateTime.tryParse(ticket.lastVerifyFailedAt ?? '');
    final heading = ticket.state == 'done' ? '上一次驗收不通過' : '驗收不通過';
    final meta = [by, if (at != null) repairClockText(at)].join(' · ');
    return TmsSemantics(
      id: 'ticket.last-verify-failure',
      // 原因與判定人放在同一個節點上，讀的人一次就拿到「誰、為什麼」。
      label: '$heading：$reason。$meta',
      child: ExcludeSemantics(
        child: Container(
          padding: const EdgeInsets.all(TmsSpacing.md),
          decoration: BoxDecoration(
            color: TmsColors.brandAccentContainer,
            borderRadius: BorderRadius.circular(TmsRadius.card),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                heading,
                style: TmsText.footnoteEmphasized.copyWith(
                  color: TmsColors.brandOnAccentContainer,
                ),
              ),
              const SizedBox(height: TmsSpacing.xxs),
              Text(reason, style: TmsText.body),
              const SizedBox(height: TmsSpacing.xxs),
              Text(
                meta,
                style: TmsText.footnote.copyWith(
                  color: TmsColors.brandOnAccentContainer,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
