import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/connection_status.dart';
import '../../core/system_status.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_option_row.dart';
import '../../core/widgets/tms_sheet.dart';

enum HoldSheetMode {
  defer,

  interrupt,
}

extension HoldSheetModeCopy on HoldSheetMode {
  String get title => switch (this) {
        HoldSheetMode.defer => '延後',
        HoldSheetMode.interrupt => '中斷',
      };

  String get question => switch (this) {
        HoldSheetMode.defer => '為什麼要延後？',
        HoldSheetMode.interrupt => '為什麼要中斷？',
      };

  List<String> get presetReasons => switch (this) {
        HoldSheetMode.defer => const ['請勿打擾', '住客還在房內', '先處理別的單'],
        HoldSheetMode.interrupt => const [
            '缺備品，去拿',
            '需要工務協助',
            '住客回房',
            '臨時被叫去支援',
          ],
      };

  Future<TaskDetail> submit(String taskId, String reason) => switch (this) {
        HoldSheetMode.defer =>
          ApiClient.instance.deferTask(taskId, reason),
        HoldSheetMode.interrupt =>
          ApiClient.instance.interruptTask(taskId, reason),
      };
}

const String _otherReasonOption = '其他原因';

const String _needsMaintenanceOption = '需要工務協助';

const int _maxReasonLength = 60;

sealed class HoldSheetResult {
  const HoldSheetResult();
}

class HoldSheetSubmitted extends HoldSheetResult {
  const HoldSheetSubmitted(this.task);

  final TaskDetail task;
}

class HoldSheetNeedsMaintenance extends HoldSheetResult {
  const HoldSheetNeedsMaintenance();
}

enum _HoldStep {
  reasons,

  other,
}

class HoldSheet extends StatefulWidget {
  const HoldSheet({super.key, required this.mode, required this.taskId});

  final HoldSheetMode mode;

  final String taskId;

  static Future<HoldSheetResult?> show(
    BuildContext context, {
    required HoldSheetMode mode,
    required String taskId,
  }) {
    return TmsSheetRoute.show<HoldSheetResult>(
      context,
      (_) => HoldSheet(mode: mode, taskId: taskId),
    );
  }

  @override
  State<HoldSheet> createState() => _HoldSheetState();
}

class _HoldSheetState extends State<HoldSheet> {
  _HoldStep _step = _HoldStep.reasons;

  final TextEditingController _noteController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _noteController.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  bool get _canSubmitNote {
    final note = _noteController.text.trim();
    return note.isNotEmpty && note.length <= _maxReasonLength;
  }

  void _pickReason(String option) {
    switch (option) {
      case _otherReasonOption:
        setState(() => _step = _HoldStep.other);
      case _needsMaintenanceOption:
        Navigator.of(context).pop(const HoldSheetNeedsMaintenance());
      default:
        _submit(option);
    }
  }

  Future<void> _submit(String reason) async {
    if (ConnectionStatus.instance.isOffline) {
      await showOfflineBlockedAlert(context);
      return;
    }
    final updated = await widget.mode.submit(widget.taskId, reason);
    if (!mounted) {
      return;
    }
    Navigator.of(context).pop(HoldSheetSubmitted(updated));
  }

  @override
  Widget build(BuildContext context) {
    final onList = _step == _HoldStep.reasons;
    return TmsSemantics(
      id: 'hold.sheet',
      explicitChildNodes: true,
      child: TmsSheet(
        title: onList ? widget.mode.question : _otherReasonOption,
        leading: onList ? TmsSheetLeading.close : TmsSheetLeading.back,
        onLeadingTap: onList
            ? () => Navigator.of(context).pop()
            : () => setState(() => _step = _HoldStep.reasons),
        bottomAction: onList
            ? null
            : TmsSemantics(
                id: 'hold.confirm-button',
                enabled: _canSubmitNote,
                child: TmsSheetButton(
                  label: '確定${widget.mode.title}',
                  onPressed: _canSubmitNote
                      ? () => _submit(_noteController.text.trim())
                      : null,
                ),
              ),
        children: onList ? [_reasonList()] : [_noteCard()],
      ),
    );
  }

  Widget _reasonList() {
    final options = [...widget.mode.presetReasons, _otherReasonOption];
    return TmsGroupedSection(
      style: TmsCardStyle.inSheet,
      trailingDivider: true,
      children: [
        for (final option in options)
          TmsSemantics(
            id: 'hold.reason.$option',
            child: TmsOptionRow.reason(
              title: Text(option),
              onTap: () => _pickReason(option),
            ),
          ),
      ],
    );
  }

  Widget _noteCard() {
    return TmsCard(
      style: TmsCardStyle.inSheet,
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.md2,
        TmsSpacing.md,
        TmsSpacing.md2,
        TmsSpacing.md,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('請簡單說明原因', style: TmsText.footnoteEmphasized),
          const SizedBox(height: TmsSpacing.sm),
          TmsSemantics(
            id: 'hold.other-reason-field',
            child: CupertinoTextField(
              controller: _noteController,
              placeholder: '例如：住客說下午 2 點後再來打掃',
              maxLines: 3,
              minLines: 3,
              padding: EdgeInsets.zero,
              decoration: const BoxDecoration(),
              style: TmsText.callout,
            ),
          ),
          const SizedBox(height: TmsSpacing.sm),
          TmsSemantics(
            id: 'hold.char-count',
            child: Text(
              _charCountText,
              textAlign: TextAlign.right,
              style: TmsText.caption1.copyWith(
                color: TmsColors.secondaryLabel,
              ),
            ),
          ),
        ],
      ),
    );
  }

  String get _charCountText =>
      '${_noteController.text.length} / $_maxReasonLength';
}
