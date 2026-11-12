import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_sheet.dart';
import '../manager/manager_ids.dart';

/// 主管在工務單詳情按「不通過」後升起的原因面板。
///
/// 原因必填，去掉頭尾空白後是空的就不能送出。送出交給 [onSubmit]：它回 null
/// 代表這一次已經有結論（成功，或單已被別人處理），面板自己收起；回一句話代表
/// 沒送成（例如離線），話寫在面板上、原因留著，主管不必重打。
class TicketVerifyFailSheet extends StatefulWidget {
  const TicketVerifyFailSheet({
    super.key,
    required this.ticketNo,
    required this.onSubmit,
  });

  /// 標題帶單號，主管才確定自己退的是哪一張。
  final String ticketNo;

  /// 帶著去掉頭尾空白的原因送出驗收不通過。
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
        // CupertinoButton 的 onPressed 為 null 時，框架不會把 disabled 曝露到
        // accessibility tree，所以可否送出要顯式標出來。
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
