import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_category_square.dart';
import '../../core/widgets/tms_content_unavailable.dart';
import '../../core/widgets/tms_fab.dart';
import '../../core/widgets/tms_greeting_row.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_home_cards.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_status_chip.dart';
import '../../core/widgets/tms_tip_popover.dart';
import '../pool/pool_widgets.dart';
import '../shell/main_shell.dart';
import '../ticket/repair_labels.dart';
import '../ticket/repair_ticket_form_sheet.dart';
import '../ticket/ticket_screen.dart';
import '../today/my_today_scope.dart';
import '../today/progress_value.dart';

/// 工務首頁：問候列、今日進度三格（手上／今天已完修／急件），以及我手上的
/// 工務單。
///
/// 三格的值直接取自 `GET /me/today` 的 engineeringSummary（由外殼上的
/// [MyTodayScope] 取一次、整個畫面共用，與房務首頁同一條取用路徑），App 不自己數。
///
/// 手上的單同樣來自 engineeringSummary：後端的 tickets 已經收齊本人手上
/// （已接單／維修中）與今天完修的單，也已經排好（急件在前、未結案在已完修
/// 之前），所以這個畫面依序呈現就好，不自己查、不自己排。
///
/// 版型與房務首頁同一套：問候列、進度環卡、主卡、白底群組清單四段，共用
/// [TmsGreetingRow]、[TmsProgressRingCard] 與 [TmsHeroCard]。兩個首頁長得
/// 一樣，是因為同一位主管要同時看懂兩邊，而工務與房務的員工也可能互相代班。
///
/// 畫面最上面沒有導覽列（`Main.dc.html:856-863`）：第一塊就是問候列，標題由
/// 問候語本身擔任，再加一條寫著「工務首頁」的導覽列等於把同一件事說兩次。
/// 開工單的浮動按鈕只掛在這一頁，待認領不放（`Main.dc.html:4051`）：待認領是
/// 員工去接別人開的單的地方，在那裡擺一顆開單鈕，他會分不清自己按下去是要接
/// 單還是要開一張新的。
class EngHomeScreen extends StatefulWidget {
  const EngHomeScreen({super.key});

  @override
  State<EngHomeScreen> createState() => _EngHomeScreenState();
}

class _EngHomeScreenState extends State<EngHomeScreen> {
  /// 員工已經看過開工單的教學氣泡了。
  ///
  /// 按 ×、按那顆按鈕、離開首頁，三者任一發生就算看過（`Main.dc.html:4052`、
  /// `Main.dc.html:4046`）。這是畫面自己的狀態而不是元件的狀態，因為後兩個
  /// 條件元件看不到。
  bool _tipSeen = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MainShellTab.indexOf(context) != MainShellTab.homeIndex) {
      _tipSeen = true;
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.paddingOf(context).bottom;
    final fabBottom = TmsFab.bottomAboveTabBar(bottomInset);
    return TmsSemantics(
      id: 'engHome.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.groupedBackground,
        // 氣泡下緣的箭頭尖凸出卡片外面，預設的裁切會把它削掉。
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            // 底部不讓 SafeArea 收邊：內容要從浮動分頁列底下捲過去，讓開分頁
            // 列的那段留白由捲動內容自己帶。
            SafeArea(
              bottom: false,
              child: ListView(
                padding: EdgeInsets.fromLTRB(
                  TmsSpacing.screenEdge,
                  TmsSpacing.md,
                  TmsSpacing.screenEdge,
                  // 浮動按鈕壓在最後一張卡上面，所以分頁列的留白之外還要再讓
                  // 開按鈕（`Main.dc.html:4448`）。
                  TmsSpacing.scrollPadBottom(bottomInset) +
                      TmsFab.scrollPadClearance,
                ),
                children: const [
                  // 連線狀態列固定在內容最上方，員工一眼就知道現在是離線還是
                  // 連線不穩定，不必等某個操作失敗才發現。
                  SystemStatusBar(),
                  _LoadFailureNotice(),
                  _GreetingRow(),
                  SizedBox(height: TmsSpacing.lg),
                  _EngineeringProgress(),
                  SizedBox(height: TmsSpacing.lg),
                  _FocusCard(),
                  SizedBox(height: TmsSpacing.lg),
                  _InHandTickets(),
                ],
              ),
            ),
            Positioned(
              right: TmsFab.sideInset,
              bottom: fabBottom,
              child: TmsSemantics(
                id: 'engHome.create-ticket-fab',
                child: TmsFab(
                  glyph: TmsFabGlyph.plus,
                  semanticsLabel: '開工單',
                  onPressed: () {
                    setState(() => _tipSeen = true);
                    RepairTicketFormSheet.show(context);
                  },
                ),
              ),
            ),
            if (_tipVisible)
              Positioned(
                right: TmsFab.sideInset,
                bottom: TmsTipPopover.bottomAbove(fabBottom, TmsFab.diameter),
                child: TmsTipPopover(
                  title: '在這裡開工單',
                  message: '巡檢時發現問題，拍照就能直接開單。',
                  width: _tipWidth,
                  onDismiss: () => setState(() => _tipSeen = true),
                ),
              ),
          ],
        ),
      ),
    );
  }

  /// 氣泡此刻該不該出現。
  ///
  /// 畫面上已經有別的東西蓋上來（確認對話框、開工單面板、推到上面的詳情頁）
  /// 時這一顆讓位，否則兩層提示會同時要員工的注意力。這些東西都是推在這一頁
  /// 上面的路由，所以問這一頁還是不是最上層那個就知道（`Main.dc.html:4052`
  /// 的 `!s.eDetail && !s.eSheet && !s.alert`）。
  bool get _tipVisible {
    if (_tipSeen) {
      return false;
    }
    return ModalRoute.of(context)?.isCurrent ?? true;
  }

  /// `Main.dc.html:1037` — 開工單氣泡的卡片寬度 260
  static const double _tipWidth = 260;
}

