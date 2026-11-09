import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 一個任務狀態在畫面上長什麼樣：要顯示哪幾個字、配哪一組底色與字色。
///
/// 底色與字色永遠成對取用，對比度是成對調過的；把某一組的底色配到另一組的
/// 字色會讓文字在現場的燈光下讀不出來。
class TmsStatusAppearance {
  const TmsStatusAppearance({
    required this.text,
    required this.background,
    required this.foreground,
  });

  final String text;
  final Color background;
  final Color foreground;
}

/// 把房務任務狀態換算成畫面上的狀態標籤。
///
/// 用詞出自後端的狀態用語對照（`features/backend/dsl.md`），房務側全 App 只
/// 有這一份：首頁的任務列、任務頁的狀態標籤、待認領單卡講的是同一套狀態，
/// 各寫一份的話同一個狀態會在三個畫面上出現三種說法與三種顏色，員工得逐個
/// 畫面重學。工務單有自己一套狀態值，對照表在 `features/ticket/
/// repair_labels.dart`。
///
/// [state] 用契約的任務狀態值（`accepted`、`doing`、`deferred`、
/// `interrupted`、`done`、`inspected`、`cancelled`、`pool`）。[type] 與
/// [inspection] 只在少數狀態下影響結果：打掃做完要等查房、其他類型做完就是
/// 送達，而開工當下就免查房的單結案時要標示出來，否則員工會一直等一個
/// 不會來的查房結果。
///
/// [redoCount] 是這間房被退回重做的次數。還沒開工而次數大於零的單要讓員工
/// 一眼看出它是被退回來的，所以標籤改說「退回重做」並換成紅色系；只說
/// 「待開始」的話它和一般新單長得一樣，員工會按清單順序慢慢做到它，而退回
/// 的房通常正等著給下一位住客。
///
/// 認不得的狀態值原樣顯示並配中性色，不讓畫面空掉：看到一個沒見過的字，
/// 比看到一個空白的標籤更容易問出問題在哪。
TmsStatusAppearance tmsStatusAppearance({
  required String state,
  String? type,
  String? inspection,
  int redoCount = 0,
}) {
  switch (state) {
    case 'pool':
      return const TmsStatusAppearance(
        text: '待認領',
        background: TmsColors.brandAccentContainer,
        foreground: TmsColors.brandOnAccentContainer,
      );
    case 'accepted':
      if (redoCount > 0) {
        return const TmsStatusAppearance(
          text: '退回重做',
          background: TmsColors.errorContainer,
          foreground: TmsColors.onErrorContainer,
        );
      }
      return const TmsStatusAppearance(
        text: '待開始',
        background: TmsColors.neutralContainer,
        foreground: TmsColors.onNeutralContainer,
      );
    case 'doing':
      return const TmsStatusAppearance(
        text: '進行中',
        background: TmsColors.brandPrimary,
        foreground: TmsColors.brandOnPrimary,
      );
    case 'deferred':
      return const TmsStatusAppearance(
        text: '已延後',
        background: TmsColors.brandAccentContainer,
        foreground: TmsColors.brandOnAccentContainer,
      );
    case 'interrupted':
      return const TmsStatusAppearance(
        text: '已中斷',
        background: TmsColors.otherContainer,
        foreground: TmsColors.onOtherContainer,
      );
    case 'done':
      return TmsStatusAppearance(
        text: type == 'cleaning' ? '待查房' : '已送達',
        background: TmsColors.brandContainer,
        foreground: TmsColors.brandOnContainerStrong,
      );
    case 'inspected':
      return inspection == 'waived'
          ? const TmsStatusAppearance(
              text: '完成 · 免查房',
              background: TmsColors.groupedBackground,
              foreground: TmsColors.onSuccessContainer,
            )
          : const TmsStatusAppearance(
              text: '查房通過',
              background: TmsColors.successContainer,
              foreground: TmsColors.onSuccessContainer,
            );
    case 'cancelled':
      return const TmsStatusAppearance(
        text: '已取消',
        background: TmsColors.neutralContainer,
        foreground: TmsColors.onNeutralContainer,
      );
    default:
      return TmsStatusAppearance(
        text: state,
        background: TmsColors.neutralContainer,
        foreground: TmsColors.onNeutralContainer,
      );
  }
}

/// 狀態標籤：一顆膠囊，裡面是狀態的中文說法。
///
/// 外觀固定為上下 4、左右 10 的內距、12pt 的半粗字與不換行，顏色來自
/// [TmsStatusAppearance]。房務畫面把狀態值交給預設建構式即可，不自己挑
/// 顏色；工務單有自己一套狀態值與配色，算好外觀之後走 [TmsStatusChip.of]，
/// 兩邊因此共用同一顆膠囊的尺寸、圓角與字級。
///
/// 這個元件刻意不掛 accessibility id：同一種標籤會出現在任務頁與各張單卡
/// 上，id 得由使用它的畫面依所在位置決定，在這裡掛死會讓兩個畫面撞同一個
/// id。它也不自建 semantics 節點，文字因此會併進外層節點的 label，外層用
/// 一個節點就同時完成定位與讀值。
class TmsStatusChip extends StatelessWidget {
  /// 外觀已經算好的標籤。
  const TmsStatusChip.of(this.appearance, {super.key, this.onDark = false});

  /// 房務任務的標籤：狀態值交進來，用詞與配色查 [tmsStatusAppearance]。
  factory TmsStatusChip({
    Key? key,
    required String state,
    String? type,
    String? inspection,
    int redoCount = 0,
    bool onDark = false,
  }) {
    return TmsStatusChip.of(
      tmsStatusAppearance(
        state: state,
        type: type,
        inspection: inspection,
        redoCount: redoCount,
      ),
      key: key,
      onDark: onDark,
    );
  }

  final TmsStatusAppearance appearance;

  /// 給放在品牌色深底上的標籤用：設成 true 時這顆標籤一律畫成白底品牌色字，
  /// 不取狀態色表的底色與字色。預設 false，也就是照狀態色表上色。
  ///
  /// 狀態色表的底色都是淺色，疊在品牌色深底上辨識不出來，所以深底上改由文字
  /// 說明狀態，顏色只負責讓標籤本身浮出來。
  final bool onDark;

  @override
  Widget build(BuildContext context) {
    final background = onDark
        ? TmsColors.cardBackground
        : appearance.background;
    final foreground = onDark ? TmsColors.brandPrimary : appearance.foreground;
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.sm2,
        vertical: TmsSpacing.xs,
      ),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(TmsRadius.pill),
      ),
      child: Text(
        appearance.text,
        maxLines: 1,
        softWrap: false,
        style: TmsText.caption1.copyWith(color: foreground),
      ),
    );
  }
}
