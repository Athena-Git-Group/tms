import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/connection_status.dart';
import 'package:tms_staff_app/core/system_status.dart';
import 'package:tms_staff_app/core/theme.dart';

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
