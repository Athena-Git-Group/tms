import 'package:flutter/widgets.dart';

import '../theme.dart';

class TmsSegment<T> {
  const TmsSegment({
    required this.value,
    required this.label,
    this.id,
    this.wrap,
  });

  final T value;

  final String label;

  /// 這一段的 accessibility id。給了就掛在「選中與否」那一個 semantics 節點
  /// 上，Appium 以 id 找到這一段時讀到的 `selected` 就是它現在有沒有被選。
  /// [wrap] 另外包的一層是分開的節點，id 與選中狀態會落在不同元素上，所以
  /// 要同時讀選中狀態的切換用這個欄位，不用 [wrap]。
  final String? id;

  /// 把這一段的節點再包一層，用來掛 accessibility id。
  final Widget Function(Widget node)? wrap;
}

/// 在兩三個互斥的選項之間挑一個的分段控制（`Main.dc.html:2198`）。
///
/// 開工單面板用它兩次：挑地點是客房還是公共區域，以及公共區域的單要自己處理
/// 還是放進待認領。兩處的長相由原型的同一個 `seg()` 產生，所以這裡也是同一個
/// 元件，不是各畫一份。
///
/// 為什麼不是膠囊列：分段控制把「選項只有這幾個、而且只能選一個」畫進外框裡，
/// 員工看外框就知道還有另一段可以選。膠囊列沒有外框，沒選中的那顆看起來像一
/// 顆還沒被按的按鈕，而不是同一個問題的另一個答案。
///
/// 不用 `CupertinoSlidingSegmentedControl`：框架那一顆的段高、圓角與選中段的
/// 陰影都由它自己決定，調不到原型的 32／9／7 與 `0 1px 3px`。
class TmsSegmentedControl<T> extends StatelessWidget {
  const TmsSegmentedControl({
    super.key,
    required this.segments,
    required this.value,
    required this.onChanged,
  });

  final List<TmsSegment<T>> segments;

  final T value;

  final ValueChanged<T> onChanged;

  static const double trackPadding = 2;

  static const double trackRadius = 9;

  static const double segmentHeight = 32;

  static const double segmentRadius = 7;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(trackPadding),
      decoration: BoxDecoration(
        color: TmsColors.segmentedTrack,
        borderRadius: BorderRadius.circular(trackRadius),
      ),
      child: Row(
        children: [
          for (final segment in segments)
            Expanded(child: _segment(segment, segment.value == value)),
        ],
      ),
    );
  }

  Widget _segment(TmsSegment<T> segment, bool selected) {
    final Widget node = GestureDetector(
      onTap: selected ? null : () => onChanged(segment.value),
      behavior: HitTestBehavior.opaque,
      child: Container(
        constraints: const BoxConstraints(minHeight: segmentHeight),
        alignment: Alignment.center,
        decoration: selected
            ? BoxDecoration(
                color: TmsColors.cardBackground,
                borderRadius: BorderRadius.circular(segmentRadius),
                boxShadow: const <BoxShadow>[
                  BoxShadow(
                    color: TmsColors.segmentedThumbShadow,
                    offset: Offset(0, 1),
                    blurRadius: 3,
                  ),
                ],
              )
            : null,
        child: Text(
          segment.label,
          style: (selected ? TmsText.footnoteEmphasized : TmsText.footnote)
              .copyWith(color: TmsColors.label),
        ),
      ),
    );
    final wrap = segment.wrap;
    final id = segment.id;
    return Semantics(
      identifier: id,
      // 帶 id 的那一段要有自己的節點，id 才不會併進上層。
      container: id != null,
      selected: selected,
      button: true,
      child: wrap == null ? node : wrap(node),
    );
  }
}
