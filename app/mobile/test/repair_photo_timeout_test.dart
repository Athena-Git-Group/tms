import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/repair_photo_image.dart';

import 'support/fixture_jpeg.dart';

class _StallFirstServer {
  _StallFirstServer._(this._server) {
    _server.listen((request) {
      requests++;
      if (requests == 1) {
        return;
      }
      request.response
        ..headers.contentType = ContentType('image', 'jpeg')
        ..add(fixtureJpeg)
        ..close();
    });
  }

  static Future<_StallFirstServer> start() async => _StallFirstServer._(
    await HttpServer.bind(InternetAddress.loopbackIPv4, 0),
  );

  final HttpServer _server;
  int requests = 0;

  String get baseUrl => 'http://${_server.address.address}:${_server.port}';

  Future<void> close() => _server.close(force: true);
}

String? photoState(WidgetTester tester) =>
    tester.getSemantics(find.byType(RepairPhotoImage)).value;

Future<void> waitReal(WidgetTester tester, Duration duration) async {
  await tester.runAsync(() => Future<void>.delayed(duration));
  await tester.pump();
}

void main() {
  late ApiClient original;
  HttpOverrides? overrides;

  setUp(() {
    original = ApiClient.instance;
    overrides = HttpOverrides.current;
    HttpOverrides.global = null;
  });

  tearDown(() {
    HttpOverrides.global = overrides;
    ApiClient.instance = original;
  });

  testWidgets('讀照片卡住會逾時顯示照片無法顯示，重新打開時重抓並顯示圖片', (tester) async {
    final server = (await tester.runAsync(_StallFirstServer.start))!;
    addTearDown(() => tester.runAsync(server.close));
    ApiClient.instance = ApiClient(
      baseUrl: server.baseUrl,
      photoReadTimeout: const Duration(milliseconds: 300),
    );

    Widget photo() => const CupertinoApp(
      home: SizedBox(
        width: 120,
        height: 120,
        child: RepairPhotoImage(photoId: 'p-1'),
      ),
    );

    await tester.pumpWidget(photo());
    expect(photoState(tester), 'loading');

    while (server.requests == 0) {
      await waitReal(tester, const Duration(milliseconds: 20));
    }
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump();
    expect(photoState(tester), 'failed');
    expect(find.text('照片無法顯示'), findsOneWidget);

    await tester.pumpWidget(const CupertinoApp(home: SizedBox()));
    await tester.pumpWidget(photo());
    for (var i = 0; i < 100 && photoState(tester) != 'loaded'; i++) {
      await waitReal(tester, const Duration(milliseconds: 50));
    }

    expect(photoState(tester), 'loaded');
    expect(server.requests, 2);
  });
}
