import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_category_square.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_home_cards.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_status_chip.dart';
import '../../core/widgets/tms_task_row.dart';
import '../task/task_screen.dart';
import '../today/my_today_scope.dart';
import '../today/progress_value.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'home.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        child: SafeArea(
          bottom: false,
          child: ListView(
            padding: EdgeInsets.fromLTRB(
              TmsSpacing.screenEdge,
              TmsSpacing.md,
              TmsSpacing.screenEdge,
              TmsSpacing.scrollPadBottom(MediaQuery.paddingOf(context).bottom),
            ),
            children: const [
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
    );
  }
}

class _GreetingRow extends StatelessWidget {
  const _GreetingRow();

  static const TextStyle _shiftStyle = TextStyle(
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    final name = ApiClient.instance.session?.name;
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text.rich(
                TextSpan(
                  text: _dateText(DateTime.now()),
                  children: [
                    if (today?.shift case final shift?
                        when shift.isNotEmpty) ...[
                      const TextSpan(text: ' · '),
                      TextSpan(text: _shiftText(shift), style: _shiftStyle),
                    ],
                    if (today?.floors case final floors? when floors.isNotEmpty)
                      TextSpan(text: ' · ${floors.join('、')} 樓'),
                  ],
                ),
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
              const SizedBox(height: TmsSpacing.xxs),
              Text(_greeting(today, name), style: TmsText.title1),
            ],
          ),
        ),
        const SizedBox(width: TmsSpacing.md),
        Container(
          width: TmsSize.minTouchTarget,
          height: TmsSize.minTouchTarget,
          alignment: Alignment.center,
          decoration: const BoxDecoration(
            color: TmsColors.brandAccentContainer,
            shape: BoxShape.circle,
          ),
          child: name == null || name.isEmpty
              ? null
              : Text(
                  name.substring(name.length - 1),
                  style: TmsText.headline.copyWith(
                    fontWeight: FontWeight.w800,
                    color: TmsColors.brandOnAccentContainer,
                  ),
                ),
        ),
      ],
    );
  }

  static String _dateText(DateTime now) {
    const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
    return '週${weekdays[now.weekday - 1]} ${now.month}/${now.day}';
  }

  static String _shiftText(String shift) {
    switch (shift) {
      case 'morning':
        return '早班';
      case 'afternoon':
        return '晚班';
      default:
        return shift;
    }
  }

  static String _greeting(MyToday? today, String? name) {
    final tasks = today?.todayTasks ?? const <TodayTask>[];
    final open = tasks.where((task) => _openStates.contains(task.state)).length;
    final done = tasks.where((task) => _doneStates.contains(task.state)).length;
    final greeting = open == 0 && done > 0 ? '辛苦了' : '早安';
    return name == null || name.isEmpty ? greeting : '$greeting，$name';
  }
}

const _openStates = {'accepted', 'doing', 'deferred', 'interrupted'};

