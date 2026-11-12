import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_grouped_list.dart';

/// 狀態列的語氣。語氣決定圖示顏色、底色與圖示形狀三件事，呼叫端只選語氣。
///
/// 嚴重度靠圖示顏色表達，底色只是同色系的淡底，兩者成對出現。讓呼叫端各自
/// 挑顏色的話，同一種嚴重度在任務頁是紅圈紅底、在工務單詳情可能變成橘圈白
/// 底，員工得在兩個畫面各學一次顏色的意思。
///
/// [caution] 的顏色與 [warn] 相同，只有圖示換成驚嘆號（`Main.dc.html:3891`）。
/// 「進不了房」要的是提醒員工停下來確認，不是「有東西被停住」，兩條槓的暫停
/// 符號會讀成後者。
enum TmsCalloutTone {
  /// 出事了，要人去處理：查房退回、急件、完修結果是無法修復。
  error(symbol: TmsColors.systemRed, background: TmsColors.errorContainer),

  /// 事情停在這裡等人：已延後、已中斷、工務暫停。
  warn(
    symbol: TmsColors.systemOrange,
    background: TmsColors.brandAccentContainer,
  ),

  /// 動手前要先確認一件事：房間現在進不去。
  caution(
    symbol: TmsColors.systemOrange,
    background: TmsColors.brandAccentContainer,
  ),

  /// 只是讓人知道目前情況，沒有好壞：等主管查房、等主管審核。
  info(symbol: TmsColors.brandPrimary, background: TmsColors.brandContainer),

  /// 做完了：查房通過、完成免查房、已驗收。
  success(
    symbol: TmsColors.systemGreen,
    background: TmsColors.successContainer,
  ),

  /// 這張單不用再做了，也不是好事或壞事：已取消。
  neutral(
    symbol: TmsColors.quaternaryLabel,
    background: TmsColors.neutralContainer,
  );

  const TmsCalloutTone({required this.symbol, required this.background});

  /// 圖示那顆實心圓的顏色（`Main.dc.html:4299`）。
  final Color symbol;

  /// 整條狀態列的底色（`Main.dc.html:4299`）。
  final Color background;
}

/// 哪一條規則產生了這則狀態列。
///
/// **宣告順序就是優先序**，由上而下由高到低。[TmsCalloutSlot] 只渲染候選裡
/// 名次最前的那一則，一個畫面因此最多只會出現一條狀態列。
///
/// 把優先序放在這裡而不是各畫面裡，是因為它是整個 App 的同一條規則。各畫面
/// 自己排的話，任務頁可能把退回排在取消前面、工務單詳情反過來，同時成立的兩
/// 件事在兩個畫面會挑出不同的一則，員工看到的「現在最該管的事」就不一致了。
enum TmsCalloutKind {
  /// 房務任務頁：查房退回（`Main.dc.html:4308`）。
  taskReturned(TmsCalloutSurface.task),

  /// 房務任務頁：主管作廢這張單。
  taskCancelled(TmsCalloutSurface.task),

  /// 房務任務頁：已延後或已中斷。
  taskHeld(TmsCalloutSurface.task),

  /// 房務任務頁：開工前的固定教學。
  taskTeaching(TmsCalloutSurface.task),

  /// 房務任務頁：做完了，等主管查房或已送達住客。
  taskAwaitingInspection(TmsCalloutSurface.task),

  /// 房務任務頁：查房通過，或完成免查房。
  taskFinished(TmsCalloutSurface.task),

  /// 工務單詳情：維修暫停中（`Main.dc.html:3896`）。
  ticketPaused(TmsCalloutSurface.ticket),

  /// 工務單詳情：驗收不通過、退回重修中。排在待認領之前：同一張單只會是其中
  /// 一種，但兩者都成立時「被退回來了」才是員工得先知道的事。
  ticketReworking(TmsCalloutSurface.ticket),

  /// 工務單詳情：這張單回到待認領，現在沒有人負責。排在暫停之後，因為「有人
  /// 在處理但停住了」比「還沒有人接」更該先讓員工看到。
  ticketInPool(TmsCalloutSurface.ticket),

  /// 工務單詳情：已完修等驗收，或已驗收結案。
  ticketFixed(TmsCalloutSurface.ticket),

