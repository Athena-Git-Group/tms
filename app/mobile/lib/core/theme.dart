import 'dart:math' as math;

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

abstract final class TmsColors {

  static const Color brandPrimary = Color(0xFF0C7680);

  static const Color brandOnPrimary = Color(0xFFFFFFFF);

  static const Color brandContainer = Color(0xFFC8E8ED);

  static const Color brandOnContainerStrong = Color(0xFF2F5357);

  static const Color brandAccentContainer = Color(0xFFFFDCC5);

  static const Color brandOnAccentContainer = Color(0xFF703700);

  static const Color brandAccent = Color(0xFF944B00);

  static const Color brandAccentVivid = Color(0xFFFF8E2B);

  static const Color brandAccentSoft = Color(0xFFFFB783);


  static const Color successContainer = Color(0xFFDDF3E4);

  static const Color onSuccessContainer = Color(0xFF1A6B34);

  static const Color errorContainer = Color(0xFFFFE4E1);

  static const Color onErrorContainer = Color(0xFFB42318);

  static const Color otherContainer = Color(0xFFFFF1C2);

  static const Color onOtherContainer = Color(0xFF6B4E00);

  static const Color neutralContainer = Color(0xFFE5E5EA);

  static const Color onNeutralContainer = Color(0xFF3C3C43);

  static const Color label = Color(0xFF000000);

  static const Color secondaryLabel = Color(0xFF3C3C43);

  static const Color tertiaryLabel = Color(0xFF6C6C70);

  static const Color quaternaryLabel = Color(0xFF8E8E93);

  static const Color systemGray2 = Color(0xFFAEAEB2);

  static const Color systemGray3 = Color(0xFFC7C7CC);

  static const Color opaqueSeparator = Color(0xFFC6C6C8);

  static const Color chevron = Color(0xFFC4C4C6);

  static const Color separator = Color(0xFFE5E5EA);

  static const Color headerMetaOnDark = Color(0xD9FFFFFF);

  static const Color headerMetaOnLight = Color(0xD9000000);

  static const Color groupedBackground = Color(0xFFF2F2F7);

  static const Color cardBackground = Color(0xFFFFFFFF);

  static const Color systemRed = Color(0xFFFF3B30);

  static const Color systemOrange = Color(0xFFFF9500);

  static const Color overtimeCapsule = Color(0xFFFF9F0A);

  static const Color onOvertimeCapsule = Color(0xFF3A1C00);

  static const Color overtimeTimerOnDark = Color(0xFFFFD60A);

  static const Color overtimeTimerOnLight = Color(0xFFC93400);

  static const Color systemGreen = Color(0xFF34C759);

  static const Color maintenanceIcon = Color(0xFF5E5CE6);

  static const Color labelSoft = Color(0xFF1C1C1E);


  static const Color offlineBarBackground = Color(0xFF2C2C2E);

  static const Color offlineBarLabel = Color(0xFFFFFFFF);

  static const Color offlineBarSecondaryLabel = Color(0xBFEBEBF5);

  static const Color glassSurface = Color(0x8CFFFFFF);

  static const Color glassBorder = Color(0xB3FFFFFF);

  static const Color glassHighlight = Color(0xCCFFFFFF);

  static const Color glassShadow = Color(0x1F000000);

  static const Color fabSurface = Color(0x9EFFFFFF);

  static const Color fabBorder = Color(0xCCFFFFFF);

  static const Color fabHighlight = Color(0xE6FFFFFF);

  static const Color fabShadow = Color(0x29000000);

  static const Color tipPopoverShadow = Color(0x2E000000);

  static const Color tabBarSelectedPill = Color(0x29767680);

  static const Color segmentedTrack = Color(0x1F767680);

  static const Color segmentedThumbShadow = Color(0x1F000000);

  static const Color bottomScrollEdge = Color(0xB8F2F2F7);

  static const Color frozenNavGlass = Color(0xB8F2F2F7);

  static const Color navButtonSurface = Color(0xB3FFFFFF);

  static const Color navButtonSurfaceOnDark = Color(0x33FFFFFF);

  static const Color navButtonBorder = Color(0xE6FFFFFF);

  static const Color navButtonBorderOnDark = Color(0x59FFFFFF);

  static const Color plainNavButtonBorder = Color(0xCCFFFFFF);

