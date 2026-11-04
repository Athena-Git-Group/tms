import 'package:flutter/material.dart';

/// App 品牌色：青綠色，所有畫面的主色一律從這個值派生，不要在個別畫面
/// 另外寫死顏色常數。
const Color tmsBrandColor = Color(0xFF0C7680);

/// 最小可用主題：以品牌青綠為種子色，走 Cupertino 風視覺（圓角、無系統陰影
/// 質感堆疊），不追 iOS 的 Liquid Glass 效果。畫面需要的其餘視覺細節（陰影
/// 高度、圓角半徑數值等）之後再逐步補進這個主題，不要散落在個別畫面裡。
ThemeData buildTmsTheme() {
  final colorScheme = ColorScheme.fromSeed(seedColor: tmsBrandColor);
  return ThemeData(
    useMaterial3: true,
    colorScheme: colorScheme,
    scaffoldBackgroundColor: colorScheme.surface,
    appBarTheme: AppBarTheme(
      backgroundColor: tmsBrandColor,
      foregroundColor: Colors.white,
    ),
  );
}
