import 'dart:math' as math;
import 'dart:ui' show lerpDouble;

import 'package:flutter/widgets.dart';

/// App 全域共用的動畫時長與曲線，數值取自設計原型 `Claude Design/Main.dc.html`。
///
/// 原型在第 24 到 37 行用 CSS keyframes 定義了七種動畫，第 139 與 1308 行另外
/// 用 CSS transition 定義了兩段補間。每個畫面要掛動畫時引用這裡的名字，不自己
/// 抄秒數；九條規則由九個畫面各抄一次的話，同一種進場動畫會長出好幾個不一樣的
/// 秒數，日後拿畫面跟原型比對就分不出哪一個才是原型的值。
///
/// CSS 的 easing 關鍵字本身就是固定係數的三次貝茲曲線，Flutter 的 `Curves` 有
/// 同係數的項目，所以換算是等值代換而不是目測近似：
///
/// * `ease-out` 即 `cubic-bezier(0, 0, 0.58, 1)`，等於 [Curves.easeOut]。
/// * `ease-in-out` 即 `cubic-bezier(0.42, 0, 0.58, 1)`，等於 [Curves.easeInOut]。
///
/// 原型第 40 行的 `@media (prefers-reduced-motion: reduce)` 只關掉 `tmsPop`、
/// `tmsDrop`、`tmsSlide`、`tmsFloat`、`tmsPulse` 五種，`tmsFlash`、`tmsUp` 與
/// 兩段 transition 不在清單內。[TmsMotionSpec.stopsForReducedMotion] 逐條記下
/// 這個差別，[TmsMotionView] 在執行時讀系統的減少動態設定決定播不播。
abstract final class TmsMotion {
  /// 彈入。原型 `tmsPop`，用在打勾之類「剛剛成立」的小圖示上。
  ///
  /// 中途會放大到 1.06 再收回 1.0，因此它不是單一區間的補間：0% 到 60% 從 0.7
  /// 放大到 1.06 並淡入，60% 到 100% 從 1.06 收回 1.0。兩段各自套一次 ease-out，
  /// 和 CSS 對多段 keyframes 的算法一致。
  static const TmsMotionSpec pop = TmsMotionSpec(
    name: 'tmsPop',
    duration: Duration(milliseconds: 420),
    curve: Curves.easeOut,
    stopsForReducedMotion: true,
    frame: _popFrame,
  );

  /// 由上落下並淡入。原型 `tmsDrop`，用在卡片與列表項目進場。
  static const TmsMotionSpec drop = TmsMotionSpec(
    name: 'tmsDrop',
    duration: Duration(milliseconds: 280),
    curve: Curves.easeOut,
    stopsForReducedMotion: true,
    frame: _dropFrame,
  );

  /// 由右滑入並淡入。原型 `tmsSlide`，用在橫向推進的次層畫面內容。
  static const TmsMotionSpec slide = TmsMotionSpec(
    name: 'tmsSlide',
    duration: Duration(milliseconds: 240),
    curve: Curves.easeOut,
    stopsForReducedMotion: true,
    frame: _slideFrame,
  );

  /// 浮起、旋轉並淡出，無限循環。原型 `tmsFloat`，慶祝頁的彩屑用它。
  static const TmsMotionSpec float = TmsMotionSpec(
    name: 'tmsFloat',
    duration: Duration(milliseconds: 1600),
    curve: Curves.easeOut,
    repeat: TmsMotionRepeat.loop,
    stopsForReducedMotion: true,
    frame: _floatFrame,
  );

  /// 明暗呼吸，無限來回。原型 `tmsPulse`，用在「進行中」的指示圓點。
  ///
  /// CSS 寫的 1.4 秒是「亮到暗再回亮」的完整一圈，中間的 50% 是最暗的 0.45。
  /// [duration] 記的是這完整一圈，[TmsMotionView] 播放時會把它折半後來回跑，
  /// 否則一圈會變成 2.8 秒。
  static const TmsMotionSpec pulse = TmsMotionSpec(
    name: 'tmsPulse',
    duration: Duration(milliseconds: 1400),
    curve: Curves.easeInOut,
    repeat: TmsMotionRepeat.pingPong,
    stopsForReducedMotion: true,
    frame: _pulseFrame,
  );

  /// 由下升起。原型 `tmsUp`，底部面板進場用它。
  ///
  /// 原型的位移量是元件自身高度的 100% 而不是固定點數，所以這裡用
  /// [FractionalTranslation] 而不是 [Transform.translate]。
  static const TmsMotionSpec up = TmsMotionSpec(
    name: 'tmsUp',
    duration: Duration(milliseconds: 260),
    curve: Curves.easeOut,
    frame: _upFrame,
  );

