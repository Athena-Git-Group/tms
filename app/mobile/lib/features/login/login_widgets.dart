import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';
import 'password_rules.dart';

/// 登入與強制改密兩個畫面共用的版面零件。
///
/// 這兩頁是同一段流程的前後兩步，員工在幾秒內會連著看到。零件各寫一份的話，
/// 兩頁的欄位高度、卡片圓角與按鈕位置就會差一點，看起來像兩個不同的 App。

/// 登入流程頁面最上面的招牌：品牌方塊、一行大標、一行說明。
class LoginBrandHeader extends StatelessWidget {
  const LoginBrandHeader({super.key, required this.title, this.subtitle});

  /// 大標，例如「歡迎回來」或員工的姓名。
  final String title;

  /// 大標下面那一行說明；不給就不畫。
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
              // Main.dc.html:2324 的 `0 6px 16px rgba(12,118,128,.25)`。
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

/// 品牌方塊裡那個「房子外框加一個打勾」的圖。
///
/// `CupertinoIcons` 的房子字形都是整間房子（實心或線框），沒有一個在屋內帶
/// 打勾，所以照原型的兩條 path 自己描一次（`Main.dc.html:2324`）。
class _HouseCheckPainter extends CustomPainter {
  const _HouseCheckPainter();

  @override
  void paint(Canvas canvas, Size size) {
    // 原型的 path 畫在 24×24 的 viewBox 上，線寬 1.9 也是那個尺度下的值。
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

/// 記住帳號時，表單白卡最上面那一列：圓形頭像、姓名、以及身分的第二行。
///
/// 它和密碼欄在同一張卡裡，不是另外一張。員工看到的是「這個人，輸入他的密碼」
/// 一件事；拆成兩張卡會變成兩件不相干的事，中間那段空白還會讓人以為身分卡是
/// 可以點開來換人的。
class LoginIdentityRow extends StatelessWidget {
  const LoginIdentityRow({
    super.key,
    required this.name,
    required this.subtitle,
  });

  /// 員工姓名。
  final String name;

  /// 姓名底下那一行，例如員工編號。
  final String subtitle;

  /// 頭像裡那個字。中文名取最後一個字（阿美→美、阿德→德），對齊原型的
  /// `acct.initial`（`Main.dc.html:4504`）。
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
              // 19pt 不在字級表上，是原型給頭像單獨訂的（Main.dc.html:2342）。
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

/// 密碼欄右側那顆眼睛：切換密碼要不要遮起來。
///
/// 員工在走廊上單手打密碼，打錯了看不出來是哪一個字打錯；能看一眼再送出，比
/// 連錯五次被鎖十五分鐘好。
class LoginPasswordVisibilityButton extends StatelessWidget {
  const LoginPasswordVisibilityButton({
    super.key,
    required this.obscured,
    required this.onPressed,
    required this.semanticsId,
  });

  /// 密碼現在是不是遮著的。
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

/// 表單的白卡：裡面的列由上到下排，列與列之間一條髮絲線。
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

/// 表單白卡裡的一列輸入欄：左邊是欄位名稱，右邊是輸入區。
///
/// 名稱固定留在列內，不用提示字（placeholder）：提示字一輸入就消失，員工打到
/// 一半被打斷回來時，看到的是兩個都有字的欄位，分不出哪一格是員工編號。
class LoginFieldRow extends StatelessWidget {
  const LoginFieldRow({
    super.key,
    required this.label,
    required this.field,
    this.trailing,
    this.hasError = false,
  });

  /// 欄位名稱。
  final String label;

  /// 輸入區本身，由呼叫端給——它要掛自己的 accessibility id。
  final Widget field;

  /// 輸入區右側的鈕，目前只有密碼欄的眼睛用得到。
  ///
  /// 有這顆鈕的那一列，右側內距是 6 而不是 16。鈕本身是 44pt 見方的觸控區，
  /// 中間的圖示只佔 22pt，右側一樣給 16 的話，圖示會比其他列的右緣再往內縮。
  final Widget? trailing;

  /// 這一欄這次送出有沒有出錯。
  ///
  /// 出錯時名稱轉紅。錯誤訊息一律是「員工編號或密碼不正確」，不講是哪一格
  /// 打錯的，紅色的名稱是員工唯一看得出該重打哪一格的線索。
  final bool hasError;

  /// 名稱欄的寬度。兩頁的欄位名稱都在四個字以內，固定寬度讓各列的輸入區左緣
  /// 對齊；跟著文字長度變寬的話，每一列的游標起點都不一樣。
  ///
  /// 80 是原型的繁體中文值（`Main.dc.html:4526`）。原型另外給日文與英文 116、
  /// 越南文 140，那幾個值要等介面語言真的換得動才有意義。
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

/// 表單列裡的輸入框：沒有外框與底色，框線交給外面那張白卡。
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

  /// 最多能打幾個字；null 代表不限。超過的字打不進去，不另外跳提示。
  final int? maxLength;

  /// 打進去的小寫字母要不要當場變成大寫。
  ///
  /// 員工編號印在識別證上是 `H-0532` 這種大寫寫法，鍵盤卻會從小寫開始。
  /// 不轉的話，螢幕上的字和識別證上的字長得不一樣，員工會以為自己打錯了。
  final bool uppercase;

  /// 交給 iOS 鑰匙圈判斷要不要跳自動填入的提示，例如
  /// [AutofillHints.username]。
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

/// 把輸入的字一律轉成大寫。
///
/// [TextCapitalization.characters] 只是請鍵盤把 shift 打開，員工貼上一段
/// 小寫文字、或用不理會這個提示的第三方鍵盤時都不會生效，所以實際的轉換在
/// 這裡做一次。
class UpperCaseTextInputFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    return newValue.copyWith(text: newValue.text.toUpperCase());
  }
}

/// 送出失敗時的一行紅字，貼在表單白卡下面。
///
/// [semanticsId] 有值時整段包一層 [TmsSemantics]，並把 [message] 同時放到
/// accessibility label——要斷言的文字和定位用的 id 才會落在同一個節點。沒有
/// 測試要讀的那一段就不帶 id，不多掛 semantics 節點。
class LoginErrorText extends StatelessWidget {
  const LoginErrorText({super.key, required this.message, this.semanticsId});

  final String message;

  /// accessibility id（例如 `login.error-banner`）；null 表示這一段不需要被
  /// 測試定位。
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

/// 在員工按下任何按鈕之前，先用一張白卡告訴他現在的處境：帳號被鎖住了，或者
/// 他手上這組是臨時密碼。卡上是一顆圓形鎖頭加兩行字。
///
/// 做成一張卡而不是一行紅字，是因為這兩種處境員工都得先讀懂「要等多久、或者
/// 接下來要做什麼」；一行紅字會被當成打錯字那種可以立刻重試的錯誤。
class LoginNoticeCard extends StatelessWidget {
  const LoginNoticeCard({
    super.key,
    required this.semanticsId,
    required this.iconColor,
    required this.title,
    required this.body,
  });

  /// accessibility id（`login.lockout-banner`）。[body] 同時放到這個節點的
  /// accessibility label，讀值與定位才落在同一個元素上。
  final String semanticsId;

  /// 鎖頭底下那顆圓的顏色。擋住去路的用系統紅，還能往下走的用系統橘
  /// （`Main.dc.html:709`、`:719`）。
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

/// 告訴員工「這一項已經過了」的綠色圓底白勾。
///
/// 確認密碼欄右側用 20pt 的一顆，密碼規則清單每一列用 14pt 的一顆
/// （`Main.dc.html:745`、`:762`）。`CupertinoIcons` 的實心打勾圓是整塊同色，
/// 勾痕要靠底下的背景透出來；這兩處都疊在白卡上，換個底色就看不見勾，所以
/// 照原型的兩條 path 自己描。
class LoginCheckBadge extends StatelessWidget {
  const LoginCheckBadge({
    super.key,
    required this.size,
    required this.strokeWidth,
  });

  /// 整顆圓的邊長。
  final double size;

  /// 白色勾痕的線寬，以原型的 24×24 viewBox 為尺度。
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

/// 還沒符合的那幾條規則前面的灰色空心圈（`Main.dc.html:763`）。
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

/// 讓員工在打字的當下看得到自己離「可以送出」還差哪幾條規則，貼在輸入卡下方，
/// 一條一列。
///
/// 每一列前面一顆記號：符合了換成綠色打勾、字轉深綠，還沒符合維持灰色空心圈
/// （`Main.dc.html:757-766`）。整份清單一直在畫面上，不是只在出錯時才出現，
/// 員工才不必靠送出失敗的訊息去猜後端要的是什麼。
class PasswordRuleChecklist extends StatelessWidget {
  const PasswordRuleChecklist({
    super.key,
    required this.rules,
    this.semanticsId,
  });

  final List<PasswordRule> rules;

  /// 自動化測試用來讀到整份清單的 accessibility id。清單的 accessibility
  /// label 會把每一條規則的現況串成一句，例如「至少 8 碼：已符合」。
  final String? semanticsId;

  /// 記號的邊長（`Main.dc.html:762`）。
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
