import 'package:flutter/cupertino.dart';

import '../api_client.dart';
import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_category_square.dart';
import 'tms_home_cards.dart';
import 'tms_status_chip.dart';

class TmsTaskRow extends StatelessWidget {
  const TmsTaskRow({
    super.key,
    required this.task,
    required this.showSeparator,
    required this.onTap,
  });

  final TodayTask task;
  final bool showSeparator;

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final subtitle = _subtitle(task);
    return TmsSemantics(
      id: 'home.task.${task.roomNo}.card',
      label: '${task.roomNo} ${task.title}',
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          constraints: const BoxConstraints(minHeight: 64),
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.md2,
            vertical: TmsSpacing.md,
          ),
          decoration: BoxDecoration(
            color: TmsColors.cardBackground,
            border: showSeparator
                ? const Border(
                    bottom: BorderSide(
                      color: TmsColors.separator,
                      width: TmsSize.hairline,
                    ),
                  )
                : null,
          ),
          child: ExcludeSemantics(
            child: Row(
              children: [
                TmsCategorySquare(
                  where: task.roomNo,
                  palette: tmsCategoryAppearance(task.type).palette,
                ),
                const SizedBox(width: TmsSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        task.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TmsText.headline,
                      ),
                      if (subtitle != null) ...[
                        const SizedBox(height: TmsSpacing.xxs),
                        Text(
                          subtitle,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TmsText.subheadline.copyWith(
                            color: TmsColors.secondaryLabel,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: TmsSpacing.md),
                TmsStatusChip(
                  state: task.state,
                  type: task.type,
                  inspection: task.inspection,
                  redoCount: task.redoCount,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  static String? _subtitle(TodayTask task) {
    final segments = <String>[];
    switch (task.state) {
      case 'doing':
        segments.add('已進行 ${task.workedMinutes} 分');
        final overtime = tmsOvertimeMinutes(
          estMin: task.estMin,
          workedMinutes: task.workedMinutes,
        );
        if (overtime != null) {
          segments.add('超時 $overtime 分');
        }
      case 'interrupted':
        segments.add('已做 ${task.workedMinutes} 分');
        _addIfPresent(segments, task.holdReason);
      case 'deferred':
        _addIfPresent(segments, task.holdReason);
      case 'cancelled':
        break;
      case 'done':
      case 'inspected':
        if (task.workedMinutes > 0) {
          segments.add('實際 ${task.workedMinutes} 分');
          if (task.estMin case final estMin?) {
            segments.add('預估 $estMin 分');
          }
        } else {
          segments.addAll(_plannedSegments(task));
        }
      default:
        segments.addAll(_plannedSegments(task));
    }
    if (task.inspectionWaived &&
        (task.state == 'accepted' || task.state == 'doing')) {
      segments.add('免查房');
    }
    return segments.isEmpty ? null : segments.join(' · ');
  }

  static List<String> _plannedSegments(TodayTask task) {
    if (task.type != 'cleaning') {
      return const ['臨時需求'];
    }
    return task.estMin == null ? const [] : ['約 ${task.estMin} 分'];
  }

  static void _addIfPresent(List<String> segments, String? value) {
    if (value != null && value.isNotEmpty) {
      segments.add(value);
    }
  }
}
