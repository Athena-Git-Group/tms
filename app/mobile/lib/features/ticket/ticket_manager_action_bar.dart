import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../manager/manager_ids.dart';

class TicketManagerActionBar extends StatelessWidget {
  const TicketManagerActionBar({
    super.key,
    required this.onPass,
    required this.onFail,
  });

  final VoidCallback onPass;
  final VoidCallback onFail;

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