/// 今日進度載入失敗時的一行提示。
class _LoadFailureNotice extends StatelessWidget {
  const _LoadFailureNotice();

  @override
  Widget build(BuildContext context) {
    if (MyTodayScope.errorOf(context) == null) {
      return const SizedBox.shrink();
    }
    return const Padding(
      padding: EdgeInsets.only(bottom: TmsSpacing.lg),
      child: Text('載入今日進度失敗，請稍後再試。'),
    );
  }
}

/// 工務的問候列（`Main.dc.html:857-863`）。
///
/// 日期列最後一段寫死「工務」：這一段在房務首頁是負責樓層，工務不分樓層，
/// 原型在這裡放的是部門名。問候語依手上還有沒有未結案的單分兩種說法
/// （`Main.dc.html:4079`），不看時鐘：「辛苦了」是一天收尾的回饋，在只剩
/// 已完修的單時才成立，而這件事跟現在幾點沒有關係。
class _GreetingRow extends StatelessWidget {
  const _GreetingRow();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    final summary = today?.engineeringSummary ?? EngineeringSummary.none;
    // 今日進度還沒回來時當成還有單在手上：問候語是進場第一眼看到的字，先說
    // 「辛苦了」再因為資料回來改口會讓員工以為自己看錯。
    final settled =
        today != null && summary.openCount == 0 && summary.completedToday > 0;
    return TmsGreetingRow(
      greeting: settled ? '辛苦了' : '早安',
      name: ApiClient.instance.session?.name,
      shift: today?.shift,
      trailing: '工務',
    );
  }
}

