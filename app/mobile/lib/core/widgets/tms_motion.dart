import 'dart:math' as math;
import 'dart:ui' show lerpDouble;

import 'package:flutter/widgets.dart';

abstract final class TmsMotion {
  static const TmsMotionSpec pop = TmsMotionSpec(
    name: 'tmsPop',
    duration: Duration(milliseconds: 420),
    curve: Curves.easeOut,
    stopsForReducedMotion: true,
    frame: _popFrame,
  );

  static const TmsMotionSpec drop = TmsMotionSpec(
    name: 'tmsDrop',
    duration: Duration(milliseconds: 280),
    curve: Curves.easeOut,
    stopsForReducedMotion: true,
    frame: _dropFrame,
  );

  static const TmsMotionSpec slide = TmsMotionSpec(
    name: 'tmsSlide',
    duration: Duration(milliseconds: 240),
    curve: Curves.easeOut,
    stopsForReducedMotion: true,
    frame: _slideFrame,
  );

  static const TmsMotionSpec float = TmsMotionSpec(
    name: 'tmsFloat',
    duration: Duration(milliseconds: 1600),
    curve: Curves.easeOut,
    repeat: TmsMotionRepeat.loop,
    stopsForReducedMotion: true,
    frame: _floatFrame,
  );

  static const TmsMotionSpec pulse = TmsMotionSpec(
    name: 'tmsPulse',
    duration: Duration(milliseconds: 1400),
    curve: Curves.easeInOut,
    repeat: TmsMotionRepeat.pingPong,
    stopsForReducedMotion: true,
    frame: _pulseFrame,
  );

  static const TmsMotionSpec up = TmsMotionSpec(
    name: 'tmsUp',
    duration: Duration(milliseconds: 260),
    curve: Curves.easeOut,
    frame: _upFrame,
  );

  static const TmsMotionSpec flash = TmsMotionSpec(
    name: 'tmsFlash',
    duration: Duration(milliseconds: 350),
    curve: Curves.easeOut,
    frame: _flashFrame,
  );

  static const TmsMotionSpec ringSweep = TmsMotionSpec(
    name: 'ringSweep',
    duration: Duration(milliseconds: 500),
    curve: Curves.easeOut,
  );

  static const TmsMotionSpec barSweep = TmsMotionSpec(
    name: 'barSweep',
    duration: Duration(milliseconds: 300),
    curve: Curves.easeOut,
  );

  static const List<TmsMotionSpec> all = <TmsMotionSpec>[
    pop,
    drop,
    float,
    slide,
    pulse,
    up,
    flash,
    ringSweep,
    barSweep,
  ];
}

enum TmsMotionRepeat {
  none,

  loop,

  pingPong,
}

typedef TmsMotionFrame = Widget Function(
  TmsMotionSpec spec,
  double t,
  Widget child,
);

@immutable
class TmsMotionSpec {
  const TmsMotionSpec({
    required this.name,
    required this.duration,
    required this.curve,
    this.repeat = TmsMotionRepeat.none,
    this.stopsForReducedMotion = false,
    this.frame,
  });

  final String name;

  final Duration duration;

  final Curve curve;

  final TmsMotionRepeat repeat;

  final bool stopsForReducedMotion;

  final TmsMotionFrame? frame;

  double get restingFrame => repeat == TmsMotionRepeat.none ? 1.0 : 0.0;

  bool isStoppedIn(BuildContext context) =>
      stopsForReducedMotion && MediaQuery.disableAnimationsOf(context);

  Duration durationIn(BuildContext context) =>
      isStoppedIn(context) ? Duration.zero : duration;
}

class TmsMotionView extends StatefulWidget {
  const TmsMotionView({
    super.key,
    required this.spec,
    required this.child,
    this.delay = Duration.zero,
  });

  final TmsMotionSpec spec;
  final Widget child;

  final Duration delay;

  @override
  State<TmsMotionView> createState() => _TmsMotionViewState();
}

