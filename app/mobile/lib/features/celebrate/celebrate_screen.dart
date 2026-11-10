import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_motion.dart';

/// 完成慶祝畫面：員工把一間房做完之後看到的回饋。上面寫剛做完的是哪一間、
/// 這一間接下來會怎樣、這次花了多久、今天做到第幾單，以及接下來往哪裡走。
///
/// 版面是一張浮在半透明遮罩上的對話卡，不是鋪滿整個螢幕的一頁內容。員工看完
/// 這張卡就要往下一間走，整頁式的畫面會讓人以為自己進了另一個流程、還得找路
/// 回去。
///
/// 卡上最強調的動作是接力（下一間／去待認領），回到首頁是底部的文字按鈕。
/// 兩者的視覺層級不能對調：做完一間之後絕大多數時候要接著做下一間，把回首頁
/// 做成唯一一顆實心鍵等於把少數情況擺在最前面。
///
/// 沒有資料的段落整段不出現，不填假值也不留佔位字。畫殼填假值會讓驗收分不出
/// 「做好了」和「資料還沒來」。
///
/// **accessibility id 詞彙表**（App 端與 `app/bdd` 的 step definitions 共用同
/// 一組名稱，兩邊改名要一起改）：
///
/// - `celebrate.screen`：整張覆蓋層。遮罩底下那一頁必須仍看得見，所以它疊在
///   任務頁上、不是取代任務頁。
/// - `celebrate.title`：標題，帶剛完成的房號。
/// - `celebrate.subline`：標題下方那一句「接下來會怎樣」。
/// - `celebrate.actual-minutes`：這次用時（主要數字）與當初預估（附註）同一塊。
///   沒有預估分鐘時那一截尾巴整個不出現，不是顯示 0 或空白。
/// - `celebrate.today-progress`：今日進度「第 N 單」那一塊。非打掃類沒有這個
///   數字，整塊不渲染——是不存在，不是顯示 0 或空白。
/// - `celebrate.queued-note`：離線時「已先記錄在手機」那一行。
/// - `celebrate.all-done-note`：都清完時的提示句。
/// - `celebrate.next-button`：往下一步走的實心主按鈕（下一間／回到某一間／去待認領）。
/// - `celebrate.back-home-button`：底部回到首頁的文字按鈕。
///
/// - `celebrate.celebration`：氣氛元素的群組（頂部飾帶、彩屑與入場動態）。租戶
///   關掉完成慶祝時整組不渲染，今日進度也跟著收起來——關掉的是氣氛，不是接力，
///   所以標題、說明、這次用時與接續鍵都還在。
class CelebrateScreen extends StatelessWidget {
  const CelebrateScreen({
    super.key,
    required this.actualMinutes,
    required this.subline,
    required this.onBackHome,
    this.roomNo,
    this.estimateMinutes,
    this.todayNth,
    this.nextStep,
    this.queued = false,
    this.celebrationEnabled = true,
    this.onNextStep,
  });

  /// 這張卡是疊在底下那一頁上的覆蓋層，不是一條路由，所以離開要由推它上來的
  /// 那一頁決定。做成路由的話 Flutter 的 `ModalBarrier` 會連帶把底下那一頁的
  /// accessibility 節點整批擋掉（`BlockSemantics`），遮罩底下那一間房就再也讀
  /// 不到了。原型也是把它畫在任務頁裡（`Main.dc.html:1370-1371` 的
  /// `position: absolute; inset: 0`）。
  final VoidCallback onBackHome;

  /// 租戶是否開著完成慶祝的氣氛呈現（契約 `TaskCompletion.celebrationEnabled`，
  /// 省略時視為 true）。
  final bool celebrationEnabled;

  /// 剛完成的房號。後端那一筆沒有房號時標題只說「完成！」——沒有房號可帶，
  /// 不是帶一個湊出來的房號。
  final String? roomNo;

  /// 標題下方那一句「這一間接下來會怎樣」，由完成的那張單決定，不是畫面自己挑。
  final String subline;

