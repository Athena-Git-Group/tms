import 'package:flutter/cupertino.dart';

import '../theme.dart';
import 'tms_glass.dart';

class TmsScrollNavBar extends StatelessWidget {
  const TmsScrollNavBar({
    super.key,
    required this.scrolled,
    required this.restingBackground,
    required this.restingForeground,
    required this.restingTitle,
    required this.scrolledTitle,
    this.titleSuffix,
    this.topInset = 0,
    this.glassFadeStop = 0.6,
    this.leading,
    this.trailing,
  });

  final bool scrolled;

  final Color restingBackground;

  final Color restingForeground;

  final String restingTitle;

  final String scrolledTitle;

  final String? titleSuffix;

  final double topInset;

  final double glassFadeStop;

  final Widget? leading;

  final Widget? trailing;

  static const double scrollThreshold = 72;

  static const double glassBlur = 12;

  static const double glassSaturation = 160;

  @override
  Widget build(BuildContext context) {
    final onDarkHero = !scrolled && _isDark(restingBackground);
    final foreground = scrolled ? TmsColors.label : restingForeground;
    final title = scrolled ? scrolledTitle : restingTitle;
    final suffix = scrolled ? titleSuffix : null;

    return SizedBox(
      height: topInset + TmsSize.frozenNavBarHeight,
      child: Stack(
        children: [
          Positioned.fill(child: _buildBackground()),
          Positioned(
            top: topInset,
            left: 0,
            right: 0,
            height: TmsSize.minTouchTarget,
            child: _TmsNavBarTint(
              color: foreground,
              onDarkHero: onDarkHero,
              child: Row(
                children: [
                  const SizedBox(width: TmsSpacing.md),
                  ?leading,
                  const Spacer(),
                  ?trailing,
                  const SizedBox(width: TmsSpacing.md),
                ],
              ),
            ),
          ),
          Positioned(
            top: topInset,
            left: TmsSize.frozenNavTitleInset,
            right: TmsSize.frozenNavTitleInset,
            height: TmsSize.minTouchTarget,
            child: Center(child: _buildTitle(title, suffix, foreground)),
          ),
        ],
      ),
    );
  }

  Widget _buildTitle(String title, String? suffix, Color foreground) {
    final style = TmsText.headline.copyWith(
      color: foreground,
      fontFeatures: TmsText.tabularNumbers,
    );
    if (suffix == null) {
      return Text(
        title,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        textAlign: TextAlign.center,
        style: style,
      );
    }
    return Text.rich(
      TextSpan(
        text: title,
        children: <InlineSpan>[
          TextSpan(
            text: suffix,
            style: const TextStyle(color: TmsColors.overtimeTimerOnLight),
          ),
        ],
      ),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      textAlign: TextAlign.center,
      style: style,
    );
  }

  Widget _buildBackground() {
    if (!scrolled) {
      return ColoredBox(color: restingBackground);
    }
    // Flutter 的 BackdropFilter 包進 ShaderMask 之後模糊不到背後的頁面，
    // 所以模糊維持整片均勻，只有疊上去的底色做漸層。
    return TmsGlass(
      blur: glassBlur,
      saturation: glassSaturation,
      color: const Color(0x00000000),
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: const <Color>[
              TmsColors.frozenNavGlass,
              TmsColors.frozenNavGlass,
              Color(0x00F2F2F7),
            ],
            stops: <double>[0, glassFadeStop, 1],
          ),
        ),
        child: const SizedBox.expand(),
      ),
    );
  }

  static bool _isDark(Color color) => color.computeLuminance() < 0.5;
}

class _TmsNavBarTint extends InheritedWidget {
  const _TmsNavBarTint({
    required this.color,
    required this.onDarkHero,
    required super.child,
  });

  final Color color;
  final bool onDarkHero;

  static _TmsNavBarTint? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_TmsNavBarTint>();

  @override
  bool updateShouldNotify(_TmsNavBarTint oldWidget) =>
      color != oldWidget.color || onDarkHero != oldWidget.onDarkHero;
}

enum TmsNavBarButtonStyle {
  tinted,
  plain,
}

class TmsNavBarButton extends StatelessWidget {
  const TmsNavBarButton({
    super.key,
    required this.semanticsLabel,
    required this.onPressed,
    required this.child,
    this.semanticsId,
    this.style = TmsNavBarButtonStyle.tinted,
  });

  final TmsNavBarButtonStyle style;

  final String semanticsLabel;

  final String? semanticsId;

  final VoidCallback onPressed;

  final Widget child;

  static const double glassBlur = 12;

  static const double glassSaturation = 180;

