import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_tag.dart';
import 'manager_ids.dart';

class InspectionResultScreen extends StatelessWidget {
  const InspectionResultScreen({
    super.key,
    required this.outcome,
    required this.cleanerName,
  });

  final InspectionOutcome outcome;

  final String cleanerName;

  bool get _passed => outcome.result == 'passed';

  String get _cleaner {
    final name = outcome.cleanerName;
    return name == null || name.isEmpty ? cleanerName : name;
  }

  String get _headline => _passed ? '查房通過，已結案' : '退回重做';

  String get _subtitle {
    if (_passed) {
      return '$_cleaner會收到通知';
    }
    return outcome.destination == 'pool'
        ? '$_cleaner已下班，這張單回到了房務待認領'
        : '回到$_cleaner名下';
  }

  List<String> get _failedLabels => [
    for (final item in outcome.failedItems) '不合格：${item.label}',
  ];

  String get _label =>
      [outcome.roomNo, _headline, _subtitle, ..._failedLabels].join(' ');

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: ManagerIds.inspectResult,
      label: _label,
      explicitChildNodes: true,
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        child: Column(
          children: [
            Expanded(
              child: SafeArea(
                bottom: false,
                child: Center(child: ExcludeSemantics(child: _body())),
              ),
            ),
            TmsActionBar(
              primary: TmsSemantics(
                id: ManagerIds.inspectResultBackButton,
                child: TmsPillButton(
                  label: '回到待查房',
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _body() {
    return Padding(
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
                color: _passed
                    ? TmsColors.brandPrimary
                    : TmsColors.brandAccentVivid,
                borderRadius: BorderRadius.circular(28),
              ),
              child: Icon(
                _passed
                    ? CupertinoIcons.checkmark_alt
                    : CupertinoIcons.arrow_counterclockwise,
                size: 44,
                color: TmsColors.brandOnPrimary,
              ),
            ),
          ),
          const SizedBox(height: TmsSpacing.lg2),
          Text(outcome.roomNo, style: TmsText.displayRoomNo),
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
          if (outcome.failedItems.isNotEmpty) ...[
            const SizedBox(height: TmsSpacing.md),
            Wrap(
              spacing: TmsTag.gap,
              runSpacing: TmsTag.gap,
              alignment: WrapAlignment.center,
              children: [
                for (final label in _failedLabels)
                  TmsTag(label: label, tone: TmsTagTone.warn),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
