import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';

/// 驗收不通過送出後，取代底部動作列的結果卡。
///
/// 依 [outcome] 的去處說明單去了哪裡：回到原處理人名下（原處理人會收到通知），
/// 或原處理人已下班、單回到工務待認領。回清單鈕交給 [onBack]。版面尚未實作。
class TicketVerifyResultCard extends StatelessWidget {
  const TicketVerifyResultCard({
    super.key,
    required this.outcome,
    required this.onBack,
  });

  final RepairVerificationOutcome outcome;

  /// 回到待驗收清單。
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}