  /// 工務單詳情：等主管審核。
  ticketAwaitingReview(TmsCalloutSurface.ticket),

  /// 工務單詳情：房間目前進不去。
  ticketRoomNotEnterable(TmsCalloutSurface.ticket),

  /// 工務單詳情：急件。
  ticketUrgent(TmsCalloutSurface.ticket);

  const TmsCalloutKind(this.surface);

  final TmsCalloutSurface surface;
}

/// 狀態列規則所屬的畫面。
///
/// 兩個畫面各有一條互不相干的優先序，排在一起比大小沒有意義，所以挑選時會檢
/// 查候選是不是同一個畫面的。
enum TmsCalloutSurface {
  task,

  ticket,
}

/// 狀態列上的一個文字鍵，例如「查看工務單」與「補資料再送出」。
class TmsCalloutAction {
  const TmsCalloutAction({
    required this.label,
    required this.onPressed,
    this.showsChevron = false,
    this.wrap,
  });

  final String label;
  final VoidCallback onPressed;

  /// 文字後面要不要接一個往前的箭頭。會換頁的那一顆才接。
  final bool showsChevron;

  /// 給呼叫端把自己的 accessibility 節點包在這顆鍵外面用；意義見
  /// [TmsCalloutSpec.wrap]。
  final Widget Function(Widget node)? wrap;
}

/// 一則候選狀態列。
///
/// 畫面把當下成立的每一條規則各做成一個 spec 交給 [TmsCalloutSlot]，由它挑出
/// 優先序最前的一則來畫。畫面自己不判斷誰蓋過誰。
sealed class TmsCalloutSpec {
  const TmsCalloutSpec({required this.kind, this.wrap});

  /// 產生這則狀態列的規則，決定它在優先序裡的名次。
  final TmsCalloutKind kind;

  /// 給呼叫端把自己的 accessibility 節點包在這一塊外面用。狀態列自己不掛 id：
  /// 同一個元件會出現在任務頁與工務單詳情上，兩邊的 id 命名不一樣，在元件裡
  /// 掛死會讓其中一邊定位不到。
  final Widget Function(Widget node)? wrap;
}

/// 一般的狀態列：填色圓形圖示加標題、原因、說明，底下可以再接文字鍵。
class TmsStatusCallout extends TmsCalloutSpec {
  const TmsStatusCallout({
    required super.kind,
    required this.tone,
    required this.title,
    required this.body,
    this.reason,
    this.actions = const <TmsCalloutAction>[],
    this.idPrefix,
    super.wrap,
  });

  final TmsCalloutTone tone;

  /// 標題、原因與說明三段各自掛的 accessibility id 的前綴，例如給
  /// `task.status-row.return-info` 就得到 `….title`、`….reason`、`….body`。
  ///
  /// 不給就三段都不掛 id。前綴由呼叫端給而不是寫死在元件裡，理由同 [wrap]：
  /// 同一個元件在任務頁與工務單詳情上的命名不一樣。
  final String? idPrefix;

  /// 一句話說明現在是什麼狀況，例如「查房退回：床鋪不合格」。
  final String title;

  /// 接下來要做什麼，或這個狀況代表什麼。
  final String body;

  /// 人手打的原因，例如「主管：住客延後退房，今天不用打掃」。
  ///
  /// 自成一行而不併進標題，而且用正文黑字：這段是別人說的話，長度不受控，
  /// 接在標題後面會把標題擠成兩行、也分不出哪一段是系統講的、哪一段是人講的。
  final String? reason;

  final List<TmsCalloutAction> actions;
}

/// 固定教學（`Main.dc.html:1248`）：主色淡底、燈泡圖示，右上角一個關閉鍵。
///
/// 關掉之後就不再出現，所以它只講「第一次做這件事要知道的規矩」，不講當下這
/// 張單的狀況。它排在狀態列的優先序裡，是因為一次只給員工一個提示：真的有事
/// 情發生時，教學要讓位給那件事。
class TmsTeachingCallout extends TmsCalloutSpec {
  const TmsTeachingCallout({
    required this.title,
    required this.body,
    required this.onDismiss,
    super.wrap,
  }) : super(kind: TmsCalloutKind.taskTeaching);

  final String title;
  final String body;

