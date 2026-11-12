import 'package:flutter/cupertino.dart';

import '../theme.dart';
import '../tms_semantics.dart';
import 'tms_glass.dart';

enum TmsAlertActionStyle {
  normal,

  primary,

  destructive,
}

class TmsAlertAction {
  const TmsAlertAction({
    required this.label,
    required this.onPressed,
    this.style = TmsAlertActionStyle.normal,
    this.id,
  });

  final String label;

  final VoidCallback onPressed;

  final TmsAlertActionStyle style;

  final String? id;
}

class TmsAlert extends StatelessWidget {
  const TmsAlert({
    super.key,
    this.title,
    required this.message,
    required this.actions,
    this.messageId,
  });

  final String? title;

  final String message;

  final String? messageId;

  final List<TmsAlertAction> actions;

  static const double width = 300;

  static const double _blur = 30;
  static const double _saturation = 180;

  static const Color _background = Color(0xD1FFFFFF);

  static const Color _secondaryActionBackground = Color(0x29787880);

  static const double _actionHeight = 48;

  static const double _actionGap = 10;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      scopesRoute: true,
      explicitChildNodes: true,
      child: Center(
        child: SizedBox(
          width: width,
          child: TmsGlass(
            blur: _blur,
            saturation: _saturation,
            color: _background,
            borderRadius: BorderRadius.circular(TmsRadius.alert),
            boxShadow: const [
              BoxShadow(
                color: Color(0x40000000),
                offset: Offset(0, 20),
                blurRadius: 50,
              ),
            ],
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Flexible(child: SingleChildScrollView(child: _textBlock())),
                _actionRow(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _textBlock() {
    Widget messageText = Text(
      message,
      textAlign: TextAlign.left,
      style: TmsText.subheadline.copyWith(height: 1.4),
    );
    final id = messageId;
    if (id != null) {
      messageText = TmsSemantics(
        id: id,
        label: message,
        child: ExcludeSemantics(child: messageText),
      );
    }
    final titleText = title;
    return Padding(
      padding: const EdgeInsets.fromLTRB(22, 22, 22, 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (titleText != null) ...[
            Text(titleText, textAlign: TextAlign.left, style: TmsText.headline),
            const SizedBox(height: 4),
          ],
          messageText,
        ],
      ),
    );
  }

  Widget _actionRow() {
    final buttons = <Widget>[];
    for (final action in actions) {
      if (buttons.isNotEmpty) {
        buttons.add(const SizedBox(width: _actionGap));
      }
      buttons.add(Expanded(child: _actionButton(action)));
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
      child: Row(children: buttons),
    );
  }

  Widget _actionButton(TmsAlertAction action) {
    final Color background;
    final Color foreground;
    final FontWeight weight;
    switch (action.style) {
      case TmsAlertActionStyle.normal:
        background = _secondaryActionBackground;
        foreground = TmsColors.brandPrimary;
        weight = FontWeight.w400;
      case TmsAlertActionStyle.primary:
        background = TmsColors.brandPrimary;
        foreground = TmsColors.brandOnPrimary;
        weight = FontWeight.w600;
      case TmsAlertActionStyle.destructive:
        background = _secondaryActionBackground;
        foreground = TmsColors.systemRed;
        weight = FontWeight.w600;
    }
    final Widget button = CupertinoButton(
      padding: EdgeInsets.zero,
      borderRadius: BorderRadius.circular(TmsRadius.pill),
      color: background,
      minimumSize: const Size(double.infinity, _actionHeight),
      onPressed: action.onPressed,
      child: Text(
        action.label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TmsText.body.copyWith(color: foreground, fontWeight: weight),
      ),
    );
    final id = action.id;
    if (id == null) {
      return button;
    }
    return TmsSemantics(id: id, child: button);
  }
}