  static const Color plainNavButtonShadow = Color(0x14000000);


  static const Color scrimCelebrate = Color(0x73000000);

  static const Color scrimSheet = Color(0x52000000);

  static const Color scrimAlert = Color(0x33000000);

  static const Color pressedOverlay = Color(0x1A000000);
}

abstract final class TmsText {
  static const List<String> fontFamilyStack = <String>[
    'PingFang TC',
    'Noto Sans TC',
  ];

  static const List<FontFeature> tabularNumbers = <FontFeature>[
    FontFeature.enable('tnum'),
  ];

  static const TextStyle largeTitle = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 34,
    height: 41 / 34,
    fontWeight: FontWeight.w700,
    letterSpacing: 0.4,
    color: TmsColors.label,
  );

  static const TextStyle title1 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 28,
    height: 34 / 28,
    fontWeight: FontWeight.w700,
    letterSpacing: 0.36,
    color: TmsColors.label,
  );

  static const TextStyle title2 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 22,
    height: 28 / 22,
    fontWeight: FontWeight.w700,
    color: TmsColors.label,
  );

  static const TextStyle title3 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 20,
    height: 25 / 20,
    fontWeight: FontWeight.w700,
    color: TmsColors.label,
  );

  static const TextStyle title3Regular = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 20,
    fontWeight: FontWeight.w400,
    color: TmsColors.label,
  );

  static const TextStyle title3Emphasized = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 20,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  static const TextStyle headline = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 17,
    height: 22 / 17,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  static const TextStyle body = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 17,
    height: 22 / 17,
    fontWeight: FontWeight.w400,
    color: TmsColors.label,
  );

  static const TextStyle callout = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 16,
    height: 21 / 16,
    fontWeight: FontWeight.w400,
    color: TmsColors.label,
  );

  static const TextStyle subheadline = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 15,
    height: 20 / 15,
    fontWeight: FontWeight.w400,
    color: TmsColors.label,
  );

  static const TextStyle subheadlineEmphasized = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 15,
    height: 20 / 15,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  static const TextStyle subheadlineBold = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 15,
    height: 20 / 15,
    fontWeight: FontWeight.w700,
    color: TmsColors.label,
  );

  static const TextStyle subheadlineMedium = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 15,
    height: 20 / 15,
    fontWeight: FontWeight.w500,
    color: TmsColors.label,
  );

  static const TextStyle footnote = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 13,
    height: 18 / 13,
    fontWeight: FontWeight.w400,
    color: TmsColors.secondaryLabel,
  );

  static const TextStyle footnoteEmphasized = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 13,
    height: 18 / 13,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  static const TextStyle footnoteMedium = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 13,
    height: 18 / 13,
    fontWeight: FontWeight.w500,
    color: TmsColors.label,
  );

  static const TextStyle caption1 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 12,
    height: 16 / 12,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  static const TextStyle caption1Regular = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 12,
    fontWeight: FontWeight.w400,
    color: TmsColors.secondaryLabel,
  );

  static const TextStyle caption2 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 11,
    height: 13 / 11,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  static const TextStyle caption3 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 10,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  static const TextStyle displayHero = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 56,
    height: 1,
    fontWeight: FontWeight.w800,
    fontFeatures: tabularNumbers,
    color: TmsColors.label,
  );

  static const TextStyle displayRoomNo = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 44,
    height: 1,
    fontWeight: FontWeight.w800,
    fontFeatures: tabularNumbers,
    color: TmsColors.label,
  );

  static const TextStyle displayAreaName = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 30,
    height: 1,
    fontWeight: FontWeight.w800,
    color: TmsColors.label,
  );

  static const TextStyle displayMetric = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 30,
    height: 1,
    fontWeight: FontWeight.w700,
    fontFeatures: tabularNumbers,
    color: TmsColors.label,
  );

  static const TextStyle displayStat = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 17,
    fontWeight: FontWeight.w700,
    fontFeatures: tabularNumbers,
    color: TmsColors.label,
  );

  static const TextStyle displayTimer = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 26,
    height: 1,
    fontWeight: FontWeight.w700,
    fontFeatures: tabularNumbers,
    color: TmsColors.label,
  );

  static const TextStyle displayTimerCompact = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 22,
    fontWeight: FontWeight.w700,
    fontFeatures: tabularNumbers,
    color: TmsColors.label,
  );
}

