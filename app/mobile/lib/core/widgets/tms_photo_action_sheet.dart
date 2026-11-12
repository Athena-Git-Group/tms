import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_motion.dart';

enum TmsPhotoSource {
  camera,

  library,
}

Future<TmsPhotoSource?> showTmsPhotoActionSheet(BuildContext context) {
  return Navigator.of(context, rootNavigator: true).push<TmsPhotoSource>(
    PageRouteBuilder<TmsPhotoSource>(
      opaque: false,
      barrierDismissible: true,
      barrierColor: TmsColors.scrimSheet,
      barrierLabel: '關閉',
      transitionDuration: Duration.zero,
      pageBuilder: (context, _, _) => const Align(
        alignment: Alignment.bottomCenter,
        child: _PhotoActionSheet(),
      ),
    ),
  );
}

class _PhotoActionSheet extends StatelessWidget {
  const _PhotoActionSheet();

  static const double _minBottomInset = TmsSpacing.sm;

  static const double _rowHeight = 57;

  static const double _radius = 26;

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.paddingOf(context).bottom;
    return TmsSemantics(
      id: 'photoMenu.sheet',
      explicitChildNodes: true,
      child: TmsMotionView(
        spec: TmsMotion.up,
        child: Padding(
          padding: EdgeInsets.fromLTRB(
            TmsSpacing.sm,
            0,
            TmsSpacing.sm,
            bottom > _minBottomInset ? bottom : _minBottomInset,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(_radius),
                child: ColoredBox(
                  color: const Color(0xF5FAFAFA),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      _MenuRow(
                        id: 'photoMenu.camera-button',
                        label: '拍照',
                        style: TmsText.title3Regular,
                        onTap: () => Navigator.of(context)
                            .pop(TmsPhotoSource.camera),
                      ),
                      const SizedBox(
                        height: TmsSize.hairline,
                        child: ColoredBox(color: Color(0x4A3C3C43)),
                      ),
                      _MenuRow(
                        id: 'photoMenu.library-button',
                        label: '從相簿選擇',
                        style: TmsText.title3Regular,
                        onTap: () => Navigator.of(context)
                            .pop(TmsPhotoSource.library),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: TmsSpacing.sm),
              ClipRRect(
                borderRadius: BorderRadius.circular(_radius),
                child: ColoredBox(
                  color: TmsColors.cardBackground,
                  child: _MenuRow(
                    id: 'photoMenu.cancel-button',
                    label: '取消',
                    style: TmsText.title3Emphasized,
                    onTap: () => Navigator.of(context).pop(),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MenuRow extends StatelessWidget {
  const _MenuRow({
    required this.id,
    required this.label,
    required this.style,
    required this.onTap,
  });

  final String id;
  final String label;
  final TextStyle style;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: id,
      label: label,
      child: GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: Container(
          constraints: const BoxConstraints(
            minHeight: _PhotoActionSheet._rowHeight,
          ),
          alignment: Alignment.center,
          child: ExcludeSemantics(
            child: Text(
              label,
              style: style.copyWith(color: TmsColors.brandPrimary),
            ),
          ),
        ),
      ),
    );
  }
}
