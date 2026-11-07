import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_category_square.dart';
import '../../core/widgets/tms_progress_ring.dart';
import '../../core/widgets/tms_status_chip.dart';
import '../task/task_screen.dart';
import '../today/my_today_scope.dart';
import '../today/progress_value.dart';

/// 房務首頁：今日進度、手上或下一間要做的房，以及今天的任務清單。
///
/// 畫面上的每一個數字與任務卡的順序都直接取自 `GET /me/today`（由
/// [MyTodayScope] 取一次、整個畫面共用），App 不自己算也不自己排。
/// 算法的真相在後端，兩邊各算一次就會有兩種答案。
///
/// 整頁可捲動。員工手上的任務多到一屏放不下是常態，不能捲的版面會讓後面
/// 幾間房永遠沒辦法點到。
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'home.screen',
      child: MyTodayScope(
        child: CupertinoPageScaffold(
          backgroundColor: TmsColors.groupedBackground,
          child: SafeArea(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(
                TmsSpacing.screenEdge,
                TmsSpacing.md,
                TmsSpacing.screenEdge,
                TmsSpacing.xxl,
              ),
              children: const [
                // 連線狀態列固定在內容最上方，員工在首頁一眼就知道現在是離線
                // 還是連線不穩定，不必等某個操作失敗才發現。
                SystemStatusBar(),
                _GreetingRow(),
                SizedBox(height: TmsSpacing.lg),
                _TodayProgressCard(),
                SizedBox(height: TmsSpacing.lg),
                _FocusCard(),
                SizedBox(height: TmsSpacing.lg),
                _TodayTaskList(),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 問候語與今天的日期，右邊一顆圓形頭像。
///
/// 問候語依現在的時間分早上、下午與晚上三段。頭像目前沒有照片來源，先用
/// 品牌淡底的圓形佔住位置，讓這一列的左右配重跟設計一致。
class _GreetingRow extends StatelessWidget {
  const _GreetingRow();

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(_greeting(now.hour), style: TmsText.title1),
              const SizedBox(height: TmsSpacing.xs),
              Text(
                '${now.month} 月 ${now.day} 日',
                style: TmsText.subheadline
                    .copyWith(color: TmsColors.secondaryLabel),
              ),
            ],
          ),
        ),
        const SizedBox(width: TmsSpacing.md),
        Container(
          width: TmsSize.minTouchTarget,
          height: TmsSize.minTouchTarget,
          decoration: const BoxDecoration(
            color: TmsColors.brandAccentContainer,
            shape: BoxShape.circle,
          ),
        ),
      ],
    );
  }

  static String _greeting(int hour) {
    if (hour < 12) {
      return '早安';
    }
    if (hour < 18) {
      return '午安';
    }
    return '晚安';
  }
}

/// 今日進度卡：左邊一個打掃完成度的環，右邊三個數字。
///
/// 環的中央是「已清完／總數」，和右邊三格一樣直接顯示後端算好的值。載入中
/// 或載入失敗時整張卡不出現：首頁的錯誤提示還沒有畫面規格，在拿到規格之前
/// 不自行發明一種說法。
class _TodayProgressCard extends StatelessWidget {
  const _TodayProgressCard();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    final ratio = today.cleaningTotal == 0
        ? 0.0
        : today.cleaningDone / today.cleaningTotal;
    return TmsCard(
      style: TmsCardStyle.hero,
      padding: const EdgeInsets.all(TmsSpacing.lg2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              TmsProgressRing(
                progress: ratio,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ProgressValue(
                      id: 'home.progress.cleaned-ratio',
                      value: '${today.cleaningDone} / ${today.cleaningTotal}',
                      style: TmsText.displayMetric,
                    ),
                    const SizedBox(height: TmsSpacing.xxs),
                    Text(
                      '間房已清完',
                      style: TmsText.caption1.copyWith(
                        fontWeight: FontWeight.w400,
                        color: TmsColors.tertiaryLabel,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: TmsSpacing.lg),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _ProgressStat(
                      id: 'home.progress.inspected-count',
                      caption: '查房通過',
                      value: '${today.inspectedCount}',
                      valueColor: TmsColors.onSuccessContainer,
                    ),
                    const SizedBox(height: TmsSpacing.md),
                    _ProgressStat(
                      id: 'home.progress.ad-hoc-count',
                      caption: '臨時需求完成',
                      value: '${today.adHocCompleted}',
                      valueColor: TmsColors.brandAccent,
                    ),
                    const SizedBox(height: TmsSpacing.md),
                    _ProgressStat(
                      id: 'home.progress.remaining-minutes',
                      caption: '預估剩餘工時',
                      value: '${today.remainingMinutes} 分',
                      valueColor: TmsColors.label,
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: TmsSpacing.lg),
          _StreakRow(days: today.zeroReturnStreakDays),
        ],
      ),
    );
  }
}

