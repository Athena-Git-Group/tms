import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_status_chip.dart';
import 'package:tms_staff_app/core/widgets/tms_tag.dart';
import 'package:tms_staff_app/features/ticket/repair_labels.dart';

/// 狀態標籤的用詞是驗收的斷言目標：首頁的任務列與待認領單卡都拿它說明一張
/// 單現在的狀態，所以這些字不能隨版面調整一起被改掉。這組測試把每一種狀態的
/// 字面值釘在測試裡，改動用詞時會先在這裡紅掉，而不是等模擬器跑完整套 BDD
/// 才發現。
///
/// 配色和用詞一起釘。設計稿的狀態色表（房務在 `Main.dc.html:3544-3555`、
/// 工務在 `Main.dc.html:2868` 與 `Main.dc.html:3835`）是員工在清單上分辨狀態
/// 的唯一線索，換一組色就等於換了一套視覺語言，所以每一組底色與字色都比對到
/// 色票常數。
void main() {
  test('每一種任務狀態對到固定的中文說法', () {
    expect(tmsStatusAppearance(state: 'pool').text, '待認領');
    expect(tmsStatusAppearance(state: 'accepted').text, '待開始');
    expect(tmsStatusAppearance(state: 'doing').text, '進行中');
    expect(tmsStatusAppearance(state: 'deferred').text, '已延後');
    expect(tmsStatusAppearance(state: 'interrupted').text, '已中斷');
    expect(tmsStatusAppearance(state: 'cancelled').text, '已取消');
  });

  test('做完的單依任務類型分成待查房與已送達', () {
    expect(
      tmsStatusAppearance(state: 'done', type: 'cleaning').text,
      '待查房',
    );
    expect(tmsStatusAppearance(state: 'done', type: 'amenity').text, '已送達');
  });

  test('查房結束的單依是否免查房分成兩種說法', () {
    expect(
      tmsStatusAppearance(state: 'inspected', inspection: 'waived').text,
      '完成 · 免查房',
    );
    expect(
      tmsStatusAppearance(state: 'inspected', inspection: 'passed').text,
      '查房通過',
    );
  });

  test('認不得的狀態值原樣顯示', () {
    expect(tmsStatusAppearance(state: 'brand-new-state').text,
        'brand-new-state');
  });

  // 設計稿 Main.dc.html:3547。
  test('還沒開工又被退回重做的單，標籤改說退回重做並配紅色系', () {
    final appearance = tmsStatusAppearance(state: 'accepted', redoCount: 1);
    expect(appearance.text, '退回重做');
    expect(appearance.background, TmsColors.errorContainer);
    expect(appearance.foreground, TmsColors.onErrorContainer);
  });

  // 設計稿 Main.dc.html:3547：退回次數只影響還沒開工的單。
  test('已經開工的單不論退回幾次都照原狀態上色', () {
    expect(tmsStatusAppearance(state: 'doing', redoCount: 2).text, '進行中');
    expect(
      tmsStatusAppearance(state: 'done', type: 'cleaning', redoCount: 2).text,
      '待查房',
    );
  });

  // 設計稿 Main.dc.html:3544-3555。
  test('房務每一種狀態的底色與字色成對固定', () {
    void expectColors(
      TmsStatusAppearance appearance,
      Object background,
      Object foreground,
    ) {
      expect(appearance.background, background);
      expect(appearance.foreground, foreground);
    }

    expectColors(
      tmsStatusAppearance(state: 'pool'),
      TmsColors.brandAccentContainer,
      TmsColors.brandOnAccentContainer,
    );
    expectColors(
      tmsStatusAppearance(state: 'accepted'),
      TmsColors.neutralContainer,
      TmsColors.onNeutralContainer,
    );
    expectColors(
      tmsStatusAppearance(state: 'doing'),
      TmsColors.brandPrimary,
      TmsColors.brandOnPrimary,
    );
    expectColors(
      tmsStatusAppearance(state: 'deferred'),
      TmsColors.brandAccentContainer,
      TmsColors.brandOnAccentContainer,
    );
    expectColors(
      tmsStatusAppearance(state: 'interrupted'),
      TmsColors.otherContainer,
      TmsColors.onOtherContainer,
    );
    expectColors(
      tmsStatusAppearance(state: 'done', type: 'cleaning'),
      TmsColors.brandContainer,
      TmsColors.brandOnContainerStrong,
    );
    expectColors(
      tmsStatusAppearance(state: 'inspected', inspection: 'waived'),
      TmsColors.groupedBackground,
      TmsColors.onSuccessContainer,
    );
    expectColors(
      tmsStatusAppearance(state: 'inspected', inspection: 'passed'),
      TmsColors.successContainer,
      TmsColors.onSuccessContainer,
    );
    expectColors(
      tmsStatusAppearance(state: 'cancelled'),
      TmsColors.neutralContainer,
      TmsColors.onNeutralContainer,
    );
  });

  // 設計稿 Main.dc.html:2868。
  test('工務單六種狀態各自的說法與配色', () {
    void expectChip(
      String state,
      String text,
      Object background,
      Object foreground,
    ) {
      final appearance = repairStatusAppearance(state: state);
      expect(appearance.text, text);
      expect(appearance.background, background);
      expect(appearance.foreground, foreground);
    }

    expectChip(
      'pending_review',
      '等主管審核',
      TmsColors.otherContainer,
      TmsColors.onOtherContainer,
    );
    expectChip(
      'pool',
      '待認領',
      TmsColors.brandContainer,
      TmsColors.brandOnContainerStrong,
    );
    expectChip(
      'accepted',
      '待開始',
      TmsColors.neutralContainer,
      TmsColors.onNeutralContainer,
    );
    expectChip(
      'doing',
      '維修中',
      TmsColors.brandPrimary,
      TmsColors.brandOnPrimary,
    );
    expectChip(
      'paused',
      '已暫停',
      TmsColors.brandAccentContainer,
      TmsColors.brandOnAccentContainer,
    );
    expectChip(
      'done',
      '已完修',
      TmsColors.successContainer,
      TmsColors.onSuccessContainer,
    );
  });

  // 設計稿 Main.dc.html:3835。
  test('急件在還沒動工與已暫停時併進狀態標籤，成為一顆紅標籤', () {
    for (final state in ['accepted', 'paused']) {
      final appearance = repairStatusAppearance(state: state, isUrgent: true);
      expect(
        appearance.text,
        '急件 · ${repairStatusAppearance(state: state).text}',
      );
      expect(appearance.background, TmsColors.errorContainer);
      expect(appearance.foreground, TmsColors.onErrorContainer);
    }
  });

  // 設計稿 Main.dc.html:3835：併顆只發生在 accepted 與 paused 兩種狀態。
  test('急件在審核中、待認領、維修中與已完修時不併進狀態標籤', () {
    for (final state in ['pending_review', 'pool', 'doing', 'done']) {
      final urgent = repairStatusAppearance(state: state, isUrgent: true);
      final plain = repairStatusAppearance(state: state);
      expect(urgent.text, plain.text);
      expect(urgent.background, plain.background);
      expect(urgent.foreground, plain.foreground);
    }
  });

  test('工務單認不得的狀態值原樣顯示並配中性色', () {
    final appearance = repairStatusAppearance(state: 'brand-new-state');
    expect(appearance.text, 'brand-new-state');
    expect(appearance.background, TmsColors.neutralContainer);
    expect(appearance.foreground, TmsColors.onNeutralContainer);
  });

  test('家具／設備在單卡方塊上縮成設備，其餘類別照詳情頁的說法', () {
    expect(repairCategoryShortText('furniture_equipment'), '設備');
    expect(repairCategoryShortText('air_conditioning'), '冷氣');
    expect(repairCategoryShortText('door_lock'), '門鎖');
  });

  // 設計稿 Main.dc.html:961（狀態標籤）與 Main.dc.html:281（小標籤）。狀態標籤
  // 和小標籤是兩種角色、兩個尺寸，把小標籤調成狀態標籤的內距會讓待認領單卡上
  // 並排的那幾顆標籤撐開卡片。
  testWidgets('狀態標籤的內距是 4/10，小標籤的內距是 3/8', (tester) async {
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: Column(
          children: [
            TmsStatusChip(state: 'accepted'),
            const TmsTag(label: '急件', tone: TmsTagTone.danger),
          ],
        ),
      ),
    );

    EdgeInsets paddingOf(Type widget) {
      final container = tester.widget<Container>(
        find.descendant(
          of: find.byType(widget),
          matching: find.byType(Container),
        ),
      );
      return container.padding! as EdgeInsets;
    }

    expect(
      paddingOf(TmsStatusChip),
      const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    );
    expect(
      paddingOf(TmsTag),
      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
    );
  });
}
