import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/widgets/tms_step_track.dart';

/// 步驟條是房務任務頁與工務單詳情共用的同一個元件，但兩邊掛 accessibility id
/// 的方式不一樣（一個用步驟名、一個用序號，而且工務還多一層 `--current`）。
/// 這組測試盯的是元件有沒有把包裝交回給呼叫端、以及四步在一般手機寬度下排不
/// 排得開——這兩件事壞掉時畫面不會報錯，要等模擬器跑完整套 BDD 才看得到。
void main() {
  Widget host(Widget child, {double width = 375}) {
    return CupertinoApp(
      home: Center(child: SizedBox(width: width, child: child)),
    );
  }

  testWidgets('每一步的外層包裝由呼叫端決定，四步都包得到', (tester) async {
    final wrapped = <String>[];
    await tester.pumpWidget(
      host(
        TmsStepTrack(
          steps: [
            for (final step in const ['待開始', '維修中', '完修', '驗收'])
              TmsStepTrackStep(
                label: step,
                status: step == '維修中'
                    ? TmsStepStatus.current
                    : TmsStepStatus.pending,
                wrap: (node) {
                  wrapped.add(step);
                  return node;
                },
              ),
          ],
        ),
      ),
    );

    expect(wrapped, ['待開始', '維修中', '完修', '驗收']);
    expect(find.text('維修中'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('四步在最窄的手機寬度下排得開，不會溢出', (tester) async {
    await tester.pumpWidget(
      host(
        width: 320,
        TmsStepTrack(
          steps: const [
            TmsStepTrackStep(label: '已接', status: TmsStepStatus.done),
            TmsStepTrackStep(label: '進行中', status: TmsStepStatus.done),
            TmsStepTrackStep(label: '完成', status: TmsStepStatus.current),
            TmsStepTrackStep(label: '免查房', status: TmsStepStatus.pending),
          ],
        ),
      ),
    );

    expect(tester.takeException(), isNull);
  });
}
