import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_motion.dart';
import '../../core/widgets/tms_option_row.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_sheet.dart';

class AskSheet extends StatefulWidget {
  const AskSheet({super.key, required this.taskId, required this.roomNo});

  final String taskId;

  final String roomNo;

  static Future<void> show(
    BuildContext context, {
    required String taskId,
    required String roomNo,
  }) {
    return TmsSheetRoute.show<void>(
      context,
      (_) => AskSheet(taskId: taskId, roomNo: roomNo),
    );
  }

  @override
  State<AskSheet> createState() => _AskSheetState();
}

sealed class _AskStage {
  const _AskStage();
}

class _AskBrowsing extends _AskStage {
  const _AskBrowsing();
}

class _AskWaiting extends _AskStage {
  const _AskWaiting(this.question);

  final String question;
}

class _AskAnswered extends _AskStage {
  const _AskAnswered(this.question, this.answer);

  final String question;
  final AskAnswer answer;
}

class _AskSheetState extends State<AskSheet> {
  late final Future<List<AskSuggestion>> _suggestions;

  _AskStage _stage = const _AskBrowsing();

  bool? _feedback;

  bool _escalated = false;

  @override
  void initState() {
    super.initState();
    _suggestions = ApiClient.instance.listAskSuggestions(widget.taskId);
  }

  Future<void> _ask(String question) async {
    setState(() {
      _stage = _AskWaiting(question);
      _feedback = null;
      _escalated = false;
    });
    final answer = await ApiClient.instance.askRoomMemory(
      taskId: widget.taskId,
      question: question,
    );
    if (!mounted) {
      return;
    }
    setState(() => _stage = _AskAnswered(question, answer));
  }

  void _sendFeedback(String answerId, bool helpful) {
    setState(() => _feedback = helpful);
    ApiClient.instance.submitAskFeedback(
      answerId: answerId,
      helpful: helpful,
    );
  }

  Future<void> _askManager(String answerId) async {
    await ApiClient.instance.escalateAskQuestion(answerId);
    if (!mounted) {
      return;
    }
    setState(() => _escalated = true);
  }