  /// 閃一下就退掉。原型 `tmsFlash`，用在拍照後的白色閃光。
  ///
  /// 起點的不透明度是 0.95 而不是 1，拿一般的淡出代替會比原型亮一截。
  static const TmsMotionSpec flash = TmsMotionSpec(
    name: 'tmsFlash',
    duration: Duration(milliseconds: 350),
    curve: Curves.easeOut,
    frame: _flashFrame,
  );

  /// 進度環弧長的補間。原型第 139 行的 `transition: stroke-dasharray .5s ease-out`。
  ///
  /// 原型把它寫成 CSS transition 而不是 keyframes，第 40 行那條減少動態規則點名
  /// 的五個 class 不含它，所以 [TmsMotionSpec.stopsForReducedMotion] 維持預設的否。
  static const TmsMotionSpec ringSweep = TmsMotionSpec(
    name: 'ringSweep',
    duration: Duration(milliseconds: 500),
    curve: Curves.easeOut,
  );

  /// 進度條寬度的補間。原型第 1308 行的 `transition: width .3s ease-out`。
  static const TmsMotionSpec barSweep = TmsMotionSpec(
    name: 'barSweep',
    duration: Duration(milliseconds: 300),
    curve: Curves.easeOut,
  );

  /// 原型定義的九條動畫規則，順序與 `Main.dc.html` 的出現順序一致。
  ///
  /// 測試逐條走這份清單比對原型的時長與曲線，之後新增規則不必再補一次測試。
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

/// 一條動畫規則播完之後要不要繼續。
enum TmsMotionRepeat {
  /// 播一次就停在結束狀態，對應 CSS 的 `animation-fill-mode: both`。
  none,

  /// 播完從頭再來，對應 CSS 的 `infinite`。
  loop,

  /// 播完倒著播回起點再循環，用來表達「0% 與 100% 同值、50% 才是另一端」的 keyframes。
  pingPong,
}

/// 單一幀的畫法。[t] 是 0 到 1 的未套曲線進度，曲線由各畫法在自己的區間上套用。
typedef TmsMotionFrame = Widget Function(
  TmsMotionSpec spec,
  double t,
  Widget child,
);

/// 一條從原型搬過來的動畫規則。
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

  /// 原型裡的 keyframes 名稱，或 transition 的用途名，方便回頭對照設計稿。
  final String name;

  /// 原型 CSS 宣告的時長。來回型（[TmsMotionRepeat.pingPong]）記的是完整一圈。
  final Duration duration;

  final Curve curve;

  final TmsMotionRepeat repeat;

  /// 系統開啟減少動態時這一條停不停，逐條照原型第 40 行的清單。
  final bool stopsForReducedMotion;

  /// 怎麼把進度畫成一幀。兩條 transition 由呼叫端自己補間，所以沒有畫法。
  final TmsMotionFrame? frame;

  /// 這條規則停播時畫面要定格的進度值。進場型的正常樣貌在結尾，循環型在開頭。
  double get restingFrame => repeat == TmsMotionRepeat.none ? 1.0 : 0.0;

  bool isStoppedIn(BuildContext context) =>
      stopsForReducedMotion && MediaQuery.disableAnimationsOf(context);

  /// 這條規則在目前環境下實際可用的時長，停播時為零。
  ///
  /// 自己用 [AnimatedContainer] 之類做補間的呼叫端（例如進度環與進度條）拿這個
  /// 值當 `duration`，就不必各自再判斷一次減少動態設定。
  Duration durationIn(BuildContext context) =>
      isStoppedIn(context) ? Duration.zero : duration;
}

/// 播放一條 [TmsMotionSpec]。
///
/// 進場型在掛上畫面時自動播一次，循環型持續播到元件移除為止。系統開啟減少動態
/// 而這條規則在原型的停播清單內時，這個元件不建立任何 ticker，直接定格在
/// [TmsMotionSpec.restingFrame]。
class TmsMotionView extends StatefulWidget {
  const TmsMotionView({
    super.key,
    required this.spec,
    required this.child,
    this.delay = Duration.zero,
  });

  final TmsMotionSpec spec;
  final Widget child;

  /// 開始播放前的等待。原型慶祝頁的四顆彩屑用依序 0／.3／.6／.9 秒的
  /// `animation-delay` 錯開，由呼叫端在這裡填入那個值。
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

/// 在 [start] 到 [end] 這段原始進度上套一次曲線，區間外夾成 0 或 1。
///
/// CSS 對多段 keyframes 的算法是「每個相鄰區間各套一次 easing」而不是整條動畫
/// 套一次，所以中途有轉折的動畫要逐段算，整條套一次會讓轉折點落在錯的時間。
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
