import 'dart:convert';

import 'api_client.dart';

/// 工務單照片的「拍一張並上傳」流程：建立照片 id（`POST /repair-photos`）→
/// 上傳內容（`PUT /repair-photos/{id}/content`），回傳可引用的照片 id。
/// 完修回報與開工單表單共用這一條，照片引用規則（1–3 張、全部要已上傳）
/// 由後端把關，這裡不重複驗。
///
/// 相機本體還沒有 feature 承接：壓縮（長邊 1600px、JPEG）與「房號 · 拍攝
/// 時間」浮水印屬於報修照片 feature 的範圍，到時候 [takePhotoJpeg] 要換成
/// 真的取景與影像處理。在那之前先回傳固定的 1×1 JPEG，讓兩段式上傳走的是
/// 真端點、後端的照片張數驗證吃到的是真照片列。
Future<String> captureAndUploadRepairPhoto({required String purpose}) async {
  final photoId =
      await ApiClient.instance.createRepairPhoto(purpose: purpose);
  await ApiClient.instance.uploadRepairPhotoContent(photoId, takePhotoJpeg());
  return photoId;
}

/// 佔位的拍攝結果：一張合法的 1×1 JPEG。
List<int> takePhotoJpeg() => _placeholderJpeg;

final List<int> _placeholderJpeg = base64Decode(
  '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRof'
  'Hh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAAB'
  'AAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==',
);
