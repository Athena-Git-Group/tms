import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';
import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/repair_photo_capture.dart';
import 'package:tms_staff_app/core/test_backdoor.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_photo_action_sheet.dart';

import 'support/fixture_jpeg.dart';

class _RecordingApi extends ApiClient {
  final created = <({String purpose, DateTime takenAt})>[];
  final uploaded = <String, List<int>>{};

  @override
  Future<String> createRepairPhoto({
    required String purpose,
    required DateTime takenAt,
  }) async {
    created.add((purpose: purpose, takenAt: takenAt));
    return 'photo-${created.length}';
  }

  @override
  Future<void> uploadRepairPhotoContent(
    String photoId,
    List<int> jpegBytes,
  ) async {
    uploaded[photoId] = jpegBytes;
  }
}

void main() {
  late _RecordingApi api;
  late ApiClient originalApi;
  late RepairPhotoPicker originalPicker;
  final fileTime = DateTime(2026, 11, 26, 14, 32);

  setUp(() {
    originalApi = ApiClient.instance;
    originalPicker = repairPhotoPicker;
    api = _RecordingApi();
    ApiClient.instance = api;
  });

  tearDown(() {
    ApiClient.instance = originalApi;
    repairPhotoPicker = originalPicker;
    TestHooks.instance.reset();
  });

  test('拍照打開相機，從相簿選擇打開相簿', () async {
    final sources = <ImageSource>[];
    repairPhotoPicker = (source) async {
      sources.add(source);
      return XFile.fromData(fixtureJpeg, lastModified: fileTime);
    };

    await captureRepairPhoto(TmsPhotoSource.camera);
    await captureRepairPhoto(TmsPhotoSource.library);

    expect(sources, [ImageSource.camera, ImageSource.gallery]);
  });

  test('在相機或相簿裡取消，沒有照片，也不建立照片', () async {
    repairPhotoPicker = (_) async => null;

    expect(await captureRepairPhoto(TmsPhotoSource.camera), isNull);
    expect(api.created, isEmpty);
  });

  test('拿到的不是 JPEG 就是照片不能用，不上傳', () async {
    repairPhotoPicker = (_) async => XFile.fromData(
      Uint8List.fromList([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A]),
      lastModified: fileTime,
    );

    await expectLater(
      captureRepairPhoto(TmsPhotoSource.library),
      throwsA(isA<RepairPhotoUnsupported>()),
    );
    expect(api.created, isEmpty);
  });

  test('相機或相簿權限被拒，分得出是哪一種', () async {
    repairPhotoPicker = (source) async => throw PlatformException(
      code: source == ImageSource.camera
          ? 'camera_access_denied'
          : 'photo_access_denied',
    );

    await expectLater(
      captureRepairPhoto(TmsPhotoSource.camera),
      throwsA(
        isA<RepairPhotoAccessDenied>().having(
          (e) => e.source,
          'source',
          TmsPhotoSource.camera,
        ),
      ),
    );
    await expectLater(
      captureRepairPhoto(TmsPhotoSource.library),
      throwsA(
        isA<RepairPhotoAccessDenied>().having(
          (e) => e.source,
          'source',
          TmsPhotoSource.library,
        ),
      ),
    );
  });

  test('拍攝時間用照片檔案的時間，上傳的是拍到的那份位元組', () async {
    repairPhotoPicker = (_) async =>
        XFile.fromData(fixtureJpeg, lastModified: fileTime);

    final photo = await captureRepairPhoto(TmsPhotoSource.camera);
    final photoId = await uploadRepairPhoto(photo!, purpose: 'report');

    expect(photo.takenAt, fileTime);
    expect(api.created.single.purpose, 'report');
    expect(api.created.single.takenAt, fileTime);
    expect(api.uploaded[photoId], fixtureJpeg);
  });

  test('拿不到檔案時間時用當下時間', () async {
    TestHooks.instance.injectedClockIso = '2026-11-26T09:15+08:00';
    repairPhotoPicker = (_) async => XFile.fromData(fixtureJpeg);

    final photo = await captureRepairPhoto(TmsPhotoSource.camera);

    expect(photo!.takenAt, deviceNow());
  });

  test('測試版不打開相機或相簿，回固定的測試照片', () async {
    TestHooks.instance.injectedClockIso = '2026-11-26T09:15+08:00';

    final file = await testBuildRepairPhotoPicker(ImageSource.camera);
    final bytes = await file!.readAsBytes();

    expect(bytes.sublist(0, 3), [0xFF, 0xD8, 0xFF]);
    expect(await file.lastModified(), deviceNow());
  });

  Future<CapturedRepairPhoto?> pickThrough(
    WidgetTester tester,
    String menuItem,
  ) async {
    CapturedRepairPhoto? picked;
    await tester.pumpWidget(
      CupertinoApp(
        theme: buildTmsCupertinoTheme(),
        home: Builder(
          builder: (context) => CupertinoButton(
            onPressed: () async => picked = await pickRepairPhoto(context),
            child: const Text('加入照片'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('加入照片'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(menuItem));
    await tester.pumpAndSettle();
    return picked;
  }

  testWidgets('相機權限被拒，跳出沒有相機權限的提示', (tester) async {
    repairPhotoPicker = (_) async =>
        throw PlatformException(code: 'camera_access_denied');

    expect(await pickThrough(tester, '拍照'), isNull);
    expect(find.text('沒有相機權限'), findsOneWidget);
    expect(find.text('請到手機的設定裡允許，才能附照片。'), findsOneWidget);
    await tester.tap(find.text('好'));
    await tester.pumpAndSettle();
    expect(find.text('沒有相機權限'), findsNothing);
  });

  testWidgets('相簿權限被拒，跳出沒有相簿權限的提示', (tester) async {
    repairPhotoPicker = (_) async =>
        throw PlatformException(code: 'photo_access_denied');

    expect(await pickThrough(tester, '從相簿選擇'), isNull);
    expect(find.text('沒有相簿權限'), findsOneWidget);
  });

  testWidgets('選到不是 JPEG 的照片，跳出這張照片不能用', (tester) async {
    repairPhotoPicker = (_) async =>
        XFile.fromData(Uint8List.fromList([0x89, 0x50, 0x4E, 0x47]));

    expect(await pickThrough(tester, '從相簿選擇'), isNull);
    expect(find.text('這張照片不能用'), findsOneWidget);
    expect(find.text('請改用拍照，或選另一張照片。'), findsOneWidget);
    expect(api.created, isEmpty);
  });
}
