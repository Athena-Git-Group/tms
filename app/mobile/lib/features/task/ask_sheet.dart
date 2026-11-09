import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_grouped_list.dart';
import '../../core/widgets/tms_option_row.dart';
import '../../core/widgets/tms_sheet.dart';
import 'task_callouts.dart';

/// 查一下面板（ui-plan 畫面 8 的 `askSheet`）：列出這張任務的推薦問題，
/// 讓員工在房內遇到不確定的事時先查、查不到再麻煩主管。
///
/// 推薦問題的順序與範圍標籤全部由後端決定（`GET /room-memories/suggestions`
/// 依任務情境排好：剛跳過的 SOP 項排最前面），面板只照順序畫出來。App 自己
/// 排序的話，畫面上看到的優先順序就和後端稽核紀錄裡的不是同一回事。
class AskSheet extends StatefulWidget {
  const AskSheet({super.key, required this.taskId});

  /// 要查哪一張任務的情境。
  final String taskId;

  /// 任務頁的開啟入口統一走這裡。
  static Future<void> show(BuildContext context, {required String taskId}) {
    return TmsSheetRoute.show<void>(
      context,
      (_) => AskSheet(taskId: taskId),
    );
  }

  @override
  State<AskSheet> createState() => _AskSheetState();
}

class _AskSheetState extends State<AskSheet> {
  late final Future<List<AskSuggestion>> _suggestions;

  /// 目前這一次提問的結果；還沒問過任何問題時是 null。
  AskAnswer? _answer;

  @override
  void initState() {
    super.initState();
    _suggestions = ApiClient.instance.listAskSuggestions(widget.taskId);
  }

  /// 點一則推薦問題就把它原文送去後端查。問題字串照推薦清單原樣帶走，不重組——
  /// 後端的稽核紀錄記的是員工實際問出口的那一句。
  Future<void> _ask(String question) async {
    final answer = await ApiClient.instance.askRoomMemory(
      taskId: widget.taskId,
      question: question,
    );
    if (!mounted) {
      return;
    }
    setState(() => _answer = answer);
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'ask.sheet',
      explicitChildNodes: true,
      child: TmsSheet(
        title: '查一下',
        onLeadingTap: () => Navigator.of(context).pop(),
        bottomAction: TmsSemantics(
          id: 'ask.ask-manager-button',
          child: TmsSheetButton(
            label: '問主管',
            filled: false,
            // 問主管的送出是後續 slice；這裡先露出可點的入口。
            onPressed: () {},
          ),
        ),
        children: [
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
          const Text(
            '先查房間記錄或工務記錄，查不到再問主管',
            style: TmsText.footnote,
          ),
          if (_answer != null)
            _answer!.found ? _buildAnswer(_answer!) : _buildNotFound(_answer!),
        ],
      ),
    );
  }

  /// 查不到時只說一句「查不到」，畫面上不出現回答區，也不出現任何出處卡——
  /// 不是畫一個空的回答區。空的回答區會讓員工以為「有查到，但系統沒話說」，
  /// 而事實是這件事沒有記錄可查，該去問主管。訊息全文由後端給。
  Widget _buildNotFound(AskAnswer answer) {
    return TmsCard(
      child: TmsSemantics(
        id: 'ask.not-found-message',
        child: Text(answer.notFoundMessage ?? '', style: TmsText.headline),
      ),
    );
  }

  /// 回答區：回答全文在上，出處卡逐張排在下面。
  Widget _buildAnswer(AskAnswer answer) {
    return TmsCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TmsSemantics(
            id: 'ask.answer.text',
            child: Text(answer.answer ?? '', style: TmsText.headline),
          ),
          for (var i = 0; i < answer.sources.length; i++)
            Padding(
              padding: const EdgeInsets.only(top: TmsSpacing.md),
              child: _SourceCard(index: i, source: answer.sources[i]),
            ),
        ],
      ),
    );
  }
}

/// 一則推薦問題：問題本身一行，底下一行小字是範圍，整則可點。
///
/// 問題與範圍各自掛 id，測試才讀得到「第幾則的問題是什麼、範圍是什麼」。整則
/// 合成一個節點的話，兩段文字會被併成同一個 label，分不出哪一段是範圍。
class _SuggestionRow extends StatelessWidget {
  const _SuggestionRow({
    required this.index,
    required this.suggestion,
    required this.onTap,
  });

  /// 這則在推薦清單裡的位置，用來組 accessibility id。順序由後端決定。
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

/// 一張出處卡：種類與日期並排在第一行，底下是標題與原文。
///
/// 卡片只畫契約 `KbSource` 的四個欄位（種類、標題、原文、日期）——查一下不得
/// 帶出住客身分，所以畫面連「要不要顯示住客欄位」這個選擇都不留。
class _SourceCard extends StatelessWidget {
  const _SourceCard({required this.index, required this.source});

  /// 這張卡在回答底下的位置，用來組 accessibility id。順序由後端決定。
  final int index;

  final AskSource source;

  /// 出處種類的畫面用詞。對照表與契約 `KbSource.kind` 的 enum 一對一。
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

  /// 出處日期在畫面上只顯示月日（`2026-10-02` → `10/02`）：員工關心的是「多久
  /// 以前的事」，年份在房務現場幾乎不會用到。解不開的字串原樣顯示，不自己補值。
  static String _monthDay(String isoDate) {
    final parts = isoDate.split('-');
    return parts.length == 3 ? '${parts[1]}/${parts[2]}' : isoDate;
  }

  @override
  Widget build(BuildContext context) {
    const metaStyle = TmsText.footnote;
    return TaskCallout(
      id: 'ask.answer.source-card.$index',
      tint: calloutSourceTint,
      explicitChildNodes: true,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            TmsSemantics(
              id: 'ask.answer.source-card.$index.type',
              child: Text(_kindText(source.kind), style: metaStyle),
            ),
            TmsSemantics(
              id: 'ask.answer.source-card.$index.date',
              child: Text(_monthDay(source.updatedOn), style: metaStyle),
            ),
          ],
        ),
        const SizedBox(height: TmsSpacing.xs),
        TmsSemantics(
          id: 'ask.answer.source-card.$index.title',
          child: Text(source.title, style: TmsText.subheadlineEmphasized),
        ),
        const SizedBox(height: TmsSpacing.xxs),
        TmsSemantics(
          id: 'ask.answer.source-card.$index.excerpt',
          child: Text(source.excerpt, style: TmsText.footnote),
        ),
      ],
    );
  }
}
