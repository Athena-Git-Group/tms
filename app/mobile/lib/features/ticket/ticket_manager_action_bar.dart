import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../manager/manager_ids.dart';

/// 主管視角工務單詳情的底部動作列：描邊「不通過」在左、實心「通過」在右。
///
/// 只在已完修、等主管驗收時出現，其他狀態沒有主管動作；要不要畫由
/// [shows] 一處決定。按通過直接送出、不開面板；按不通過升起原因面板。
class TicketManagerActionBar extends StatelessWidget {
  const TicketManagerActionBar({
    super.key,
    required this.onPass,
    required this.onFail,
  });

  final VoidCallback onPass;
  final VoidCallback onFail;

  /// 這個狀態有沒有主管動作：只看狀態，不看驗收欄位。
  static bool shows(String state) => state == 'done';

  @override
  Widget build(BuildContext context) {
    return TmsActionBar(
      secondary: TmsSemantics(
        id: ManagerIds.ticketFailButton,
        child: TmsPillButton(
          label: '不通過',
          style: TmsPillButtonStyle.outlined,
          onPressed: onFail,
        ),
      ),
      primary: TmsSemantics(
        id: ManagerIds.ticketPassButton,
        child: TmsPillButton(label: '通過', onPressed: onPass),
      ),
    );
  }
}
