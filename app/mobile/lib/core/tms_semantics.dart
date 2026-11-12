import 'package:flutter/widgets.dart';

/// `CupertinoButton` 的 `onPressed` 為 `null` 時，Flutter 不會把停用狀態寫進
/// accessibility tree，所以可停用的元件要由呼叫端顯式傳入 [enabled]。
class TmsSemantics extends StatelessWidget {
  const TmsSemantics({
    super.key,
    required this.id,
    required this.child,
    this.enabled = true,
    this.label,
    this.explicitChildNodes = false,
  });

  final String id;

  final bool enabled;

  final String? label;

  final bool explicitChildNodes;

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      identifier: id,
      enabled: enabled,
      label: label,
      container: true,
      child: child,
    );
  }
}
