import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_motion.dart';

/// 全螢幕檢視裡的一張照片。
class TmsViewerPhoto {
  const TmsViewerPhoto({this.image, required this.description});

  /// 要放大顯示的影像。不給時這一張只有黑底。
  final ImageProvider<Object>? image;

  /// 這張照片的描述，通常是房號與拍攝時間。
  final String description;
}

/// 全螢幕放大看照片。
///
/// 現場要判斷的是「這張照片拍清楚了沒有」，縮圖只有 72pt，牆上的裂縫或水漬
/// 在那個尺寸裡看不出來。放大之後還要能兩指縮放，因為浮水印上的房號與時間
/// 是事後對帳的依據。
///
/// 版面取自原型 `Main.dc.html:1857-1872`。
Future<void> showTmsPhotoViewer(
  BuildContext context, {
  required String title,
  required List<TmsViewerPhoto> photos,
  int initialIndex = 0,
}) {
  return Navigator.of(context, rootNavigator: true).push<void>(
    PageRouteBuilder<void>(
      opaque: true,
      transitionDuration: Duration.zero,
      pageBuilder: (context, _, _) => TmsPhotoViewer(
        title: title,
        photos: photos,
        initialIndex: initialIndex,
      ),
    ),
  );
}

class TmsPhotoViewer extends StatefulWidget {
  const TmsPhotoViewer({
    super.key,
    required this.title,
    required this.photos,
    this.initialIndex = 0,
  });

  /// 標題列中央那一行，通常是單號與類別。
  final String title;

  final List<TmsViewerPhoto> photos;

  /// 一進來停在第幾張。
  final int initialIndex;

  @override
  State<TmsPhotoViewer> createState() => _TmsPhotoViewerState();
}

class _TmsPhotoViewerState extends State<TmsPhotoViewer> {
  late final PageController _pages;
  late int _index;

  /// 標題列的最小高度。
  static const double _headerHeight = 52;

  /// 左右切換鈕的直徑與距螢幕邊的距離。
  static const double _navButtonSize = TmsSize.minTouchTarget;
  static const double _navButtonInset = TmsSpacing.sm;

  /// 安全區為零的機型在置底內容下面補的留白。
  static const double _fallbackBottomInset = TmsSpacing.lg;

  @override
  void initState() {
    super.initState();
    _index = widget.initialIndex.clamp(0, widget.photos.length - 1);
    _pages = PageController(initialPage: _index);
  }

  @override
  void dispose() {
    _pages.dispose();
    super.dispose();
  }

  void _goTo(int index) {
    _pages.animateToPage(
      index,
      duration: TmsMotion.slide.duration,
      curve: TmsMotion.slide.curve,
    );
  }