/// 今日進度卡：左邊一個完修完成度的環，右邊手上張數與急件數。
class _EngineeringProgress extends StatelessWidget {
  const _EngineeringProgress();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    // 房務身分拿到的回應沒有 engineeringSummary，三格當成 0 張畫出來，
    // 而不是整塊消失：這個畫面的主要內容就是這三格，沒有它們等於空白一片。
    final summary = today.engineeringSummary ?? EngineeringSummary.none;
    // 環的分母優先用後端排出來的「今天預計要修幾張」（plannedToday）：那是
    // 排程說了算的目標量，一整天不會因為新單進來就變動，環的進度才有意義。
    //
    // 排不出預計量時後端給 null，這時退回「今天已完修＋手上還沒結」相加。
    // 這條退路是畫面自己湊的數字、會隨著新單進來往上跳（完修一張、又進一張
    // 時環甚至會倒退），但它是這份回應唯一還能支撐的分母——分母拿不到就不畫
    // 環的話，這張卡的主要內容會整塊消失，比一個會動的分母更糟。使用者已經
    // 裁決接受這個現況，所以保留退路、而不是等後端補齊才畫。
    final denominator =
        summary.plannedToday ?? (summary.completedToday + summary.openCount);
    final ratio = denominator == 0 ? 0.0 : summary.completedToday / denominator;
    return TmsProgressRingCard(
      progress: ratio,
      ringCenter: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              ProgressValue(
                id: 'engHome.progress.repaired-count',
                value: '${summary.completedToday}',
                style: TmsText.displayMetric,
              ),
              Text(
                ' / $denominator',
                style: TmsText.callout.copyWith(
                  fontWeight: FontWeight.w600,
                  color: TmsColors.tertiaryLabel,
                ),
              ),
            ],
          ),
          const SizedBox(height: TmsSpacing.xs),
          Text(
            '張已完修',
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
          // 「手上還有」獨佔一行、字級也比下排大（`Main.dc.html:879-890`）：
          // 它回答「今天還剩多少」，是這一欄裡工務最常看的一格。單位字放在
          // 掛 id 的節點外面，讀這個數字的一方拿到的才是張數本身。
          TmsStatBlock(
            value: Row(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                ProgressValue(
                  id: 'engHome.progress.in-hand-count',
                  value: '${summary.openCount}',
                  style: TmsText.title3,
                ),
                const Text(' 張工單', style: TmsText.footnoteMedium),
              ],
            ),
            caption: '手上還有',
          ),
          const SizedBox(height: TmsSpacing.md),
          _UrgentAndMaintenance(summary: summary),
        ],
      ),
    );
  }
}

/// 今日進度卡右欄下半：急件，以及有保養資料時並排的今日保養。
///
/// 兩格並排而不是上下堆，是照原型：急件與今日保養都是「今天還剩什麼」的同一
/// 層資訊，上面的「手上還有」才是主數字。
class _UrgentAndMaintenance extends StatelessWidget {
  const _UrgentAndMaintenance({required this.summary});

  final EngineeringSummary summary;

  @override
  Widget build(BuildContext context) {
    final urgent = TmsStatBlock(
      value: ProgressValue(
        id: 'engHome.progress.urgent-count',
        value: '${summary.urgentCount}',
        style: TmsText.displayStat.copyWith(
          // 急件是唯一要把人拉過去的數字，所以只有它會變色；是 0 的時候
          // 沒有人要被拉過去，跟著其餘數字用一般文字色。
          color: summary.urgentCount > 0
              ? TmsColors.onErrorContainer
              : TmsColors.label,
        ),
      ),
      caption: '急件',
    );
    final done = summary.maintenanceDone;
    final total = summary.maintenanceTotal;
    // 兩個欄位同時有值才畫這一格。只有一邊有值的「3 / —」看不出今天保養排了
    // 幾張，不如整格不畫；後端沒有保養維度時兩邊都是 null，也走這條路。
    if (done == null || total == null) {
      return urgent;
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        urgent,
        const SizedBox(width: TmsSpacing.lg),
        TmsStatBlock(
          // 這一格刻意不掛 accessibility id：沒有斷言在讀保養張數，而新增 id
          // 會動到驗收基準。要驗收時再由 ui-plan 統一編號。
          value: Text(
            '$done / $total',
            style: TmsText.displayStat.copyWith(color: TmsColors.brandAccent),
          ),
          caption: '今日保養',
        ),
      ],
    );
  }
}

/// 工務首頁的主卡：手上有維修中的單就顯示那一張，沒有就顯示待開始的第一張，
/// 兩者都沒有時這個位置換成空狀態卡。
///
/// 主卡的位置一定有東西（`Main.dc.html:936`）。讓它消失的話，手上沒事的那天
/// 首頁只剩一張全是 0 的進度卡，工務看不出來是沒派到單、還是 App 壞了。
///
/// 這張卡刻意不掛 accessibility id：同一張單在下方清單裡已經有一個掛 id 的
/// 節點，這裡再掛一次會讓同一個 id 在畫面上出現兩個節點。
class _FocusCard extends StatelessWidget {
  const _FocusCard();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    final summary = today.engineeringSummary ?? EngineeringSummary.none;
    for (final ticket in summary.tickets) {
      if (ticket.state == 'doing') {
        return _TicketHeroCard(ticket: ticket, doing: true);
      }
    }
    for (final ticket in summary.tickets) {
      if (ticket.state == 'accepted') {
        return _TicketHeroCard(ticket: ticket, doing: false);
      }
    }
    return TmsContentUnavailable.inCard(
      id: 'engHome.empty-state',
      copy: TmsEmptyStates.engineeringHome(
        doneCount: summary.completedToday,
        poolHasItems: today.poolBadgeCount > 0,
      ),
    );
  }
}

