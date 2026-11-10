import 'package:flutter/cupertino.dart';

import '../theme.dart';

/// 一張照片目前的上傳進度。
///
/// 做成四個型別而不是一個 enum 加一個百分比欄位，是為了讓「上傳中但不知道
/// 百分之幾」這種狀態寫不出來。照片格的上傳中那一態一定要畫出百分比與進度
/// 條（原型 `Main.dc.html:1518-1523`），百分比若能是 null，呼叫端就能交出一
/// 格畫不出進度條的上傳中照片，而畫面上那一格會變成一片沒有任何訊息的黑遮罩。
sealed class TmsPhotoUpload {
  const TmsPhotoUpload();
}

/// 已經加進表單、還沒開始上傳。
class TmsPhotoIdle extends TmsPhotoUpload {
  const TmsPhotoIdle();
}

/// 正在上傳。[progress] 是 0 到 1 的完成比例。
class TmsPhotoUploading extends TmsPhotoUpload {
  const TmsPhotoUploading(this.progress);

  final double progress;
}

/// 最近一次上傳失敗，可以只重試這一張。
class TmsPhotoFailed extends TmsPhotoUpload {
  const TmsPhotoFailed();
}

/// 已經上傳成功。
class TmsPhotoUploaded extends TmsPhotoUpload {
  const TmsPhotoUploaded();
}

/// 表單與詳情頁上的一格照片。
///
/// 這一格要讓員工不讀字就知道兩件事：這張是什麼，以及它送出去了沒有。縮圖
/// 回答第一件，蓋在縮圖上的四種長相回答第二件。沒有遮罩是還沒開始傳，半透
/// 明黑遮罩加百分比是傳到一半，紅遮罩加重試是傳失敗，左上角綠圓勾是傳好了。
///
/// 報修面板、完修回報、開工單表單與工務單詳情共用這一格。四個入口各畫一個
/// 灰方塊的話，同一張照片在四個地方會有四種長相，而上傳狀態正是員工最需要
/// 一眼看出來的事。
///
/// 畫法與數值取自原型 `Main.dc.html:1509-1533`。
class TmsPhotoCell extends StatelessWidget {
  const TmsPhotoCell({
    super.key,
    this.image,
    this.sizeLabel,
    this.upload = const TmsPhotoIdle(),
    this.onRemove,
    this.onRetry,
    this.onTap,
    this.semanticsLabel,
    this.edge = size,
  });

  /// 這一格要顯示的縮圖。不給時整格是原型的空照片底色。
  final ImageProvider<Object>? image;

  /// 底部字條上的壓縮後大小，例如 `19 KB`；壓縮還沒完成時是「壓縮中…」。
  /// 不給時不畫字條。
  final String? sizeLabel;

  final TmsPhotoUpload upload;

  /// 讓員工把這一張從表單上拿掉。不給時右上角不畫移除鈕，呼叫端用這個方式
  /// 表達「這一張現在不能拿掉」，例如整批照片正在上傳的途中。
  final VoidCallback? onRemove;

  /// 讓員工只重傳這一張。上傳失敗時整格就是這顆按鈕。
  final VoidCallback? onRetry;

  /// 讓員工點縮圖放大看。上傳失敗時這一格改成重試，不會放大。
  final VoidCallback? onTap;

  /// 這張照片的描述，通常是房號與拍攝時間。浮水印燒進像素以後讀螢幕的人讀不
  /// 到，所以同一段文字要同時掛在這裡。
  final String? semanticsLabel;

  /// 這一格的邊長。給 null 時填滿呼叫端給的空間，工務單詳情一列三張的方格
  /// 用這種。
  final double? edge;

  static const double size = 72;

  @override
  Widget build(BuildContext context) {
    final upload = this.upload;
    final sizeLabel = this.sizeLabel;
    final image = this.image;
    final onRemove = this.onRemove;
    final onTap = upload is TmsPhotoFailed ? onRetry : this.onTap;

    final cell = ClipRRect(
      borderRadius: BorderRadius.circular(TmsRadius.sm),
      child: Stack(
        fit: StackFit.expand,
        children: [
          const ColoredBox(color: TmsColors.neutralContainer),
          if (image != null) Image(image: image, fit: BoxFit.cover),
          if (sizeLabel != null)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: ColoredBox(
                color: _stripBackground,
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    vertical: TmsSpacing.xxs,
                  ),
                  child: Text(
                    sizeLabel,
                    textAlign: TextAlign.center,
                    style: TmsText.caption3.copyWith(
                      color: TmsColors.brandOnPrimary,
                    ),
                  ),
                ),
              ),
            ),
          if (onRemove != null)
            Positioned(
              top: TmsSpacing.xxs,
              right: TmsSpacing.xxs,
              child: _RemoveButton(onTap: onRemove),
            ),
          if (upload is TmsPhotoUploading) _UploadingOverlay(upload.progress),
          if (upload is TmsPhotoFailed) const _FailedOverlay(),
          if (upload is TmsPhotoUploaded)
            const Positioned(
              top: TmsSpacing.xs,
              left: TmsSpacing.xs,
              child: _UploadedBadge(),
            ),
        ],
      ),
    );

    final sized = edge == null
        ? cell
        : SizedBox(width: edge, height: edge, child: cell);
    final tappable = onTap == null
        ? sized
        : GestureDetector(
            onTap: onTap,
            behavior: HitTestBehavior.opaque,
            child: sized,
          );
    return semanticsLabel == null
        ? tappable
        : Semantics(label: semanticsLabel, child: tappable);
  }
}

