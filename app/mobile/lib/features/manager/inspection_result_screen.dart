import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_tag.dart';
import 'manager_ids.dart';

/// 查房結果（ui-plan 畫面 4）：送出查房之後換上來的那一頁。
///
/// 說法只有三種，全照後端回的 [InspectionOutcome]（`result` 加 `destination`），
/// App 不自己看班表判斷原清房者在不在班：
/// - passed／closed：查房通過，已結案；原清房者會收到通知。
/// - failed／cleaner：退回重做，回到原清房者名下，列出不合格項。
/// - failed／pool：退回重做，原清房者已下班，這張單回到了房務待認領。
///
/// 底部只有一顆「回到待查房」。清單的重取由開查房畫面的那一端負責。
class InspectionResultScreen extends StatelessWidget {
  const InspectionResultScreen({
    super.key,
    required this.outcome,
    required this.cleanerName,
  });

  final InspectionOutcome outcome;

  /// 回應沒帶原清房者姓名時，用清單列上的姓名。
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

  /// 整個結果節點的讀值：房號、標題、副文與不合格項。
  String get _label =>
      [outcome.roomNo, _headline, _subtitle, ..._failedLabels].join(' ');

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: ManagerIds.inspectResult,
      // 測試直接讀這個節點的 label 驗三種說法；子節點只留返回鈕。
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