/// 進度卡右欄的一格：數字在上、說明在下。
///
/// 說明文字放在掛 id 的節點外面，讀值的一方拿到的才是數字本身，不含說明。
class _ProgressStat extends StatelessWidget {
  const _ProgressStat({
    required this.id,
    required this.caption,
    required this.value,
    required this.valueColor,
  });

  final String id;
  final String caption;
  final String value;
  final Color valueColor;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ProgressValue(
          id: id,
          value: value,
          style: TmsText.title3.copyWith(color: valueColor),
        ),
        const SizedBox(height: TmsSpacing.xxs),
        Text(
          caption,
          style: TmsText.footnote.copyWith(color: TmsColors.secondaryLabel),
        ),
      ],
    );
  }
}

/// 連續零退回天數：一行說明加一個數字，接在進度卡的下半部。
class _StreakRow extends StatelessWidget {
  const _StreakRow({required this.days});

  final int days;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.md,
        vertical: TmsSpacing.sm2,
      ),
      decoration: BoxDecoration(
        color: TmsColors.successContainer,
        borderRadius: BorderRadius.circular(TmsRadius.card),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              '連續零退回天數',
              style: TmsText.footnote
                  .copyWith(color: TmsColors.onSuccessContainer),
            ),
          ),
          ProgressValue(
            id: 'home.streak-badge',
            value: '$days',
            style: TmsText.subheadlineEmphasized
                .copyWith(color: TmsColors.onSuccessContainer),
          ),
        ],
      ),
    );
  }
}

/// 首頁的主卡：手上有進行中的房就顯示那一間，否則顯示下一間要做的房。
///
/// 兩間都沒有時整張卡不出現，下方的任務清單自己會說明今天還有什麼。
class _FocusCard extends StatelessWidget {
  const _FocusCard();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    for (final task in today.todayTasks) {
      if (task.state == 'doing') {
        return _DoingCard(task: task);
      }
    }
    if (today.nextTasks.isEmpty) {
      return const SizedBox.shrink();
    }
    return _NextRoomCard(task: today.nextTasks.first);
  }
}

/// 進行中那一間：品牌色底、白字，房號是整張卡最大的東西。
///
/// 房號用等寬數字，計時每分鐘跳一次時整串數字不會左右抖動。
class _DoingCard extends StatelessWidget {
  const _DoingCard({required this.task});

  final TodayTask task;

  @override
  Widget build(BuildContext context) {
    return TmsCard(
      style: TmsCardStyle.hero,
      backgroundColor: TmsColors.brandPrimary,
      padding: const EdgeInsets.all(TmsSpacing.lg2),
      onTap: () => _openTask(context, task.taskId, task.roomNo),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: TmsSpacing.sm,
                height: TmsSpacing.sm,
                decoration: const BoxDecoration(
                  color: Color(0xFFFFB783),
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: TmsSpacing.xs2),
              Text(
                '進行中',
                style: TmsText.footnoteEmphasized
                    .copyWith(color: TmsColors.brandOnPrimary),
              ),
            ],
          ),
          const SizedBox(height: TmsSpacing.md2),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                task.roomNo,
                style: TmsText.displayRoomNo
                    .copyWith(color: TmsColors.brandOnPrimary),
              ),
              const SizedBox(width: TmsSpacing.md),
              Padding(
                padding: const EdgeInsets.only(bottom: TmsSpacing.xs),
                child: Text(
                  '${task.workedMinutes} 分',
                  style: TmsText.displayTimer
                      .copyWith(color: TmsColors.brandOnPrimary),
                ),
              ),
            ],
          ),
          const SizedBox(height: TmsSpacing.xs),
          Text(
            task.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TmsText.subheadline
                .copyWith(color: TmsColors.brandContainer),
          ),
          const SizedBox(height: TmsSpacing.md2),
          _CardButton(
            text: '繼續處理',
            background: TmsColors.brandOnPrimary,
            foreground: TmsColors.brandPrimary,
            height: 48,
            onPressed: () => _openTask(context, task.taskId, task.roomNo),
          ),
        ],
      ),
    );
  }
}

