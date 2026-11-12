import 'package:flutter/cupertino.dart';

import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_alert.dart';
import 'manager_ids.dart';

Future<void> showManagerConflictAlert(
  BuildContext context, {
  String message = '可能是另一位主管剛處理過，或這張單被改派了。',
}) {
  return showCupertinoDialog<void>(
    context: context,
    builder: (dialogContext) => TmsSemantics(
      id: ManagerIds.conflictAlert,
      label: '這張單已被處理',
      explicitChildNodes: true,
      child: TmsAlert(
        title: '這張單已被處理',
        message: message,
        actions: [
          TmsAlertAction(
            label: '知道了',
            style: TmsAlertActionStyle.primary,
            onPressed: () => Navigator.of(dialogContext).pop(),
          ),
        ],
      ),
    ),
  );
}
