import 'package:flutter/widgets.dart';

import '../theme.dart';
import '../tms_semantics.dart';

/// iOS 的開關（`Main.dc.html:470`）。
///
/// 自己畫而不用 `CupertinoSwitch`。系統元件的軌道與旋鈕尺寸、位移量都由
/// Flutter 內部決定，調不到原型的 51 × 31 與 27 的旋鈕；開關跟同一組清單裡
/// 的列同高，差幾個 pixel 就會讓那一列看起來比上下兩列胖一點。
class TmsToggle extends StatelessWidget {
  const TmsToggle({
    super.key,
    required this.value,
    required this.onChanged,
    this.label,
    this.id,
  });

  final bool value;

  final ValueChanged<bool> onChanged;

  /// 螢幕報讀器唸出來的名稱。開關本身只有一個圓點，沒有文字可唸。
  final String? label;

  /// 自動化測試用來點到這個開關的 accessibility id。
  final String? id;

  static const double _trackWidth = 51;
  static const double _trackHeight = 31;

  static const double _knobSize = 27;
  static const double _knobInset = 2;

  static const Color _trackOff = Color(0xFFE9E9EA);

  static const Color _knobShadow = Color(0x33000000);

  static const Duration _duration = Duration(milliseconds: 200);

  @override
  Widget build(BuildContext context) {
    final Widget track = AnimatedContainer(
      duration: _duration,
      curve: Curves.ease,
      width: _trackWidth,
      height: _trackHeight,
      padding: const EdgeInsets.all(_knobInset),
      decoration: BoxDecoration(
        color: value ? TmsColors.systemGreen : _trackOff,
        borderRadius: BorderRadius.circular(TmsRadius.pill),
      ),
      child: AnimatedAlign(
        duration: _duration,
        curve: Curves.ease,
        alignment: value ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
          width: _knobSize,
          height: _knobSize,
          decoration: const BoxDecoration(
            color: TmsColors.cardBackground,
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: _knobShadow,
                offset: Offset(0, 2),
                blurRadius: 4,
              ),
            ],
          ),
        ),
      ),
    );
    final Widget button = GestureDetector(
      onTap: () => onChanged(!value),
      behavior: HitTestBehavior.opaque,
      child: Semantics(
        container: true,
        toggled: value,
        label: label,
        child: track,
      ),
    );
    final String? semanticsId = id;
    if (semanticsId == null) {
      return button;
    }
    return TmsSemantics(id: semanticsId, child: button);
  }
}
