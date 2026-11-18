import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import 'password_rules.dart';

class LoginBrandHeader extends StatelessWidget {
  const LoginBrandHeader({super.key, required this.title, this.subtitle});

  final String title;

  final String? subtitle;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 64,
          height: 64,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: TmsColors.brandPrimary,
            borderRadius: BorderRadius.circular(TmsRadius.card),
            boxShadow: const <BoxShadow>[
              BoxShadow(
                color: Color(0x400C7680),
                offset: Offset(0, 6),
                blurRadius: 16,
              ),
            ],
          ),
          child: const CustomPaint(
            size: Size.square(34),
            painter: _HouseCheckPainter(),
          ),
        ),
        const SizedBox(height: TmsSpacing.md2),
        Text(title, style: TmsText.title1),
        if (subtitle != null) ...[
          const SizedBox(height: TmsSpacing.xs2),
          Text(
            subtitle!,
            style: TmsText.subheadline.copyWith(
              color: TmsColors.secondaryLabel,
            ),
          ),
        ],
      ],
    );
  }
}

class _HouseCheckPainter extends CustomPainter {
  const _HouseCheckPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / 24;
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..color = TmsColors.brandOnPrimary
      ..strokeWidth = 1.9 * scale
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final house = Path()
      ..moveTo(3.5 * scale, 10.5 * scale)
      ..lineTo(12 * scale, 4 * scale)
      ..lineTo(20.5 * scale, 10.5 * scale)
      ..lineTo(20.5 * scale, 20 * scale)
      ..lineTo(3.5 * scale, 20 * scale)
      ..close();
    final check = Path()
      ..moveTo(9 * scale, 14.5 * scale)
      ..lineTo(11.2 * scale, 16.7 * scale)
      ..lineTo(15.5 * scale, 12 * scale);

    canvas.drawPath(house, paint);
    canvas.drawPath(check, paint);
  }

  @override
  bool shouldRepaint(_HouseCheckPainter oldDelegate) => false;
}

class LoginIdentityRow extends StatelessWidget {
  const LoginIdentityRow({
    super.key,
    required this.name,
    required this.subtitle,
  });

  final String name;

  final String subtitle;

  static String initialOf(String name) =>
      name.isEmpty ? '' : name.characters.last;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: TmsColors.cardBackground,
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.screenEdge,
        vertical: TmsSpacing.md,
      ),
      child: Row(
        children: <Widget>[
          Container(
            width: TmsSize.minTouchTarget,
            height: TmsSize.minTouchTarget,
            alignment: Alignment.center,
            decoration: const BoxDecoration(
              color: TmsColors.brandAccentContainer,
              shape: BoxShape.circle,
            ),
            child: Text(
              initialOf(name),
              style: const TextStyle(
                fontFamilyFallback: TmsText.fontFamilyStack,
                fontSize: 19,
                fontWeight: FontWeight.w700,
                color: TmsColors.brandOnAccentContainer,
              ),
            ),
          ),
          const SizedBox(width: TmsSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Text(name, style: TmsText.headline),
                const SizedBox(height: TmsSpacing.xxs),
                Text(
                  subtitle,
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.secondaryLabel,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class LoginPasswordVisibilityButton extends StatelessWidget {
  const LoginPasswordVisibilityButton({
    super.key,
    required this.obscured,
    required this.onPressed,
    required this.semanticsId,
  });

  final bool obscured;

  final VoidCallback onPressed;

  final String semanticsId;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: semanticsId,
      label: obscured ? '顯示密碼' : '隱藏密碼',
      child: ExcludeSemantics(
        child: CupertinoButton(
          padding: EdgeInsets.zero,
          minimumSize: const Size(
            TmsSize.minTouchTarget,
            TmsSize.minTouchTarget,
          ),
          onPressed: onPressed,
          child: Icon(
            obscured ? CupertinoIcons.eye : CupertinoIcons.eye_slash,
            size: 22,
            color: TmsColors.quaternaryLabel,
          ),
        ),
      ),
    );
  }
}

class LoginFormCard extends StatelessWidget {
  const LoginFormCard({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      if (i > 0) {
        rows.add(
          const SizedBox(
            height: TmsSize.hairline,
            child: ColoredBox(color: TmsColors.separator),
          ),
        );
      }
      rows.add(children[i]);
    }
    return TmsCard(
      style: TmsCardStyle.inSheet,
      padding: EdgeInsets.zero,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
        child: Column(mainAxisSize: MainAxisSize.min, children: rows),
      ),
    );
  }
}

class LoginFieldRow extends StatelessWidget {
  const LoginFieldRow({
    super.key,
    required this.label,
    required this.field,
    this.trailing,
    this.hasError = false,
  });

  final String label;

  final Widget field;

  final Widget? trailing;

  final bool hasError;

  static const double _labelWidth = 80;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: TmsColors.cardBackground,
      padding: EdgeInsets.only(
        left: TmsSpacing.screenEdge,
        right: trailing == null ? TmsSpacing.screenEdge : TmsSpacing.xs2,
      ),
      constraints: const BoxConstraints(minHeight: 48),
      child: Row(
        children: <Widget>[
          SizedBox(
            width: _labelWidth,
            child: Text(
              label,
              style: hasError
                  ? TmsText.body.copyWith(color: TmsColors.systemRed)
                  : TmsText.body,
            ),
          ),
          Expanded(child: field),
          ?trailing,
        ],
      ),
    );
  }
}

