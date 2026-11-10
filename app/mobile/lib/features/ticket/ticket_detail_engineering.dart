import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_callout.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_photo_cell.dart';
import '../../core/widgets/tms_pill_button.dart';
import '../../core/widgets/tms_scroll_nav_bar.dart';
import '../../core/widgets/tms_step_track.dart';
import 'repair_labels.dart';

/// 工務單詳情的工務視角（`Main.dc.html:1911`）。
///
/// 版面與房務任務頁同一套：滿版標題區、橫向步驟條、一條狀態列、照片、資訊列
/// 表、置底一主一次兩顆鍵。維修中的單整塊標題區換成品牌色深底，員工隔著走廊
/// 也認得出手上這張單正在跑。
///
/// 和房務視角分成兩支畫面的理由見 [TicketDetailHousekeeping]。
class TicketDetailEngineering extends StatelessWidget {
  const TicketDetailEngineering({
    super.key,
    required this.ticket,
    required this.errorMessage,
    required this.resolutionLabel,
    required this.onClaim,
    required this.onStart,
    required this.onReleaseToPool,
    required this.onComplete,
    required this.onOpenPhoto,
  });

  /// 這張單；還沒載回來是 null。
  final RepairTicketDetail? ticket;

  final String? errorMessage;

  /// 本次在這個畫面完修時選的處理結果文字。後端的工務單詳情沒有回傳
  /// resolutionType（只有 resolutionNote，上游缺口已回報），所以狀態列裡那
  /// 句結果只在完修的當下講得出來。
  final String? resolutionLabel;

  final VoidCallback onClaim;
  final VoidCallback onStart;
  final VoidCallback onReleaseToPool;
  final VoidCallback onComplete;
  final void Function(int index) onOpenPhoto;

  /// 四步固定這四個名字（`Main.dc.html:3873`）。
  static const List<String> _stepLabels = ['待開始', '維修中', '完修', '驗收'];

