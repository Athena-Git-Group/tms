import 'package:flutter/widgets.dart';

/// 全 App 掛 accessibility id 的唯一通道。
///
/// 鐵則：任何需要被 Appium 定位的元件，一律包一層 [TmsSemantics] 並給
/// [id]，不要直接在元件上散用 `Semantics(identifier: ...)`——id 的命名規則
/// （dot 分段＋kebab-case，例如 `login.account-field`）見
/// `specs/plans/001-staff-app-increment/ui/ui-plan.md` 的「accessibility id
/// 約定」一節。這個字串在兩平台底層映射到不同屬性：iOS 曝露為
/// `accessibilityIdentifier`，Android 曝露為 `resource-id`，但 App 端只要
/// 掛 `Semantics(identifier:)` 一次，兩邊就都有——這是
/// `specs/plans/001-staff-app-increment/research.md` 決策 6（「accessibility
/// id 的雙平台定位策略」）訂下的作法。
///
/// [enabled] 是給可禁用元件用的：research.md 決策 6 的 spike 實證，
/// `CupertinoButton` 把 `onPressed` 設成 `null` 時，Flutter 不會自動把
/// disabled 狀態曝露到 accessibility tree，所以呼叫端要自己把目前的
/// enabled/disabled 狀態顯式傳進來，不能假設框架幫你做掉。
class TmsSemantics extends StatelessWidget {
  const TmsSemantics({
    super.key,
    required this.id,
    required this.child,
    this.enabled = true,
  });

  /// accessibility id，dot 分段＋kebab-case，例如 `login.submit-button`。
  final String id;

  /// 目前是否啟用（給按鈕等可禁用元件用；預設 true）。
  final bool enabled;

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      identifier: id,
      enabled: enabled,
      container: true,
      child: child,
    );
  }
}