class LoginTextField extends StatelessWidget {
  const LoginTextField({
    super.key,
    required this.controller,
    this.obscureText = false,
    this.maxLength,
    this.uppercase = false,
    this.autofillHints,
  });

  final TextEditingController controller;
  final bool obscureText;

  final int? maxLength;

  final bool uppercase;

  final List<String>? autofillHints;

  @override
  Widget build(BuildContext context) {
    return CupertinoTextField(
      controller: controller,
      obscureText: obscureText,
      autofillHints: autofillHints,
      textCapitalization: uppercase
          ? TextCapitalization.characters
          : TextCapitalization.none,
      maxLength: maxLength,
      inputFormatters: <TextInputFormatter>[
        if (uppercase) UpperCaseTextInputFormatter(),
      ],
      padding: const EdgeInsets.symmetric(vertical: TmsSpacing.md),
      decoration: const BoxDecoration(),
      style: TmsText.body,
    );
  }
}

/// [TextCapitalization.characters] 只是請鍵盤把 shift 打開。
/// 貼上的文字與不理會這個提示的第三方鍵盤不會被轉成大寫。
class UpperCaseTextInputFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    return newValue.copyWith(text: newValue.text.toUpperCase());
  }
}

class LoginErrorText extends StatelessWidget {
  const LoginErrorText({super.key, required this.message, this.semanticsId});

  final String message;

  final String? semanticsId;

  @override
  Widget build(BuildContext context) {
    final Widget text = Padding(
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      child: Text(
        message,
        style: TmsText.footnote.copyWith(color: TmsColors.systemRed),
      ),
    );
    final id = semanticsId;
    if (id == null) {
      return text;
    }
    return TmsSemantics(
      id: id,
      label: message,
      child: ExcludeSemantics(child: text),
    );
  }
}

class LoginNoticeCard extends StatelessWidget {
  const LoginNoticeCard({
    super.key,
    required this.semanticsId,
    required this.iconColor,
    required this.title,
    required this.body,
  });

  final String semanticsId;

