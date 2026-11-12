import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import 'repair_labels.dart';

class TicketLastVerifyFailureCard extends StatelessWidget {
  const TicketLastVerifyFailureCard({super.key, required this.ticket});

  final RepairTicketDetail ticket;

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