const _doneStates = {'done', 'inspected'};

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
    return TmsProgressRingCard(
      progress: ratio,
      ringCenter: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ProgressValue(
            id: 'home.progress.cleaned-ratio',
            value: '${today.cleaningDone}',
            style: TmsText.displayMetric,
            unit: '/ ${today.cleaningTotal}',
            unitStyle: TmsText.callout.copyWith(
              fontWeight: FontWeight.w600,
              color: TmsColors.tertiaryLabel,
            ),
          ),
          const SizedBox(height: TmsSpacing.xs),
          Text(
            '間房已清完',
            style: TmsText.caption1.copyWith(
              fontWeight: FontWeight.w400,
              color: TmsColors.secondaryLabel,
            ),
          ),
        ],
      ),
      details: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _ProgressStat(
            id: 'home.progress.remaining-minutes',
            caption: '預估剩餘工時',
            value: '${today.remainingMinutes}',
            valueStyle: TmsText.title3,
            unit: '分鐘',
          ),
          const SizedBox(height: TmsSpacing.md),
          Row(
            children: [
              _ProgressStat(
                id: 'home.progress.inspected-count',
                caption: '查房通過',
                value: '${today.inspectedCount}',
                valueStyle: TmsText.displayStat.copyWith(
                  color: TmsColors.onSuccessContainer,
                ),
              ),
              const SizedBox(width: TmsSpacing.lg),
              _ProgressStat(
                id: 'home.progress.ad-hoc-count',
                caption: '臨時需求完成',
                value: '${today.adHocCompleted} 單',
                valueStyle: TmsText.displayStat.copyWith(
                  color: TmsColors.brandAccent,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ProgressStat extends StatelessWidget {
  const _ProgressStat({
    required this.id,
    required this.caption,
    required this.value,
    required this.valueStyle,
    this.unit,
  });

  final String id;
  final String caption;
  final String value;
  final TextStyle valueStyle;

  final String? unit;

  @override
  Widget build(BuildContext context) {
    return TmsStatBlock(
      value: ProgressValue(
        id: id,
        value: value,
        style: valueStyle,
        unit: unit,
        unitStyle: TmsText.footnoteMedium,
      ),
      caption: caption,
    );
  }
}

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
    if (today.nextTasks.isNotEmpty) {
      return _NextRoomCard(
        task: today.nextTasks.first,
        estMin: _estMinOf(today, today.nextTasks.first.taskId),
      );
    }
    return _EmptyCard(today: today);
  }

  static int? _estMinOf(MyToday today, String taskId) {
    for (final task in today.todayTasks) {
      if (task.taskId == taskId) {
        return task.estMin;
      }
    }
    return null;
  }
}

class _EmptyCard extends StatelessWidget {
  const _EmptyCard({required this.today});

  final MyToday today;

  @override
  Widget build(BuildContext context) {
    final tasks = today.todayTasks;
    return TmsContentUnavailable.inCard(
      id: 'home.empty-state',
      copy: TmsEmptyStates.housekeepingHome(
        snoozedCount: tasks
            .where((task) => _openStates.contains(task.state))
            .length,
        doneCount: tasks
            .where((task) => _doneStates.contains(task.state))
            .length,
        poolHasItems: today.poolBadgeCount > 0,
      ),
    );
  }
}

class _DoingCard extends StatelessWidget {
  const _DoingCard({required this.task});

  final TodayTask task;

  static const double _titleDrop = 5;

  @override
  Widget build(BuildContext context) {
    return TmsHeroCard(
      onDark: true,
      eyebrow: '進行中 · ${tmsCategoryAppearance(task.type).label}',
      onTap: () => _openTask(context, task.taskId, task.roomNo),
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              task.roomNo,
              style: TmsText.displayRoomNo.copyWith(
                color: TmsColors.brandOnPrimary,
              ),
            ),
            const SizedBox(width: TmsSpacing.md),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(bottom: _titleDrop),
                child: Text(
                  task.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.brandContainer,
                  ),
                ),
              ),
            ),
            const SizedBox(width: TmsSpacing.md),
            TmsHeroTimer(
              workedMinutes: task.workedMinutes,
              estMin: task.estMin,
            ),
          ],
        ),
        TmsPillButton(
          label: '繼續處理',
          style: TmsPillButtonStyle.inverse,
          height: 48,
          onPressed: () => _openTask(context, task.taskId, task.roomNo),
        ),
      ],
    );
  }
}

class _NextRoomCard extends StatelessWidget {
  const _NextRoomCard({required this.task, this.estMin});

  final NextTask task;

  final int? estMin;

  static const double _playIconSize = 18;

  @override
  Widget build(BuildContext context) {
    final estMin = this.estMin;
    final buttonLabel = '開始打掃 ${task.roomNo}';
    return TmsHeroCard(
      eyebrow: '下一間',
      ornamentIcon: CupertinoIcons.bed_double,
      onTap: () => _openTask(context, task.taskId, task.roomNo),
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(task.roomNo, style: TmsText.displayRoomNo),
            const SizedBox(height: TmsSpacing.xs),
            Text(
              estMin == null ? task.title : '${task.title} · 預估 $estMin 分',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TmsText.subheadline,
            ),
          ],
        ),
        if (task.redoCount > 0)
          TmsStatusChip.of(
            TmsStatusAppearance(
              text: '查房退回 · 第 ${task.redoCount} 次重做',
              background: TmsColors.errorContainer,
              foreground: TmsColors.onErrorContainer,
            ),
          ),
        TmsPillButton(
          label: buttonLabel,
          height: 50,
          onPressed: () => _openTask(context, task.taskId, task.roomNo),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                CupertinoIcons.play_fill,
                size: _playIconSize,
                color: TmsColors.brandOnPrimary,
              ),
              const SizedBox(width: TmsSpacing.sm),
              Flexible(
                child: Text(
                  buttonLabel,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TmsText.headline.copyWith(
                    color: TmsColors.brandOnPrimary,
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

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
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.xs,
            0,
            TmsSpacing.xs,
            TmsSpacing.sm,
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('我的任務', style: TmsText.title3),
              Text(
                '依樓層排序',
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
            ],
          ),
        ),
        TmsCard(
          padding: EdgeInsets.zero,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(TmsRadius.card),
            child: Column(
              children: [
                for (var i = 0; i < tasks.length; i++)
                  TmsTaskRow(
                    task: tasks[i],
                    showSeparator: i < tasks.length - 1,
                    onTap: () =>
                        _openTask(context, tasks[i].taskId, tasks[i].roomNo),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

void _openTask(BuildContext context, String taskId, String roomNo) {
  Navigator.of(context).push(
    CupertinoPageRoute<void>(
      builder: (_) => TaskScreen(roomNo: roomNo, taskId: taskId),
    ),
  );
}
