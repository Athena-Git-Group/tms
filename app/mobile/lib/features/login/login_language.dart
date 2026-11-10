import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_action_bar.dart';
import '../../core/widgets/tms_sheet.dart';

/// 登入頁語言鈕提供的五種語言。
///
/// 每一列印的是該語言自己的寫法，不翻譯（`Main.dc.html:4595`）。看得懂「日本語」
/// 的人不一定看得懂「日文」——選語言這件事的讀者，剛好就是現在這個介面看不懂
/// 的那個人。
enum LoginLanguage {
  traditionalChinese('zh-Hant', '繁體中文'),
  simplifiedChinese('zh-Hans', '简体中文'),
  english('en', 'English'),
  japanese('ja', '日本語'),
  vietnamese('vi', 'Tiếng Việt');

  const LoginLanguage(this.code, this.nativeName);

  /// BCP 47 語言代碼，與原型的 `s.lang` 同一套值。
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

/// 選語言的底部面板。選了哪一個，右側就打一個勾。
///
/// 它不是 [TmsSheet]。原型的面板有兩種：延後、報修、工務單那一類是上緣 38pt
/// 圓角、頂上帶抓柄、左上角一顆玻璃鈕（`Main.dc.html:4932`），而這一個是 iOS
/// 的挑選器——上緣 16pt 圓角、沒有抓柄、右上角一顆「完成」（`Main.dc.html:2377`）。
/// 兩者只有「從底部升起」這件事相同，所以共用的是 [TmsSheetRoute]（遮罩與升起
/// 的曲線），面板本體各畫各的。
class LoginLanguageSheet extends StatelessWidget {
  const LoginLanguageSheet({
    super.key,
    required this.current,
    required this.onPick,
  });

  final LoginLanguage current;

  /// 點某一列時呼叫；關面板由這個面板自己負責。
  final ValueChanged<LoginLanguage> onPick;

  /// 開面板。回傳值是員工選的語言，直接按「完成」或點旁邊關掉時回 null。
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

  /// 標題列：標題在正中央，「完成」靠右。左右兩欄等寬，標題才不會被「完成」
  /// 推離中線。
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
