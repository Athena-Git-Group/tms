import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';

/// 完成慶祝畫面：員工把一間房做完之後看到的回饋，上面寫這次實際做了幾分鐘、
/// 當初預估幾分鐘。
///
/// 兩個數字都由任務頁在完成成功之後傳進來，取自後端回的已做分鐘與預估分鐘。
/// 任務沒有預估分鐘時只顯示實際時間。
///
/// 版面是一張浮在半透明遮罩上的對話卡，不是鋪滿整個螢幕的一頁內容。員工看完
/// 這張卡就要往下一間走，整頁式的畫面會讓人以為自己進了另一個流程、還得找路
/// 回去。
///
/// **accessibility id 詞彙表**（plan 002 Foundational T403 釘定；App 端與
/// `app/bdd` 的 step definitions 共用同一組名稱，兩邊改名要一起改）：
///
/// - `celebrate.screen`：整張覆蓋層。遮罩底下那一頁必須仍看得見，所以它疊在
///   任務頁上、不是取代任務頁。
/// - `celebrate.title`：標題，帶剛完成的房號。
/// - `celebrate.subline`：標題下方那一句「接下來會怎樣」。
/// - `celebrate.actual-minutes`：這次用時（主要數字）與當初預估（附註）同一塊。
/// - `celebrate.today-progress`：今日進度「第 N 單」那一塊。非打掃類與氣氛關閉
///   時整塊不渲染——是不存在，不是顯示 0 或空白。
/// - `celebrate.queued-note`：離線時「已先記錄在手機」那一行。
/// - `celebrate.all-done-note`：都清完時的提示句。
/// - `celebrate.next-button`：往下一步走的實心主按鈕（下一間／回到某一間／去待認領）。
/// - `celebrate.back-home-button`：底部回到首頁的文字按鈕。
/// - `celebrate.celebration`：氣氛元素（飾帶、彩屑、勾勾方塊）的群組。租戶關閉
///   `completion_celebration_enabled` 時整組不渲染。
///
/// 目前這個檔只實作了 `celebrate.screen`、`celebrate.title`、
/// `celebrate.actual-minutes`、`celebrate.estimate-minutes` 與
/// `celebrate.back-home-button`；其餘在 Phase 4D 隨對應元素一起落地。
class CelebrateScreen extends StatelessWidget {
  const CelebrateScreen({
    super.key,
    required this.actualMinutes,
    this.estimateMinutes,
  });

  /// 這一間實際做了幾分鐘。
  final int actualMinutes;

  /// 這一間預估幾分鐘；沒有估時的任務為 null。
  final int? estimateMinutes;

