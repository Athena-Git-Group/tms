import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_photo_action_sheet.dart';
import 'package:tms_staff_app/core/widgets/tms_photo_viewer.dart';

/// 報修、完修回報、開工單與工務單詳情共用加入照片的選單與全螢幕照片檢視，
/// 其中一個壞掉就是四個畫面一起壞。兩者都要操作過才看得到，模擬器上不容易
/// 每次都跑到，所以這裡先釘住選完回傳什麼、以及切換照片時標題列的張數跟不
/// 跟著走。
void main() {
  Widget host(Widget child) {
    return CupertinoApp(theme: buildTmsCupertinoTheme(), home: child);
  }

  // 原型 Main.dc.html:1682-1691。
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

  // 原型 Main.dc.html:1857-1872。
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