const Color _stripBackground = Color(0x80000000);

const double _glyphSize = 12;

class _RemoveButton extends StatelessWidget {
  const _RemoveButton({required this.onTap});

  final VoidCallback onTap;

  static const double _buttonSize = 28;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        width: _buttonSize,
        height: _buttonSize,
        decoration: const BoxDecoration(
          color: Color(0x8C000000),
          shape: BoxShape.circle,
        ),
        child: const Icon(
          CupertinoIcons.xmark,
          size: _glyphSize,
          color: TmsColors.brandOnPrimary,
        ),
      ),
    );
  }
}

/// 畫百分比而不是轉圈，因為員工在走廊上按下送出之後唯一想知道的是還要等多
/// 久。轉圈動畫對三張 2 MB 的照片和對一張 19 KB 的照片長得一模一樣。
class _UploadingOverlay extends StatelessWidget {
  const _UploadingOverlay(this.progress);

  final double progress;

  static const double _barWidth = 52;
  static const double _barHeight = 4;

  @override
  Widget build(BuildContext context) {
    final clamped = progress.clamp(0.0, 1.0);
    return ColoredBox(
      color: const Color(0x73000000),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            '${(clamped * 100).round()}%',
            style: TmsText.caption1.copyWith(
              color: TmsColors.brandOnPrimary,
            ),
          ),
          const SizedBox(height: TmsSpacing.xs2),
          ClipRRect(
            borderRadius: BorderRadius.circular(TmsRadius.pill),
            child: SizedBox(
              width: _barWidth,
              height: _barHeight,
              child: ColoredBox(
                color: const Color(0x59FFFFFF),
                child: FractionallySizedBox(
                  alignment: Alignment.centerLeft,
                  widthFactor: clamped,
                  child: const ColoredBox(color: TmsColors.brandOnPrimary),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// 失敗那一格的長相固定在元件裡，不開放呼叫端傳，因為四個入口的失敗重試是
/// 同一件事。開放之後報修面板畫箭頭、開工單只畫文字，員工就得在兩個地方各
/// 學一次這一格紅紅的要怎麼救。
class _FailedOverlay extends StatelessWidget {
  const _FailedOverlay();

  static const double _retryIconSize = 20;

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: const Color(0xB8B42318),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            CupertinoIcons.arrow_clockwise,
            size: _retryIconSize,
            color: TmsColors.brandOnPrimary,
          ),
          const SizedBox(height: TmsSpacing.xxs),
          Text(
            '重試',
            style: TmsText.caption1.copyWith(
              color: TmsColors.brandOnPrimary,
            ),
          ),
        ],
      ),
    );
  }
}

class _UploadedBadge extends StatelessWidget {
  const _UploadedBadge();

  static const double _badgeSize = 20;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: _badgeSize,
      height: _badgeSize,
      decoration: const BoxDecoration(
        color: TmsColors.systemGreen,
        shape: BoxShape.circle,
      ),
      child: const Icon(
        CupertinoIcons.check_mark,
        size: _glyphSize,
        color: TmsColors.brandOnPrimary,
      ),
    );
  }
}

/// 照片列尾端的「加入照片」格：與照片同尺寸的虛線方塊，裡面是相機圖示加一行
/// 小字。
///
/// 做成跟照片一樣大的一格而不是一顆文字按鈕，是為了讓員工看得出這裡還能再
/// 放一張。按鈕只說得出可以按，說不出會多出什麼。
///
/// 兩種版型共用同一顆按鈕。表單上的照片列是固定 72pt、間距 2、文字 12pt；
/// 工務單詳情一列三張的方格會隨欄寬變化、間距 4、文字 13pt（原型
/// `Main.dc.html:1536-1539` 與 `Main.dc.html:1813-1816`）。
class TmsAddPhotoButton extends StatelessWidget {
  const TmsAddPhotoButton({
    super.key,
    required this.label,
    required this.onTap,
  })  : _edge = TmsPhotoCell.size,
        _gap = TmsSpacing.xxs,
        _labelStyle = TmsText.caption1;

  /// 工務單詳情一列三張的方格版本：填滿欄寬，由呼叫端維持 1:1。
  const TmsAddPhotoButton.inGrid({
    super.key,
    required this.label,
    required this.onTap,
  })  : _edge = null,
        _gap = TmsSpacing.xs,
        _labelStyle = TmsText.footnoteEmphasized;

  final String label;

  final VoidCallback? onTap;

  final double? _edge;
  final double _gap;
  final TextStyle _labelStyle;

  @override
  Widget build(BuildContext context) {
    final content = CustomPaint(
      painter: const _DashedBorderPainter(),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            CupertinoIcons.camera,
            size: _cameraIconSize,
            color: TmsColors.brandPrimary,
          ),
          SizedBox(height: _gap),
          Text(
            label,
            textAlign: TextAlign.center,
            style: _labelStyle.copyWith(color: TmsColors.brandPrimary),
          ),
        ],
      ),
    );
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: _edge == null
          ? content
          : SizedBox(width: _edge, height: _edge, child: content),
    );
  }
}

const double _cameraIconSize = 26;

/// 「加入照片」格的虛線框。Flutter 的 `Border` 只有實線，虛線得自己畫：沿著
/// 圓角矩形的路徑，每隔一段取一段畫出來。
class _DashedBorderPainter extends CustomPainter {
  const _DashedBorderPainter();

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