/// 下一間要做的房：白底卡，版型與進行中那一張對齊，只有配色與按鈕文字不同。
class _NextRoomCard extends StatelessWidget {
  const _NextRoomCard({required this.task});

  final NextTask task;

  @override
  Widget build(BuildContext context) {
    return TmsCard(
      style: TmsCardStyle.hero,
      padding: const EdgeInsets.all(TmsSpacing.lg2),
      onTap: () => _openTask(context, task.taskId, task.roomNo),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '下一間',
            style: TmsText.footnoteEmphasized
                .copyWith(color: TmsColors.brandPrimary),
          ),
          const SizedBox(height: TmsSpacing.md2),
          Text(task.roomNo, style: TmsText.displayRoomNo),
          const SizedBox(height: TmsSpacing.xs),
          Text(
            task.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TmsText.subheadline
                .copyWith(color: TmsColors.secondaryLabel),
          ),
          const SizedBox(height: TmsSpacing.md2),
          _CardButton(
            text: '開始這一間',
            background: TmsColors.brandPrimary,
            foreground: TmsColors.brandOnPrimary,
            height: 50,
            onPressed: () => _openTask(context, task.taskId, task.roomNo),
          ),
        ],
      ),
    );
  }
}

/// 主卡底部的滿寬膠囊按鈕。
class _CardButton extends StatelessWidget {
  const _CardButton({
    required this.text,
    required this.background,
    required this.foreground,
    required this.height,
    required this.onPressed,
  });

  final String text;
  final Color background;
  final Color foreground;
  final double height;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return CupertinoButton(
      padding: EdgeInsets.zero,
      borderRadius: BorderRadius.circular(TmsRadius.pill),
      color: background,
      minimumSize: Size(double.infinity, height),
      onPressed: onPressed,
      child: Text(text, style: TmsText.headline.copyWith(color: foreground)),
    );
  }
}

/// 今天的任務清單：白底群組，一間房一列。
///
/// 清單的順序照後端給的 todayTasks，App 不重排。用 todayTasks 而不是
/// nextTasks，是因為 nextTasks 只收待開始（accepted）的單，進行中、延後、
/// 中斷與今天已完成的單都不在裡面，員工就回不到自己手上那張。
class _TodayTaskList extends StatelessWidget {
  const _TodayTaskList();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null || today.todayTasks.isEmpty) {
      return const SizedBox.shrink();
    }
    final tasks = today.todayTasks;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: TmsSpacing.sm),
          child: Text('我的任務', style: TmsText.title3),
        ),
        TmsCard(
          padding: EdgeInsets.zero,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(TmsRadius.card),
            child: Column(
              children: [
                for (var i = 0; i < tasks.length; i++)
                  _TaskRow(
                    task: tasks[i],
                    showSeparator: i < tasks.length - 1,
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// 任務清單裡的一列：類別方塊、房號與標題、狀態標籤。
///
/// 掛 id 的節點顯式帶「房號 標題」當 label，列裡的文字因此怎麼排版都不影響
/// 讀值；點整列進那間房的任務頁。
class _TaskRow extends StatelessWidget {
  const _TaskRow({required this.task, required this.showSeparator});

  final TodayTask task;
  final bool showSeparator;

  @override
  Widget build(BuildContext context) {
    final subtitle = _subtitle(task);
    return TmsSemantics(
      id: 'home.task.${task.roomNo}.card',
      label: '${task.roomNo} ${task.title}',
      child: GestureDetector(
        onTap: () => _openTask(context, task.taskId, task.roomNo),
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
                TmsCategorySquare(type: task.type),
                const SizedBox(width: TmsSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        '${task.roomNo} ${task.title}',
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
                          style: TmsText.subheadline
                              .copyWith(color: TmsColors.secondaryLabel),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: TmsSpacing.sm),
                TmsStatusChip(
                  state: task.state,
                  type: task.type,
                  inspection: task.inspection,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 列的第二行：停住的單說明為什麼停住，其餘的單顯示預估時間。
  ///
  /// 兩者都沒有時回 null，列就只有一行主文，不留一行空白。
  static String? _subtitle(TodayTask task) {
    final holdReason = task.holdReason;
    if (holdReason != null && holdReason.isNotEmpty) {
      return holdReason;
    }
    final estMin = task.estMin;
    return estMin == null ? null : '預估 $estMin 分';
  }
}

void _openTask(BuildContext context, String taskId, String roomNo) {
  Navigator.of(context).push(
    CupertinoPageRoute<void>(
      builder: (_) => TaskScreen(roomNo: roomNo, taskId: taskId),
    ),
  );
}
