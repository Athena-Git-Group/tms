import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_sheet.dart';

enum LoginLanguage {
  traditionalChinese('zh-Hant', '繁體中文'),
  simplifiedChinese('zh-Hans', '简体中文'),
  english('en', 'English'),
  japanese('ja', '日本語'),
  vietnamese('vi', 'Tiếng Việt');

  const LoginLanguage(this.code, this.nativeName);

  final String code;

  final String nativeName;
}

class LoginLanguageButton extends StatelessWidget {
  const LoginLanguageButton({
    super.key,
    required this.current,
    required this.onPressed,
  });

  final LoginLanguage current;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'login.language-button',
      label: '語言，${current.nativeName}',
      child: ExcludeSemantics(
        child: CupertinoButton(
          padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.md),
          minimumSize: const Size(0, TmsSize.minTouchTarget),
          onPressed: onPressed,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const Icon(
                CupertinoIcons.globe,
                size: 20,
                color: TmsColors.brandPrimary,
              ),
              const SizedBox(width: TmsSpacing.xs2),
              Text(
                current.nativeName,
                style: TmsText.subheadline.copyWith(
                  color: TmsColors.brandPrimary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class LoginLanguageSheet extends StatelessWidget {
  const LoginLanguageSheet({
    super.key,
    required this.current,
    required this.onPick,
  });

  final LoginLanguage current;

  final ValueChanged<LoginLanguage> onPick;

  static Future<LoginLanguage?> show(
    BuildContext context, {
    required LoginLanguage current,
  }) {
    return TmsSheetRoute.show<LoginLanguage>(
      context,
      (sheetContext) => LoginLanguageSheet(
        current: current,
        onPick: (language) => Navigator.of(sheetContext).pop(language),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return TmsSemantics(
      id: 'login.language-sheet',
      explicitChildNodes: true,
      child: Container(
        width: double.infinity,
        padding: EdgeInsets.fromLTRB(
          TmsSpacing.screenEdge,
          TmsSpacing.sm,
          TmsSpacing.screenEdge,
          TmsActionBar.bottomInset(context),
        ),
        decoration: const BoxDecoration(
          color: TmsColors.groupedBackground,
          borderRadius: BorderRadius.vertical(
            top: Radius.circular(TmsRadius.card),
          ),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            _titleRow(context),
            const SizedBox(height: TmsSpacing.sm),
            _rows(),
          ],
        ),
      ),
    );
  }

  Widget _titleRow(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 52),
      child: Row(
        children: <Widget>[
          const Expanded(child: SizedBox.shrink()),
          const Text('語言', style: TmsText.headline),
          Expanded(
            child: Align(
              alignment: Alignment.centerRight,
              child: CupertinoButton(
                padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.xs),
                minimumSize: const Size(0, TmsSize.minTouchTarget),
                onPressed: () => Navigator.of(context).pop(),
                child: Text(
                  '完成',
                  style: TmsText.headline.copyWith(
                    color: TmsColors.brandPrimary,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _rows() {
    final rows = <Widget>[];
    for (final language in LoginLanguage.values) {
      if (rows.isNotEmpty) {
        rows.add(
          const SizedBox(
            height: TmsSize.hairline,
            child: ColoredBox(color: TmsColors.separator),
          ),
        );
      }
      rows.add(_row(language));
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(TmsRadius.cardInSheet),
      child: ColoredBox(
        color: TmsColors.cardBackground,
        child: Column(mainAxisSize: MainAxisSize.min, children: rows),
      ),
    );
  }

  Widget _row(LoginLanguage language) {
    final selected = language == current;
    return CupertinoButton(
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      minimumSize: const Size(double.infinity, 48),
      borderRadius: BorderRadius.zero,
      onPressed: () => onPick(language),
      child: Row(
        children: <Widget>[
          Expanded(
            child: Text(
              language.nativeName,
              style: TmsText.body,
              textAlign: TextAlign.start,
            ),
          ),
          if (selected)
            const Icon(
              CupertinoIcons.check_mark,
              size: 18,
              color: TmsColors.brandPrimary,
            ),
        ],
      ),
    );
  }
}
