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

/// 房務首頁：今日進度、手上或下一間要做的房，以及今天的任務清單。
///
/// 畫面上的每一個數字與任務卡的順序都直接取自 `GET /me/today`（由外殼上的
/// [MyTodayScope] 取一次、整個外殼共用），App 不自己算也不自己排。
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
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        // 底部不讓 SafeArea 收邊：內容要從浮動分頁列底下捲過去，讓開分頁列
        // 的那段留白由捲動內容自己帶。
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
    );
  }
}

/// 日期、班別與負責樓層在上，問候語在下，右邊一顆圓形頭像。
///
/// 日期列在上、問候語在下（`Main.dc.html:128-129`）：員工一早開 App 第一眼要
/// 確認的是「今天是哪一班、顧哪幾層」，這兩件事錯了後面整天的任務都對不上，
/// 問候語則是讀完這一行之後的招呼。班別與樓層來自 `GET /me/today`，後端還沒
/// 給、或今天根本沒排班時是 null，該段就整段不出現。
///
/// 問候語依手上還有沒有未完成的單分兩種說法（`Main.dc.html:5108`），不看時鐘：
/// 「辛苦了」是一天收尾的回饋，在只剩已完成的單時才成立，而這件事跟現在幾點
/// 沒有關係——早上八點把手上的單全做完，同樣該收到「辛苦了」。
class _GreetingRow extends StatelessWidget {
  const _GreetingRow();

  /// 日期列裡班別那一段的字重與顏色（`Main.dc.html:128`）。整列是 15pt 灰字，
  /// 只有班別加粗轉黑，員工掃一眼就能從日期列裡先讀到班別。
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

  /// `週一 10/19`（`Main.dc.html:128`）。星期在前，員工排班是按星期記的。
  static String _dateText(DateTime now) {
    const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
    return '週${weekdays[now.weekday - 1]} ${now.month}/${now.day}';
  }

  /// 班別的白話說法。
  ///
  /// 後端回的是租戶設定 `shift_times` 的鍵（`morning`／`afternoon`），不是中文
  /// 名稱——系統裡沒有任何地方存班別的中文說法。翻成白話在這裡做，跟來源
  /// （`住客掃碼`）、工務單類別（`冷氣`）走同一套做法：enum 值不露在畫面上。
  /// 認不得的鍵原樣顯示，不猜一個說法。
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

  /// 手上還有沒做完的單就是「早安」，全部做完才換成「辛苦了」。
  ///
  /// 今日進度還沒回來時當成還有單在手上：問候語是進場第一眼看到的字，先說
  /// 「辛苦了」再因為資料回來改口會讓員工以為自己看錯。登入身分沒有姓名時
  /// 只留招呼，不補一個稱呼。
  static String _greeting(MyToday? today, String? name) {
    final tasks = today?.todayTasks ?? const <TodayTask>[];
    final open = tasks.where((task) => _openStates.contains(task.state)).length;
    final done = tasks.where((task) => _doneStates.contains(task.state)).length;
    final greeting = open == 0 && done > 0 ? '辛苦了' : '早安';
    return name == null || name.isEmpty ? greeting : '$greeting，$name';
  }
}

/// 還在手上、今天得做完的狀態（`Main.dc.html:5108`）。
const _openStates = {'accepted', 'doing', 'deferred', 'interrupted'};

/// 今天已經結束的狀態。
const _doneStates = {'done', 'inspected'};

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
            // 分母降到 16pt 並轉灰（`Main.dc.html:143`）：分子是今天長出來的
            // 數字，分母一整天不變，兩個同字級會讓人要讀兩次才知道進度。
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
          // 「預估剩餘工時」獨佔一行、字級也比下排大（`Main.dc.html:148-162`）：
          // 它回答「今天還要多久」，是這一欄裡員工最常看的一格。
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

/// 進度卡右欄的一格：數字在上、說明在下。
///
/// 說明文字放在掛 id 的節點外面，讀值的一方拿到的才是數字本身，不含說明。
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

  /// 數字後面那個降一級的單位字；null 代表這一格只有數字。
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

/// 首頁的主卡：手上有進行中的房就顯示那一間，沒有就顯示下一間要做的房，
/// 兩者都沒有時這個位置換成空狀態卡。
///
/// 主卡的位置一定有東西（`Main.dc.html:213`）。讓它消失的話，手上沒事的那天
/// 首頁只剩一張全是 0 的進度卡，員工看不出來是沒派到單、還是 App 壞了。
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

  /// 下一間那張卡要顯示的預估分鐘。
  ///
  /// `NextTask` 本身沒有這一欄，但同一張單也在 `todayTasks` 裡，那裡有
  /// `estMin`。兩份講的是同一張單，所以照 taskId 取回來的就是這張單的預估，
  /// 不是另算一個數字。對不到就回 null，那一段文字整段不出現。
  static int? _estMinOf(MyToday today, String taskId) {
    for (final task in today.todayTasks) {
      if (task.taskId == taskId) {
        return task.estMin;
      }
    }
    return null;
  }
}

/// 手上沒有進行中、也沒有下一間時主卡的位置（`Main.dc.html:213-219`）。
///
/// 三種說法分別對應「只剩延後的單」「今天都做完了」「真的沒事」，由
/// [TmsEmptyStates.housekeepingHome] 依當下的數字挑。卡上不放按鈕，下一步交給
/// 分頁列的「待認領」。
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

/// 進行中那一間：品牌色底、白字，房號是整張卡最大的東西。
///
/// 房號、任務名與計時同一列並且底部對齊（`Main.dc.html:174-183`）：三者講的是
/// 同一張單的「哪一間、做什麼、做多久」，拆成兩列會讓計時看起來像另一段資訊。
class _DoingCard extends StatelessWidget {
  const _DoingCard({required this.task});

  final TodayTask task;

  /// 任務名那一段的下沉量（`Main.dc.html:176`）。15pt 的字跟 44pt 的房號底部
  /// 對齊時會貼著基線，壓低 5 讓它落在房號的字腰上。
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

/// 下一間要做的房：白底卡，版型與進行中那一張對齊，只有配色與按鈕文字不同。
class _NextRoomCard extends StatelessWidget {
  const _NextRoomCard({required this.task, this.estMin});

  final NextTask task;

  /// 這張單的預估分鐘；沒有就不顯示副標那一段。
  final int? estMin;

  /// 主按鈕左邊那顆播放三角的邊長（`Main.dc.html:207`）。
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
          // 按鈕上帶房號，員工按下去之前最後一次確認開的是哪一間
          // （`Main.dc.html:206-208`）。
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
          // 標題列比底下的群組卡往內縮 4（`Main.dc.html:226`），讓標題的視覺
          // 左緣對上卡內那一欄文字，而不是對上卡的邊。
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
              // 排序規則由後端定，寫在標題旁邊是為了讓員工知道「找不到某一間」
              // 要往哪個方向捲，而不是以為清單漏了那張單。
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
