import 'package:flutter/widgets.dart';

import '../../core/tms_semantics.dart';

/// 進度數字：掛 [id] 的節點讀出來剛好是 [value] 本身，不多不少。
///
/// 怎麼做到：值顯式放進 [TmsSemantics] 的 label，底下那段文字則用
/// [ExcludeSemantics] 擋掉自己的 semantics 節點。名稱（「打掃完成／總數」
/// 這類說明文字）由呼叫端放在這個元件外面，不要包進來。
///
/// 兩件事都是實跑打出來的，不是推測：文字沒擋掉時，節點讀到的是 label 與
/// 子節點文字接在一起的兩段（「2」變成「2\n2」）；說明文字若包進同一個節點，
/// 讀到的會是「2 / 7\n打掃完成／總數 2 / 7」。讀值的一方要的是值本身，
/// 它得去剝前綴才拿得到，等於把畫面排版的細節洩進斷言裡。
class ProgressValue extends StatelessWidget {
  const ProgressValue({
    super.key,
    required this.id,
    required this.value,
    this.style,
    this.unit,
    this.unitStyle,
  });

  /// accessibility id，例如 `home.progress.cleaned-ratio`。
  final String id;

  /// 要顯示、也要被讀到的值，例如 `2 / 7`、`135 分`。
  final String value;

  final TextStyle? style;

  /// 數字後面那個字級較小的單位字，例如「分鐘」「張工單」；null 代表這一格
  /// 只有數字。
  ///
  /// 單位跟著數字一起進 label，讀值的一方拿到的是員工看到的那一整串。單位
  /// 自己抽一個節點的話，驗收得把兩個節點讀回來再拼，拼法又只有測試知道。
  final String? unit;

  final TextStyle? unitStyle;

  @override
  Widget build(BuildContext context) {
    final unit = this.unit;
    return TmsSemantics(
      id: id,
      label: unit == null ? value : '$value $unit',
      child: ExcludeSemantics(
        child: unit == null
            ? Text(value, style: style)
            : Text.rich(
                TextSpan(
                  text: '$value ',
                  style: style,
                  children: [TextSpan(text: unit, style: unitStyle)],
                ),
              ),
      ),
    );
  }
}
