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

/// 把任務狀態換算成畫面上的狀態標籤。
///
/// 用詞出自後端的狀態用語對照（`features/backend/dsl.md`），全 App 只有這一
/// 份：首頁的任務列、任務頁的狀態標籤、待認領單卡講的是同一套狀態，各寫一份
/// 的話同一個狀態會在三個畫面上出現三種說法與三種顏色，員工得逐個畫面重學。
///
/// [state] 用契約的任務狀態值（`accepted`、`doing`、`deferred`、
/// `interrupted`、`done`、`inspected`、`cancelled`、`pool`）。[type] 與
/// [inspection] 只在少數狀態下影響結果：打掃做完要等查房、其他類型做完就是
/// 送達，而開工當下就免查房的單結案時要標示出來，否則員工會一直等一個
/// 不會來的查房結果。
///
/// 認不得的狀態值原樣顯示並配中性色，不讓畫面空掉：看到一個沒見過的字，
/// 比看到一個空白的標籤更容易問出問題在哪。
TmsStatusAppearance tmsStatusAppearance({
  required String state,
  String? type,
  String? inspection,
}) {
  switch (state) {
    case 'pool':
      return const TmsStatusAppearance(
        text: '待認領',
        background: TmsColors.brandAccentContainer,
        foreground: TmsColors.brandOnAccentContainer,
      );
    case 'accepted':
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
/// [tmsStatusAppearance]。畫面只需要把狀態值交給它，不自己挑顏色。
///
/// 這個元件刻意不掛 accessibility id：同一種標籤會出現在任務頁與各張單卡
/// 上，id 得由使用它的畫面依所在位置決定，在這裡掛死會讓兩個畫面撞同一個
/// id。它也不自建 semantics 節點，文字因此會併進外層節點的 label，外層用
/// 一個節點就同時完成定位與讀值。
class TmsStatusChip extends StatelessWidget {
  const TmsStatusChip({
    super.key,
    required this.state,
    this.type,
    this.inspection,
  });

  final String state;
  final String? type;
  final String? inspection;

  @override
  Widget build(BuildContext context) {
    final appearance =
        tmsStatusAppearance(state: state, type: type, inspection: inspection);
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.sm2,
        vertical: TmsSpacing.xs,
      ),
      decoration: BoxDecoration(
        color: appearance.background,
        borderRadius: BorderRadius.circular(TmsRadius.pill),
      ),
      child: Text(
        appearance.text,
        maxLines: 1,
        softWrap: false,
        style: TmsText.caption1.copyWith(color: appearance.foreground),
      ),
    );
  }
}
