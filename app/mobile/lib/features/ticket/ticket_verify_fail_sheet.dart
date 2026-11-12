import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_sheet.dart';
import '../manager/manager_ids.dart';

class TicketVerifyFailSheet extends StatefulWidget {
  const TicketVerifyFailSheet({
    super.key,
    required this.ticketNo,
    required this.onSubmit,
  });

  final String ticketNo;

  final Future<String?> Function(String reason) onSubmit;

  @override
  State<TicketVerifyFailSheet> createState() => _TicketVerifyFailSheetState();
}

class _TicketVerifyFailSheetState extends State<TicketVerifyFailSheet> {
  final TextEditingController _reasonController = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _reasonController.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  String get _reason => _reasonController.text.trim();

  Future<void> _submit() async {
    if (_reason.isEmpty || _submitting) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    final error = await widget.onSubmit(_reason);
    if (!mounted) {
      return;
    }
    if (error == null) {
      Navigator.of(context).pop();
      return;
    }
    setState(() {
      _submitting = false;
      _error = error;
    });
  }

  @override
  Widget build(BuildContext context) {
    final canSubmit = _reason.isNotEmpty && !_submitting;
    return TmsSheet(
      title: '${widget.ticketNo} 不通過',
      onLeadingTap: () => Navigator.of(context).pop(),
      bottomAction: TmsSemantics(
        id: ManagerIds.ticketFailSheetSubmitButton,
        enabled: canSubmit,
        child: TmsSheetButton(
          label: '送出不通過',
          onPressed: canSubmit ? _submit : null,
          child: _submitting ? const CupertinoActivityIndicator() : null,
        ),
      ),
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.screenEdge,
          ),
          child: Text(
            '原因會推播給處理人，也會留在這張單上',
            style: TmsText.footnote.copyWith(color: TmsColors.secondaryLabel),
          ),
        ),
        TmsCard(
          style: TmsCardStyle.inSheet,
          padding: const EdgeInsets.symmetric(
            horizontal: TmsSpacing.screenEdge,
            vertical: TmsSpacing.md,
          ),
          child: TmsSemantics(
            id: ManagerIds.ticketFailSheetReasonField,
            child: CupertinoTextField(
              controller: _reasonController,
              autofocus: true,
              placeholder: '寫下沒有修好的地方，例如：刷卡仍偶爾無反應',
              minLines: 3,
              maxLines: 5,
              padding: EdgeInsets.zero,
              decoration: const BoxDecoration(),
              style: TmsText.callout,
            ),
          ),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: TmsSpacing.screenEdge,
            ),
            child: Text(
              _error!,
              style: TmsText.footnote.copyWith(
                color: TmsColors.onErrorContainer,
              ),
            ),
          ),
      ],
    );
  }
}
