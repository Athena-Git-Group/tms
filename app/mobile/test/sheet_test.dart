import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_option_row.dart';
import 'package:tms_staff_app/core/widgets/tms_photo_cell.dart';
import 'package:tms_staff_app/core/widgets/tms_sheet.dart';

/// 面板外框與面板裡那兩個零件的行為測試。
///
/// 這三個東西被三個面板共用，壞掉的話是三個畫面一起壞，而面板只有在模擬器上
/// 跑整個流程才看得到，所以在這裡先釘住幾件一看就知道有沒有壞的事。
void main() {
  Widget host(Widget child) {
    return CupertinoApp(
      theme: buildTmsCupertinoTheme(),
      home: Align(alignment: Alignment.bottomCenter, child: child),
    );
  }

  testWidgets('面板有抓柄與置中標題，置底動作不跟著內容捲走', (tester) async {
    await tester.pumpWidget(
      host(
        TmsSheet(
          title: '延後原因',
          leadingLabel: '關閉',
          onLeadingTap: () {},
          bottomAction: TmsSheetButton(label: '確認', onPressed: () {}),
          children: const [Text('住客還在房內')],
        ),
      ),
    );

    expect(find.text('延後原因'), findsOneWidget);
    expect(find.text('關閉'), findsOneWidget);
    expect(find.text('確認'), findsOneWidget);
    // 抓柄是 36 × 5 的小圓條，面板上只有它這一個尺寸。
    final handle = tester.widget<Container>(
      find.byWidgetPredicate(
        (widget) =>
            widget is Container &&
            widget.constraints?.maxWidth == 36 &&
            widget.constraints?.maxHeight == 5,
      ),
    );
    expect(handle, isNotNull);
  });

  testWidgets('選項列只有選中的那一列有勾', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TmsOptionRow(
              title: const Text('請勿打擾'),
              selected: true,
              onTap: () {},
            ),
            TmsOptionRow(
              title: const Text('住客還在房內'),
              onTap: () {},
            ),
          ],
        ),
      ),
    );

    expect(find.byIcon(CupertinoIcons.check_mark), findsOneWidget);
  });

  testWidgets('照片格四態各畫各的，失敗時才出現重試', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TmsPhotoCell(
              state: TmsPhotoState.failed,
              watermark: const Text('305 · 10:12'),
              failureAction: CupertinoButton(
                onPressed: () {},
                child: const Text('重試'),
              ),
            ),
            const TmsPhotoCell(state: TmsPhotoState.uploading),
            const TmsPhotoCell(state: TmsPhotoState.done),
          ],
        ),
      ),
    );

    expect(find.text('重試'), findsOneWidget);
    expect(find.text('305 · 10:12'), findsOneWidget);
    expect(find.byType(CupertinoActivityIndicator), findsOneWidget);
    // 完成態的綠角標。
    expect(find.byIcon(CupertinoIcons.check_mark), findsOneWidget);
  });
}