  @override
  Widget build(BuildContext context) {
    final detail = ticket;
    final onDark = detail?.state == 'doing';
    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      // 置底操作列自己吃掉底部安全區，外面再包一層就會留兩倍白。
      child: SafeArea(
        bottom: false,
        child: Column(
          children: [
            Expanded(
              child: TmsScrollNavBarScroll(
                builder: (context, scrolled) => Stack(
                  children: [
                    _buildScrollView(detail, onDark: onDark),
                    Positioned(
                      top: 0,
                      left: 0,
                      right: 0,
                      child: _buildNavBar(
                        context,
                        detail,
                        scrolled: scrolled,
                        onDark: onDark,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            if (detail != null) ?_buildActionBar(detail),
          ],
        ),
      ),
    );
  }

  /// 導覽列：靜止時與標題區同色、只寫「工務單」，標題區捲走之後補上地點
  /// （`Main.dc.html:3948`）。
  ///
  /// 原型捲動後還會把計時接在地點後面，計時起算時刻後端還沒出，所以這裡只接
  /// 得到地點。
  Widget _buildNavBar(
    BuildContext context,
    RepairTicketDetail? detail, {
    required bool scrolled,
    required bool onDark,
  }) {
    final where = detail?.roomNo;
    return TmsSemantics(
      id: 'ticket.nav',
      explicitChildNodes: true,
      child: TmsScrollNavBar(
        scrolled: scrolled,
        restingBackground: onDark
            ? TmsColors.brandPrimary
            : TmsColors.cardBackground,
        restingForeground: onDark ? TmsColors.brandOnPrimary : TmsColors.label,
        restingTitle: onDark && where != null ? '$where · 工務單' : '工務單',
        scrolledTitle: where == null ? '工務單' : '$where · 工務單',
        leading: TmsNavBarBackButton(
          onPressed: () => Navigator.of(context).maybePop(),
        ),
      ),
    );
  }

  Widget _buildScrollView(
    RepairTicketDetail? detail, {
    required bool onDark,
  }) {
    final error = errorMessage;
    if (error != null || detail == null) {
      return SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(
          TmsSpacing.screenEdge,
          TmsSize.frozenNavBarHeight + TmsSpacing.md,
          TmsSpacing.screenEdge,
          TmsSpacing.xl2,
        ),
        child: error != null
            ? Text(error, style: TmsText.body)
            : const Center(child: CupertinoActivityIndicator()),
      );
    }
    return SingleChildScrollView(
      // 標題區是滿版的，所以捲動容器本身不留左右內距，底下的區塊各自留。
      padding: const EdgeInsets.only(top: TmsSize.frozenNavBarHeight),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildHero(detail, onDark: onDark),
          Padding(
            padding: const EdgeInsets.fromLTRB(
              TmsSpacing.screenEdge,
              TmsSpacing.md2,
              TmsSpacing.screenEdge,
              TmsSpacing.xl2,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: _spaced(_sections(detail)),
            ),
          ),
        ],
      ),
    );
  }

  /// 標題區以外的區塊之間一律 14（`Main.dc.html:1923`）。
  List<Widget> _spaced(List<Widget> sections) {
    final spaced = <Widget>[];
    for (var i = 0; i < sections.length; i++) {
      if (i > 0) {
        spaced.add(const SizedBox(height: TmsSpacing.md2));
      }
      spaced.add(sections[i]);
    }
    return spaced;
  }


  /// 滿版標題區：地點當主角、問題標題、來源與類別那一行，最後是步驟條。
  ///
  /// 原型維修中時改由 56pt 的計時當主角、地點退到問題前面。後端還沒有計時起
  /// 算時刻，寫不出那個數字，所以維修中也維持地點當主角，只換深底；計時欄位
  /// 補上來之後再把主角換成計時。寧可少一塊也不畫一個停在 00:00 的計時，那會
  /// 讓員工以為自己的工時沒有在算。
  Widget _buildHero(RepairTicketDetail detail, {required bool onDark}) {
    final foreground = onDark ? TmsColors.brandOnPrimary : TmsColors.label;
    final secondary = onDark
        ? TmsColors.brandContainer
        : TmsColors.secondaryLabel;
    return Container(
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.xl,
        TmsSpacing.sm,
        TmsSpacing.xl,
        TmsSpacing.xl,
      ),
      decoration: BoxDecoration(
        color: onDark ? TmsColors.brandPrimary : TmsColors.cardBackground,
        borderRadius: const BorderRadius.only(
          bottomLeft: Radius.circular(TmsRadius.xl),
          bottomRight: Radius.circular(TmsRadius.xl),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TmsSemantics(
            id: 'ticket.hero.where',
            label: detail.roomNo,
            child: ExcludeSemantics(
              child: Text(
                detail.roomNo,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                // 公共區域沒有房號，那幾個字照 56pt 排會塞不下一行。
                style: (detail.isPublicArea
                        ? TmsText.title1
                        : TmsText.displayHero)
                    .copyWith(color: foreground, fontWeight: FontWeight.w800),
              ),
            ),
          ),
          const SizedBox(height: TmsSpacing.xs2),
          Text(
            _heroTitle(detail),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TmsText.headline.copyWith(color: foreground),
          ),
          const SizedBox(height: TmsSpacing.xxs),
          Text(
            _heroMeta(detail),
            style: TmsText.subheadline.copyWith(color: secondary),
          ),
          Padding(
            padding: const EdgeInsets.only(top: TmsSpacing.md2),
            child: _buildStepTrack(detail, onDark: onDark),
          ),
        ],
      ),
    );
  }

  /// 標題區第二行那句話，講的是「這張單要修什麼」。
  ///
  /// 報修的人填了說明就用說明，沒填就退回類別：空著的話這一行會變成一條看不
  /// 出意義的留白，而類別至少講得出是冷氣還是水電。
  String _heroTitle(RepairTicketDetail detail) {
    final description = detail.description;
    if (description == null || description.isEmpty) {
      return repairCategoryText(detail.category);
    }
    return description;
  }

  /// 標題區最後一行：誰報的、什麼類別、多久以前。
  ///
  /// 原型這一行還有「預估 N 分」，後端沒有預估工時欄位，那一段整段不寫。
  String _heroMeta(RepairTicketDetail detail) {
    final createdAt = detail.createdAt;
    return [
      '${detail.reporterName} 報修',
      repairCategoryText(detail.category),
      if (createdAt != null) '${repairMinutesSince(createdAt)} 分鐘前',
    ].join(' · ');
  }

  Widget _buildStepTrack(RepairTicketDetail detail, {required bool onDark}) {
    final currentIndex = _currentStepIndex(detail);
    return TmsSemantics(
      id: 'ticket.step-track',
      explicitChildNodes: true,
      child: TmsStepTrack(
        onDark: onDark,
        steps: [
          for (var i = 0; i < _stepLabels.length; i++)
            _trackStep(i, currentIndex),
        ],
      ),
    );
  }

  /// 目前在四步中的哪一步；對照 dsl 的狀態對照表（待開始＝已接單、維修中、
  /// 完修＝已完修未驗收、驗收＝已驗收）。待認領等其他狀態沒有「目前步驟」。
  int _currentStepIndex(RepairTicketDetail detail) {
    switch (detail.state) {
      case 'accepted':
        return 0;
      case 'doing':
        return 1;
      case 'done':
        return detail.verifiedBy == null ? 2 : 3;
      default:
        return -1;
    }
  }

  /// 一步的畫面：外層節點只在「這一步是目前步驟」時掛 `--current` id，
  /// 內層節點（固定 id＋步驟名）給步驟順序的斷言用。
  ///
  /// 還沒有人接單時 [currentIndex] 是 -1，四步就全部是未到——這時步驟條上沒有
  /// 任何一步是目前步驟，`--current` 也不會出現在畫面上。
  TmsStepTrackStep _trackStep(int index, int currentIndex) {
    final TmsStepStatus status;
    if (index < currentIndex) {
      status = TmsStepStatus.done;
    } else if (index == currentIndex) {
      status = TmsStepStatus.current;
    } else {
      status = TmsStepStatus.pending;
    }
    return TmsStepTrackStep(
      label: _stepLabels[index],
      status: status,
      wrap: (node) {
        final Widget step = TmsSemantics(
          id: 'ticket.stepper.${index + 1}',
          label: _stepLabels[index],
          child: node,
        );
        if (status != TmsStepStatus.current) {
          return step;
        }
        return TmsSemantics(
          id: 'ticket.stepper.${index + 1}--current',
          child: step,
        );
      },
    );
  }


  List<Widget> _sections(RepairTicketDetail detail) {
    return [
      _buildCallouts(detail),
      ?_photoSection(detail),
      _sectionWithHeader('這張單的資訊', _infoCard(detail)),
    ];
  }

  Widget _sectionWithHeader(String header, Widget card) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            TmsSpacing.screenEdge,
            0,
            TmsSpacing.screenEdge,
            TmsSpacing.xs2,
          ),
          child: Text(header, style: TmsText.footnote),
        ),
        card,
      ],
    );
  }


  /// 一頁最多一條狀態列，誰蓋過誰由 [TmsCalloutKind] 的宣告順序決定，這裡只
  /// 說哪幾條成立。
  ///
  /// 原型的暫停與「房間現在進不去」兩條在後端還沒有對應欄位（沒有 paused 狀
  /// 態值，也沒有房況），所以目前永遠不成立，不是漏掉。
  Widget _buildCallouts(RepairTicketDetail detail) {
    return TmsCalloutSlot(
      candidates: [
        _fixedCallout(detail),
        if (detail.state == 'pool')
          TmsStatusCallout(
            kind: TmsCalloutKind.ticketInPool,
            tone: TmsCalloutTone.info,
            title: repairStateText(detail.state),
            body: '還沒有人認領這張單，其他工務同仁都接得到。',
            // 前綴給了，標題與說明才各自成為獨立節點；不給的話那兩段是裸
            // Text，會被併進外層節點的 label，讀狀態的一方拿到的是兩段接起
            // 來的字。
            idPrefix: 'ticket.status-summary-card',
            wrap: (node) => _summaryWrap(node, repairStateText(detail.state)),
          ),
        if (detail.state == 'pending_review')
          const TmsStatusCallout(
            kind: TmsCalloutKind.ticketAwaitingReview,
            tone: TmsCalloutTone.info,
            title: '等主管審核',
            body: '房務報修的一般件，主管核准後才會進待認領。現在先讓你知道。',
          ),
        if (detail.isUrgent)
          TmsStatusCallout(
            kind: TmsCalloutKind.ticketUrgent,
            tone: TmsCalloutTone.error,
            title: detail.urgentRule == null
                ? '急件'
                : '急件 · ${detail.urgentRule}',
            body: '系統依飯店設定的規則判定，已同步通知主管。',
          ),
      ],
    );
  }

  /// 已完修與已驗收共用的那一條。
  ///
  /// 完修結果寫進狀態列內文而不另開一張卡（`Main.dc.html:2013`）：員工掃到這
  /// 一條就看完了整件事，分成兩塊的話得先讀狀態、再往下找結果。
  TmsCalloutSpec? _fixedCallout(RepairTicketDetail detail) {
    if (detail.state != 'done') {
      return null;
    }
    final verifiedBy = detail.verifiedBy;
    final title = verifiedBy == null
        ? '已完修 · 等主管驗收'
        : repairStatusSummaryText(detail);
    if (verifiedBy != null) {
      return TmsStatusCallout(
        kind: TmsCalloutKind.ticketFixed,
        tone: TmsCalloutTone.success,
        title: title,
        body: '這張單已結案。',
        wrap: (node) => _summaryWrap(node, title),
      );
    }
    final parts = <String>[
      if (resolutionLabel != null) '$resolutionLabel。',
      if (detail.resolutionNote != null) '說明：${detail.resolutionNote}。',
    ];
    return TmsStatusCallout(
      kind: TmsCalloutKind.ticketFixed,
      tone: TmsCalloutTone.success,
      title: title,
      body: parts.isEmpty ? '等主管驗收後這張單就結案。' : parts.join(''),
      wrap: (node) => _summaryWrap(
        TmsSemantics(
          id: 'ticket.resolution-label',
          label: parts.join(''),
          child: node,
        ),
        title,
      ),
    );
  }

  /// 工務視角沒有房務那張摘要卡，回答「現在到哪了」的是這一條狀態列，所以它
  /// 掛同一個 id 與同一句話，兩個視角的驗收才問得到同一件事。
  Widget _summaryWrap(Widget node, String title) {
    return TmsSemantics(
      id: 'ticket.status-summary-card',
      label: title,
      child: node,
    );
  }


  /// 工務視角只看得到報修照片，沒有補照片格：補照片是報修的人在自己那一頁做
  /// 的事（`Main.dc.html:2000`）。
  Widget? _photoSection(RepairTicketDetail detail) {
    final photos = detail.photos;
    if (photos.isEmpty) {
      return null;
    }
    return _sectionWithHeader(
      '報修照片（${photos.length} 張，含浮水印）',
      TmsCard(
        style: TmsCardStyle.inSheet,
        padding: const EdgeInsets.all(TmsSpacing.md),
        child: GridView.count(
          crossAxisCount: 3,
          mainAxisSpacing: TmsSpacing.sm,
          crossAxisSpacing: TmsSpacing.sm,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: [
            for (var i = 0; i < photos.length; i++)
              TmsSemantics(
                id: 'ticket.photo.${i + 1}',
                child: TmsPhotoCell(
                  edge: null,
                  semanticsLabel: '放大報修照片 ${i + 1}',
                  onTap: () => onOpenPhoto(i),
                ),
              ),
          ],
        ),
      ),
    );
  }


  Widget _infoCard(RepairTicketDetail detail) {
    final rows = <({String key, String value})>[
      (key: '地點', value: detail.roomNo),
      (key: '類別', value: repairCategoryText(detail.category)),
      (key: '報修人', value: detail.reporterName),
      (key: '負責人', value: detail.assigneeName ?? '等工務認領'),
    ];
    return ClipRRect(
      borderRadius: BorderRadius.circular(TmsRadius.card),
      child: ColoredBox(
        color: TmsColors.cardBackground,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < rows.length; i++)
              _TicketInfoRow(
                label: rows[i].key,
                value: rows[i].value,
                showDivider: i < rows.length - 1,
              ),
          ],
        ),
      ),
    );
  }


  /// 一個狀態一組按鍵（`Main.dc.html:3908`）。
  ///
  /// 原型的「暫停」在這裡還掛不上去：暫停要先有暫停面板與後端的暫停狀態，兩
  /// 者都還沒有，畫一顆按了沒反應的鍵比沒有更糟。「放回待認領」照原型應該收
  /// 進暫停面板，同一個理由先留在這裡，等暫停面板做好再一起搬。
  Widget? _buildActionBar(RepairTicketDetail detail) {
    final secondary = _secondaryAction(detail);
    final primary = _primaryAction(detail);
    if (secondary == null && primary == null) {
      return null;
    }
    return TmsActionBar(secondary: secondary, primary: primary);
  }

  Widget? _secondaryAction(RepairTicketDetail detail) {
    if (detail.isPublicArea &&
        (detail.state == 'accepted' || detail.state == 'doing')) {
      return TmsSemantics(
        id: 'ticket.release-to-pool-button',
        child: TmsPillButton(
          label: '放回待認領',
          style: TmsPillButtonStyle.outlined,
          onPressed: onReleaseToPool,
        ),
      );
    }
    return null;
  }

  Widget? _primaryAction(RepairTicketDetail detail) {
    switch (detail.state) {
      case 'pool':
        return TmsSemantics(
          id: 'ticket.claim-button',
          child: TmsPillButton(
            label: '認領這單',
            glow: TmsPillGlow.alt,
            onPressed: onClaim,
          ),
        );
      case 'accepted':
        return TmsSemantics(
          id: 'ticket.start-button',
          child: TmsPillButton(
            label: '開始維修',
            glow: TmsPillGlow.alt,
            onPressed: onStart,
          ),
        );
      case 'doing':
        return TmsSemantics(
          id: 'ticket.complete-button',
          child: TmsPillButton(
            label: '完修回報',
            glow: TmsPillGlow.alt,
            onPressed: onComplete,
          ),
        );
      default:
        return null;
    }
  }
}

/// 「這張單的資訊」卡裡的一列（`Main.dc.html:2027`）。
///
/// 欄名靠左、值靠右：這張卡的值多半只有兩三個字，靠右對齊之後四列的值排成一
/// 條直線，員工掃一眼就讀完，不必逐列從左邊找起。
class _TicketInfoRow extends StatelessWidget {
  const _TicketInfoRow({
    required this.label,
    required this.value,
    required this.showDivider,
  });

  final String label;
  final String value;
  final bool showDivider;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: TmsSize.rowMinHeight),
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.screenEdge,
        vertical: TmsSpacing.groupedRowVertical,
      ),
      decoration: showDivider
          ? const BoxDecoration(
              border: Border(
                bottom: BorderSide(
                  color: TmsColors.separator,
                  width: TmsSize.hairline,
                ),
              ),
            )
          : null,
      child: Row(
        children: [
          Text(label, style: TmsText.body),
          const SizedBox(width: TmsSpacing.md),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: TmsText.subheadline.copyWith(
                color: TmsColors.secondaryLabel,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
