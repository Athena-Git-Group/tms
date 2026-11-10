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
import '../../core/widgets/tms_timeline.dart';
import 'repair_labels.dart';

/// 工務單詳情的房務視角（`Main.dc.html:1746`）。
///
/// 報修的人打開這一頁要知道的只有三件事：單走到哪一步、我還能做什麼、當初報
/// 了什麼。所以版面由上而下是一句話的狀態摘要、一條四步的審核時間軸、照片與
/// 內容，動作只有撤回與補資料再送出兩顆。
///
/// 和工務視角分成兩支畫面而不是同一支加分支，是因為兩邊要回答的問題不同。工
/// 務要的是「現在修到第幾分鐘、下一步按哪顆」，所以那一頁以計時當主角；房務
/// 要的是「主管核了沒、工務接了沒」，那是一條時間軸。把兩種版面壓在同一支畫
/// 面裡，每加一個狀態就要同時想兩個角色看到什麼，兩邊都會做不乾淨。
class TicketDetailHousekeeping extends StatelessWidget {
  const TicketDetailHousekeeping({
    super.key,
    required this.ticket,
    required this.errorMessage,
    required this.onWithdraw,
    required this.onResubmit,
    required this.onAddPhoto,
    required this.onOpenPhoto,
  });

  /// 這張單；還沒載回來是 null。
  final RepairTicketDetail? ticket;

  final String? errorMessage;

  final VoidCallback onWithdraw;
  final VoidCallback onResubmit;
  final VoidCallback onAddPhoto;

  /// 點第幾張照片（從 0 起算）要放大。
  final void Function(int index) onOpenPhoto;

  /// 捲多少之後導覽列換成毛玻璃。這一頁最上面就是白卡，捲一點點卡的上緣就鑽
  /// 到導覽列底下，所以門檻訂得比有大標題區的頁面低得多
  /// （`Main.dc.html:4764`）。
  static const double _scrollThreshold = 4;

  /// 這一頁的毛玻璃晚到七成才淡掉；底下一路都是同一塊灰，早淡會看成一條橫帶
  /// （`Main.dc.html:4762`）。
  static const double _glassFadeStop = 0.7;