  @override
  Widget build(BuildContext context) {
    final actualText = '實際 $actualMinutes 分';
    final estimateText =
        estimateMinutes == null ? null : '預估 $estimateMinutes 分';
    return TmsSemantics(
      id: 'celebrate.screen',
      child: CupertinoPageScaffold(
        backgroundColor: TmsColors.scrimCelebrate,
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(TmsSpacing.xl2),
              child: _CelebrateDialog(
                actualText: actualText,
                estimateText: estimateText,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 對話卡本體：頂部飾帶與彩屑、旋轉的勾勾方塊、標題、統計磚、回首頁的按鈕。
class _CelebrateDialog extends StatelessWidget {
  const _CelebrateDialog({required this.actualText, required this.estimateText});

  final String actualText;
  final String? estimateText;

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
            Positioned(
              left: 0,
              right: 0,
              top: 0,
              height: _bannerHeight,
              child: const ColoredBox(color: TmsColors.brandContainer),
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
            ),
            const _Confetti(
              right: 48,
              top: 100,
              size: 10,
              radius: TmsRadius.pill,
              color: Color(0xFFFFB783),
            ),
            const _Confetti(
              right: 86,
              top: 116,
              size: 8,
              radius: 2,
              color: TmsColors.systemGreen,
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
                children: [
                  const _CheckMark(),
                  const SizedBox(height: TmsSpacing.xl),
                  const TmsSemantics(
                    id: 'celebrate.title',
                    child: Text('這間做完了！', style: _headlineStyle),
                  ),
                  const SizedBox(height: TmsSpacing.md),
                  _StatTiles(
                    actualText: actualText,
                    estimateText: estimateText,
                  ),
                  const SizedBox(height: TmsSpacing.lg),
                  TmsSemantics(
                    id: 'celebrate.back-home-button',
                    child: CupertinoButton(
                      padding: EdgeInsets.zero,
                      borderRadius: BorderRadius.circular(TmsRadius.pill),
                      color: TmsColors.brandPrimary,
                      minimumSize: const Size(
                        double.infinity,
                        _backHomeButtonHeight,
                      ),
                      onPressed: () {
                        // 回到導航堆疊最底的首頁；慶祝頁之後的「下一間卡」
                        // 呈現是首頁 feature 的事，這裡只負責返回。
                        Navigator.of(context)
                            .popUntil((route) => route.isFirst);
                      },
                      child: Text(
                        '回到首頁',
                        style: TmsText.headline
                            .copyWith(color: TmsColors.brandOnPrimary),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 慶祝標題的字級。它比首頁最大的標題再大一階，是這張卡上唯一的主角。
const TextStyle _headlineStyle = TextStyle(
  fontSize: 26,
  height: 1.2,
  fontWeight: FontWeight.w800,
  color: TmsColors.label,
);

/// 對話卡置底按鈕的高度。這顆按鈕比任務頁的主按鈕略矮，卡的高度才不會被
/// 按鈕撐開。
const double _backHomeButtonHeight = 52;

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
/// 用畫的而不是放一個字元，是因為勾的粗細與兩段的比例要跟方塊大小一起縮放；
/// 字型裡的勾在不同系統字型下長得不一樣。
class _CheckPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = TmsColors.brandOnPrimary
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.width * 2.6 / 24
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    // 勾的三個轉折點取自原型的 48 × 48 圖示，換算成這個方塊的比例。
    final unit = size.width / 24;
    final path = Path()
      ..moveTo(unit * 7, unit * 12.25)
      ..lineTo(unit * 10.25, unit * 15.5)
      ..lineTo(unit * 17, unit * 8.75);
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(_CheckPainter oldDelegate) => false;
}

/// 飾帶上飄著的一顆彩屑。
class _Confetti extends StatelessWidget {
  const _Confetti({
    this.left,
    this.right,
    required this.top,
    required this.size,
    required this.radius,
    required this.color,
  });

  final double? left;
  final double? right;
  final double top;
  final double size;
  final double radius;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: left,
      right: right,
      top: top,
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: color,
          borderRadius: BorderRadius.circular(radius),
        ),
      ),
    );
  }
}

/// 並排的兩塊統計磚：左邊是這次實際花的時間，右邊是當初的預估。
///
/// 沒有預估分鐘的任務只留左邊一塊，並讓它佔滿整列。留一塊沒有數字的空磚會
/// 讓員工以為資料載不出來。
class _StatTiles extends StatelessWidget {
  const _StatTiles({required this.actualText, required this.estimateText});

  final String actualText;
  final String? estimateText;

  @override
  Widget build(BuildContext context) {
    final estimate = estimateText;
    // 兩塊磚的文字長度不一樣，各自算高度的話會一高一低。先用 IntrinsicHeight
    // 量出較高的那一塊，再讓兩塊都撐到同一個高度。
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: _StatTile(
              id: 'celebrate.actual-minutes',
              caption: '這次用時',
              value: actualText,
              background: TmsColors.groupedBackground,
              captionColor: TmsColors.secondaryLabel,
              valueColor: TmsColors.label,
            ),
          ),
          if (estimate != null) ...[
            const SizedBox(width: TmsSpacing.sm),
            Expanded(
              child: _StatTile(
                id: 'celebrate.estimate-minutes',
                caption: '當初預估',
                value: estimate,
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
/// 掛 id 的節點顯式帶 [value] 當 label，底下的文字用 [ExcludeSemantics]
/// 擋掉自己的節點，讀值的一方拿到的就是數字那一句，不含說明文字。
class _StatTile extends StatelessWidget {
  const _StatTile({
    required this.id,
    required this.caption,
    required this.value,
    required this.background,
    required this.captionColor,
    required this.valueColor,
  });

  final String id;
  final String caption;
  final String value;
  final Color background;
  final Color captionColor;
  final Color valueColor;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      label: value,
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
              Text(
                value,
                style: TmsText.title3.copyWith(color: valueColor),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
