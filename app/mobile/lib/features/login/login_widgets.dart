import 'package:flutter/cupertino.dart';

import '../../core/theme.dart';
import '../../core/tms_semantics.dart';
import '../../core/widgets/tms_card.dart';

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
          ),
          child: const Icon(
            CupertinoIcons.house_fill,
            size: 34,
            color: TmsColors.brandOnPrimary,
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
  const LoginFieldRow({super.key, required this.label, required this.field});

  /// 欄位名稱。
  final String label;

  /// 輸入區本身，由呼叫端給——它要掛自己的 accessibility id。
  final Widget field;

  /// 名稱欄的寬度。兩頁的欄位名稱都在四個字以內，固定寬度讓各列的輸入區左緣
  /// 對齊；跟著文字長度變寬的話，每一列的游標起點都不一樣。
  static const double _labelWidth = 76;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: TmsColors.cardBackground,
      padding: const EdgeInsets.symmetric(horizontal: TmsSpacing.screenEdge),
      constraints: const BoxConstraints(minHeight: 48),
      child: Row(
        children: [
          SizedBox(
            width: _labelWidth,
            child: Text(label, style: TmsText.body),
          ),
          Expanded(child: field),
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
  });

  final TextEditingController controller;
  final bool obscureText;

  @override
  Widget build(BuildContext context) {
    return CupertinoTextField(
      controller: controller,
      obscureText: obscureText,
      padding: const EdgeInsets.symmetric(vertical: TmsSpacing.md),
      decoration: const BoxDecoration(),
      style: TmsText.body,
    );
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

/// 帳號被鎖住時的白卡：紅色圓形鎖頭加兩行字。
///
/// 做成一張卡而不是一行紅字，是因為這時員工什麼都不能做，得先讀懂「要等多
/// 久、或者找誰」；一行紅字會被當成打錯字那種可以立刻重試的錯誤。
class LoginLockoutCard extends StatelessWidget {
  const LoginLockoutCard({
    super.key,
    required this.semanticsId,
    required this.title,
    required this.body,
  });

  /// accessibility id（`login.lockout-banner`）。[body] 同時放到這個節點的
  /// accessibility label，讀值與定位才落在同一個元素上。
  final String semanticsId;

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
            decoration: const BoxDecoration(
              color: TmsColors.systemRed,
              shape: BoxShape.circle,
            ),
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
