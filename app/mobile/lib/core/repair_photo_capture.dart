import 'dart:convert';

import 'api_client.dart';
import 'test_backdoor.dart';

Future<String> captureAndUploadRepairPhoto({
  required String purpose,
  int? photoIndex,
}) async {
  if (photoIndex != null &&
      TestHooks.instance.uploadFailAtIndex == photoIndex) {
    throw const RepairPhotoUploadFailure();
  }
  final photoId =
      await ApiClient.instance.createRepairPhoto(purpose: purpose);
  await ApiClient.instance.uploadRepairPhotoContent(photoId, takePhotoJpeg());
  return photoId;
}

class RepairPhotoUploadFailure implements Exception {
  const RepairPhotoUploadFailure();

  @override
  String toString() => '照片上傳失敗';
}

class RepairPhoto {
  RepairPhoto({required this.watermark});

  final String watermark;

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

List<int> takePhotoJpeg() => _placeholderJpeg;

final List<int> _placeholderJpeg = base64Decode(
  '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRof'
  'Hh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAAB'
  'AAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==',
);