  /// 按下右上角關閉鍵。呼叫端要自己記住已經關掉了，下次不要再給這一則。
  final VoidCallback onDismiss;
}

/// 狀態列的位置：收下所有候選，只畫優先序最前的一則。
///
/// 畫面把這個元件放在提示區，候選列表照規則寫成「成立就給 spec、不成立就給
/// null」。沒有任何一則成立時整塊不佔空間。
class TmsCalloutSlot extends StatelessWidget {
  const TmsCalloutSlot({super.key, required this.candidates});

  /// 當下成立的候選，順序隨便給——名次由 [TmsCalloutKind] 的宣告順序決定。
  final List<TmsCalloutSpec?> candidates;

  /// 圖示與文字之間、以及圖示欄的寬度（`Main.dc.html:1225`）。
  static const double _iconGap = TmsSpacing.md;

  /// 文字欄比圖示低 2，標題的字身才跟圓形圖示的圓心對齊
  /// （`Main.dc.html:1228`）。
  static const double _textTopInset = TmsSpacing.xxs;

  /// 挑出優先序最前的一則。候選全是 null 時回傳 null。
  static TmsCalloutSpec? highestPriority(List<TmsCalloutSpec?> candidates) {
    TmsCalloutSpec? best;
    for (final candidate in candidates) {
      if (candidate == null) {
        continue;
      }
      assert(
        best == null || best.kind.surface == candidate.kind.surface,
        '同一個位置只能收同一個畫面的候選，兩個畫面的優先序各自獨立。',
      );
      if (best == null || candidate.kind.index < best.kind.index) {
        best = candidate;
      }
    }
    return best;
  }

  @override
  Widget build(BuildContext context) {
    final spec = highestPriority(candidates);
    if (spec == null) {
      return const SizedBox.shrink();
    }
    final Widget node = switch (spec) {
      TmsStatusCallout() => _buildStatus(spec),
      TmsTeachingCallout() => _buildTeaching(spec),
    };
    final wrap = spec.wrap;
    return wrap == null ? node : wrap(node);
  }

