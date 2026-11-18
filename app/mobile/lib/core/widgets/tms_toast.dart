import 'dart:async';

import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';

class TmsToast {
  const TmsToast._();

  static const Duration visible = Duration(milliseconds: 2200);

  static const Duration fade = Duration(milliseconds: 400);

  static const Duration lifetime = Duration(milliseconds: 2800);

  static TmsToastHandle? _current;

  static TmsToastHandle show(
    BuildContext context,
    String message, {
    String id = 'toast',
  }) {
    _current?.dismiss();
    final overlay = Overlay.of(context, rootOverlay: true);
    final handle = TmsToastHandle._();
    final entry = OverlayEntry(
      builder: (_) => _TmsToastView(message: message, id: id),
    );
    handle._attach(entry);
    overlay.insert(entry);
    _current = handle;
    ModalRoute.of(context)?.popped.whenComplete(handle.dismiss);
    return handle;
  }
}

class TmsToastHandle {
  TmsToastHandle._();

  OverlayEntry? _entry;
  Timer? _timer;

  void _attach(OverlayEntry entry) {
    _entry = entry;
    _timer = Timer(TmsToast.lifetime, dismiss);
  }

  void dismiss() {
    _timer?.cancel();
    _timer = null;
    final entry = _entry;
    _entry = null;
    if (entry != null) {
      entry.remove();
      entry.dispose();
    }
    if (identical(TmsToast._current, this)) {
      TmsToast._current = null;
    }
  }
}

class _TmsToastView extends StatefulWidget {
  const _TmsToastView({required this.message, required this.id});

  final String message;

  final String id;

  @override
  State<_TmsToastView> createState() => _TmsToastViewState();
}

class _TmsToastViewState extends State<_TmsToastView> {
  static const double _gapBelowStatusBar = 8;

  double _opacity = 1;
  Timer? _fadeTimer;

  @override
  void initState() {
    super.initState();
    _fadeTimer = Timer(TmsToast.visible, () {
      if (mounted) {
        setState(() => _opacity = 0);
      }
    });
  }

  @override
  void dispose() {
    _fadeTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final top = MediaQuery.paddingOf(context).top + _gapBelowStatusBar;
    return Positioned(
      top: top,
      left: TmsSpacing.xl,
      right: TmsSpacing.xl,
      child: IgnorePointer(
        child: AnimatedOpacity(
          opacity: _opacity,
          duration: TmsToast.fade,
          child: TmsSemantics(
            id: widget.id,
            child: Container(
              padding: const EdgeInsets.symmetric(
                horizontal: TmsSpacing.lg,
                vertical: TmsSpacing.md,
              ),
              decoration: BoxDecoration(
                color: TmsColors.brandPrimaryDark,
                borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
                boxShadow: const <BoxShadow>[
                  BoxShadow(
                    color: Color(0x33000000),
                    offset: Offset(0, 8),
                    blurRadius: 24,
                  ),
                ],
              ),
              child: Text(
                widget.message,
                style: TmsText.subheadlineEmphasized.copyWith(
                  color: TmsColors.brandOnPrimary,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