class _TmsMotionViewState extends State<TmsMotionView>
    with SingleTickerProviderStateMixin {
  AnimationController? _controller;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(TmsMotionView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.spec != widget.spec || oldWidget.delay != widget.delay) {
      _disposeController();
      _sync();
    }
  }

  void _disposeController() {
    _controller?.dispose();
    _controller = null;
  }

  void _sync() {
    final spec = widget.spec;
    if (spec.isStoppedIn(context) || spec.frame == null) {
      _disposeController();
      return;
    }
    if (_controller != null) {
      return;
    }
    final controller = AnimationController(
      vsync: this,
      duration: spec.repeat == TmsMotionRepeat.pingPong
          ? spec.duration ~/ 2
          : spec.duration,
    );
    _controller = controller;
    void start() {
      switch (spec.repeat) {
        case TmsMotionRepeat.none:
          controller.forward();
        case TmsMotionRepeat.loop:
          controller.repeat();
        case TmsMotionRepeat.pingPong:
          controller.repeat(reverse: true);
      }
    }

    if (widget.delay == Duration.zero) {
      start();
      return;
    }
    Future<void>.delayed(widget.delay, () {
      if (mounted && _controller == controller) {
        start();
      }
    });
  }

  @override
  void dispose() {
    _disposeController();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final spec = widget.spec;
    final frame = spec.frame;
    if (frame == null) {
      return widget.child;
    }
    final controller = _controller;
    if (controller == null) {
      return frame(spec, spec.restingFrame, widget.child);
    }
    return AnimatedBuilder(
      animation: controller,
      builder: (context, child) => frame(spec, controller.value, child!),
      child: widget.child,
    );
  }
}

double _eased(TmsMotionSpec spec, double t, double start, double end) {
  if (t <= start) {
    return 0;
  }
  if (t >= end) {
    return 1;
  }
  return spec.curve.transform((t - start) / (end - start));
}

Widget _popFrame(TmsMotionSpec spec, double t, Widget child) {
  final grow = _eased(spec, t, 0, 0.6);
  final settle = _eased(spec, t, 0.6, 1);
  final scale = t < 0.6
      ? lerpDouble(0.7, 1.06, grow)!
      : lerpDouble(1.06, 1.0, settle)!;
  return Opacity(
    opacity: grow,
    child: Transform.scale(scale: scale, child: child),
  );
}

Widget _dropFrame(TmsMotionSpec spec, double t, Widget child) {
  final progress = _eased(spec, t, 0, 1);
  return Opacity(
    opacity: progress,
    child: Transform.translate(
      offset: Offset(0, lerpDouble(-24, 0, progress)!),
      child: child,
    ),
  );
}

Widget _slideFrame(TmsMotionSpec spec, double t, Widget child) {
  final progress = _eased(spec, t, 0, 1);
  return Opacity(
    opacity: progress,
    child: Transform.translate(
      offset: Offset(lerpDouble(40, 0, progress)!, 0),
      child: child,
    ),
  );
}

Widget _floatFrame(TmsMotionSpec spec, double t, Widget child) {
  final progress = _eased(spec, t, 0, 1);
  return Opacity(
    opacity: 1 - progress,
    child: Transform.translate(
      offset: Offset(0, lerpDouble(0, -70, progress)!),
      child: Transform.rotate(
        angle: lerpDouble(0, 200 * math.pi / 180, progress)!,
        child: child,
      ),
    ),
  );
}

Widget _pulseFrame(TmsMotionSpec spec, double t, Widget child) {
  return Opacity(
    opacity: lerpDouble(1, 0.45, _eased(spec, t, 0, 1))!,
    child: child,
  );
}

Widget _upFrame(TmsMotionSpec spec, double t, Widget child) {
  return FractionalTranslation(
    translation: Offset(0, lerpDouble(1, 0, _eased(spec, t, 0, 1))!),
    child: child,
  );
}

Widget _flashFrame(TmsMotionSpec spec, double t, Widget child) {
  return Opacity(
    opacity: lerpDouble(0.95, 0, _eased(spec, t, 0, 1))!,
    child: child,
  );
}
