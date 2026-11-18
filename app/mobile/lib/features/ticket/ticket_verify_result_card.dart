import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../manager/manager_ids.dart';

class TicketVerifyResultCard extends StatelessWidget {
  const TicketVerifyResultCard({
    super.key,
    required this.outcome,
    required this.onBack,
  });

  final RepairVerificationOutcome outcome;

  final VoidCallback onBack;

  static const String _headline = '退回重修';

  String get _subtitle {
    final assignee = outcome.assigneeName ?? '處理人';
    return outcome.destination == 'pool'
        ? '$assignee已下班，這張單回到了工務待認領'
        : '回到$assignee名下，$assignee會收到推播';
  }

  String? get _reasonText {
    final reason = outcome.reason;
    return reason == null ? null : '不通過原因：$reason';
  }

  String get _label =>
      [outcome.ticketNo, _headline, _subtitle, ?_reasonText].join(' ');

  @override
  Widget build(BuildContext context) {
    final reasonText = _reasonText;
    return TmsSemantics(
      id: ManagerIds.ticketResult,
      label: _label,
      explicitChildNodes: true,
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        child: Column(
          children: [
            Expanded(
              child: SafeArea(
                bottom: false,
                child: Center(
                  child: ExcludeSemantics(
                    child: Padding(
                      padding: const EdgeInsets.all(TmsSpacing.xl),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Transform.rotate(
                            angle: -0.105,
                            child: Container(
                              width: 88,
                              height: 88,
                              alignment: Alignment.center,
                              decoration: BoxDecoration(
                                color: TmsColors.brandAccentVivid,
                                borderRadius: BorderRadius.circular(28),
                              ),
                              child: const Icon(
                                CupertinoIcons.arrow_counterclockwise,
                                size: 44,
                                color: TmsColors.brandOnPrimary,
                              ),
                            ),
                          ),
                          const SizedBox(height: TmsSpacing.lg2),
                          Text(outcome.ticketNo, style: TmsText.displayRoomNo),
                          const SizedBox(height: TmsSpacing.xs2),
                          Text(_headline, style: TmsText.title1),
                          const SizedBox(height: TmsSpacing.sm2),
                          Text(
                            _subtitle,
                            textAlign: TextAlign.center,
                            style: TmsText.subheadline.copyWith(
                              color: TmsColors.secondaryLabel,
                              height: 1.6,
                            ),
                          ),
                          if (reasonText != null) ...[
                            const SizedBox(height: TmsSpacing.md),
                            Text(
                              reasonText,
                              textAlign: TextAlign.center,
                              style: TmsText.body,
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
            TmsActionBar(
              primary: TmsSemantics(
                id: ManagerIds.ticketResultBackButton,
                child: TmsPillButton(label: '回到待驗收', onPressed: onBack),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
