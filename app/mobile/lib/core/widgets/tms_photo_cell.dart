import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 一張照片在表單上的上傳狀態。四態各有自己的長相，員工不用讀字就知道這一張
/// 到底送出去了沒。
enum TmsPhotoState {
  /// 已經拍好、還沒開始上傳。
  pending,

  /// 正在上傳。
  uploading,

  /// 上傳失敗，要重試。
  failed,

  /// 已經上傳成功。
  done,
}

/// 表單上的一格照片：72pt 的方塊，底下壓一條浮水印字條，上傳中／失敗／完成各
/// 蓋一層不同的東西。
///
/// 報修面板與工務開工單共用這一格。兩邊各畫一個灰方塊的話，同一張照片在兩個
/// 入口會有兩種長相，而「這張到底傳上去沒有」正是員工最需要一眼看出來的事。
///
/// [watermark] 是呼叫端自己包好的文字節點（通常外面還有一層 accessibility
/// id），這裡只負責把它放進底部那條字條並統一字級與顏色。
class TmsPhotoCell extends StatelessWidget {
  const TmsPhotoCell({
    super.key,
    this.state = TmsPhotoState.pending,
    this.watermark,
    this.failureAction,
  });

  final TmsPhotoState state;

  /// 底部字條裡的浮水印（房號與拍攝時間）；不給就不畫字條。
  final Widget? watermark;

  /// 上傳失敗時蓋在照片上的重試動作；不給就只有紅色遮罩。
  final Widget? failureAction;

  /// 原型的照片格邊長。
  static const double size = 72;

  @override
  Widget build(BuildContext context) {
    final mark = watermark;
    return SizedBox(
      width: size,
      height: size,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(TmsRadius.sm),
        child: Stack(
          fit: StackFit.expand,
          children: [
            // 真正的縮圖還沒接上（照片是在送出那一刻才拍的，App 手上沒有
            // 影像資料），先用系統的照片占位底色。
            const ColoredBox(color: TmsColors.neutralContainer),
            if (mark != null)
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: Container(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  color: const Color(0x80000000),
                  alignment: Alignment.center,
                  child: DefaultTextStyle(
                    textAlign: TextAlign.center,
                    style: TmsText.caption2.copyWith(
                      fontSize: 10,
                      height: 12 / 10,
                      color: TmsColors.brandOnPrimary,
                    ),
                    child: mark,
                  ),
                ),
              ),
            if (state == TmsPhotoState.uploading)
              const ColoredBox(
                color: Color(0x73000000),
                child: Center(
                  child: CupertinoActivityIndicator(
                    color: TmsColors.brandOnPrimary,
                  ),
                ),
              ),
            if (state == TmsPhotoState.failed)
              ColoredBox(
                color: const Color(0xB8B42318),
                child: Center(child: failureAction ?? const SizedBox.shrink()),
              ),
            if (state == TmsPhotoState.done)
              const Positioned(
                top: TmsSpacing.xs,
                left: TmsSpacing.xs,
                child: _DoneBadge(),
              ),
          ],
        ),
      ),
    );
  }
}

/// 上傳成功的角標：20pt 綠圓加一個白勾。
class _DoneBadge extends StatelessWidget {
  const _DoneBadge();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 20,
      height: 20,
      decoration: const BoxDecoration(
        color: TmsColors.systemGreen,
        shape: BoxShape.circle,
      ),
      child: const Icon(
        CupertinoIcons.check_mark,
        size: 14,
        color: TmsColors.brandOnPrimary,
      ),
    );
  }
}

/// 照片列尾端的「加入照片」格：與照片同尺寸的虛線方塊，裡面是相機圖示加一行
/// 小字。
///
/// 做成跟照片一樣大的一格而不是一顆文字按鈕，是為了讓員工看得出「這裡還能再
/// 放一張」——按鈕只說得出可以按，說不出會多出什麼。
class TmsAddPhotoButton extends StatelessWidget {
  const TmsAddPhotoButton({
    super.key,
    required this.label,
    required this.onTap,
  });

  final String label;

  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: CustomPaint(
        painter: const _DashedBorderPainter(),
        child: SizedBox(
          width: TmsPhotoCell.size,
          height: TmsPhotoCell.size,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(
                CupertinoIcons.camera,
                size: 24,
                color: TmsColors.brandPrimary,
              ),
              const SizedBox(height: TmsSpacing.xxs),
              Text(
                label,
                textAlign: TextAlign.center,
                style: TmsText.caption1.copyWith(
                  color: TmsColors.brandPrimary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 「加入照片」格的虛線框。Flutter 的 `Border` 只有實線，虛線得自己畫：沿著
/// 圓角矩形的路徑，每隔一段取一段畫出來。
class _DashedBorderPainter extends CustomPainter {
  const _DashedBorderPainter();

  /// 一段實線與一段空白的長度。
  static const double _dash = 5;
  static const double _gap = 4;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5
      ..color = TmsColors.opaqueSeparator;
    final path = Path()
      ..addRRect(
        RRect.fromRectAndRadius(
          Offset.zero & size,
          const Radius.circular(TmsRadius.sm),
        ),
      );
    for (final metric in path.computeMetrics()) {
      var distance = 0.0;
      while (distance < metric.length) {
        final end = (distance + _dash).clamp(0.0, metric.length);
        canvas.drawPath(metric.extractPath(distance, end), paint);
        distance = end + _gap;
      }
    }
  }

  @override
  bool shouldRepaint(_DashedBorderPainter oldDelegate) => false;
}