  @override
  Widget build(BuildContext context) {
    final stage = _stage;
    final browsing = stage is _AskBrowsing;
    return TmsSemantics(
      id: 'ask.sheet',
      explicitChildNodes: true,
      child: TmsSheet(
        title: '查一下 · ${widget.roomNo}',
        leading:
            browsing ? TmsSheetLeading.close : TmsSheetLeading.back,
        onLeadingTap: browsing
            ? () => Navigator.of(context).pop()
            : () => setState(() => _stage = const _AskBrowsing()),
        bottomAction: browsing
            ? null
            : TmsSemantics(
                id: 'ask.back-to-task-button',
                child: TmsSheetButton(
                  label: '回到任務',
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ),
        children: switch (stage) {
          _AskBrowsing() => _browsingChildren(),
          _AskWaiting(:final question) => [
              _QuestionBubble(question: question),
              const _LoadingCard(),
            ],
          _AskAnswered(:final question, :final answer) => [
              _QuestionBubble(question: question),
              if (answer.found) _buildAnswer(answer) else _buildNotFound(answer),
              if (answer.found) _feedbackRow(answer.answerId),
            ],
        },
      ),
    );
  }

  List<Widget> _browsingChildren() {
    return [
      FutureBuilder<List<AskSuggestion>>(
        future: _suggestions,
        builder: (context, snapshot) {
          final suggestions = snapshot.data;
          if (suggestions == null) {
            return const SizedBox.shrink();
          }
          return TmsGroupedSection(
            style: TmsCardStyle.inSheet,
            trailingDivider: true,
            children: [
              for (var i = 0; i < suggestions.length; i++)
                _SuggestionRow(
                  index: i,
                  suggestion: suggestions[i],
                  onTap: () => _ask(suggestions[i].question),
                ),
            ],
          );
        },
      ),
      const Padding(
        padding: EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
        child: Text(
          '答案只從飯店的 SOP 和這間房的記錄整理，並附上出處。',
          style: TmsText.footnote,
        ),
      ),
    ];
  }

  Widget _buildNotFound(AskAnswer answer) {
    return TmsCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          TmsSemantics(
            id: 'ask.not-found-message',
            child: Text(answer.notFoundMessage ?? '', style: TmsText.headline),
          ),
          const SizedBox(height: TmsSpacing.sm2),
          const Text(
            '可能是沒有記錄，或資料還在更新。系統不會用猜的回答。',
            style: TmsText.subheadline,
          ),
          const SizedBox(height: TmsSpacing.sm2),
          if (_escalated)
            const _EscalatedNotice()
          else
            TmsSemantics(
              id: 'ask.ask-manager-button',
              child: TmsPillButton(
                label: '問主管',
                style: TmsPillButtonStyle.outlined,
                height: _askManagerButtonHeight,
                onPressed: () => _askManager(answer.answerId),
              ),
            ),
        ],
      ),
    );
  }

  static const double _askManagerButtonHeight = 48;

  Widget _buildAnswer(AskAnswer answer) {
    return TmsCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          TmsSemantics(
            id: 'ask.answer.text',
            child: Text(answer.answer ?? '', style: TmsText.headline),
          ),
          if (answer.sources.isNotEmpty) ...[
            const SizedBox(height: TmsSpacing.md),
            Text(
              '出處',
              style: TmsText.caption1.copyWith(
                color: TmsColors.secondaryLabel,
              ),
            ),
            for (var i = 0; i < answer.sources.length; i++)
              Padding(
                padding: const EdgeInsets.only(top: TmsSpacing.xs2),
                child: _SourceCard(index: i, source: answer.sources[i]),
              ),
          ],
          const SizedBox(height: TmsSpacing.md),
          Text(
            '答案由 AI 根據上面的資料整理，請以出處內容為準。',
            style: TmsText.caption1Regular.copyWith(
              color: TmsColors.tertiaryLabel,
              height: 1.45,
            ),
          ),
        ],
      ),
    );
  }

  Widget _feedbackRow(String answerId) {
    final feedback = _feedback;
    if (feedback != null) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.xs),
        child: TmsSemantics(
          id: 'ask.feedback.thanks',
          child: Text(
            feedback ? '謝謝，已記錄。' : '謝謝，已轉給 SOP 管理者檢查這筆資料。',
            style: TmsText.subheadline.copyWith(
              color: TmsColors.onSuccessContainer,
            ),
          ),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.xs),
      child: Row(
        children: [
          const Expanded(
            child: Text('這個答案有幫助嗎？', style: TmsText.subheadline),
          ),
          TmsSemantics(
            id: 'ask.feedback.helpful-button',
            child: _FeedbackChip(
              label: '有幫助',
              onTap: () => _sendFeedback(answerId, true),
            ),
          ),
          const SizedBox(width: TmsSpacing.sm),
          TmsSemantics(
            id: 'ask.feedback.not-helpful-button',
            child: _FeedbackChip(
              label: '沒幫助',
              onTap: () => _sendFeedback(answerId, false),
            ),
          ),
        ],
      ),
    );
  }
}

class _QuestionBubble extends StatelessWidget {
  const _QuestionBubble({required this.question});

  final String question;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerRight,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width * 0.85,
        ),
        child: TmsSemantics(
          id: 'ask.question-bubble',
          child: Container(
            padding: const EdgeInsets.symmetric(
              vertical: TmsSpacing.sm2,
              horizontal: TmsSpacing.md2,
            ),
            decoration: const BoxDecoration(
              color: TmsColors.brandPrimary,
              borderRadius: BorderRadius.only(
                topLeft: Radius.circular(_bubbleRadius),
                topRight: Radius.circular(_bubbleRadius),
                bottomRight: Radius.circular(_bubbleTailRadius),
                bottomLeft: Radius.circular(_bubbleRadius),
              ),
            ),
            child: Text(
              question,
              style: TmsText.subheadline.copyWith(
                color: TmsColors.brandOnPrimary,
                height: 1.4,
              ),
            ),
          ),
        ),
      ),
    );
  }

  static const double _bubbleRadius = 18;
  static const double _bubbleTailRadius = 4;
}

class _LoadingCard extends StatelessWidget {
  const _LoadingCard();

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'ask.loading',
      child: TmsCard(
        child: TmsMotionView(
          spec: TmsMotion.pulse,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                '正在查 SOP 和這間房的記錄…',
                style: TmsText.subheadline,
              ),
              const SizedBox(height: TmsSpacing.sm),
              _bar(0.8),
              const SizedBox(height: TmsSpacing.sm),
              _bar(0.55),
            ],
          ),
        ),
      ),
    );
  }

  Widget _bar(double widthFactor) {
    return FractionallySizedBox(
      alignment: Alignment.centerLeft,
      widthFactor: widthFactor,
      child: Container(
        height: _barHeight,
        decoration: BoxDecoration(
          color: TmsColors.neutralContainer,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
        ),
      ),
    );
  }

  static const double _barHeight = 10;
}

