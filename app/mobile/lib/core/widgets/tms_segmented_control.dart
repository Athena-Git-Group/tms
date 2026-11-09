import 'package:flutter/widgets.dart';

import '../theme.dart';

/// 分段控制裡的一段。
class TmsSegment<T> {
  const TmsSegment({required this.value, required this.label, this.wrap});

  /// 選到這一段代表的值。
  final T value;

  final String label;

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

  /// 目前選中的值。
  final T value;

  final ValueChanged<T> onChanged;

  /// 外框內距。`Main.dc.html:2198` — padding 2
  static const double trackPadding = 2;

  /// 外框圓角。`Main.dc.html:2198` — border-radius 9px
  static const double trackRadius = 9;

  /// 一段的最小高度。`Main.dc.html:2200` — min-height 32px
  static const double segmentHeight = 32;

  /// 一段的圓角。`Main.dc.html:2200` — border-radius 7px
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
          // `Main.dc.html:4003` — 選中的那一段字重 600，其餘 400
          style: (selected ? TmsText.footnoteEmphasized : TmsText.footnote)
              .copyWith(color: TmsColors.label),
        ),
      ),
    );
    final wrap = segment.wrap;
    return Semantics(
      selected: selected,
      button: true,
      child: wrap == null ? node : wrap(node),
    );
  }
}
