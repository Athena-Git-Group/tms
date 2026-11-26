import 'dart:convert';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

import 'api_client.dart';
import 'test_backdoor.dart';
import 'widgets/tms_alert.dart';
import 'widgets/tms_photo_action_sheet.dart';

class CapturedRepairPhoto {
  const CapturedRepairPhoto({required this.jpeg, required this.takenAt});

  final Uint8List jpeg;

  final DateTime takenAt;
}

typedef RepairPhotoPicker = Future<XFile?> Function(ImageSource source);

RepairPhotoPicker repairPhotoPicker =
    const bool.fromEnvironment('TEST_BACKDOOR')
    ? testBuildRepairPhotoPicker
    : _pickFromDevice;

Future<XFile?> _pickFromDevice(ImageSource source) {
  return ImagePicker().pickImage(
    source: source,
    maxWidth: 1600,
    maxHeight: 1600,
    imageQuality: 80,
  );
}

/// 模擬器沒有相機，自動化測試版改送固定的測試照片。
Future<XFile?> testBuildRepairPhotoPicker(ImageSource source) async {
  return XFile.fromData(_placeholderJpeg, lastModified: deviceNow());
}

Future<CapturedRepairPhoto?> captureRepairPhoto(TmsPhotoSource source) async {
  final XFile? file;
  try {
    file = await repairPhotoPicker(switch (source) {
      TmsPhotoSource.camera => ImageSource.camera,
      TmsPhotoSource.library => ImageSource.gallery,
    });
  } on PlatformException catch (e) {
    if (e.code == 'camera_access_denied' || e.code == 'photo_access_denied') {
      throw RepairPhotoAccessDenied(source);
    }
    rethrow;
  }
  if (file == null) {
    return null;
  }
  final bytes = await file.readAsBytes();
  if (!_isJpeg(bytes)) {
    throw const RepairPhotoUnsupported();
  }
  return CapturedRepairPhoto(jpeg: bytes, takenAt: await _takenAt(file));
}

bool _isJpeg(Uint8List bytes) =>
    bytes.length >= 3 &&
    bytes[0] == 0xFF &&
    bytes[1] == 0xD8 &&
    bytes[2] == 0xFF;

Future<DateTime> _takenAt(XFile file) async {
  try {
    return await file.lastModified();
  } catch (_) {
    return deviceNow();
  }
}

Future<String> uploadRepairPhoto(
  CapturedRepairPhoto photo, {
  required String purpose,
  int? photoIndex,
}) async {
  if (photoIndex != null &&
      TestHooks.instance.uploadFailAtIndex == photoIndex) {
    throw const RepairPhotoUploadFailure();
  }
  final photoId = await ApiClient.instance.createRepairPhoto(
    purpose: purpose,
    takenAt: photo.takenAt,
  );
  await ApiClient.instance.uploadRepairPhotoContent(photoId, photo.jpeg);
  return photoId;
}

Future<CapturedRepairPhoto?> pickRepairPhoto(BuildContext context) async {
  final source = await showTmsPhotoActionSheet(context);
  if (source == null || !context.mounted) {
    return null;
  }
  try {
    return await captureRepairPhoto(source);
  } on RepairPhotoAccessDenied catch (e) {
    if (context.mounted) {
      _showPhotoAlert(
        context,
        title: e.source == TmsPhotoSource.camera ? '沒有相機權限' : '沒有相簿權限',
        message: '請到手機的設定裡允許，才能附照片。',
      );
    }
  } on RepairPhotoUnsupported {
    if (context.mounted) {
      _showPhotoAlert(context, title: '這張照片不能用', message: '請改用拍照，或選另一張照片。');
    }
  }
  return null;
}

void _showPhotoAlert(
  BuildContext context, {
  required String title,
  required String message,
}) {
  showCupertinoDialog<void>(
    context: context,
    builder: (dialogContext) => TmsAlert(
      title: title,
      message: message,
      messageId: 'photoAlert.message',
      actions: [
        TmsAlertAction(
          label: '好',
          id: 'photoAlert.confirm-button',
          style: TmsAlertActionStyle.primary,
          onPressed: () => Navigator.of(dialogContext).pop(),
        ),
      ],
    ),
  );
}

class RepairPhotoAccessDenied implements Exception {
  const RepairPhotoAccessDenied(this.source);

  final TmsPhotoSource source;
}

class RepairPhotoUnsupported implements Exception {
  const RepairPhotoUnsupported();
}

class RepairPhotoUploadFailure implements Exception {
  const RepairPhotoUploadFailure();

  @override
  String toString() => '照片上傳失敗';
}

class RepairPhoto {
  RepairPhoto({required this.capture, required this.watermark});

  final CapturedRepairPhoto capture;

  final String watermark;

  late final ImageProvider<Object> thumbnail = MemoryImage(capture.jpeg);

  String? photoId;

  bool uploadFailed = false;

  bool uploading = false;
}

String repairPhotoWatermark({
  required String roomNo,
  required DateTime takenAt,
}) {
  String two(int value) => value.toString().padLeft(2, '0');
  return '$roomNo · ${two(takenAt.month)}/${two(takenAt.day)} '
      '${two(takenAt.hour)}:${two(takenAt.minute)}';
}

final Uint8List _placeholderJpeg = base64Decode(
  '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRof'
  'Hh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAAB'
  'AAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==',
);