  @override
  Widget build(BuildContext context) {
    final padding = MediaQuery.paddingOf(context);
    final total = widget.photos.length;
    return TmsSemantics(
      id: 'photoViewer.screen',
      explicitChildNodes: true,
      child: TmsMotionView(
        spec: TmsMotion.pop,
        child: ColoredBox(
          color: const Color(0xFF000000),
          child: Column(
            children: [
              Padding(
                padding: EdgeInsets.fromLTRB(
                  TmsSpacing.sm,
                  padding.top,
                  TmsSpacing.sm,
                  0,
                ),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: _headerHeight),
                  child: Row(
                    children: [
                      Expanded(child: _doneButton()),
                      _titleBlock(total),
                      const Expanded(child: SizedBox.shrink()),
                    ],
                  ),
                ),
              ),
              Expanded(child: _stage(total)),
              Padding(
                padding: EdgeInsets.fromLTRB(
                  TmsSpacing.lg,
                  TmsSpacing.md,
                  TmsSpacing.lg,
                  padding.bottom > 0 ? padding.bottom : _fallbackBottomInset,
                ),
                child: const Text(
                  '雙指縮放查看細節 · 左右滑動切換',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontFamilyFallback: TmsText.fontFamilyStack,
                    fontSize: 13,
                    color: _secondaryOnBlack,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _doneButton() {
    return Align(
      alignment: Alignment.centerLeft,
      child: TmsSemantics(
        id: 'photoViewer.done-button',
        label: '完成',
        child: GestureDetector(
          onTap: () => Navigator.of(context).pop(),
          behavior: HitTestBehavior.opaque,
          child: Container(
            constraints: const BoxConstraints(
              minHeight: TmsSize.minTouchTarget,
            ),
            padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.md),
            alignment: Alignment.center,
            child: const ExcludeSemantics(
              child: Text(
                '完成',
                style: TextStyle(
                  fontFamilyFallback: TmsText.fontFamilyStack,
                  fontSize: 17,
                  fontWeight: FontWeight.w600,
                  color: TmsColors.brandOnPrimary,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _titleBlock(int total) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        TmsSemantics(
          id: 'photoViewer.title-label',
          label: widget.title,
          child: ExcludeSemantics(
            child: Text(
              widget.title,
              style: const TextStyle(
                fontFamilyFallback: TmsText.fontFamilyStack,
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: TmsColors.brandOnPrimary,
              ),
            ),
          ),
        ),
        TmsSemantics(
          id: 'photoViewer.count-label',
          label: '${_index + 1} / $total',
          child: ExcludeSemantics(
            child: Text(
              '${_index + 1} / $total',
              style: const TextStyle(
                fontFamilyFallback: TmsText.fontFamilyStack,
                fontSize: 13,
                color: _secondaryOnBlack,
                fontFeatures: TmsText.tabularNumbers,
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _stage(int total) {
    return Stack(
      children: [
        PageView.builder(
          controller: _pages,
          itemCount: total,
          onPageChanged: (index) => setState(() => _index = index),
          itemBuilder: (context, index) {
            final photo = widget.photos[index];
            final image = photo.image;
            return TmsSemantics(
              id: 'photoViewer.photo.${index + 1}',
              label: photo.description,
              child: InteractiveViewer(
                maxScale: 4,
                child: image == null
                    ? const SizedBox.expand()
                    : Image(image: image, fit: BoxFit.contain),
              ),
            );
          },
        ),
        if (_index > 0)
          _NavButton(
            id: 'photoViewer.prev-button',
            label: '上一張',
            icon: CupertinoIcons.chevron_back,
            alignment: Alignment.centerLeft,
            onTap: () => _goTo(_index - 1),
          ),
        if (_index < total - 1)
          _NavButton(
            id: 'photoViewer.next-button',
            label: '下一張',
            icon: CupertinoIcons.chevron_forward,
            alignment: Alignment.centerRight,
            onTap: () => _goTo(_index + 1),
          ),
      ],
    );
  }
}

/// 左右切換鈕：44pt 的半透明圓，裡面一個箭頭。
class _NavButton extends StatelessWidget {
  const _NavButton({
    required this.id,
    required this.label,
    required this.icon,
    required this.alignment,
    required this.onTap,
  });

  final String id;
  final String label;
  final IconData icon;
  final Alignment alignment;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: alignment,
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: _TmsPhotoViewerState._navButtonInset,
        ),
        child: TmsSemantics(
          id: id,
          label: label,
          child: GestureDetector(
            onTap: onTap,
            behavior: HitTestBehavior.opaque,
            child: Container(
              width: _TmsPhotoViewerState._navButtonSize,
              height: _TmsPhotoViewerState._navButtonSize,
              decoration: const BoxDecoration(
                color: Color(0x993C3C43),
                shape: BoxShape.circle,
              ),
              child: Icon(
                icon,
                size: TmsSpacing.xl,
                color: TmsColors.brandOnPrimary,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 黑底上的次要文字，原型寫 `rgba(235,235,245,.6)`。
const Color _secondaryOnBlack = Color(0x99EBEBF5);