  @override
  Widget build(BuildContext context) {
    final detail = ticket;
    final title = detail == null
        ? '工務單'
        : '${detail.ticketNo} · ${repairCategoryText(detail.category)}';
    return CupertinoPageScaffold(
      backgroundColor: TmsColors.groupedBackground,
      // 置底操作列自己吃掉底部安全區，外面再包一層就會留兩倍白。
      child: SafeArea(
        bottom: false,
        child: Column(
          children: [
            Expanded(
              child: TmsScrollNavBarScroll(
                threshold: _scrollThreshold,
                builder: (context, scrolled) => Stack(
                  children: [
                    _buildScrollView(detail),
                    Positioned(
                      top: 0,
                      left: 0,
                      right: 0,
                      child: _buildNavBar(context, scrolled, title),
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

  Widget _buildNavBar(BuildContext context, bool scrolled, String title) {
    // 標題不隨捲動換內容：這一頁沒有標題區可以捲走，單號與類別從頭到尾都是
    // 導覽列上唯一那一行字（`Main.dc.html:4821`）。
    return TmsScrollNavBar(
      scrolled: scrolled,
      // 靜止時原型是全透明，而此時導覽列底下就是頁面自己的灰，兩者看起來
      // 一樣；寫成灰色才不會被元件判成深色標題區而把返回鈕壓暗。
      restingBackground: TmsColors.groupedBackground,
      restingForeground: TmsColors.label,
      restingTitle: title,
      scrolledTitle: title,
      glassFadeStop: _glassFadeStop,
      leading: TmsNavBarBackButton(
        style: TmsNavBarButtonStyle.plain,
        onPressed: () => Navigator.of(context).maybePop(),
      ),
    );
  }

  Widget _buildScrollView(RepairTicketDetail? detail) {
    final error = errorMessage;
    final Widget body;
    if (error != null) {
      body = Text(error, style: TmsText.body);
    } else if (detail == null) {
      body = const Center(child: CupertinoActivityIndicator());
    } else {
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: _spaced(_sections(detail)),
      );
    }
    return SingleChildScrollView(
      // 導覽列疊在內容上面，內容自己要讓開那麼高，否則摘要卡的第一行永遠
      // 鑽在導覽列底下。
      padding: const EdgeInsets.fromLTRB(
        TmsSpacing.screenEdge,
        TmsSize.frozenNavBarHeight + TmsSpacing.md,
        TmsSpacing.screenEdge,
        TmsSpacing.xl2,
      ),
      child: body,
    );
  }

  /// 區塊之間一律 12（`Main.dc.html:1769`）。
  List<Widget> _spaced(List<Widget> sections) {
    final spaced = <Widget>[];
    for (var i = 0; i < sections.length; i++) {
      if (i > 0) {
        spaced.add(const SizedBox(height: TmsSpacing.md));
      }
      spaced.add(sections[i]);
    }
    return spaced;
  }

  List<Widget> _sections(RepairTicketDetail detail) {
    final summary = _summaryOf(detail);
    final note = _actionNote(detail);
    return [
      TmsSemantics(
        id: 'ticket.status-summary-card',
        label: '${summary.title} ${summary.detail}',
        child: ExcludeSemantics(
          child: TmsStatusSummaryCard(
            glyph: summary.glyph,
            glyphBackground: summary.background,
            title: summary.title,
            detail: summary.detail,
            detailColor: summary.detailColor,
          ),
        ),
      ),
      _sectionWithHeader(
        '進度',
        TmsSemantics(
          id: 'ticket.timeline',
          explicitChildNodes: true,
          child: TmsTimeline(steps: _timelineSteps(detail)),
        ),
      ),
      ?_photoSection(detail),
      _sectionWithHeader('內容', _infoCard(detail)),
      if (note != null)
        TmsSemantics(
          id: 'ticket.action-note',
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: TmsSpacing.screenEdge,
            ),
            child: Text(note, style: TmsText.footnote.copyWith(height: 1.4)),
          ),
        ),
    ];
  }

  /// 小標加一張卡。小標與卡之間 6、左右各留 16（`Main.dc.html:1790`）。
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


  /// 摘要卡要說的話，以及那顆圓形圖示的形狀與底色。
  ///
  /// 顏色對照原型的九個分支（`Main.dc.html:4801`）：還在等的事是橘的、要員工
  /// 回頭補東西的是紅的、工務手上的是品牌色、結案的是綠或灰。
  ({
    String title,
    String detail,
    TmsStatusSummaryGlyph glyph,
    Color background,
    Color detailColor,
  })
  _summaryOf(RepairTicketDetail detail) {
    final who = detail.assigneeName;
    switch (detail.state) {
      case 'pending_review':
        final createdAt = detail.createdAt;
        // 分鐘數要有開單時刻才算得出來；沒有就只留後半句，不寫「已送出 0
        // 分鐘」騙人。
        final sent = createdAt == null
            ? ''
            : '已送出 ${repairMinutesSince(createdAt)} 分鐘 · ';
        return (
          title: '等主管審核',
          detail: '$sent審核前可以補照片或撤回。',
          glyph: TmsStatusSummaryGlyph.clock,
          background: TmsColors.systemOrange,
          detailColor: TmsColors.secondaryLabel,
        );
      case 'rejected':
        final reason = detail.rejectReason;
        return (
          title: '主管未核准，需要你補資料',
          detail: '${reason == null ? '' : '$reason。'}補好後按下方「補資料再送出」。',
          glyph: TmsStatusSummaryGlyph.exclamation,
          background: TmsColors.systemRed,
          detailColor: TmsColors.onErrorContainer,
        );
      case 'cancelled':
        return (
          title: '已撤回',
          detail: '主管與工務都不會再看到這張單。',
          glyph: TmsStatusSummaryGlyph.cross,
          background: TmsColors.quaternaryLabel,
          detailColor: TmsColors.secondaryLabel,
        );
      case 'pool':
        return (
          title: '等工務接單',
          detail: detail.isUrgent ? '急件已直接送工務。' : '主管已核准。',
          glyph: TmsStatusSummaryGlyph.clock,
          background: TmsColors.systemOrange,
          detailColor: TmsColors.secondaryLabel,
        );
      case 'accepted':
        return (
          title: who == null ? '已接單' : '工務 $who 已接單',
          detail: '還沒開始維修。',
          glyph: TmsStatusSummaryGlyph.checkmark,
          background: TmsColors.brandPrimary,
          detailColor: TmsColors.secondaryLabel,
        );
      case 'doing':
        return (
          title: who == null ? '維修中' : '工務 $who 維修中',
          detail: '修好會通知你。',
          glyph: TmsStatusSummaryGlyph.wrench,
          background: TmsColors.brandPrimary,
          detailColor: TmsColors.secondaryLabel,
        );
      case 'done':
        return (
          title: '已修好',
          detail: who == null ? '工務已回報完修。' : '工務 $who 回報完修。',
          glyph: TmsStatusSummaryGlyph.checkmark,
          background: TmsColors.systemGreen,
          detailColor: TmsColors.secondaryLabel,
        );
      default:
        return (
          title: repairStateText(detail.state),
          detail: '',
          glyph: TmsStatusSummaryGlyph.clock,
          background: TmsColors.quaternaryLabel,
          detailColor: TmsColors.secondaryLabel,
        );
    }
  }


  /// 四步時間軸：房務送出 → 主管審核 → 工務處理 → 完修。
  ///
  /// 每一步的狀態由後端那一個 state 推出來，畫面不另外記一份進度——別的裝置把
  /// 單推進之後，自己記的那一份就是舊的。
  List<TmsTimelineStep> _timelineSteps(RepairTicketDetail detail) {
    final state = detail.state;
    final who = detail.assigneeName;
    final createdAt = detail.createdAt;
    final sentTime = createdAt == null ? null : repairClockText(createdAt);

    final TmsTimelineStep review;
    if (state == 'cancelled') {
      review = const TmsTimelineStep(
        label: '已撤回',
        status: TmsTimelineStatus.failed,
      );
    } else if (detail.isUrgent) {
      review = TmsTimelineStep(
        label: '主管審核',
        status: TmsTimelineStatus.done,
        note: '系統判定急件（${detail.urgentRule ?? '規則'}），'
            '略過審核直接送工務，已同步通知主管',
        time: sentTime,
      );
    } else if (state == 'pending_review') {
      review = const TmsTimelineStep(
        label: '主管審核',
        status: TmsTimelineStatus.current,
        note: '等待主管審核',
      );
    } else if (state == 'rejected') {
      review = TmsTimelineStep(
        label: '主管未核准',
        status: TmsTimelineStatus.failed,
        note: detail.rejectReason,
      );
    } else {
      final downgrade = detail.urgentDowngradeReason;
      review = TmsTimelineStep(
        label: '主管審核',
        status: TmsTimelineStatus.done,
        note: downgrade == null ? '已核准' : '主管改為一般件：$downgrade',
      );
    }

    final bool workReached =
        state == 'pool' ||
        state == 'accepted' ||
        state == 'doing' ||
        state == 'done';
    final TmsTimelineStep work;
    if (state == 'done') {
      work = const TmsTimelineStep(
        label: '工務處理',
        status: TmsTimelineStatus.done,
      );
    } else if (workReached) {
      work = TmsTimelineStep(
        label: '工務處理',
        status: TmsTimelineStatus.current,
        note: who == null
            ? '還在待認領，等工務接單'
            : '工務 $who ${state == 'doing' ? '維修中' : '已接單'}',
      );
    } else {
      work = const TmsTimelineStep(
        label: '工務處理',
        status: TmsTimelineStatus.pending,
      );
    }

    return [
      TmsTimelineStep(
        label: '房務送出',
        status: TmsTimelineStatus.done,
        note: '房務 ${detail.reporterName}',
        time: sentTime,
      ),
      review,
      work,
      state == 'done'
          ? TmsTimelineStep(
              label: '完修',
              status: TmsTimelineStatus.done,
              note: who == null ? null : '工務 $who',
            )
          : const TmsTimelineStep(
              label: '完修',
              status: TmsTimelineStatus.pending,
            ),
    ];
  }


  /// 能不能補照片看的是後端收不收：契約 appendRepairTicketPhotos 在單結案後
  /// 回 409，所以這裡用同一條界線決定要不要畫出「補照片」格，不另外猜一套。
  Widget? _photoSection(RepairTicketDetail detail) {
    final canAdd = detail.state != 'done' && detail.state != 'cancelled';
    final photos = detail.photos;
    if (photos.isEmpty && !canAdd) {
      return null;
    }
    final head = '照片（${photos.length} 張，含浮水印）';
    return _sectionWithHeader(
      head,
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
                  semanticsLabel: '放大 ${detail.ticketNo} 照片 ${i + 1}',
                  onTap: () => onOpenPhoto(i),
                ),
              ),
            if (canAdd)
              TmsSemantics(
                id: 'ticket.photo.add-button',
                child: TmsAddPhotoButton.inGrid(
                  label: '補照片',
                  onTap: onAddPhoto,
                ),
              ),
          ],
        ),
      ),
    );
  }


  Widget _infoCard(RepairTicketDetail detail) {
    final rows = <({String key, String value})>[
      (key: '房號', value: detail.roomNo),
      (key: '類別', value: _categoryValue(detail)),
      (key: '說明', value: detail.description ?? '—'),
      (key: '報修人', value: _reporterValue(detail)),
    ];
    return ClipRRect(
      borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
      child: ColoredBox(
        color: TmsColors.cardBackground,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < rows.length; i++)
              _InfoRow(
                label: rows[i].key,
                value: rows[i].value,
                showDivider: i < rows.length - 1,
              ),
          ],
        ),
      ),
    );
  }

  String _categoryValue(RepairTicketDetail detail) {
    final category = repairCategoryText(detail.category);
    if (detail.isUrgent) {
      final rule = detail.urgentRule;
      return rule == null ? '$category（急件）' : '$category（急件 · $rule）';
    }
    if (detail.urgentDowngradeReason != null) {
      return '$category（原為急件，主管已改為一般件）';
    }
    return category;
  }

  String _reporterValue(RepairTicketDetail detail) {
    final createdAt = detail.createdAt;
    return createdAt == null
        ? detail.reporterName
        : '${detail.reporterName} · ${repairClockText(createdAt)}';
  }

  /// 列表底下那一句，講的是「這個狀態下你還能做什麼」
  /// （`Main.dc.html:4836`）。單已經撤回或結案時沒有這一句。
  String? _actionNote(RepairTicketDetail detail) {
    switch (detail.state) {
      case 'pending_review':
        return '主管審核前可以補照片或撤回；核准後只能補照片。';
      case 'rejected':
        return '打開原本的單修改，不用整張重填。';
      case 'pool':
      case 'accepted':
      case 'doing':
        return '工務處理中仍可補照片，例如問題變嚴重時。';
      default:
        return null;
    }
  }


  /// 置底操作列。這一頁一個狀態最多只有一顆鍵，兩顆不會同時出現。
  ///
  /// 撤回只在主管還沒審核前成立（`Main.dc.html:4812`）。工務已經認領的單讓按
  /// 鈕直接不存在，而不是先顯示再讓員工點了才報 409——那是拿錯誤訊息當說明書。
  Widget? _buildActionBar(RepairTicketDetail detail) {
    if (detail.state == 'pending_review') {
      return TmsActionBar(
        primary: TmsSemantics(
          id: 'ticket.withdraw-button',
          child: TmsPillButton(
            label: '撤回工務單',
            style: TmsPillButtonStyle.dangerOutlined,
            onPressed: onWithdraw,
          ),
        ),
      );
    }
    if (detail.state == 'rejected') {
      return TmsActionBar(
        primary: TmsSemantics(
          id: 'ticket.resubmit-button',
          child: TmsPillButton(label: '補資料再送出', onPressed: onResubmit),
        ),
      );
    }
    return null;
  }
}

/// 「內容」卡裡的一列：左邊固定寬的欄名，右邊值（`Main.dc.html:1838`）。
///
/// 欄名寬度寫死 64 而不是讓它自己撐開，四列的值才會對齊在同一條直線上；值靠
/// 左而不是靠右，是因為「說明」那一列可能長到換行，靠右的長句讀起來要從不同
/// 位置起跳。
class _InfoRow extends StatelessWidget {
  const _InfoRow({
    required this.label,
    required this.value,
    required this.showDivider,
  });

  final String label;
  final String value;
  final bool showDivider;

  /// `Main.dc.html:4445` 的 `kvW`，中文介面是 64。
  static const double _labelWidth = 64;

  @override
  Widget build(BuildContext context) {
    return Container(
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
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: _labelWidth,
            child: Text(
              label,
              style: TmsText.body.copyWith(color: TmsColors.secondaryLabel),
            ),
          ),
          const SizedBox(width: TmsSpacing.md),
          Expanded(child: Text(value, style: TmsText.body)),
        ],
      ),
    );
  }
}