  @override
  Widget build(BuildContext context) {
    final tint = _TmsNavBarTint.maybeOf(context);
    final plain = style == TmsNavBarButtonStyle.plain;
    final onDarkHero = !plain && (tint?.onDarkHero ?? false);
    return MergeSemantics(
      child: Semantics(
        identifier: semanticsId,
        label: semanticsLabel,
        child: CupertinoButton(
          padding: EdgeInsets.zero,
          minimumSize: const Size(
            TmsSize.minTouchTarget,
            TmsSize.minTouchTarget,
          ),
          onPressed: onPressed,
          child: ExcludeSemantics(
            child: TmsGlass(
              blur: glassBlur,
              saturation: glassSaturation,
              color: onDarkHero
                  ? TmsColors.navButtonSurfaceOnDark
                  : TmsColors.navButtonSurface,
              borderRadius: BorderRadius.circular(TmsRadius.pill),
              border: Border.all(
                color: plain
                    ? TmsColors.plainNavButtonBorder
                    : (onDarkHero
                          ? TmsColors.navButtonBorderOnDark
                          : TmsColors.navButtonBorder),
                width: plain ? 1 : TmsSize.hairline,
              ),
              boxShadow: plain
                  ? const <BoxShadow>[
                      BoxShadow(
                        color: TmsColors.plainNavButtonShadow,
                        offset: Offset(0, 2),
                        blurRadius: 10,
                      ),
                    ]
                  : const <BoxShadow>[
                      BoxShadow(
                        color: TmsColors.glassShadow,
                        offset: Offset(0, 4),
                        blurRadius: 16,
                      ),
                    ],
              child: SizedBox(
                width: TmsSize.minTouchTarget,
                height: TmsSize.minTouchTarget,
                child: IconTheme.merge(
                  data: IconThemeData(
                    color: plain
                        ? TmsColors.labelSoft
                        : (tint?.color ?? TmsColors.label),
                    size: TmsSize.navBarItemIcon,
                  ),
                  child: Center(child: child),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class TmsNavBarBackButton extends StatelessWidget {
  const TmsNavBarBackButton({
    super.key,
    required this.onPressed,
    this.semanticsLabel = '返回',
    this.semanticsId = defaultSemanticsId,
    this.style = TmsNavBarButtonStyle.tinted,
  });

  final TmsNavBarButtonStyle style;

  static const String defaultSemanticsId = 'nav.back-button';

  final VoidCallback onPressed;

  final String semanticsLabel;

  final String? semanticsId;

  @override
  Widget build(BuildContext context) {
    final tint = _TmsNavBarTint.maybeOf(context);
    final plain = style == TmsNavBarButtonStyle.plain;
    return TmsNavBarButton(
      semanticsId: semanticsId,
      semanticsLabel: semanticsLabel,
      onPressed: onPressed,
      style: style,
      child: CustomPaint(
        size: plain
            ? const Size(_plainChevronWidth, _plainChevronHeight)
            : const Size(TmsSize.navBarChevron, TmsSize.navBarChevron),
        painter: plain
            ? const _PlainChevronPainter()
            : _NavChevronPainter(color: tint?.color ?? TmsColors.label),
      ),
    );
  }

  static const double _plainChevronWidth = 11;
  static const double _plainChevronHeight = 18;
}

class _PlainChevronPainter extends CustomPainter {
  const _PlainChevronPainter();

  @override
  void paint(Canvas canvas, Size size) {
    canvas.scale(size.width / 12, size.height / 20);
    canvas.drawPath(
      Path()
        ..moveTo(10, 2)
        ..lineTo(2, 10)
        ..lineTo(10, 18),
      Paint()
        ..color = TmsColors.labelSoft
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.6
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(_PlainChevronPainter oldDelegate) => false;
}

class _NavChevronPainter extends CustomPainter {
  const _NavChevronPainter({required this.color});

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / TmsSize.navBarChevron;
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = TmsSize.navBarChevronStroke * scale
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final path = Path()
      ..moveTo(15 * scale, 5 * scale)
      ..lineTo(8 * scale, 12 * scale)
      ..lineTo(15 * scale, 19 * scale);
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(_NavChevronPainter oldDelegate) =>
      oldDelegate.color != color;
}

class TmsScrollNavBarScroll extends StatefulWidget {
  const TmsScrollNavBarScroll({
    super.key,
    required this.builder,
    this.threshold = TmsScrollNavBar.scrollThreshold,
  });

  final Widget Function(BuildContext context, bool scrolled) builder;

  final double threshold;

  @override
  State<TmsScrollNavBarScroll> createState() => _TmsScrollNavBarScrollState();
}

class _TmsScrollNavBarScrollState extends State<TmsScrollNavBarScroll> {
  bool _scrolled = false;

  bool _onNotification(ScrollNotification notification) {
    if (notification.depth != 0 || notification.metrics.axis != Axis.vertical) {
      return false;
    }
    final scrolled = notification.metrics.pixels > widget.threshold;
    if (scrolled != _scrolled) {
      setState(() => _scrolled = scrolled);
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    return NotificationListener<ScrollNotification>(
      onNotification: _onNotification,
      child: Builder(builder: (context) => widget.builder(context, _scrolled)),
    );
  }
}