  final Color iconColor;

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final card = TmsCard(
      style: TmsCardStyle.inSheet,
      padding: const EdgeInsets.symmetric(
        horizontal: TmsSpacing.screenEdge,
        vertical: TmsSpacing.md2,
      ),
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            alignment: Alignment.center,
            decoration: BoxDecoration(color: iconColor, shape: BoxShape.circle),
            child: const Icon(
              CupertinoIcons.lock_fill,
              size: 18,
              color: TmsColors.brandOnPrimary,
            ),
          ),
          const SizedBox(width: TmsSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(title, style: TmsText.headline),
                const SizedBox(height: TmsSpacing.xxs),
                Text(
                  body,
                  style: TmsText.subheadline.copyWith(
                    color: TmsColors.secondaryLabel,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
    return TmsSemantics(
      id: semanticsId,
      label: body,
      child: ExcludeSemantics(child: card),
    );
  }
}

class LoginCheckBadge extends StatelessWidget {
  const LoginCheckBadge({
    super.key,
    required this.size,
    required this.strokeWidth,
  });

  final double size;

  final double strokeWidth;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      size: Size.square(size),
      painter: _CheckBadgePainter(strokeWidth: strokeWidth),
    );
  }
}

class _CheckBadgePainter extends CustomPainter {
  const _CheckBadgePainter({required this.strokeWidth});

  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / 24;
    canvas.drawCircle(
      Offset(12 * scale, 12 * scale),
      10.5 * scale,
      Paint()..color = TmsColors.systemGreen,
    );
    final check = Path()
      ..moveTo(7.5 * scale, 12.4 * scale)
      ..lineTo(10.5 * scale, 15.4 * scale)
      ..lineTo(16.5 * scale, 8.8 * scale);
    canvas.drawPath(
      check,
      Paint()
        ..style = PaintingStyle.stroke
        ..color = TmsColors.brandOnPrimary
        ..strokeWidth = strokeWidth * scale
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(_CheckBadgePainter oldDelegate) =>
      oldDelegate.strokeWidth != strokeWidth;
}

class LoginUncheckedMark extends StatelessWidget {
  const LoginUncheckedMark({super.key, required this.size});

  final double size;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      size: Size.square(size),
      painter: const _UncheckedMarkPainter(),
    );
  }
}

class _UncheckedMarkPainter extends CustomPainter {
  const _UncheckedMarkPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / 24;
    canvas.drawCircle(
      Offset(12 * scale, 12 * scale),
      9.5 * scale,
      Paint()
        ..style = PaintingStyle.stroke
        ..color = TmsColors.systemGray3
        ..strokeWidth = 2 * scale,
    );
  }

  @override
  bool shouldRepaint(_UncheckedMarkPainter oldDelegate) => false;
}

class PasswordRuleChecklist extends StatelessWidget {
  const PasswordRuleChecklist({
    super.key,
    required this.rules,
    this.semanticsId,
  });

  final List<PasswordRule> rules;

  final String? semanticsId;

  static const double _markSize = 14;

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    for (final rule in rules) {
      if (rows.isNotEmpty) {
        rows.add(const SizedBox(height: TmsSpacing.xs));
      }
      rows.add(
        Row(
          children: <Widget>[
            rule.satisfied
                ? const LoginCheckBadge(size: _markSize, strokeWidth: 2.6)
                : const LoginUncheckedMark(size: _markSize),
            const SizedBox(width: TmsSpacing.xs2),
            Expanded(
              child: Text(
                rule.label,
                style: TmsText.footnote.copyWith(
                  height: 1.3,
                  color: rule.satisfied
                      ? TmsColors.onSuccessContainer
                      : TmsColors.secondaryLabel,
                ),
              ),
            ),
          ],
        ),
      );
    }
    final Widget list = Padding(
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: rows,
      ),
    );
    final id = semanticsId;
    if (id == null) {
      return list;
    }
    return TmsSemantics(
      id: id,
      label: rules
          .map((rule) => '${rule.label}：${rule.satisfied ? '已符合' : '還沒符合'}')
          .join('，'),
      child: ExcludeSemantics(child: list),
    );
  }
}
