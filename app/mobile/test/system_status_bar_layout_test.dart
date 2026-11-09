import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/connection_status.dart';
import 'package:tms_staff_app/core/system_status.dart';
import 'package:tms_staff_app/core/theme.dart';

/// 釘住連線狀態列的兩段留白，兩者都是原型量得出來、肉眼看不出來的值。
///
/// 下緣那 16 是七個畫面共用的一條規則（`Main.dc.html:76`：捲動容器 `gap: 16px`），
/// 過去由各畫面自己傳進來，七個掛點傳出了 0 與 12 兩種值。把它搬進元件之後，
/// 這兩條測試是它唯一的保證：改動元件或新增掛點時留白走掉會在這裡紅掉。
void main() {
  const nextBlock = Key('next-block');

  tearDown(() {
    ConnectionStatus.instance.setOffline(false);
    ConnectionStatus.instance.setUnstable(false);
  });

  Future<void> pumpBar(WidgetTester tester) {
    return tester.pumpWidget(
      const CupertinoApp(
        home: Column(
          children: [
            SystemStatusBar(),
            SizedBox(key: nextBlock, height: 40, width: double.infinity),
          ],
        ),
      ),
    );
  }

  /// 深色離線列那一塊的外框。`SystemStatusBar` 自己的框已經含了下緣留白，所以
  /// 量留白要量這一層。
  Finder barBox() => find.descendant(
    of: find.byType(SystemStatusBar),
    matching: find.byType(Container),
  );

  testWidgets('離線列與下方內容相距 16（Main.dc.html:76）', (tester) async {
    ConnectionStatus.instance.setOffline(true);
    await pumpBar(tester);

    final gap =
        tester.getTopLeft(find.byKey(nextBlock)).dy -
        tester.getBottomLeft(barBox()).dy;
    expect(gap, SystemStatusBar.contentGap);
    expect(gap, 16);
  });

  testWidgets('連線異常列與下方內容相距 16（Main.dc.html:76）', (tester) async {
    ConnectionStatus.instance.setUnstable(true);
    await pumpBar(tester);

    final gap =
        tester.getTopLeft(find.byKey(nextBlock)).dy -
        tester.getBottomLeft(barBox()).dy;
    expect(gap, 16);
  });

  testWidgets('離線列標題與說明相距 1（Main.dc.html:84）', (tester) async {
    ConnectionStatus.instance.setOffline(true);
    await pumpBar(tester);

    final gap =
        tester.getTopLeft(find.text(offlineDescription)).dy -
        tester.getBottomLeft(find.text(offlineTitle)).dy;
    expect(gap, TmsSpacing.xxxs);
    expect(gap, 1);
  });

  testWidgets('連線正常時整塊不佔高度，下緣留白也不佔', (tester) async {
    await pumpBar(tester);

    expect(tester.getSize(find.byType(SystemStatusBar)).height, 0);
    expect(tester.getTopLeft(find.byKey(nextBlock)).dy, 0);
  });
}
