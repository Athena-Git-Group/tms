import 'package:flutter/cupertino.dart';

import '../../core/api_client.dart';
import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import '../../core/widgets/tms_motion.dart';

class CelebrateScreen extends StatelessWidget {
  const CelebrateScreen({
    super.key,
    required this.actualMinutes,
    required this.subline,
    required this.onBackHome,
    this.roomNo,
    this.estimateMinutes,
    this.todayNth,
    this.nextStep,
    this.queued = false,
    this.celebrationEnabled = true,
    this.onNextStep,
  });

  /// 這張卡是覆蓋層而不是路由，離開要由推它上來的那一頁決定。Flutter 路由的
  /// `ModalBarrier` 會用 `BlockSemantics` 擋掉底下那一頁的 accessibility 節點。
  final VoidCallback onBackHome;

  final bool celebrationEnabled;

  final String? roomNo;

  final String subline;

  final int actualMinutes;

  final int? estimateMinutes;

  final int? todayNth;

  final TaskNextStep? nextStep;

  final bool queued;

  final void Function(TaskNextStep step)? onNextStep;

  @override
  Widget build(BuildContext context) {
    final dialog = _CelebrateDialog(
      roomNo: roomNo,
      subline: subline,
      actualMinutes: actualMinutes,
      estimateMinutes: estimateMinutes,
      todayNth: celebrationEnabled ? todayNth : null,
      nextStep: nextStep,
      queued: queued,
      celebrationEnabled: celebrationEnabled,
      onNextStep: onNextStep,
      onBackHome: onBackHome,
    );
    return TmsSemantics(
      id: 'celebrate.screen',
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        child: ColoredBox(
          color: TmsColors.scrimCelebrate,
          child: SafeArea(
            child: Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(TmsSpacing.xl2),
                child: celebrationEnabled
                    ? TmsMotionView(spec: TmsMotion.pop, child: dialog)
                    : dialog,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _CelebrateDialog extends StatelessWidget {
  const _CelebrateDialog({
    required this.roomNo,
    required this.subline,
    required this.actualMinutes,
    required this.estimateMinutes,
    required this.todayNth,
    required this.nextStep,
    required this.queued,
    required this.celebrationEnabled,
    required this.onNextStep,
    required this.onBackHome,
  });

  final String? roomNo;
  final String subline;
  final int actualMinutes;
  final int? estimateMinutes;
  final int? todayNth;
  final TaskNextStep? nextStep;
  final bool queued;
  final bool celebrationEnabled;
  final void Function(TaskNextStep step)? onNextStep;
  final VoidCallback onBackHome;

  static const double _bannerHeight = 132;

  @override
  Widget build(BuildContext context) {
    return TmsCard(
      style: TmsCardStyle.dialog,
      padding: EdgeInsets.zero,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(TmsRadius.dialog),
        child: Stack(
          children: [
            if (celebrationEnabled)
              Positioned.fill(
                child: TmsSemantics(
                  id: 'celebrate.celebration',
                  child: Stack(
                    children: [
                      Positioned(
                        left: 0,
                        right: 0,
                        top: 0,
                        height: _bannerHeight,
                        child: const ColoredBox(
                          color: TmsColors.brandContainer,
                        ),
                      ),
                      const _Confetti(
                        left: 34,
                        top: 92,
                        size: 10,
                        radius: 3,
                        color: TmsColors.brandAccentVivid,
                      ),
                      const _Confetti(
                        left: 72,
                        top: 110,
                        size: 8,
                        radius: TmsRadius.pill,
                        color: TmsColors.brandPrimary,
                        delay: Duration(milliseconds: 300),
                      ),
                      const _Confetti(
                        right: 48,
                        top: 100,
                        size: 10,
                        radius: TmsRadius.pill,
                        color: Color(0xFFFFB783),
                        delay: Duration(milliseconds: 600),
                      ),
                      const _Confetti(
                        right: 86,
                        top: 116,
                        size: 8,
                        radius: 2,
                        color: TmsColors.systemGreen,
                        delay: Duration(milliseconds: 900),
                      ),
                    ],
                  ),
                ),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(
                22,
                TmsSpacing.xxl,
                22,
                TmsSpacing.xl,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: _cardChildren(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _cardChildren() {
    final step = nextStep;
    return <Widget>[
      const _CheckMark(),
      const SizedBox(height: TmsSpacing.xl),
      TmsSemantics(
        id: 'celebrate.title',
        child: Text(
          roomNo == null ? '完成！' : '$roomNo 完成！',
          textAlign: TextAlign.center,
          style: _headlineStyle,
        ),
      ),
      const SizedBox(height: TmsSpacing.md),
      TmsSemantics(
        id: 'celebrate.subline',
        label: subline,
        child: ExcludeSemantics(
          child: Text(
            subline,
            textAlign: TextAlign.center,
            style: TmsText.subheadline.copyWith(
              color: TmsColors.secondaryLabel,
            ),
          ),
        ),
      ),
      const SizedBox(height: TmsSpacing.lg),
      _StatTiles(
        actualMinutes: actualMinutes,
        estimateMinutes: estimateMinutes,
        todayNth: todayNth,
      ),
      if (queued) ...[
        const SizedBox(height: TmsSpacing.md),
        TmsSemantics(
          id: 'celebrate.queued-note',
          label: _queuedNote,
          child: ExcludeSemantics(
            child: Text(
              _queuedNote,
              textAlign: TextAlign.center,
              style: TmsText.footnote.copyWith(color: TmsColors.brandAccent),
            ),
          ),
        ),
      ],
      if (step != null) ...[
        if (step.kind == TaskNextStepKind.allClear) ...[
          const SizedBox(height: TmsSpacing.lg),
          TmsSemantics(
            id: 'celebrate.all-done-note',
            label: _allDoneNote,
            child: ExcludeSemantics(
              child: Text(
                _allDoneNote,
                textAlign: TextAlign.center,
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.secondaryLabel,
                ),
              ),
            ),
          ),
          const SizedBox(height: TmsSpacing.md),
        ] else
          const SizedBox(height: TmsSpacing.lg),
        _NextStepButton(step: step, onPressed: onNextStep),
      ],
      const SizedBox(height: TmsSpacing.md),
      TmsSemantics(
        id: 'celebrate.back-home-button',
        label: _backHomeLabel,
        child: ExcludeSemantics(
          child: CupertinoButton(
            padding: EdgeInsets.zero,
            borderRadius: BorderRadius.circular(TmsRadius.pill),
            minimumSize: const Size(double.infinity, _backHomeButtonHeight),
            onPressed: onBackHome,
            child: Text(
              _backHomeLabel,
              style: TmsText.headline.copyWith(color: TmsColors.brandPrimary),
            ),
          ),
        ),
      ),
    ];
  }
}

const String _backHomeLabel = '回到首頁';

const String _queuedNote ='目前離線，已先記錄在手機，恢復連線後自動送出。';

const String _allDoneNote = '排定的房都清完了，有空可以到「待認領」幫忙接單。';

class _NextStepButton extends StatelessWidget {
  const _NextStepButton({required this.step, required this.onPressed});

  final TaskNextStep step;
  final void Function(TaskNextStep step)? onPressed;

  @override
  Widget build(BuildContext context) {
    final roomNo = step.roomNo;
    final label = switch (step.kind) {
      TaskNextStepKind.allClear => '去「待認領」看看',
      _ => roomNo == null ? '下一間' : '下一間：$roomNo',
    };
    final callback = onPressed;
    return TmsSemantics(
      id: 'celebrate.next-button',
      label: label,
      enabled: callback != null,
      child: ExcludeSemantics(
        child: CupertinoButton(
          padding: EdgeInsets.zero,
          borderRadius: BorderRadius.circular(TmsRadius.pill),
          color: TmsColors.brandPrimary,
          minimumSize: const Size(double.infinity, _nextButtonHeight),
          onPressed: callback == null ? null : () => callback(step),
          child: Text(
            label,
            style: TmsText.headline.copyWith(color: TmsColors.brandOnPrimary),
          ),
        ),
      ),
    );
  }
}

const TextStyle _headlineStyle = TextStyle(
  fontSize: 26,
  fontWeight: FontWeight.w800,
  color: TmsColors.label,
);

const double _nextButtonHeight = 52;

const double _backHomeButtonHeight = 44;

class _CheckMark extends StatelessWidget {
  const _CheckMark();

  static const double _size = 88;

  @override
  Widget build(BuildContext context) {
    return Transform.rotate(
      angle: -6 * 3.1415926535 / 180,
      child: Container(
        width: _size,
        height: _size,
        decoration: BoxDecoration(
          color: TmsColors.brandPrimary,
          borderRadius: BorderRadius.circular(TmsRadius.xl),
          boxShadow: const [
            BoxShadow(
              color: Color(0x590C7680),
              blurRadius: 24,
              offset: Offset(0, 10),
            ),
          ],
        ),
        child: CustomPaint(painter: _CheckPainter()),
      ),
    );
  }
}

class _CheckPainter extends CustomPainter {
  static const double _glyphSize = 48;

  static const double _viewBox = 24;

  @override
  void paint(Canvas canvas, Size size) {
    final unit = _glyphSize / _viewBox;
    final origin = Offset(
      (size.width - _glyphSize) / 2,
      (size.height - _glyphSize) / 2,
    );
    Offset at(double x, double y) => origin + Offset(x * unit, y * unit);
    final paint = Paint()
      ..color = TmsColors.brandOnPrimary
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.6 * unit
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final path = Path()
      ..moveTo(at(5, 12.5).dx, at(5, 12.5).dy)
      ..lineTo(at(9.5, 17).dx, at(9.5, 17).dy)
      ..lineTo(at(19, 7.5).dx, at(19, 7.5).dy);
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(_CheckPainter oldDelegate) => false;
}

class _Confetti extends StatelessWidget {
  const _Confetti({
    this.left,
    this.right,
    required this.top,
    required this.size,
    required this.radius,
    required this.color,
    this.delay = Duration.zero,
  });

  final double? left;
  final double? right;
  final double top;
  final double size;
  final double radius;
  final Color color;
  final Duration delay;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: left,
      right: right,
      top: top,
      child: TmsMotionView(
        spec: TmsMotion.float,
        delay: delay,
        child: Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(radius),
          ),
        ),
      ),
    );
  }
}

class _StatTiles extends StatelessWidget {
  const _StatTiles({
    required this.actualMinutes,
    required this.estimateMinutes,
    required this.todayNth,
  });

  final int actualMinutes;
  final int? estimateMinutes;
  final int? todayNth;

  @override
  Widget build(BuildContext context) {
    final estimate = estimateMinutes;
    final nth = todayNth;
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: _StatTile(
              id: 'celebrate.actual-minutes',
              caption: estimate == null ? '實際' : '實際／預估',
              value: '$actualMinutes',
              valueTail: estimate == null ? ' 分' : ' / $estimate 分',
              background: TmsColors.groupedBackground,
              captionColor: TmsColors.secondaryLabel,
              valueColor: TmsColors.label,
            ),
          ),
          if (nth != null) ...[
            const SizedBox(width: TmsSpacing.sm),
            Expanded(
              child: _StatTile(
                id: 'celebrate.today-progress',
                caption: '今日進度',
                value: '第 $nth 單',
                background: TmsColors.brandAccentContainer,
                captionColor: TmsColors.brandOnAccentContainer,
                valueColor: TmsColors.onOvertimeCapsule,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _StatTile extends StatelessWidget {
  const _StatTile({
    required this.id,
    required this.caption,
    required this.value,
    required this.background,
    required this.captionColor,
    required this.valueColor,
    this.valueTail,
  });

  final String id;
  final String caption;
  final String value;

  final String? valueTail;
  final Color background;
  final Color captionColor;
  final Color valueColor;

  @override
  Widget build(BuildContext context) {
    final tail = valueTail;
    return TmsSemantics(
      id: id,
      label: tail == null ? value : '$value$tail',
      child: ExcludeSemantics(
        child: TmsCard(
          backgroundColor: background,
          padding: const EdgeInsets.all(TmsSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                caption,
                style: TmsText.caption1
                    .copyWith(fontWeight: FontWeight.w400, color: captionColor),
              ),
              const SizedBox(height: TmsSpacing.xxs),
              Text.rich(
                TextSpan(
                  text: value,
                  style: TmsText.title3.copyWith(color: valueColor),
                  children: [
                    if (tail != null)
                      TextSpan(
                        text: tail,
                        style: TmsText.footnoteMedium
                            .copyWith(color: TmsColors.secondaryLabel),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