/// 主卡本體：維修中那一張是品牌色底白字，下一張是白底。
class _TicketHeroCard extends StatelessWidget {
  const _TicketHeroCard({required this.ticket, required this.doing});

  final EngineeringTicket ticket;
  final bool doing;

  /// 標題那一段的下沉量（`Main.dc.html:907`）。15pt 的字跟 44pt 的地點底部
  /// 對齊時會貼著基線，壓低 5 讓它落在地點的字腰上。
  static const double _titleDrop = 5;

  /// 下一張那張卡的文字欄要讓開右上角那塊裝飾方塊（`Main.dc.html:920`）。
  static const double _ornamentGutter = 80;

  /// 主按鈕左邊那顆播放三角的邊長（`Main.dc.html:930`）。
  static const double _playIconSize = 18;

  @override
  Widget build(BuildContext context) {
    final place = ticket.roomNo ?? '公共區域';
    final foreground = doing ? TmsColors.brandOnPrimary : TmsColors.label;
    // 地點是房號時用 44pt，是區域名時降到 30pt（`Main.dc.html:906`、`921`）。
    final placeStyle =
        (ticket.roomNo == null
                ? TmsText.displayAreaName
                : TmsText.displayRoomNo)
            .copyWith(color: foreground);
    return doing
        ? _buildDoing(context, place, placeStyle)
        : _buildNext(context, place, placeStyle);
  }

