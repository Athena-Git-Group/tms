import 'package:flutter/cupertino.dart';

import 'tms_semantics.dart';

/// 登入、強制改密等表單畫面共用的紅色錯誤橫幅：一段置中的紅字，
/// 下方留 12 的間距與送出按鈕隔開。
///
/// [semanticsId] 有值時整段包一層 [TmsSemantics]，並把 [message] 同時放到
/// accessibility label——測試要斷言的文字和定位用的 id 才會落在同一個節點
/// （原因見 [TmsSemantics.label]）。沒有測試要讀的橫幅就不帶 id，不多掛
/// semantics 節點。
class ErrorBanner extends StatelessWidget {
  const ErrorBanner({super.key, required this.message, this.semanticsId});

  /// 顯示的錯誤句，通常直接用後端 ApiError 的 `message`。
  final String message;

  /// accessibility id（例如 `login.error-banner`）；null 表示這個橫幅
  /// 不需要被測試定位。
  final String? semanticsId;

  @override
  Widget build(BuildContext context) {
    final Widget text = Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Text(
        message,
        style: const TextStyle(color: CupertinoColors.destructiveRed),
        textAlign: TextAlign.center,
      ),
    );
    final id = semanticsId;
    if (id == null) {
      return text;
    }
    return TmsSemantics(id: id, label: message, child: text);
  }
}