class _EscalatedNotice extends StatelessWidget {
  const _EscalatedNotice();

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'ask.ask-manager-sent-label',
      child: SizedBox(
        height: 48,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              CupertinoIcons.check_mark,
              size: 18,
              color: TmsColors.systemGreen,
            ),
            const SizedBox(width: TmsSpacing.xs2),
            Text(
              '已傳給主管，回覆會用推播通知你',
              style: TmsText.headline.copyWith(
                color: TmsColors.secondaryLabel,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _FeedbackChip extends StatelessWidget {
  const _FeedbackChip({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: TmsColors.cardBackground,
        borderRadius: BorderRadius.circular(TmsRadius.pill),
        border: Border.all(color: TmsColors.opaqueSeparator),
      ),
      child: CupertinoButton(
        padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.md2),
        minimumSize: const Size(0, _height),
        borderRadius: BorderRadius.circular(TmsRadius.pill),
        onPressed: onTap,
        child: Text(label, style: TmsText.subheadlineEmphasized),
      ),
    );
  }

  static const double _height = 40;
}

class _SuggestionRow extends StatelessWidget {
  const _SuggestionRow({
    required this.index,
    required this.suggestion,
    required this.onTap,
  });

  final int index;

  final AskSuggestion suggestion;

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'ask.suggestion.$index',
      explicitChildNodes: true,
      child: TmsOptionRow.suggestion(
        onTap: onTap,
        title: TmsSemantics(
          id: 'ask.suggestion.$index.question',
          child: Text(suggestion.question),
        ),
        scope: TmsSemantics(
          id: 'ask.suggestion.$index.scope',
          child: Text(suggestion.scope),
        ),
      ),
    );
  }
}

class _SourceCard extends StatelessWidget {
  const _SourceCard({required this.index, required this.source});

  final int index;

  final AskSource source;

  static String _kindText(String kind) {
    switch (kind) {
      case 'room_memory':
        return '房間記錄';
      case 'sop':
        return 'SOP';
      default:
        return kind;
    }
  }

  (Color, Color) get _kindColors {
    switch (source.kind) {
      case 'room_memory':
        return (
          TmsColors.brandAccentContainer,
          TmsColors.brandOnAccentContainer,
        );
      case 'sop':
        return (TmsColors.brandContainer, TmsColors.brandOnContainerStrong);
      default:
        return (TmsColors.neutralContainer, TmsColors.onNeutralContainer);
    }
  }

  static String _monthDay(String isoDate) {
    final parts = isoDate.split('-');
    return parts.length == 3 ? '${parts[1]}/${parts[2]}' : isoDate;
  }

  @override
  Widget build(BuildContext context) {
    final (pillBackground, pillForeground) = _kindColors;
    return _SourceCardFrame(
      id: 'ask.answer.source-card.$index',
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            TmsSemantics(
              id: 'ask.answer.source-card.$index.type',
              label: _kindText(source.kind),
              child: ExcludeSemantics(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    vertical: TmsSpacing.xxs,
                    horizontal: TmsSpacing.sm,
                  ),
                  decoration: BoxDecoration(
                    color: pillBackground,
                    borderRadius: BorderRadius.circular(TmsRadius.pill),
                  ),
                  child: Text(
                    _kindText(source.kind),
                    style: TmsText.caption2.copyWith(color: pillForeground),
                  ),
                ),
              ),
            ),
            const SizedBox(width: TmsSpacing.xs2),
            Expanded(
              child: TmsSemantics(
                id: 'ask.answer.source-card.$index.title',
                child: Text(source.title, style: TmsText.subheadlineBold),
              ),
            ),
          ],
        ),
        const SizedBox(height: TmsSpacing.xs),
        TmsSemantics(
          id: 'ask.answer.source-card.$index.excerpt',
          child: Text(source.excerpt, style: TmsText.footnote),
        ),
        const SizedBox(height: TmsSpacing.xs),
        TmsSemantics(
          id: 'ask.answer.source-card.$index.date',
          child: Text(
            _monthDay(source.updatedOn),
            style: TmsText.caption1Regular.copyWith(
              color: TmsColors.tertiaryLabel,
            ),
          ),
        ),
      ],
    );
  }
}

class _SourceCardFrame extends StatelessWidget {
  const _SourceCardFrame({required this.id, required this.children});

  final String id;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      explicitChildNodes: true,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(
          vertical: TmsSpacing.sm2,
          horizontal: TmsSpacing.md,
        ),
        decoration: BoxDecoration(
          color: TmsColors.groupedBackground,
          borderRadius: BorderRadius.circular(TmsRadius.md),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: children,
        ),
      ),
    );
  }
}