  /// 維修中：地點、標題、計時同一列並且底部對齊（`Main.dc.html:905-908`），
  /// 三者講的是同一張單的「哪裡、做什麼、做多久」，拆成兩列會讓計時看起來像
  /// 另一段資訊。
  ///
  /// 眉標在原型是「維修中 · {類別}」，但 `EngineeringTicket` 沒有類別欄位，
  /// 所以類別那一段整段不出現。
  Widget _buildDoing(BuildContext context, String place, TextStyle placeStyle) {
    // 卡右側的計時告訴工務「這張單你已經做了多久」。後端取不到這個數字時給
    // null，畫面就不畫這一段——把 null 畫成「0 分」，工務會讀成這張單剛剛才
    // 開工。
    final workedMinutes = ticket.workedMinutes;
    return TmsHeroCard(
      onDark: true,
      eyebrow: '維修中',
      onTap: () => _openTicket(context, ticket.taskId),
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              place,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: placeStyle,
            ),
            const SizedBox(width: TmsSpacing.md),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(bottom: _titleDrop),
                child: Text(
                  ticket.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.brandContainer,
                  ),
                ),
              ),
            ),
            if (workedMinutes != null) ...[
              const SizedBox(width: TmsSpacing.md),
              Text(
                '$workedMinutes 分',
                style: TmsText.displayTimerCompact.copyWith(
                  color: TmsColors.brandOnPrimary,
                ),
              ),
            ],
          ],
        ),
        TmsPillButton(
          label: '繼續處理',
          style: TmsPillButtonStyle.inverse,
          height: 48,
          onPressed: () => _openTicket(context, ticket.taskId),
        ),
      ],
    );
  }

  /// 下一張：白底卡，右上角一塊放扳手的旋轉方塊，版型與維修中那張對齊。
  ///
  /// 眉標寫「下一張（急件優先）」（`Main.dc.html:919`），因為這一格挑的不是
  /// 清單上的第一張，而是急件優先排過之後的第一張；只寫「下一張」，工務會
  /// 以為自己可以照清單順序挑。
  Widget _buildNext(BuildContext context, String place, TextStyle placeStyle) {
    final buttonLabel = '開始維修 $place';
    final source = ticket.source;
    final sourceText = source == null
        ? null
        : engineeringPoolSourceLabels[source];
    return TmsHeroCard(
      eyebrow: '下一張（急件優先）',
      ornamentIcon: CupertinoIcons.wrench,
      onTap: () => _openTicket(context, ticket.taskId),
      children: [
        Padding(
          padding: const EdgeInsets.only(right: _ornamentGutter),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                place,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: placeStyle,
              ),
              const SizedBox(height: TmsSpacing.xs),
              Text(
                sourceText == null
                    ? ticket.title
                    : '${ticket.title} · $sourceText',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TmsText.subheadline,
              ),
            ],
          ),
        ),
        // 急件標籤在主卡上不跟狀態併成一顆：清單列併是因為一列只放得下一顆
        // 標籤（`Main.dc.html:3835`），而主卡本身就是「接下來要做這張」，
        // 狀態不必再說一次，這一顆講的只有「它是急件」（`Main.dc.html:924`）。
        if (ticket.isUrgent)
          const TmsStatusChip.of(
            TmsStatusAppearance(
              text: '急件',
              background: TmsColors.errorContainer,
              foreground: TmsColors.onErrorContainer,
            ),
          ),
        TmsPillButton(
          label: buttonLabel,
          height: 50,
          onPressed: () => _openTicket(context, ticket.taskId),
          // 按鈕上帶地點，工務按下去之前最後一次確認開的是哪一張
          // （`Main.dc.html:929-931`）。
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

/// 我手上的工務單清單。
///
/// 內容全部來自今日進度的 engineeringSummary.tickets，與上面三格同一份回應，
/// 不必逐張再打工務單詳情：清單與三格因此一定講同一件事，也不會有一半的單
/// 載得出來、一半載不出來。
class _InHandTickets extends StatelessWidget {
  const _InHandTickets();

  @override
  Widget build(BuildContext context) {
    final today = MyTodayScope.todayOf(context);
    if (today == null) {
      return const SizedBox.shrink();
    }
    final tickets =
        (today.engineeringSummary ?? EngineeringSummary.none).tickets;
    if (tickets.isEmpty) {
      return const SizedBox.shrink();
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          // 標題列比底下的群組卡往內縮 4（`Main.dc.html:951`），讓標題的視覺
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
              // 排序規則由後端定，寫在標題旁邊是為了讓工務知道「找不到某一張」
              // 要往哪個方向捲，而不是以為清單漏了那張單。
              Text(
                '依緊急程度排序',
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
            ],
          ),
        ),
        TmsGroupedSection(
          children: [
            for (final ticket in tickets) _InHandTicketRow(ticket: ticket),
          ],
        ),
      ],
    );
  }
}

/// 手上的一張單：地點方塊、標題與來源，右邊狀態標籤，點一列進詳情。
class _InHandTicketRow extends StatelessWidget {
  const _InHandTicketRow({required this.ticket});

  final EngineeringTicket ticket;

  @override
  Widget build(BuildContext context) {
    // 契約把 ticketNo 列為必填，但實際回應可能是 null；id 退回用 taskId，
    // 讓單卡至少還是個可指認的節點，而不是整串變成同一個 null。
    final ticketNo = ticket.ticketNo ?? ticket.taskId;
    final place = ticket.roomNo ?? '公共區域';
    final status = repairStatusAppearance(
      state: ticket.state,
      isUrgent: ticket.isUrgent,
    );
    final source = ticket.source;
    final subtitle = source == null
        ? null
        : engineeringPoolSourceLabels[source];
    return TmsSemantics(
      id: 'engHome.in-hand.$ticketNo.card',
      label: '$ticketNo $place ${status.text}',
      child: GestureDetector(
        onTap: () => _openTicket(context, ticket.taskId),
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
                  // 「公共區域」四個字用 16pt 會撐破 48pt 的方塊。
                  fontSize: ticket.roomNo == null ? 13 : 16,
                ),
                const SizedBox(width: TmsSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // 列主文是工單標題（`Main.dc.html:959`）：地點已經在左邊
                      // 的方塊上，單號工務平常不用來認單，兩者都串進主文會把
                      // 標題擠到被截斷。
                      Text(
                        ticket.title,
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

void _openTicket(BuildContext context, String taskId) {
  Navigator.of(context).push(
    CupertinoPageRoute<void>(
      builder: (_) =>
          TicketScreen(view: TicketView.engineering, taskId: taskId),
    ),
  );
}
