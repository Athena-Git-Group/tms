import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_category_square.dart';
import 'package:tms_staff_app/core/widgets/tms_tag.dart';
import 'package:tms_staff_app/features/pool/pool_widgets.dart';

/// 待認領單卡上的每一段文字都是驗收要讀的值（來源、退池原因、接手進度、未接
/// 提醒），而這些值現在畫成一列會換行的小標籤。這組測試盯兩件事：標籤的文字
/// 有沒有跟著進 accessibility label（定位與讀值要落在同一個節點），以及整張卡
/// 在最窄的手機寬度下排不排得開。
void main() {
  Widget host(Widget child, {double width = 375}) {
    return CupertinoApp(
      home: Center(child: SizedBox(width: width, child: child)),
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
                PoolCardField(id: 'pool.card.317.source-tag', text: '請假退回'),
                PoolCardField(
                  id: 'pool.card.317.handoff-progress',
                  text: '浴室已刷完，還沒換床單',
                  tone: TmsTagTone.warn,
                ),
              ],
            ),
          ],
        ),
      ),
    );

    expect(find.text('請假退回'), findsOneWidget);
    expect(find.text('浴室已刷完，還沒換床單'), findsOneWidget);
    expect(
      tester.getSemantics(find.text('浴室已刷完，還沒換床單')).identifier,
      'pool.card.317.handoff-progress',
    );
    expect(tester.takeException(), isNull);
    semantics.dispose();
  });

  testWidgets('房號方塊：四個字的區域名縮字級，不撐破 56pt 的方塊', (tester) async {
    await tester.pumpWidget(
      host(
        const Row(
          children: [
            TmsRoomSquare(type: 'cleaning', where: '317'),
            TmsRoomSquare(type: 'other', where: '公共區域'),
          ],
        ),
      ),
    );

    for (final where in ['317', '公共區域']) {
      expect(tester.getSize(find.text(where)).width, lessThanOrEqualTo(56));
    }
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
}
