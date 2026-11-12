import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/widgets/tms_step_track.dart';

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