abstract final class TmsSpacing {
  static const double xxxs = 1;

  static const double xxs = 2;

  static const double xs = 4;

  static const double xs2 = 6;

  static const double sm = 8;

  static const double sm2 = 10;

  static const double md = 12;

  static const double md2 = 14;

  static const double lg = 16;

  static const double lg2 = 18;

  static const double xl = 20;

  static const double xl2 = 24;

  static const double xxl = 28;

  static const double screenEdge = 16;

  static const double groupedRowVertical = 11;

  static const double floatingTabBarSide = 20;

  static double floatingTabBarBottom(double bottomSafeAreaInset) {
    return math.max(bottomSafeAreaInset - 13, 10);
  }

  static double scrollPadBottom(double bottomSafeAreaInset) {
    return TmsSize.floatingTabBarHeight +
        floatingTabBarBottom(bottomSafeAreaInset) +
        20;
  }

  static double bottomScrollEdgeHeight(double bottomSafeAreaInset) {
    return TmsSize.floatingTabBarHeight +
        floatingTabBarBottom(bottomSafeAreaInset);
  }
}

abstract final class TmsRadius {
  static const double sm = 10;

  static const double md = 12;

  static const double cardInSheet = 14;

  static const double card = 16;

  static const double tile = 18;

  static const double cardHero = 20;

  static const double xl = 28;

  static const double alert = 34;

  static const double dialog = 36;

  static const double sheet = 38;

  static const double pill = 999;
}

abstract final class TmsSize {
  static const double minTouchTarget = 44;

  static const double navBarHeight = 44;

  static const double frozenNavBarHeight = 52;

  static const double frozenNavTitleInset = 96;

  static const double navBarChevron = 24;

  static const double navBarChevronStroke = 2.2;

  static const double navBarItemIcon = 22;

  static const double hairline = 0.5;

  static const double rowMinHeight = 44;

  static const double statusIcon = 24;

  static const double primaryButtonHeight = 54;

  static const double chevronWidth = 8;
  static const double chevronHeight = 13;

  static const double chevronStroke = 2;

  static const double optionCheck = 16;
  static const double optionCheckStroke = 2;

  static const double avatarLarge = 60;

  static const double notificationKindIcon = 36;

  static const double calloutIcon = 26;

  static const double calloutTeachingIcon = 28;

  static const double calloutDismissGlyph = 13;

  static const double quoteBar = 4;

  static const double quoteIcon = 18;

  static const double statusSummaryIcon = 36;

  static const double unreadDot = 8;

  static const double infoRowUnreadDot = 10;

  static const double sopCheckBox = 26;

  static const double sopCheckBoxBorder = 2;

  static const double sopCheckGlyph = 16;

  static const double sopCheckGlyphStroke = 3;

  static const double emptyStateIcon = 56;

  static const double floatingTabBarHeight = 62;

  static const double floatingTabBarRadius = 31;

  static const double floatingTabBarPadding = 4;

  static const double floatingTabBarGap = 2;

  static const double floatingTabBarPillRadius = 27;

  static const double floatingTabBarIcon = 24;
}

CupertinoThemeData buildTmsCupertinoTheme() {
  return const CupertinoThemeData(
    brightness: Brightness.light,
    primaryColor: TmsColors.brandPrimary,
  );
}

ThemeData buildTmsTheme() {
  const colorScheme = ColorScheme.light(
    primary: TmsColors.brandPrimary,
    onPrimary: TmsColors.brandOnPrimary,
    primaryContainer: TmsColors.brandContainer,
    onPrimaryContainer: TmsColors.brandOnContainerStrong,
    secondary: TmsColors.brandAccent,
    onSecondary: TmsColors.brandOnPrimary,
    secondaryContainer: TmsColors.brandAccentContainer,
    onSecondaryContainer: TmsColors.brandOnAccentContainer,
    error: TmsColors.onErrorContainer,
    onError: TmsColors.brandOnPrimary,
    errorContainer: TmsColors.errorContainer,
    onErrorContainer: TmsColors.onErrorContainer,
    surface: TmsColors.groupedBackground,
    onSurface: TmsColors.label,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: colorScheme,
    scaffoldBackgroundColor: TmsColors.groupedBackground,
  );
}
