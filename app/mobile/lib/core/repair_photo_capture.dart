import 'dart:convert';

import 'api_client.dart';
import 'test_backdoor.dart';

/// 工務單照片的「拍一張並上傳」流程：建立照片 id（`POST /repair-photos`）→
/// 上傳內容（`PUT /repair-photos/{id}/content`），回傳可引用的照片 id。
/// 完修回報與開工單表單共用這一條，照片引用規則（1–3 張、全部要已上傳）
/// 由後端把關，這裡不重複驗。
///
/// 相機本體還沒有 feature 承接：壓縮（長邊 1600px、JPEG）與「房號 · 拍攝
/// 時間」浮水印屬於報修照片 feature 的範圍，到時候 [takePhotoJpeg] 要換成
/// 真的取景與影像處理。在那之前先回傳固定的 1×1 JPEG，讓兩段式上傳走的是
/// 真端點、後端的照片張數驗證吃到的是真照片列。
Future<String> captureAndUploadRepairPhoto({
  required String purpose,
  int? photoIndex,
}) async {
  // 自動化測試要驗「某一張上傳失敗時只重試那一張」，而真實網路無法指定讓
  // 第幾張失敗；注入點放在這裡，是因為「一張照片的上傳」整段就是這個函式，
  // 放到更底層的 HTTP 送出會連建立照片位置那一段也一起算進去。
  if (photoIndex != null &&
      TestHooks.instance.uploadFailAtIndex == photoIndex) {
    throw const RepairPhotoUploadFailure();
  }
  final photoId =
      await ApiClient.instance.createRepairPhoto(purpose: purpose);
  await ApiClient.instance.uploadRepairPhotoContent(photoId, takePhotoJpeg());
  return photoId;
}

/// 一張照片沒有上傳成功。
class RepairPhotoUploadFailure implements Exception {
  const RepairPhotoUploadFailure();

  @override
  String toString() => '照片上傳失敗';
}

/// 報修面板上的一張照片：浮水印、上傳結果。
class RepairPhoto {
  RepairPhoto({required this.watermark});

  /// 打在這張照片上的浮水印，格式為「{房號} · {MM/DD HH:mm}」。
  final String watermark;

  /// 上傳成功後拿到的照片 id；還沒上傳成功時為 null。開單請求只收已上傳的
  /// 照片，所以這個欄位同時就是「這張到底送上去了沒有」。
  String? photoId;

  /// 最近一次上傳失敗了。重試成功會清回 false。
  bool uploadFailed = false;

  /// 這一張正在上傳。報修面板是按送出才整批上傳，用不到這個旗標；工務自己
  /// 開單是加一張傳一張，同一列裡可能有人在傳、有人已經傳完，所以狀態記在
  /// 每一張自己身上，而不是整張表單一個旗標。
  bool uploading = false;
}

/// 一張報修照片的浮水印字串：房號與拍攝時間。格式集中在這裡，縮圖上的可及
/// 節點與燒進圖片像素的那一份才會是同一段文字。
String repairPhotoWatermark({
  required String roomNo,
  required DateTime takenAt,
}) {
  String two(int value) => value.toString().padLeft(2, '0');
  return '$roomNo · ${two(takenAt.month)}/${two(takenAt.day)} '
      '${two(takenAt.hour)}:${two(takenAt.minute)}';
}

/// 佔位的拍攝結果：一張合法的 1×1 JPEG。
List<int> takePhotoJpeg() => _placeholderJpeg;

final List<int> _placeholderJpeg = base64Decode(
  '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRof'
  'Hh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAAB'
  'AAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==',
);