  Widget _buildStatus(TmsStatusCallout spec) {
    final reason = spec.reason;
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
      padding: const EdgeInsets.symmetric(
        vertical: TmsSpacing.md,
        horizontal: TmsSpacing.lg,
      ),
      decoration: BoxDecoration(
        color: spec.tone.background,
        borderRadius: BorderRadius.circular(TmsRadius.card),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _ToneIcon(tone: spec.tone),
          const SizedBox(width: _iconGap),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: _textTopInset),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _segment(
                    spec.idPrefix,
                    'title',
                    spec.title,
                    Text(
                      spec.title,
                      style: TmsText.headline,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (reason != null) ...[
                    const SizedBox(height: TmsSpacing.xxs),
                    _segment(
                      spec.idPrefix,
                      'reason',
                      reason,
                      Text(
                        reason,
                        style: _bodyStyle.copyWith(color: TmsColors.label),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                  const SizedBox(height: TmsSpacing.xxs),
                  _segment(
                    spec.idPrefix,
                    'body',
                    spec.body,
                    Text(spec.body, style: _bodyStyle),
                  ),
                  if (spec.actions.isNotEmpty) ...[
                    const SizedBox(height: TmsSpacing.xs2),
                    Wrap(
                      spacing: TmsSpacing.lg,
                      children: [
                        for (final action in spec.actions)
                          _ActionButton(action: action),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTeaching(TmsTeachingCallout spec) {
    return Stack(
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.only(
            left: TmsSpacing.lg,
            top: TmsSpacing.md2,
            bottom: TmsSpacing.md2,
            right: TmsSize.minTouchTarget,
          ),
          decoration: BoxDecoration(
            color: TmsColors.brandContainer,
            borderRadius: BorderRadius.circular(TmsRadius.card),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(
                CupertinoIcons.lightbulb,
                size: TmsSize.calloutTeachingIcon,
                color: TmsColors.brandPrimary,
              ),
              const SizedBox(width: _iconGap),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.only(top: _textTopInset),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(spec.title, style: TmsText.headline),
                      const SizedBox(height: TmsSpacing.xxs),
                      Text(spec.body, style: _bodyStyle),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        Positioned(
          top: 0,
          right: 0,
          child: CupertinoButton(
            padding: EdgeInsets.zero,
            minimumSize: const Size(
              TmsSize.minTouchTarget,
              TmsSize.minTouchTarget,
            ),
            onPressed: spec.onDismiss,
            child: const Icon(
              CupertinoIcons.xmark,
              size: TmsSize.calloutDismissGlyph,
              color: TmsColors.quaternaryLabel,
            ),
          ),
        ),
      ],
    );
  }
}

/// 把一段文字掛上 `<前綴>.<段名>` 的 accessibility id；沒有前綴時原樣回傳。
///
/// 同時把那段文字顯式放到節點的 label 上：容器型節點預設沒有 label，自動化
/// 驗收定位得到卻讀不到值。
Widget _segment(String? idPrefix, String segment, String text, Widget node) {
  if (idPrefix == null) {
    return node;
  }
  return TmsSemantics(
    id: '$idPrefix.$segment',
    label: text,
    child: ExcludeSemantics(child: node),
  );
}

/// 狀態列的說明段：15pt、行高 1.4、次級字色（`Main.dc.html:1234`）。
const TextStyle _bodyStyle = TextStyle(
  fontFamilyFallback: TmsText.fontFamilyStack,
  fontSize: 15,
  height: 1.4,
  fontWeight: FontWeight.w400,
  color: TmsColors.secondaryLabel,
);

class _ActionButton extends StatelessWidget {
  const _ActionButton({required this.action});

  final TmsCalloutAction action;

  @override
  Widget build(BuildContext context) {
    final style = TmsText.subheadlineEmphasized.copyWith(
      color: TmsColors.brandPrimary,
    );
    final Widget button = CupertinoButton(
      padding: EdgeInsets.zero,
      minimumSize: const Size(0, TmsSize.minTouchTarget),
      onPressed: action.onPressed,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(action.label, style: style),
          if (action.showsChevron) ...[
            const SizedBox(width: TmsSpacing.xs),
            const TmsChevron(color: TmsColors.brandPrimary),
          ],
        ],
      ),
    );
    final wrap = action.wrap;
    return wrap == null ? button : wrap(button);
  }
}

/// 狀態列左側的 26pt 填色圓形圖示（`Main.dc.html:1227`）。
class _ToneIcon extends StatelessWidget {
  const _ToneIcon({required this.tone});

  final TmsCalloutTone tone;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      size: const Size(TmsSize.calloutIcon, TmsSize.calloutIcon),
      painter: _ToneIconPainter(tone: tone),
    );
  }
}

/// 自己畫而不用 `CupertinoIcons` 的 `*_circle_fill` 字形。
///
/// 原型的圖示是兩個顏色：圓是語氣色、裡面的符號是白的。字形只有一個顏色，
/// 實心圓字形裡的符號是鏤空的，淡底色會從鏤空處透出來，變成淡紅色的驚嘆號而
/// 不是白色的。符號的筆畫粗細（2.4）也由字型決定，調不動。
class _ToneIconPainter extends CustomPainter {
  const _ToneIconPainter({required this.tone});

  final TmsCalloutTone tone;

  /// 原型的圖示畫在 24 × 24 的 viewBox 上（`Main.dc.html:1227`），這裡把畫布
  /// 等比放大到實際邊長後照抄座標。
  static const double _viewBox = 24;
  static const double _strokeWidth = 2.4;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width / _viewBox);
    canvas.drawCircle(const Offset(12, 12), 11, Paint()..color = tone.symbol);
    final stroke = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = _strokeWidth
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..color = TmsColors.brandOnPrimary;
    switch (tone) {
      // 驚嘆號：一豎加一點。
      case TmsCalloutTone.error || TmsCalloutTone.caution:
        canvas.drawLine(const Offset(12, 6.8), const Offset(12, 13.2), stroke);
        _drawDot(canvas, const Offset(12, 17.2));
      // 暫停：兩豎。
      case TmsCalloutTone.warn:
        canvas.drawLine(
          const Offset(9.8, 8.3),
          const Offset(9.8, 15.7),
          stroke,
        );
        canvas.drawLine(
          const Offset(14.2, 8.3),
          const Offset(14.2, 15.7),
          stroke,
        );
      // 小寫 i：一點加一豎。
      case TmsCalloutTone.info:
        canvas.drawLine(const Offset(12, 11), const Offset(12, 16.6), stroke);
        _drawDot(canvas, const Offset(12, 7.6));
      case TmsCalloutTone.success:
        canvas.drawPath(
          Path()
            ..moveTo(7.4, 12.4)
            ..lineTo(10.5, 15.5)
            ..lineTo(16.6, 8.9),
          stroke,
        );
      case TmsCalloutTone.neutral:
        canvas.drawLine(
          const Offset(8.6, 8.6),
          const Offset(15.4, 15.4),
          stroke,
        );
        canvas.drawLine(
          const Offset(15.4, 8.6),
          const Offset(8.6, 15.4),
          stroke,
        );
    }
    canvas.restore();
  }

  /// 原型那個 `h.01` 的路徑是長度幾乎為零、端點收圓的線段，畫出來就是一個
  /// 直徑等於筆畫粗細的圓點。
  void _drawDot(Canvas canvas, Offset at) {
    canvas.drawCircle(
      at,
      _strokeWidth / 2,
      Paint()..color = TmsColors.brandOnPrimary,
    );
  }

  @override
  bool shouldRepaint(_ToneIconPainter oldDelegate) => oldDelegate.tone != tone;
}

/// 交辦引言標註（`Main.dc.html:1268`）：白底、左側一條品牌色色條，像記事本與
/// 郵件裡引用別人說的話那一塊。
///
/// 不用狀態列的淡底色卡，是因為這塊不是在講這張單的狀態，而是把別人寫的字原
/// 樣貼出來。做成有底色的卡會跟狀態列搶同一種讀法，員工會以為又出事了。
class TmsQuoteCallout extends StatelessWidget {
  const TmsQuoteCallout({
    super.key,
    required this.title,
    required this.entries,
    this.onAcknowledge,
    this.acknowledgeLabel = '知道了',
  });

  /// 標題列的文字，例如「交辦 2 則 · 開工前請先看」。
  final String title;

  final List<TmsQuoteEntry> entries;

  /// 「知道了」文字鍵。不給就不畫這顆鍵。
  final VoidCallback? onAcknowledge;

  final String acknowledgeLabel;

  /// 左側色條離上下緣各留 10，不貼齊圓角（`Main.dc.html:1270`）。
  static const double _barInset = TmsSpacing.sm2;

  @override
  Widget build(BuildContext context) {
    final acknowledge = onAcknowledge;
    return Stack(
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.lg2,
            TmsSpacing.md,
            TmsSpacing.md2,
            TmsSpacing.md,
          ),
          decoration: BoxDecoration(
            color: TmsColors.brandOnPrimary,
            borderRadius: BorderRadius.circular(TmsRadius.md),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(
                    CupertinoIcons.bubble_left,
                    size: TmsSize.quoteIcon,
                    color: TmsColors.brandPrimary,
                  ),
                  const SizedBox(width: TmsSpacing.xs2),
                  Expanded(
                    child: Text(
                      title,
                      style: TmsText.subheadlineEmphasized.copyWith(
                        fontWeight: FontWeight.w700,
                        color: TmsColors.brandPrimary,
                      ),
                    ),
                  ),
                  if (acknowledge != null)
                    CupertinoButton(
                      padding: EdgeInsets.zero,
                      minimumSize: const Size(0, TmsSize.minTouchTarget),
                      onPressed: acknowledge,
                      child: Text(
                        acknowledgeLabel,
                        style: TmsText.subheadlineEmphasized.copyWith(
                          color: TmsColors.brandPrimary,
                        ),
                      ),
                    ),
                ],
              ),
              for (final entry in entries) _buildEntry(entry),
            ],
          ),
        ),
        Positioned(
          left: 0,
          top: _barInset,
          bottom: _barInset,
          child: Container(
            width: TmsSize.quoteBar,
            decoration: const BoxDecoration(
              color: TmsColors.brandPrimary,
              borderRadius: BorderRadius.horizontal(
                right: Radius.circular(TmsSize.quoteBar),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildEntry(TmsQuoteEntry entry) {
    final node = Padding(
      padding: const EdgeInsets.only(top: TmsSpacing.sm2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 內文在上、出處在下：員工要的是那句話，誰在什麼時候寫的是看完之後
          // 才會追的事。
          _segment(
            entry.idPrefix,
            'content',
            entry.text,
            Text(entry.text, style: TmsText.body.copyWith(height: 1.4)),
          ),
          const SizedBox(height: TmsSpacing.xxs),
          _segment(
            entry.idPrefix,
            'meta',
            entry.meta,
            Text(entry.meta, style: TmsText.footnote),
          ),
        ],
      ),
    );
    final wrap = entry.wrap;
    return wrap == null ? node : wrap(node);
  }
}

/// 交辦引言標註裡的一則。
class TmsQuoteEntry {
  const TmsQuoteEntry({
    required this.text,
    required this.meta,
    this.idPrefix,
    this.wrap,
  });

  /// 內文與出處兩段各自掛的 accessibility id 的前綴，例如給
  /// `task.handover.0` 就得到 `task.handover.0.content` 與
  /// `task.handover.0.meta`。不給就兩段都不掛 id。
  final String? idPrefix;

  final String text;

  /// 出處那一行，例如「交班單 · 阿美 · 09:30」。
  final String meta;

  /// 給呼叫端把自己的 accessibility 節點包在這一則外面用。
  final Widget Function(Widget node)? wrap;
}

/// 狀態摘要卡左側那顆圓形圖示的符號（`Main.dc.html:1771`）。
///
/// 照形狀命名而不照狀態命名：同一個形狀會被不同狀態用到（等審核與等接單都是
/// 時鐘），照狀態命名會變成兩個值畫出同一個圖。
enum TmsStatusSummaryGlyph {
  /// 在等某件事發生。
  clock(CupertinoIcons.clock),

  /// 已經完成的一步。
  checkmark(CupertinoIcons.checkmark),

  /// 出事了，要人補東西。
  exclamation(CupertinoIcons.exclamationmark),

  /// 這張單被收回去了。
  cross(CupertinoIcons.xmark),

  /// 停在這裡。
  pause(CupertinoIcons.pause_solid),

  /// 正在修。
  wrench(CupertinoIcons.wrench),

  /// 手機現在沒有網路。
  offline(CupertinoIcons.wifi_slash);

  const TmsStatusSummaryGlyph(this.icon);

  final IconData icon;
}

/// 狀態摘要卡（`Main.dc.html:1771`）：白卡上一顆隨狀態變色的圓形圖示，右邊一
/// 句標題加一句說明。
///
/// 放在詳情頁最上面，先回答「現在到哪了」，底下的時間軸才給過程。顏色與形狀
/// 由呼叫端依狀態決定：同一張卡要表達七種狀態，對照表散在各畫面會不一致，但
/// 哪一種狀態算哪一類是畫面自己的規則，元件不猜。
class TmsStatusSummaryCard extends StatelessWidget {
  const TmsStatusSummaryCard({
    super.key,
    required this.glyph,
    required this.glyphBackground,
    required this.title,
    required this.detail,
    this.detailColor = TmsColors.secondaryLabel,
  });

  final TmsStatusSummaryGlyph glyph;

  /// 圓形圖示的底色。符號一律白色。
  final Color glyphBackground;

  final String title;

  /// 標題下面那一句。
  final String detail;

  /// 說明的字色。需要把狀況講成紅字時（例如主管沒核准）由呼叫端換色。
  final Color detailColor;

  /// 圓形圖示裡那個白色符號的邊長（`Main.dc.html:1771`）。
  static const double _glyphSize = 20;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        vertical: TmsSpacing.md2,
        horizontal: TmsSpacing.lg,
      ),
      decoration: BoxDecoration(
        color: TmsColors.cardBackground,
        borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
      ),
      child: Row(
        children: [
          Container(
            width: TmsSize.statusSummaryIcon,
            height: TmsSize.statusSummaryIcon,
            decoration: BoxDecoration(
              color: glyphBackground,
              shape: BoxShape.circle,
            ),
            alignment: Alignment.center,
            child: Icon(
              glyph.icon,
              size: _glyphSize,
              color: TmsColors.brandOnPrimary,
            ),
          ),
          const SizedBox(width: TmsSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: TmsText.headline),
                const SizedBox(height: TmsSpacing.xxs),
                Text(detail, style: _bodyStyle.copyWith(color: detailColor)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
