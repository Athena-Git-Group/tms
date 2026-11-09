import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/cupertino.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_timeline.dart';

/// 直列時間軸是工務單詳情與遺失物物品詳情共用的進度元件（`Main.dc.html:1777-1812`
/// 與 `Main.dc.html:669`）。這組測試盯的是三件肉眼要很仔細才看得出來、但一壞
/// 整張單就被讀錯的事：貫穿中線哪一段該是綠的、還沒走到的步驟不能帶時間、
/// 沒通過的那一步要紅字。
void main() {
  const boundaryKey = Key('timeline-boundary');
  const width = 375.0;

  Widget host(List<TmsTimelineStep> steps) {
    return CupertinoApp(
      home: Align(
        alignment: Alignment.topLeft,
        child: RepaintBoundary(
          key: boundaryKey,
          child: SizedBox(width: width, child: TmsTimeline(steps: steps)),
        ),
      ),
    );
  }

  TmsTimelineStep step(
    String label,
    TmsTimelineStatus status, {
    String? note,
    String? time,
  }) {
    return TmsTimelineStep(
      label: label,
      status: status,
      note: note,
      time: time,
    );
  }

  /// 中線上某個高度實際畫出來的顏色。中線跨在圖示圓心上，圓心在 x = 28。
  Future<Color> lineColorAt(WidgetTester tester, double y) async {
    final boundary = tester.renderObject<RenderRepaintBoundary>(
      find.byKey(boundaryKey),
    );
    final bytes = await tester.runAsync(() async {
      final image = await boundary.toImage(pixelRatio: 1);
      final data = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
      image.dispose();
      return data!.buffer.asUint8List();
    });
    final offset = ((y.round() * width.round()) + 28) * 4;
    final rgba = Uint8List.sublistView(bytes!, offset, offset + 4);
    return Color.fromARGB(rgba[3], rgba[0], rgba[1], rgba[2]);
  }

  testWidgets('每一步的名稱、例外註記與時間都在，未到的步驟不顯示時間', (tester) async {
    await tester.pumpWidget(
      host([
        step('房務送出', TmsTimelineStatus.done, time: '09:12'),
        step(
          '主管審核',
          TmsTimelineStatus.done,
          note: '系統判定急件（規則），略過審核直接送工務，已同步通知主管',
          time: '09:12',
        ),
        step('工務處理', TmsTimelineStatus.current, time: '09:30'),
        // 還沒發生的步驟即使呼叫端給了時刻也不能畫出來：那會被讀成「排定在
        // 這個時間」。
        step('完修', TmsTimelineStatus.pending, time: '11:00'),
      ]),
    );

    expect(find.text('房務送出'), findsOneWidget);
    expect(find.text('完修'), findsOneWidget);
    expect(find.text('系統判定急件（規則），略過審核直接送工務，已同步通知主管'), findsOneWidget);
    expect(find.text('09:30'), findsOneWidget);
    expect(find.text('11:00'), findsNothing);
  });

  testWidgets('沒通過的那一步，註記是紅字；還沒走到的那一步，步驟名淡化', (tester) async {
    await tester.pumpWidget(
      host([
        step('房務送出', TmsTimelineStatus.done, time: '09:12'),
        step('主管未核准', TmsTimelineStatus.failed, note: '這間房本週已修過兩次', time: '09:20'),
        step('工務處理', TmsTimelineStatus.pending),
      ]),
    );

    expect(
      tester.widget<Text>(find.text('這間房本週已修過兩次')).style!.color,
      TmsColors.onErrorContainer,
    );
    expect(
      tester.widget<Text>(find.text('工務處理')).style!.color,
      TmsColors.quaternaryLabel,
    );
    expect(
      tester.widget<Text>(find.text('主管未核准')).style!.color,
      TmsColors.label,
    );
  });

  testWidgets('中線只有「上一步完成、下一步已開始」那一段是綠的', (tester) async {
    await tester.pumpWidget(
      host([
        step('房務送出', TmsTimelineStatus.done, time: '09:12'),
        step('主管審核', TmsTimelineStatus.done, time: '09:18'),
        step('工務處理', TmsTimelineStatus.current, time: '09:30'),
        step('完修', TmsTimelineStatus.pending),
      ]),
    );

    // 每列 44 高，列與列之間的中線落在 44 的整數倍附近。
    expect(await lineColorAt(tester, 40), TmsColors.systemGreen);
    expect(await lineColorAt(tester, 84), TmsColors.systemGreen);
    // 第三步還在進行、第四步還沒開始，這一段不能亮綠。
    expect(await lineColorAt(tester, 128), TmsColors.separator);
  });

  testWidgets('每一步的外層包裝由呼叫端決定，每一步都包得到', (tester) async {
    final wrapped = <String>[];
    await tester.pumpWidget(
      host([
        for (final label in const ['房務送出', '主管審核', '工務處理', '完修'])
          TmsTimelineStep(
            label: label,
            status: TmsTimelineStatus.pending,
            wrap: (node) {
              wrapped.add(label);
              return node;
            },
          ),
      ]),
    );

    expect(wrapped, ['房務送出', '主管審核', '工務處理', '完修']);
    expect(tester.takeException(), isNull);
  });
}
