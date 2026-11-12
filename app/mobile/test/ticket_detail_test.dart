import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_callout.dart';
import 'package:tms_staff_app/core/widgets/tms_scroll_nav_bar.dart';
import 'package:tms_staff_app/core/widgets/tms_step_track.dart';
import 'package:tms_staff_app/core/widgets/tms_timeline.dart';
import 'package:tms_staff_app/features/ticket/ticket_detail_engineering.dart';
import 'package:tms_staff_app/features/ticket/ticket_detail_housekeeping.dart';

/// 工務單詳情在原型裡是兩支獨立畫面：報修的人看審核走到哪（`Main.dc.html:1746`），
/// 工務看自己要修什麼、下一步按哪顆（`Main.dc.html:1911`）。這組測試盯的是兩邊
/// 真的各畫各的——同一張單在房務端是時間軸加撤回鍵，在工務端是深色標題區加完修
/// 回報鍵——以及後端還沒有的欄位整段不顯示，不填假值。
void main() {
  RepairTicketDetail ticket({
    String state = 'pending_review',
    String? roomNo = '305',
    bool isPublicArea = false,
    String? assigneeName,
    String? description,
    String? rejectReason,
    String? urgentDowngradeReason,
    String? urgentRule,
    String? verifiedBy,
    String? resolutionType,
    String? lastVerifyFailedReason,
    String? lastVerifyFailedBy,
    String? lastVerifyFailedAt,
    bool isUrgent = false,
    DateTime? createdAt,
  }) {
    return RepairTicketDetail(
      taskId: 't-1',
      ticketNo: 'W-0412',
      roomNo: roomNo,
      isPublicArea: isPublicArea,
      category: 'air_conditioning',
      isUrgent: isUrgent,
      state: state,
      reporterName: '阿美',
      createdAt: createdAt,
      description: description,
      assigneeName: assigneeName,
      urgentRule: urgentRule,
      verifiedBy: verifiedBy,
      resolutionType: resolutionType,
      lastVerifyFailedReason: lastVerifyFailedReason,
      lastVerifyFailedBy: lastVerifyFailedBy,
      lastVerifyFailedAt: lastVerifyFailedAt,
      rejectReason: rejectReason,
      urgentDowngradeReason: urgentDowngradeReason,
    );
  }

  Widget housekeeping(RepairTicketDetail detail) {
    return CupertinoApp(
      home: TicketDetailHousekeeping(
        ticket: detail,
        errorMessage: null,
        onWithdraw: () {},
        onAddPhoto: () {},
        onOpenPhoto: (_) {},
      ),
    );
  }

  Widget engineering(RepairTicketDetail detail, {String? resolutionLabel}) {
    return CupertinoApp(
      home: TicketDetailEngineering(
        ticket: detail,
        errorMessage: null,
        resolutionLabel: resolutionLabel,
        onClaim: () {},
        onStart: () {},
        onReleaseToPool: () {},
        onComplete: () {},
        onOpenPhoto: (_) {},
      ),
    );
  }

  group('房務視角（Main.dc.html:1746）', () {
    testWidgets('進度是直列時間軸，四步與工務側步驟條同名，不是橫向步驟條', (tester) async {
      await tester.pumpWidget(housekeeping(ticket(state: 'pool')));

      final timeline = tester.widget<TmsTimeline>(find.byType(TmsTimeline));
      expect(
        [for (final step in timeline.steps) step.label],
        ['待開始', '維修中', '完修', '驗收'],
      );
      expect(find.byType(TmsStepTrack), findsNothing);
    });

    testWidgets('報修送出、等工務接單時停在第一步，後面三步都還沒走到', (tester) async {
      await tester.pumpWidget(housekeeping(ticket(state: 'pool')));

      final timeline = tester.widget<TmsTimeline>(find.byType(TmsTimeline));
      expect(timeline.steps[0].status, TmsTimelineStatus.current);
      expect(timeline.steps[1].status, TmsTimelineStatus.pending);
      expect(timeline.steps[2].status, TmsTimelineStatus.pending);
      expect(timeline.steps[3].status, TmsTimelineStatus.pending);
    });

    testWidgets('已結案時四步全部完成', (tester) async {
      await tester.pumpWidget(
        housekeeping(
          ticket(
            state: 'closed',
            assigneeName: '阿德',
            resolutionType: 'fixed',
            verifiedBy: '王主管',
          ),
        ),
      );

      final timeline = tester.widget<TmsTimeline>(find.byType(TmsTimeline));
      expect([
        for (final step in timeline.steps) step.status,
      ], List.filled(4, TmsTimelineStatus.done));
    });

    testWidgets('沒有任何主管審核的字樣與補資料再送出', (tester) async {
      await tester.pumpWidget(housekeeping(ticket(state: 'pool')));

      expect(find.textContaining('主管審核'), findsNothing);
      expect(find.textContaining('主管未核准'), findsNothing);
      expect(find.text('補資料再送出'), findsNothing);
    });

    testWidgets('急件的判定理由寫在第一步底下', (tester) async {
      await tester.pumpWidget(
        housekeeping(
          ticket(state: 'pool', isUrgent: true, urgentRule: 'VIP · 15:00 入住'),
        ),
      );

      final timeline = tester.widget<TmsTimeline>(find.byType(TmsTimeline));
      expect(timeline.steps[0].note, contains('系統判定急件（VIP · 15:00 入住）'));
    });

    testWidgets('摘要卡講的是狀態加一句後續，不是只有狀態詞（Main.dc.html:4801）', (
      tester,
    ) async {
      await tester.pumpWidget(
        housekeeping(ticket(state: 'doing', assigneeName: '阿德')),
      );

      final card = tester.widget<TmsStatusSummaryCard>(
        find.byType(TmsStatusSummaryCard),
      );
      expect(card.title, '工務 阿德 維修中');
      expect(card.detail, '修好會通知你。');
      expect(card.glyph, TmsStatusSummaryGlyph.wrench);
    });

    testWidgets('已完修時摘要卡用完修結果的短說法講等主管驗收', (tester) async {
      await tester.pumpWidget(
        housekeeping(
          ticket(state: 'done', assigneeName: '阿德', resolutionType: 'fixed'),
        ),
      );

      final card = tester.widget<TmsStatusSummaryCard>(
        find.byType(TmsStatusSummaryCard),
      );
      expect(card.title, '已修好 · 等主管驗收');
    });

    testWidgets('退回重修中講誰在重修，下方帶最近一次不通過的原因與判定人', (tester) async {
      await tester.pumpWidget(
        housekeeping(
          ticket(
            state: 'accepted',
            assigneeName: '阿德',
            lastVerifyFailedReason: '排水仍然很慢',
            lastVerifyFailedBy: '陳主管',
            lastVerifyFailedAt: '2026-11-12T03:00:00Z',
          ),
        ),
      );

      final card = tester.widget<TmsStatusSummaryCard>(
        find.byType(TmsStatusSummaryCard),
      );
      expect(card.title, '退回重修中 · 阿德 處理中');
      expect(find.text('排水仍然很慢'), findsOneWidget);
      expect(find.textContaining('陳主管'), findsOneWidget);
    });

    testWidgets('有開單時刻時第一步的時刻接上（Main.dc.html:4783）', (tester) async {
      final createdAt = DateTime.now().subtract(const Duration(minutes: 7));
      await tester.pumpWidget(
        housekeeping(ticket(state: 'pool', createdAt: createdAt)),
      );

      final timeline = tester.widget<TmsTimeline>(find.byType(TmsTimeline));
      expect(timeline.steps[0].time, isNotNull);
    });

    testWidgets('「內容」列出房號、類別、說明與報修人（Main.dc.html:1836）', (tester) async {
      await tester.pumpWidget(
        housekeeping(ticket(description: '出風口有異音')),
      );

      expect(find.text('內容'), findsOneWidget);
      expect(find.text('房號'), findsOneWidget);
      expect(find.text('305'), findsOneWidget);
      expect(find.text('出風口有異音'), findsOneWidget);
      expect(find.text('阿美'), findsOneWidget);
    });

    testWidgets('撤回只在工務還沒接單前出現，接單後就不再有', (tester) async {
      await tester.pumpWidget(housekeeping(ticket(state: 'pool')));
      expect(find.text('撤回工務單'), findsOneWidget);

      await tester.pumpWidget(
        housekeeping(ticket(state: 'accepted', assigneeName: '阿德')),
      );
      expect(find.text('撤回工務單'), findsNothing);
    });

    testWidgets('這個狀態還能做什麼寫在列表底下（Main.dc.html:4836）', (tester) async {
      await tester.pumpWidget(housekeeping(ticket(state: 'pool')));

      expect(find.text('工務接單前可以補照片或撤回；接單後只能補照片。'), findsOneWidget);
    });

    testWidgets('導覽列標題不隨捲動換內容，從頭到尾都是單號加類別（Main.dc.html:4822）', (
      tester,
    ) async {
      await tester.pumpWidget(housekeeping(ticket()));

      final navBar = tester.widget<TmsScrollNavBar>(
        find.byType(TmsScrollNavBar),
      );
      expect(navBar.restingTitle, 'W-0412 · 冷氣');
      expect(navBar.scrolledTitle, navBar.restingTitle);
    });

    testWidgets('捲動門檻是 4，不是有大標題區那幾頁的 72（Main.dc.html:4764）', (
      tester,
    ) async {
      await tester.pumpWidget(housekeeping(ticket()));

      final scroll = tester.widget<TmsScrollNavBarScroll>(
        find.byType(TmsScrollNavBarScroll),
      );
      expect(scroll.threshold, 4);
    });

    testWidgets('返回鈕不跟著標題區變色，這一頁沒有會變的底色可跟（Main.dc.html:1759）', (
      tester,
    ) async {
      await tester.pumpWidget(housekeeping(ticket()));

      final back = tester.widget<TmsNavBarBackButton>(
        find.byType(TmsNavBarBackButton),
      );
      expect(back.style, TmsNavBarButtonStyle.plain);
    });
  });

  group('工務視角（Main.dc.html:1911）', () {
    testWidgets('維修中時標題區換成品牌色深底，步驟條跟著換深底配色（Main.dc.html:3881）', (
      tester,
    ) async {
      await tester.pumpWidget(engineering(ticket(state: 'doing')));

      final track = tester.widget<TmsStepTrack>(find.byType(TmsStepTrack));
      expect(track.onDark, isTrue);
      final hero = tester.widget<Container>(
        find
            .descendant(
              of: find.byType(TicketDetailEngineering),
              matching: find.byType(Container),
            )
            .first,
      );
      expect(
        (hero.decoration! as BoxDecoration).color,
        TmsColors.brandPrimary,
      );
    });

    testWidgets('不是維修中時標題區是白底，步驟條用淺底配色', (tester) async {
      await tester.pumpWidget(engineering(ticket(state: 'accepted')));

      final track = tester.widget<TmsStepTrack>(find.byType(TmsStepTrack));
      expect(track.onDark, isFalse);
    });

    testWidgets('步驟條四步固定，維修中停在第二步（Main.dc.html:3873）', (tester) async {
      await tester.pumpWidget(engineering(ticket(state: 'doing')));

      final track = tester.widget<TmsStepTrack>(find.byType(TmsStepTrack));
      expect(
        [for (final step in track.steps) step.label],
        ['待開始', '維修中', '完修', '驗收'],
      );
      expect(track.steps[0].status, TmsStepStatus.done);
      expect(track.steps[1].status, TmsStepStatus.current);
    });

    testWidgets('標題區沒有說明時退回類別，不留一行空白', (tester) async {
      await tester.pumpWidget(engineering(ticket(state: 'doing')));
      expect(find.text('冷氣'), findsWidgets);

      await tester.pumpWidget(
        engineering(ticket(state: 'doing', description: '出風口有異音')),
      );
      expect(find.text('出風口有異音'), findsOneWidget);
    });

    testWidgets('後端沒有預估工時，meta 行就不寫「預估 N 分」（RULINGS R11）', (
      tester,
    ) async {
      await tester.pumpWidget(engineering(ticket(state: 'doing')));

      expect(find.textContaining('預估'), findsNothing);
    });

    testWidgets('一個狀態一組按鍵，待認領是認領、維修中是完修回報（Main.dc.html:3908）', (
      tester,
    ) async {
      await tester.pumpWidget(engineering(ticket(state: 'pool')));
      expect(find.text('認領這單'), findsOneWidget);

      await tester.pumpWidget(engineering(ticket(state: 'accepted')));
      expect(find.text('開始維修'), findsOneWidget);

      await tester.pumpWidget(engineering(ticket(state: 'doing')));
      expect(find.text('完修回報'), findsOneWidget);
    });

    testWidgets('公共區域的單才有放回待認領，客房只能走暫停', (tester) async {
      await tester.pumpWidget(
        engineering(ticket(state: 'doing', roomNo: null, isPublicArea: true)),
      );
      expect(find.text('放回待認領'), findsOneWidget);

      await tester.pumpWidget(engineering(ticket(state: 'doing')));
      expect(find.text('放回待認領'), findsNothing);
    });

    // 原型放回待認領之後直接關掉詳情頁（`Main.dc.html:3762`），所以沒有這個
    // 畫面的樣本；這一條的依據是 truth 的 `工務單/dsl.md:78`，它要求放回之後
    // 工務單詳情要顯示狀態摘要卡「待認領」。
    testWidgets('放回待認領之後，工務視角的詳情用狀態摘要卡說它沒有人負責', (tester) async {
      final handle = tester.ensureSemantics();

      await tester.pumpWidget(engineering(ticket(state: 'pool')));

      expect(
        tester
            .getSemantics(
              find.bySemanticsIdentifier('ticket.status-summary-card'),
            )
            .label,
        '待認領',
      );

      await tester.pumpWidget(engineering(ticket(state: 'accepted')));
      expect(
        find.bySemanticsIdentifier('ticket.status-summary-card'),
        findsNothing,
      );

      handle.dispose();
    });

    testWidgets('「這張單的資訊」沒有負責人時寫等工務認領（Main.dc.html:3919）', (
      tester,
    ) async {
      await tester.pumpWidget(engineering(ticket(state: 'pool')));

      expect(find.text('這張單的資訊'), findsOneWidget);
      expect(find.text('負責人'), findsOneWidget);
      expect(find.text('等工務認領'), findsOneWidget);
    });

    testWidgets('完修結果寫進狀態列內文，不另開一張卡（Main.dc.html:2013）', (tester) async {
      await tester.pumpWidget(
        engineering(
          ticket(state: 'done', assigneeName: '阿德'),
          resolutionLabel: '暫時處理，需要再追蹤',
        ),
      );

      expect(find.text('已完修 · 等主管驗收'), findsOneWidget);
      expect(find.text('暫時處理，需要再追蹤。'), findsOneWidget);
    });

    testWidgets('一頁最多一條狀態列：急件又完修時只留完修那一條', (tester) async {
      await tester.pumpWidget(
        engineering(
          ticket(state: 'done', isUrgent: true, urgentRule: 'VIP'),
        ),
      );

      expect(find.text('已完修 · 等主管驗收'), findsOneWidget);
      expect(find.textContaining('急件 · VIP'), findsNothing);
    });

    testWidgets('沒有任何一條狀態列成立時那一塊不佔空間', (tester) async {
      await tester.pumpWidget(engineering(ticket(state: 'accepted')));

      final slot = tester.widget<TmsCalloutSlot>(find.byType(TmsCalloutSlot));
      expect(TmsCalloutSlot.highestPriority(slot.candidates), isNull);
    });
  });
}
