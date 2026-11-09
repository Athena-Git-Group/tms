import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_motion.dart';

/// 員工要從哪裡取得這張照片。
enum TmsPhotoSource {
  /// 現在拍一張。
  camera,

  /// 從相簿裡挑已經有的。
  library,
}

/// 問員工「拍照」還是「從相簿選擇」的 iOS 動作選單。
///
/// 選完回傳對應的 [TmsPhotoSource]；按取消或點選單外面回傳 null。相機取景
/// 介面與相簿挑選器都由 iOS 系統提供，這個選單只負責問，不負責取像。
///
/// 現場的兩種情況需要不同入口：東西就在眼前時直接拍，而相機權限被關掉、或
/// 照片是剛才在別的地方拍好的時候，只能從相簿挑。少了選單就等於強迫員工先
/// 去設定裡開權限才報得了修。
///
/// 版面取自原型 `Main.dc.html:1682-1691`。
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

  /// 選單兩張卡與螢幕下緣之間的最小留白，原型寫 `max(安全區下緣, 8)`。
  static const double _minBottomInset = TmsSpacing.sm;

  /// 選單列的最小高度。
  static const double _rowHeight = 57;

  /// iOS 26 的動作選單圓角。
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
