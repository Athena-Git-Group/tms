import 'dart:async';

import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';

/// 頂部短暫提示：做完一件事但畫面不會換頁時，用它告訴使用者那件事成功了。
///
/// 提示疊在整個 App 的最上層，停留兩秒多後自己淡掉。它不是一層路由，也不吃
/// 點擊事件，所以使用者在提示還在時仍然可以照常捲動、按按鈕、用返回手勢。
class TmsToast {
  const TmsToast._();

  /// 提示開始淡出的時間點。
  static const Duration visible = Duration(milliseconds: 2200);

  /// 淡出動畫長度。
  static const Duration fade = Duration(milliseconds: 400);

  /// 從出現到完全移除的時間。
  static const Duration lifetime = Duration(milliseconds: 2800);

  /// 同一時間只留一則提示，新的蓋掉舊的。
  static TmsToastHandle? _current;

  /// 在 [context] 所在畫面的最上層顯示一則 [message]。
  ///
  /// 提示到時間會自己移除，呼叫端通常不必做任何收尾：前一則提示還在時會先被
  /// 收掉（兩則不會疊在同一個位置互相壓字），而 [context] 所在的那一層路由被
  /// 退掉時提示也會跟著消失，不會飄在下一個畫面上。
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

/// [TmsToast.show] 顯示出來的那一則提示，讓呼叫端可以提早把它收掉。
class TmsToastHandle {
  TmsToastHandle._();

  OverlayEntry? _entry;
  Timer? _timer;

  void _attach(OverlayEntry entry) {
    _entry = entry;
    _timer = Timer(TmsToast.lifetime, dismiss);
  }

  /// 立刻移除提示並取消計時器。重複呼叫不會有副作用。
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
