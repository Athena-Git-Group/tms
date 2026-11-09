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

/// 查一下面板（ui-plan 畫面 8 的 `askSheet`）：列出這張任務的推薦問題，
/// 讓員工在房內遇到不確定的事時先查、查不到再麻煩主管。
///
/// 推薦問題的順序與範圍標籤全部由後端決定（`GET /room-memories/suggestions`
/// 依任務情境排好：剛跳過的 SOP 項排最前面），面板只照順序畫出來。App 自己
/// 排序的話，畫面上看到的優先順序就和後端稽核紀錄裡的不是同一回事。
///
/// 面板是兩段式的：第一段只有推薦問題，點一則之後整面換成那一題的問答
/// （`Main.dc.html:1598`）。問完還把清單留在旁邊的話，員工會以為剛才那一題
/// 的答案也只是清單的一部分，而答案與出處正是這個面板唯一要他讀的東西。
class AskSheet extends StatefulWidget {
  const AskSheet({super.key, required this.taskId, required this.roomNo});

  /// 要查哪一張任務的情境。
  final String taskId;

  /// 這張任務的房號，寫在面板標題上。員工手上常常同時有好幾間，標題不帶房號
  /// 的話，他看不出這些答案講的是哪一間。
  final String roomNo;

  /// 任務頁的開啟入口統一走這裡。
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

/// 面板現在停在哪一段。
///
/// 問題本身在查詢途中與拿到答案之後都要顯示在上方的氣泡裡，所以它跟著狀態一
/// 起走，不另外記一個「上次問了什麼」的欄位。分開記的話，答案換了而問題沒換
/// 的那一幀，員工會看到一題配上另一題的答案。
sealed class _AskStage {
  const _AskStage();
}

/// 第一段：只有推薦問題清單。
class _AskBrowsing extends _AskStage {
  const _AskBrowsing();
}

/// 第二段，查詢途中：問題已經問出去了，答案還沒回來。
class _AskWaiting extends _AskStage {
  const _AskWaiting(this.question);

  final String question;
}

/// 第二段，答案已經回來。[answer] 自己帶著查得到或查不到的分野。
class _AskAnswered extends _AskStage {
  const _AskAnswered(this.question, this.answer);

  final String question;
  final AskAnswer answer;
}

class _AskSheetState extends State<AskSheet> {
  late final Future<List<AskSuggestion>> _suggestions;

  _AskStage _stage = const _AskBrowsing();

  /// 員工對這則答案的回饋：true 是有幫助，false 是沒幫助，還沒回饋時是 null。
  /// 回饋只收一次，收完整列換成一句謝謝（`Main.dc.html:1667`）。
  bool? _feedback;

  /// 這一題已經轉給主管了。
  bool _escalated = false;

  @override
  void initState() {
    super.initState();
    _suggestions = ApiClient.instance.listAskSuggestions(widget.taskId);
  }

  /// 點一則推薦問題就把它原文送去後端查。問題字串照推薦清單原樣帶走，不重組——
  /// 後端的稽核紀錄記的是員工實際問出口的那一句。
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

  /// 回饋送出後畫面立刻換成謝謝，不等後端回應。
  ///
  /// 這一筆只寫進稽核紀錄，員工的下一步不依賴它成不成功；讓他在原地等一個轉
  /// 圈，換來的只是把他留在一個他已經讀完的畫面上。
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

  /// 查不到時整張卡講清楚三件事：查不到什麼、為什麼會查不到、接下來可以做
  /// 什麼。只畫一行「查不到」的話，員工會以為是系統壞了，而事實是這件事沒有
  /// 記錄可查，該去問主管。查不到的訊息全文由後端給。
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

  /// 「問主管」與它送出後那一行的高度（`Main.dc.html:1651`）。
  static const double _askManagerButtonHeight = 48;

  /// 回答區：回答全文、出處、AI 免責行，全部在同一張白卡裡。
  ///
  /// 免責行貼著出處放，而不是放在卡外或整個面板的最底下：它要講的是「上面這
  /// 段答案是整理出來的，出處才是原文」，離開這張卡就沒有人知道它在說哪一段。
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

  /// 回饋列：答案有沒有幫助。
  ///
  /// 說沒幫助會讓後端去通知 SOP 管理者檢查被引用的那筆資料，所以這一列不是
  /// 客套，是 SOP 寫錯時唯一會被發現的管道。
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

/// 員工剛問出口的那一句，靠右的對話氣泡。
///
/// 答案與出處都是系統在說話，沒有這顆氣泡的話，捲到一半的員工分不出哪一段是
/// 自己問的、哪一段是查到的。
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

  /// 氣泡三個圓角與貼著發話者那一角的小圓角（`Main.dc.html:1612`）。
  static const double _bubbleRadius = 18;
  static const double _bubbleTailRadius = 4;
}

/// 查詢途中那張卡：一句說明加兩條骨架條，全部一起明暗呼吸。
///
/// 畫骨架條而不是一顆轉圈，是因為骨架條的形狀就是答案回來後會出現在那裡的東
/// 西；轉圈只說得出「在忙」，說不出「在忙什麼、等一下會長什麼樣」。
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

  /// 骨架條的高度（`Main.dc.html:1618`）。
  static const double _barHeight = 10;
}

/// 「問主管」按下去之後取代它的那一行。
///
/// 原地換掉按鈕，不另外跳提示：員工要知道的只有「傳出去了，回覆會用推播找
/// 你」，而按鈕如果還在，他會再按一次。
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

/// 回饋列上那兩顆白底描邊的膠囊。
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

  /// 膠囊的觸控高度（`Main.dc.html:1662`）。
  static const double _height = 40;
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

/// 一張出處卡：第一行是種類膠囊與標題，接著是原文，日期單獨一行收在最後。
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

  /// 種類膠囊的配色。SOP 與房間記錄各一組，認不得的值退回中性灰——猜一組顏色
  /// 給它，員工會以為那是第三種他沒看過的出處。
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

  /// 出處日期在畫面上只顯示月日（`2026-10-02` → `10/02`）：員工關心的是「多久
  /// 以前的事」，年份在房務現場幾乎不會用到。解不開的字串原樣顯示，不自己補值。
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

/// 出處卡的外框：整頁寬、畫面背景色的圓角方塊，內容由上而下靠左排。
///
/// 底色用畫面背景色而不是白色，讓它比上面那段回答往後退一層——它不是在對員工
/// 說話，只是把答案的來源原樣貼出來。
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
