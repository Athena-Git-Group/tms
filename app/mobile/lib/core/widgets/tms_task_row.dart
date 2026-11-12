import 'package:flutter/cupertino.dart';

import '../api_client.dart';
import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_category_square.dart';
import 'tms_home_cards.dart';
import 'tms_status_chip.dart';

/// 任務清單裡的一列：房號方塊、標題與狀態副標、狀態標籤。房務首頁「我的任務」
/// 與主管首頁「我名下的單」共用這一個元件，兩邊的卡片因此長得一樣。
///
/// 房號放在左邊的彩色方塊裡（`Main.dc.html:233`），主文只留任務標題：員工在
/// 清單上是先用位置找單，房號跟著顏色一起被掃到比夾在標題裡快。
///
/// 掛 id 的節點顯式帶「房號 標題」當 label，列裡的文字因此怎麼排版都不影響
/// 讀值；點整列進那間房的任務頁。
class TmsTaskRow extends StatelessWidget {
  const TmsTaskRow({
    super.key,
    required this.task,
    required this.showSeparator,
    required this.onTap,
  });

  final TodayTask task;
  final bool showSeparator;

  /// 點整列要做的事；房務首頁與主管首頁都是進那間房的任務頁。
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

  /// 列的第二行：這張單現在卡在哪裡，一句話講完（`Main.dc.html:4215-4224`）。
  ///
  /// 每個狀態想讓員工看到的東西不同：還沒開始的只需要知道要做多久，停住的要
  /// 知道為什麼停，做完的要看得到實際花了多久跟當初的預估差多少。所以這裡是
  /// 依狀態挑段落，而不是一套文字套用到所有狀態。
  ///
  /// 段落用「 · 」串起來，取不到值的段落整段不放進去（`RULINGS.md` R11）；全部
  /// 都取不到就回 null，列只有一行主文，不留一行空白。
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
        // 作廢的人與作廢原因契約還沒有欄位，這一列就只剩狀態標籤。
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

  /// 還沒開工的單講的是「要做多久」：打掃給預估分鐘，其餘的單標成臨時需求。
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