  final int actualMinutes;

  /// 這一間預估幾分鐘；沒有估時的任務為 null。
  final int? estimateMinutes;

  /// 這是今天完成的第幾張打掃單；非打掃類的完成沒有這個數字。
  final int? todayNth;

  /// 完成之後員工該去哪裡。後端沒有給去向時為 null，接續那一段整段不出現。
  final TaskNextStep? nextStep;

  /// 這一筆完成還押在手機上等連線恢復。
  final bool queued;

  /// 按下接續主按鈕時由誰接手帶員工過去。不給的話那顆按鈕畫得出來但按不下去，
  /// 所以有 [nextStep] 的呼叫端一定要給。
  final void Function(TaskNextStep step)? onNextStep;

  @override
  Widget build(BuildContext context) {
    final dialog = _CelebrateDialog(
      roomNo: roomNo,
      subline: subline,
      actualMinutes: actualMinutes,
      estimateMinutes: estimateMinutes,
      // 今日進度算在氣氛那一組裡，關掉氣氛時整塊不出現。
      todayNth: celebrationEnabled ? todayNth : null,
      nextStep: nextStep,
      queued: queued,
      celebrationEnabled: celebrationEnabled,
      onNextStep: onNextStep,
      onBackHome: onBackHome,
    );
    return TmsSemantics(
      id: 'celebrate.screen',
      // 遮罩自己收下落在卡外面的那些點擊。這一層不是路由，不收的話底下那一頁
      // 的按鍵仍然按得到，員工會在完成畫面蓋著的時候又按一次完成。點遮罩不關
      // 卡：離開這張卡只有卡上那兩顆鍵（`Main.dc.html:1371` 的遮罩沒有關閉行為）。
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        child: ColoredBox(
          color: TmsColors.scrimCelebrate,
          child: SafeArea(
            child: Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(TmsSpacing.xl2),
                child: celebrationEnabled
                    ? TmsMotionView(spec: TmsMotion.pop, child: dialog)
                    : dialog,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _CelebrateDialog extends StatelessWidget {
  const _CelebrateDialog({
    required this.roomNo,
    required this.subline,
    required this.actualMinutes,
    required this.estimateMinutes,
    required this.todayNth,
    required this.nextStep,
    required this.queued,
    required this.celebrationEnabled,
    required this.onNextStep,
    required this.onBackHome,
  });

  final String? roomNo;
  final String subline;
  final int actualMinutes;
  final int? estimateMinutes;
  final int? todayNth;
  final TaskNextStep? nextStep;
  final bool queued;
  final bool celebrationEnabled;
  final void Function(TaskNextStep step)? onNextStep;
  final VoidCallback onBackHome;

  /// 頂部那條淡色飾帶的高度。勾勾方塊壓在飾帶的下緣上，飾帶要夠高才接得住。
  static const double _bannerHeight = 132;

  @override
  Widget build(BuildContext context) {
    return TmsCard(
      style: TmsCardStyle.dialog,
      padding: EdgeInsets.zero,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(TmsRadius.dialog),
        child: Stack(
          children: [
            if (celebrationEnabled)
              Positioned.fill(
                child: TmsSemantics(
                  id: 'celebrate.celebration',
                  child: Stack(
                    children: [
                      Positioned(
                        left: 0,
                        right: 0,
                        top: 0,
                        height: _bannerHeight,
                        child: const ColoredBox(
                          color: TmsColors.brandContainer,
                        ),
                      ),
                      const _Confetti(
                        left: 34,
                        top: 92,
                        size: 10,
                        radius: 3,
                        color: TmsColors.brandAccentVivid,
                      ),
                      const _Confetti(
                        left: 72,
                        top: 110,
                        size: 8,
                        radius: TmsRadius.pill,
                        color: TmsColors.brandPrimary,
                        delay: Duration(milliseconds: 300),
                      ),
                      const _Confetti(
                        right: 48,
                        top: 100,
                        size: 10,
                        radius: TmsRadius.pill,
                        color: Color(0xFFFFB783),
                        delay: Duration(milliseconds: 600),
                      ),
                      const _Confetti(
                        right: 86,
                        top: 116,
                        size: 8,
                        radius: 2,
                        color: TmsColors.systemGreen,
                        delay: Duration(milliseconds: 900),
                      ),
                    ],
                  ),
                ),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(
                22,
                TmsSpacing.xxl,
                22,
                TmsSpacing.xl,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: _cardChildren(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 卡上由上往下的每一段，段與段之間 12（`Main.dc.html:1374` 的 `gap: 12`）。
  /// 標題再多 8、統計磚與接續那一段再多 4，是原型在這三處各自加的 margin-top。
  List<Widget> _cardChildren() {
    final step = nextStep;
    return <Widget>[
      const _CheckMark(),
      const SizedBox(height: TmsSpacing.xl),
      TmsSemantics(
        id: 'celebrate.title',
        child: Text(
          roomNo == null ? '完成！' : '$roomNo 完成！',
          textAlign: TextAlign.center,
          style: _headlineStyle,
        ),
      ),
      const SizedBox(height: TmsSpacing.md),
      TmsSemantics(
        id: 'celebrate.subline',
        label: subline,
        child: ExcludeSemantics(
          child: Text(
            subline,
            textAlign: TextAlign.center,
            style: TmsText.subheadline.copyWith(
              color: TmsColors.secondaryLabel,
            ),
          ),
        ),
      ),
      const SizedBox(height: TmsSpacing.lg),
      _StatTiles(
        actualMinutes: actualMinutes,
        estimateMinutes: estimateMinutes,
        todayNth: todayNth,
      ),
      if (queued) ...[
        const SizedBox(height: TmsSpacing.md),
        TmsSemantics(
          id: 'celebrate.queued-note',
          label: _queuedNote,
          child: ExcludeSemantics(
            child: Text(
              _queuedNote,
              textAlign: TextAlign.center,
              style: TmsText.footnote.copyWith(color: TmsColors.brandAccent),
            ),
          ),
        ),
      ],
      if (step != null) ...[
        if (step.kind == TaskNextStepKind.allClear) ...[
          const SizedBox(height: TmsSpacing.lg),
          TmsSemantics(
            id: 'celebrate.all-done-note',
            label: _allDoneNote,
            child: ExcludeSemantics(
              child: Text(
                _allDoneNote,
                textAlign: TextAlign.center,
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
            ),
          ),
          const SizedBox(height: TmsSpacing.md),
        ] else
          const SizedBox(height: TmsSpacing.lg),
        _NextStepButton(step: step, onPressed: onNextStep),
      ],
      const SizedBox(height: TmsSpacing.md),
      TmsSemantics(
        id: 'celebrate.back-home-button',
        label: _backHomeLabel,
        child: ExcludeSemantics(
          child: CupertinoButton(
            padding: EdgeInsets.zero,
            borderRadius: BorderRadius.circular(TmsRadius.pill),
            minimumSize: const Size(double.infinity, _backHomeButtonHeight),
            onPressed: onBackHome,
            child: Text(
              _backHomeLabel,
              style: TmsText.headline.copyWith(color: TmsColors.brandPrimary),
            ),
          ),
        ),
      ),
    ];
  }
}

const String _backHomeLabel = '回到首頁';

const String _queuedNote ='目前離線，已先記錄在手機，恢復連線後自動送出。';

const String _allDoneNote = '排定的房都清完了，有空可以到「待認領」幫忙接單。';

/// 接續的實心主按鈕。三種去向的文字由 [TaskNextStep.kind] 決定。
///
/// 「還有下一間」與「都清完了」在畫面上必須分得出來：員工看到的結論相反，
/// 長一樣的話按下去才發現去錯地方。
class _NextStepButton extends StatelessWidget {
  const _NextStepButton({required this.step, required this.onPressed});

  final TaskNextStep step;
  final void Function(TaskNextStep step)? onPressed;

  @override
  Widget build(BuildContext context) {
    final roomNo = step.roomNo;
    final label = switch (step.kind) {
      TaskNextStepKind.allClear => '去「待認領」看看',
      _ => roomNo == null ? '下一間' : '下一間：$roomNo',
    };
    final callback = onPressed;
    return TmsSemantics(
      id: 'celebrate.next-button',
      label: label,
      enabled: callback != null,
      child: ExcludeSemantics(
        child: CupertinoButton(
          padding: EdgeInsets.zero,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
          color: TmsColors.brandPrimary,
          minimumSize: const Size(double.infinity, _nextButtonHeight),
          onPressed: callback == null ? null : () => callback(step),
          child: Text(
            label,
            style: TmsText.headline.copyWith(color: TmsColors.brandOnPrimary),
          ),
        ),
      ),
    );
  }
}

/// 慶祝標題的字級。它比首頁最大的標題再大一階，是這張卡上唯一的主角。
/// 行高不宣告，照原型 `Main.dc.html:1383` 交給字型自己的行高。
const TextStyle _headlineStyle = TextStyle(
  fontSize: 26,
  fontWeight: FontWeight.w800,
  color: TmsColors.label,
);

/// 接續主按鈕的高度（`Main.dc.html:1399` 的 `min-height: 52`）。
const double _nextButtonHeight = 52;

/// 回首頁文字按鈕的高度（`Main.dc.html:1406` 的 `min-height: 44`）。它比接續
/// 鍵矮一截，矮下去的那 8pt 就是兩顆鍵的層級差。
const double _backHomeButtonHeight = 44;

/// 旋轉一個小角度的品牌色方塊，中間是一個白色的勾。
///
/// 方塊刻意不擺正：正放的方形讀起來像一個狀態圖示，歪一點才看得出是在慶祝。
class _CheckMark extends StatelessWidget {
  const _CheckMark();

  static const double _size = 88;

  @override
  Widget build(BuildContext context) {
    return Transform.rotate(
      angle: -6 * 3.1415926535 / 180,
      child: Container(
        width: _size,
        height: _size,
        decoration: BoxDecoration(
          color: TmsColors.brandPrimary,
          borderRadius: BorderRadius.circular(TmsRadius.xl),
          boxShadow: const [
            BoxShadow(
              color: Color(0x590C7680),
              blurRadius: 24,
              offset: Offset(0, 10),
            ),
          ],
        ),
        child: CustomPaint(painter: _CheckPainter()),
      ),
    );
  }
}

/// 畫勾勾方塊中間那個勾。
///
/// 用畫的而不是放一個字元，是因為勾的粗細與兩段的比例要固定；字型裡的勾在
/// 不同系統字型下長得不一樣。
class _CheckPainter extends CustomPainter {
  /// 勾的圖面大小（`Main.dc.html:1381` 的 `<svg width="48" height="48">`）。
  /// 它比 88 的方塊小一圈，所以是置中擺進去，不是跟著方塊等比放大。
  static const double _glyphSize = 48;

  /// 原型 SVG 的 `viewBox` 邊長。圖面上的 1 單位等於 [_glyphSize] / 24。
  static const double _viewBox = 24;

  @override
  void paint(Canvas canvas, Size size) {
    final unit = _glyphSize / _viewBox;
    final origin = Offset(
      (size.width - _glyphSize) / 2,
      (size.height - _glyphSize) / 2,
    );
    Offset at(double x, double y) => origin + Offset(x * unit, y * unit);
    final paint = Paint()
      ..color = TmsColors.brandOnPrimary
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.6 * unit
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    // 原型的 path 是 `M5 12.5l4.5 4.5L19 7.5`。
    final path = Path()
      ..moveTo(at(5, 12.5).dx, at(5, 12.5).dy)
      ..lineTo(at(9.5, 17).dx, at(9.5, 17).dy)
      ..lineTo(at(19, 7.5).dx, at(19, 7.5).dy);
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(_CheckPainter oldDelegate) => false;
}

/// 飾帶上飄著的一顆彩屑。四顆錯開起跑時間，一起同步飄會看起來像在閃。
class _Confetti extends StatelessWidget {
  const _Confetti({
    this.left,
    this.right,
    required this.top,
    required this.size,
    required this.radius,
    required this.color,
    this.delay = Duration.zero,
  });

  final double? left;
  final double? right;
  final double top;
  final double size;
  final double radius;
  final Color color;
  final Duration delay;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: left,
      right: right,
      top: top,
      child: TmsMotionView(
        spec: TmsMotion.float,
        delay: delay,
        child: Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(radius),
          ),
        ),
      ),
    );
  }
}

/// 並排的兩塊統計磚：左邊是這次用時與當初預估，右邊是今天做到第幾單。
///
/// 今天第幾單只有打掃類有，沒有的時候右磚整塊不出現、左磚佔滿整列。留一塊
/// 沒有數字的空磚會讓員工以為資料載不出來。
class _StatTiles extends StatelessWidget {
  const _StatTiles({
    required this.actualMinutes,
    required this.estimateMinutes,
    required this.todayNth,
  });

  final int actualMinutes;
  final int? estimateMinutes;
  final int? todayNth;

  @override
  Widget build(BuildContext context) {
    final estimate = estimateMinutes;
    final nth = todayNth;
    // 兩塊磚的文字長度不一樣，各自算高度的話會一高一低。先用 IntrinsicHeight
    // 量出較高的那一塊，再讓兩塊都撐到同一個高度。
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: _StatTile(
              id: 'celebrate.actual-minutes',
              caption: estimate == null ? '實際' : '實際／預估',
              value: '$actualMinutes',
              valueTail: estimate == null ? ' 分' : ' / $estimate 分',
              background: TmsColors.groupedBackground,
              captionColor: TmsColors.secondaryLabel,
              valueColor: TmsColors.label,
            ),
          ),
          if (nth != null) ...[
            const SizedBox(width: TmsSpacing.sm),
            Expanded(
              child: _StatTile(
                id: 'celebrate.today-progress',
                caption: '今日進度',
                value: '第 $nth 單',
                background: TmsColors.brandAccentContainer,
                captionColor: TmsColors.brandOnAccentContainer,
                valueColor: TmsColors.onOvertimeCapsule,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// 一塊統計磚：上面一行小字說明這個數字是什麼，下面一行是數字本身。
///
/// 掛 id 的節點顯式帶數字那一行當 label，底下的文字用 [ExcludeSemantics]
/// 擋掉自己的節點，讀值的一方拿到的就是數字那一句，不含說明文字。
class _StatTile extends StatelessWidget {
  const _StatTile({
    required this.id,
    required this.caption,
    required this.value,
    required this.background,
    required this.captionColor,
    required this.valueColor,
    this.valueTail,
  });

  final String id;
  final String caption;
  final String value;

  /// 緊跟在主要數字後面、比它小一階的尾巴，例如「 / 35 分」。
  final String? valueTail;
  final Color background;
  final Color captionColor;
  final Color valueColor;

  @override
  Widget build(BuildContext context) {
    final tail = valueTail;
    return TmsSemantics(
      id: id,
      label: tail == null ? value : '$value$tail',
      child: ExcludeSemantics(
        child: TmsCard(
          backgroundColor: background,
          padding: const EdgeInsets.all(TmsSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                caption,
                style: TmsText.caption1
                    .copyWith(fontWeight: FontWeight.w400, color: captionColor),
              ),
              const SizedBox(height: TmsSpacing.xxs),
              Text.rich(
                TextSpan(
                  text: value,
                  style: TmsText.title3.copyWith(color: valueColor),
                  children: [
                    if (tail != null)
                      TextSpan(
                        text: tail,
                        style: TmsText.footnoteMedium
                            .copyWith(color: TmsColors.secondaryLabel),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
