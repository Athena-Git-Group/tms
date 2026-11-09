import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_category_square.dart';
import 'package:tms_staff_app/core/widgets/tms_category_tile.dart';
import 'package:tms_staff_app/core/widgets/tms_tag.dart';
import 'package:tms_staff_app/features/pool/pool_widgets.dart';

/// 待認領單卡上的每一段文字都是驗收要讀的值（來源、退池原因、接手進度、未接
/// 提醒），而這些值現在畫成一列會換行的小標籤。這組測試盯兩件事：標籤的文字
/// 有沒有跟著進 accessibility label（定位與讀值要落在同一個節點），以及整張卡
/// 在最窄的手機寬度下排不排得開。
void main() {
  Widget host(Widget child, {double width = 375}) {
    return CupertinoApp(
      home: Center(
        child: SizedBox(width: width, child: child),
      ),
    );
  }

  testWidgets('單卡上的標籤同時是定位節點，文字讀得到', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      host(
        PoolCard(
          id: 'pool.card.317',
          children: [
            PoolTagRow(
              tags: const [
                PoolCardField(id: 'pool.card.317.return-reason', text: '小陳請假'),
                PoolCardField(
                  id: 'pool.card.317.status-tag--unaccepted-notified-peer',
                  text: '未接 · 已提醒同仁',
                  tone: TmsTagTone.warn,
                ),
              ],
            ),
          ],
        ),
      ),
    );

    expect(find.text('小陳請假'), findsOneWidget);
    expect(find.text('未接 · 已提醒同仁'), findsOneWidget);
    expect(
      tester.getSemantics(find.text('未接 · 已提醒同仁')).identifier,
      'pool.card.317.status-tag--unaccepted-notified-peer',
    );
    expect(tester.takeException(), isNull);
    semantics.dispose();
  });

  // Main.dc.html:279 的副標行：來源與預估時間寫成一整行 15pt 灰字，排不下就
  // 截字，不是一顆膠囊標籤。
  testWidgets('副標是一整行灰字，定位節點讀得到來源', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      host(
        width: 200,
        const PoolCardSubtitle(id: 'pool.card.317.subtitle', text: '請假退回'),
      ),
    );

    final text = tester.widget<Text>(find.text('請假退回'));
    expect(text.style?.fontSize, 15);
    expect(text.style?.color, TmsColors.secondaryLabel);
    expect(text.maxLines, 1);
    expect(text.overflow, TextOverflow.ellipsis);
    expect(
      tester.getSemantics(find.text('請假退回')).identifier,
      'pool.card.317.subtitle',
    );
    expect(tester.takeException(), isNull);
    semantics.dispose();
  });

  // Main.dc.html:290 的備註塊：整寬灰底、圓角 12，文案帶「做到哪：」開頭。
  testWidgets('接手進度是卡內整塊備註，文案帶「做到哪：」', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      host(
        const PoolCardNote(
          id: 'pool.card.317.handoff-progress',
          text: '浴室已刷完，還沒換床單',
        ),
      ),
    );

    expect(find.text('做到哪：浴室已刷完，還沒換床單'), findsOneWidget);
    expect(
      tester.getSemantics(find.text('做到哪：浴室已刷完，還沒換床單')).identifier,
      'pool.card.317.handoff-progress',
    );
    final box = tester.widget<Container>(
      find
          .ancestor(
            of: find.text('做到哪：浴室已刷完，還沒換床單'),
            matching: find.byType(Container),
          )
          .first,
    );
    final decoration = box.decoration! as BoxDecoration;
    expect(decoration.color, TmsColors.groupedBackground);
    expect(decoration.borderRadius, BorderRadius.circular(TmsRadius.md));
    expect(tester.takeException(), isNull);
    semantics.dispose();
  });

  // Main.dc.html:4251-4252：優先通知要同樓層才講，兩種說法共用卡上同一個位置。
  testWidgets('同樓層標籤：優先通知是品牌實色，一般是淺色，跨樓層不畫', (tester) async {
    expect(
      poolSameFloorTag(id: 'x', sameFloor: false, priorityNotify: true),
      isNull,
    );

    for (final (priorityNotify, label, tone) in const [
      (true, '優先通知 · 同樓層', TmsTagTone.brand),
      (false, '同樓層 · 離你最近', TmsTagTone.info),
    ]) {
      final tag =
          poolSameFloorTag(
                id: 'pool.card.317.same-floor-tag',
                sameFloor: true,
                priorityNotify: priorityNotify,
              )!
              as PoolCardField;
      expect(tag.text, label);
      expect(tag.tone, tone);
    }

    await tester.pumpWidget(
      host(
        PoolTagRow(
          tags: [
            poolSameFloorTag(
              id: 'pool.card.317.same-floor-tag',
              sameFloor: true,
              priorityNotify: true,
            )!,
          ],
        ),
      ),
    );
    expect(find.text('優先通知 · 同樓層'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  // Main.dc.html:249 的 `gap: 16px`：單卡上下各 8，相鄰兩卡之間就是 16。
  testWidgets('相鄰兩張單卡之間是 16', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: const [
            PoolCard(id: 'pool.card.317', children: [Text('317')]),
            PoolCard(id: 'pool.card.313', children: [Text('313')]),
          ],
        ),
      ),
    );

    final first = tester.getRect(find.text('317'));
    final second = tester.getRect(find.text('313'));
    // 卡內上下內距各 16，兩卡之間的外距 16 夾在中間。
    expect(second.top - first.bottom, 16 + 16 + 16);
    expect(tester.takeException(), isNull);
  });

  testWidgets('房號方塊：四個字的區域名縮字級，不撐破 56pt 的方塊', (tester) async {
    await tester.pumpWidget(
      host(
        Row(
          children: [
            TmsRoomSquare(
              where: '317',
              label: tmsCategoryAppearance('cleaning').label,
              palette: tmsCategoryAppearance('cleaning').palette,
            ),
            TmsRoomSquare(
              where: '公共區域',
              label: tmsCategoryAppearance('other').label,
              palette: tmsCategoryAppearance('other').palette,
            ),
          ],
        ),
      ),
    );

    for (final where in ['317', '公共區域']) {
      expect(tester.getSize(find.text(where)).width, lessThanOrEqualTo(56));
    }
    expect(tester.takeException(), isNull);
  });

  // 原型的工務單方塊按「急不急、修好了沒」上色（Main.dc.html:3823），房務側
  // 才按任務類別上色（Main.dc.html:4242）。
  testWidgets('工務單方塊：急件是紅的，一般單是品牌藍綠，已完修轉灰', (tester) async {
    expect(
      tmsRepairSquarePalette(isUrgent: true, isDone: false).background,
      TmsColors.errorContainer,
    );
    expect(
      tmsRepairSquarePalette(isUrgent: false, isDone: false).background,
      TmsColors.brandContainer,
    );
    // 已完修的單不管急不急都是灰的：修完就不必再催。
    expect(
      tmsRepairSquarePalette(isUrgent: true, isDone: true).background,
      TmsColors.groupedBackground,
    );

    await tester.pumpWidget(
      host(
        TmsRoomSquare(
          where: '902',
          label: '設備',
          palette: tmsRepairSquarePalette(isUrgent: true, isDone: false),
        ),
      ),
    );

    expect(find.text('902'), findsOneWidget);
    expect(find.text('設備'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  // Main.dc.html:233 的 48pt 方塊裡放的是 `{{t.room}}`，顏色才代表類別。
  testWidgets('48pt 方塊裡放的是房號，不是類別兩個字', (tester) async {
    await tester.pumpWidget(
      host(
        Row(
          children: [
            TmsCategorySquare(
              where: '317',
              palette: tmsCategoryAppearance('cleaning').palette,
            ),
          ],
        ),
      ),
    );

    expect(find.text('317'), findsOneWidget);
    expect(find.text('打掃'), findsNothing);
    expect(tester.getSize(find.byType(TmsCategorySquare)), const Size(48, 48));
    expect(tester.takeException(), isNull);
  });

  testWidgets('四格分類磚在最窄的手機寬度下排得開，選中的那格帶數量徽章', (tester) async {
    await tester.pumpWidget(
      host(
        width: 320,
        PoolFilterRow(
          tiles: [
            for (final label in const ['全部', '備品', '打掃', '其他'])
              PoolFilterChip(
                id: 'pool.category.$label.chip',
                label: label,
                icon: CupertinoIcons.square_grid_2x2_fill,
                iconBackground: TmsColors.brandPrimary,
                iconForeground: TmsColors.brandOnPrimary,
                selected: label == '備品',
                countId: 'pool.category.$label.count',
                count: 3,
                onTap: () {},
              ),
          ],
        ),
      ),
    );

    expect(find.text('3'), findsNWidgets(4));
    expect(tester.takeException(), isNull);
  });

  // Main.dc.html:4232 的 `hasCount: count > 0`：筆數 0 的分類整顆徽章不畫。
  testWidgets('筆數 0 的分類沒有徽章節點，分類磚本身還在', (tester) async {
    await tester.pumpWidget(
      host(
        PoolFilterRow(
          tiles: [
            for (final (label, count) in const [('全部', 4), ('其他', 0)])
              PoolFilterChip(
                id: 'pool.category.$label.chip',
                label: label,
                icon: CupertinoIcons.square_grid_2x2_fill,
                iconBackground: TmsColors.brandPrimary,
                iconForeground: TmsColors.brandOnPrimary,
                selected: false,
                countId: 'pool.category.$label.count',
                count: count,
                onTap: () {},
              ),
          ],
        ),
      ),
    );

    expect(find.byType(TmsCountBadge), findsOneWidget);
    expect(find.text('4'), findsOneWidget);
    expect(find.text('0'), findsNothing);
    expect(find.text('其他'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  // 原型的選中外環是 `box-shadow: 0 0 0 2px`（Main.dc.html:4232），外擴而不內
  // 縮，所以磚裡的圖示與標籤不會隨著選中往內跳。
  testWidgets('選中與未選中的分類磚，內容位置完全一樣', (tester) async {
    Future<Offset> iconLeftWhenSelected(bool selected) async {
      await tester.pumpWidget(
        host(
          PoolFilterRow(
            tiles: [
              PoolFilterChip(
                id: 'pool.category.全部.chip',
                label: '全部',
                icon: CupertinoIcons.square_grid_2x2_fill,
                iconBackground: TmsColors.brandPrimary,
                iconForeground: TmsColors.brandOnPrimary,
                selected: selected,
                onTap: () {},
              ),
            ],
          ),
        ),
      );
      return tester.getTopLeft(find.byType(Icon));
    }

    expect(await iconLeftWhenSelected(true), await iconLeftWhenSelected(false));
    expect(tester.takeException(), isNull);
  });
}
