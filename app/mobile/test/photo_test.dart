import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_photo_action_sheet.dart';
import 'package:tms_staff_app/core/widgets/tms_photo_viewer.dart';

void main() {
  Widget host(Widget child) {
    return CupertinoApp(theme: buildTmsCupertinoTheme(), home: child);
  }

  testWidgets('加入照片的選單讓員工選相機或相簿，取消不選任何一個', (tester) async {
    final picked = <TmsPhotoSource?>[];
    await tester.pumpWidget(
      host(
        Builder(
          builder: (context) => CupertinoButton(
            onPressed: () async =>
                picked.add(await showTmsPhotoActionSheet(context)),
            child: const Text('加入照片'),
          ),
        ),
      ),
    );

    await tester.tap(find.text('加入照片'));
    await tester.pumpAndSettle();
    expect(find.text('拍照'), findsOneWidget);
    expect(find.text('從相簿選擇'), findsOneWidget);
    await tester.tap(find.text('從相簿選擇'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('加入照片'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();

    expect(picked, [TmsPhotoSource.library, null]);
  });

  testWidgets('照片檢視的張數跟著切換走，最後一張沒有下一張', (tester) async {
    await tester.pumpWidget(
      host(
        const TmsPhotoViewer(
          title: 'W-0421 · 家具／設備',
          photos: [
            TmsViewerPhoto(description: '第一張'),
            TmsViewerPhoto(description: '第二張'),
          ],
        ),
      ),
    );

    expect(find.text('1 / 2'), findsOneWidget);
    expect(find.byIcon(CupertinoIcons.chevron_back), findsNothing);

    await tester.tap(find.byIcon(CupertinoIcons.chevron_forward));
    await tester.pumpAndSettle();

    expect(find.text('2 / 2'), findsOneWidget);
    expect(find.byIcon(CupertinoIcons.chevron_forward), findsNothing);
    expect(find.byIcon(CupertinoIcons.chevron_back), findsOneWidget);
    expect(find.text('雙指縮放查看細節 · 左右滑動切換'), findsOneWidget);
  });
}
