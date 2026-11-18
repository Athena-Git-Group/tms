import 'package:flutter/cupertino.dart';

import '../../features/pool/pool_widgets.dart';
import '../../features/ticket/repair_labels.dart';
import '../api_client.dart';
import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_category_square.dart';
import 'tms_status_chip.dart';
import 'tms_tag.dart';

class TmsRepairTicketRow extends StatelessWidget {
  const TmsRepairTicketRow({
    super.key,
    required this.ticket,
    required this.onTap,
  });

  final EngineeringTicket ticket;
  final VoidCallback onTap;

  static const String verificationReturnText = '驗收退回';

  @override
  Widget build(BuildContext context) {
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
                  fontSize: ticket.roomNo == null ? 13 : 16,
                ),
                const SizedBox(width: TmsSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
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
