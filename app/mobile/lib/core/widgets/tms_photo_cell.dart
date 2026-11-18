import 'package:flutter/cupertino.dart';

import '../theme.dart';

sealed class TmsPhotoUpload {
  const TmsPhotoUpload();
}

class TmsPhotoIdle extends TmsPhotoUpload {
  const TmsPhotoIdle();
}

class TmsPhotoUploading extends TmsPhotoUpload {
  const TmsPhotoUploading(this.progress);

  final double progress;
}

class TmsPhotoFailed extends TmsPhotoUpload {
  const TmsPhotoFailed();
}

class TmsPhotoUploaded extends TmsPhotoUpload {
  const TmsPhotoUploaded();
}

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

  final ImageProvider<Object>? image;

  final String? sizeLabel;

  final TmsPhotoUpload upload;

  final VoidCallback? onRemove;

  final VoidCallback? onRetry;

  final VoidCallback? onTap;

  final String? semanticsLabel;

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

class TmsAddPhotoButton extends StatelessWidget {
  const TmsAddPhotoButton({
    super.key,
    required this.label,
    required this.onTap,
  })  : _edge = TmsPhotoCell.size,
        _gap = TmsSpacing.xxs,
        _labelStyle = TmsText.caption1;

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
