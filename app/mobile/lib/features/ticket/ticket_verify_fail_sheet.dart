import 'package:flutter/cupertino.dart';

/// 主管在工務單詳情按「不通過」後升起的原因面板。
///
/// 原因必填，去掉頭尾空白後是空的就不能送出；送出後交給 [onSubmit]，由詳情頁
/// 呼叫驗收並換上結果卡。版面與輸入行為尚未實作。
class TicketVerifyFailSheet extends StatelessWidget {
  const TicketVerifyFailSheet({super.key, required this.onSubmit});

  /// 帶著主管寫的原因送出驗收不通過。
  final ValueChanged<String> onSubmit;

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}
