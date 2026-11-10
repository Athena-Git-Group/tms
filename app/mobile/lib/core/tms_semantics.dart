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
    this.label,
    this.explicitChildNodes = false,
  });

  /// accessibility id，dot 分段＋kebab-case，例如 `login.submit-button`。
  final String id;

  final bool enabled;

  /// 顯式 accessibility label。給「測試要讀這個節點本身的文字」的元件用：
  /// 容器型 semantics 節點預設沒有 label（文字在子節點上），Appium 的
  /// getText() 讀到空字串；把要被斷言的那段文字同時顯式放到這個節點，
  /// 定位（identifier）與讀值（label）才會落在同一個元素上。一般元件不用帶。
  final String? label;

  /// 給「測試要先定位這個節點、再在它底下找子項目」的分組容器用（例如
  /// 通知中心的今天／更早區塊）。設成 true 時，子孫的 semantics 節點會
  /// 保留在這個節點底下，Appium 能以它為範圍往下搜尋。預設 false：
  /// Flutter 會把沒有自己節點的子文字合併進這個節點的 label，子項目
  /// 可能變成它的兄弟節點，從它往下搜尋會找不到東西。
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
