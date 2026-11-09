import 'dart:math' as math;

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

/// 全 App 的設計 token。值全部取自原型
/// `specs/plans/001-staff-app-increment/ui/prototype/Main.dc.html` 第 2 行的
/// `data-dc-design-system-vars` 區塊，以及原型實際渲染出來的畫面；原型註解與
/// token 跟畫面對不上的地方，一律以畫面實際呈現為準。
///
/// 這個檔案分五組：顏色 [TmsColors]、字級 [TmsText]、間距 [TmsSpacing]、
/// 圓角 [TmsRadius]、固定尺寸 [TmsSize]。畫面只准從這裡取值，不要在個別畫面
/// 寫死 `Color(0x…)` 或裸數字——同一個語意散在十個檔案各寫一次，改一次設計就
/// 得找齊十處，而且一定會漏掉一兩處。

/// 顏色 token，依**語意角色**命名，不照色相命名（不要 `teal`、`orange`）：
/// 色相會換，角色不會，按角色取值的畫面換色時一個字都不用改。
///
/// 命名跟著 container／on-container 慣例：`xxxContainer` 是淡底，
/// `onXxxContainer` 是放在那個淡底上的文字與圖示色。兩者永遠成對使用，不要把
/// 淡底配到別組的文字色——對比度是成對調過的。
abstract final class TmsColors {
  // ── 品牌色 ──────────────────────────────────────────────────────────

  /// 主色。主按鈕底、進行中狀態底、可點的文字與圖示、進度環、未讀點、勾選
  /// 框、步驟條已達色。
  static const Color brandPrimary = Color(0xFF0C7680);

  /// 主色上的文字色，同時也是所有白卡與分組列表的底色。
  static const Color brandOnPrimary = Color(0xFFFFFFFF);

  /// 主色淡底。資訊語氣的提示、教學提示、「打掃」類別、待查房與已送達狀態。
  static const Color brandContainer = Color(0xFFC8E8ED);

  /// [brandContainer] 上的文字色。
  static const Color brandOnContainerStrong = Color(0xFF2F5357);

  /// Material 側的 on-secondary-container。畫面目前沒用到，留著是因為它在原型
  /// token 裡成對存在，拿掉會讓下次比對原型的人以為這裡抄漏了。
  static const Color brandOnContainer = Color(0xFF4C696D);

  /// 強調／注意力淡底（橘）。頭像底、待認領與已延後狀態、備品類別、警告語氣。
  static const Color brandAccentContainer = Color(0xFFFFDCC5);

  /// [brandAccentContainer] 上的文字色。
  static const Color brandOnAccentContainer = Color(0xFF703700);

  /// 強調深色，用在數字與小字（臨時需求完成數、今日保養、離線待送出提示）。
  static const Color brandAccent = Color(0xFF944B00);

  /// 強調亮色。慶祝頁彩屑、工務待認領「其他」類別磚的圖示底。
  static const Color brandAccentVivid = Color(0xFFFF8E2B);

  /// 強調淺色。首頁房務與工務「進行中」卡上那顆會呼吸的小圓點，以及慶祝頁的
  /// 一部分彩屑。它只出現在品牌色深底上，比 [brandAccentVivid] 再淺一階，
  /// 深底上才讀得出來。
  static const Color brandAccentSoft = Color(0xFFFFB783);

  // ── 狀態與類別色 ────────────────────────────────────────────────────

  /// 成功淡底。查房通過、已簽收。
  static const Color successContainer = Color(0xFFDDF3E4);

  /// [successContainer] 上的文字色，也用在成功語氣的統計數字。
  static const Color onSuccessContainer = Color(0xFF1A6B34);

  /// 錯誤／急件／逾時淡底。退回重做、未接已通知主管、等待 20 分以上。
  static const Color errorContainer = Color(0xFFFFE4E1);

  /// [errorContainer] 上的文字色。
  static const Color onErrorContainer = Color(0xFFB42318);

  /// 「其他」類別淡底，也用在「已中斷」狀態。
  static const Color otherContainer = Color(0xFFFFF1C2);

  /// [otherContainer] 上的文字色。
  static const Color onOtherContainer = Color(0xFF6B4E00);

  /// 中性淡底。待開始、已取消、進度環軌道、照片占位底。
  static const Color neutralContainer = Color(0xFFE5E5EA);

  /// [neutralContainer] 上的文字色。
  static const Color onNeutralContainer = Color(0xFF3C3C43);

  // ── iOS 系統色 ──────────────────────────────────────────────────────
  //
  // 原型一律把 iOS 的半透明語意色寫成不透明值（例如次要文字用 #3C3C43 而不是
  // 帶 alpha 的 #3C3C4399），這裡照畫面實際值落，不照 token 的 alpha 版。

  /// 主要文字。標題、列主文、卡片主文。
  static const Color label = Color(0xFF000000);

  /// 次要文字。副標、說明、日期、分組標題。
  static const Color secondaryLabel = Color(0xFF3C3C43);

  /// 第三層文字。進度環分母、步驟條未達文字、交班單來源。
  static const Color tertiaryLabel = Color(0xFF6C6C70);

  /// 第四層／停用灰。通知時間、版本號、關閉 ×、空狀態圖示、未選中的 tab。
  static const Color quaternaryLabel = Color(0xFF8E8E93);

  /// 步驟條上「還沒走到」那幾步的圖示色。
  static const Color systemGray2 = Color(0xFFAEAEB2);

  /// 比 [systemGray2] 再淡一階的灰。直列時間軸上「還沒走到」那幾步的虛線圓、
  /// 待處理清單列前的空心圓（`Main.dc.html:1801`、`Main.dc.html:761`）。
  static const Color systemGray3 = Color(0xFFC7C7CC);

  /// 不透明分隔線／邊框／停用底。面板抓柄、底部動作列上緣線、停用的主按鈕底。
  static const Color opaqueSeparator = Color(0xFFC6C6C8);

  /// 列尾 chevron 的顏色。設計原型有一處寫成 #C4C4C7（`Main.dc.html:612`），
  /// 其餘六處都是這個值。兩者只差藍通道的最後一階，算同一個顏色。
  static const Color chevron = Color(0xFFC4C4C6);

  /// 群組內列與列之間那條分隔線的顏色，粗細見 [TmsSize.hairline]。
  static const Color separator = Color(0xFFE5E5EA);

  /// 畫面背景。所有捲動容器底、底部面板底。
  static const Color groupedBackground = Color(0xFFF2F2F7);

  /// 白卡底色。分組列表、主卡片、狀態列的白底版。
  static const Color cardBackground = Color(0xFFFFFFFF);

  /// 錯誤與急件的圖示色、紅點數字徽章、登出、登入錯誤字。
  static const Color systemRed = Color(0xFFFF3B30);

  /// 警告的圖示色（即時連線異常、暫停）。
  static const Color systemOrange = Color(0xFFFF9500);

  /// 首頁進行中卡的超時膠囊底（深橘），搭 [onOvertimeCapsule] 文字色。
  static const Color overtimeCapsule = Color(0xFFFF9F0A);

  /// [overtimeCapsule] 上的文字色。
  static const Color onOvertimeCapsule = Color(0xFF3A1C00);

  /// 品牌色深底上超時的計時數字色（任務頁進行中的標題區）。
  ///
  /// 深底上用黃色，是因為超時要被看見但不該被當成錯誤：紅色在青綠底上會讀成
  /// 「這張單出事了」，而超時只是提醒，不擋任何操作。
  static const Color overtimeTimerOnDark = Color(0xFFFFD60A);

  /// 白底上超時的計時數字色（任務頁暫停時的標題區）。
  static const Color overtimeTimerOnLight = Color(0xFFC93400);

  /// 成功的圖示色、照片上傳完成角標、「全部做完」空狀態圖示。
  static const Color systemGreen = Color(0xFF34C759);

  /// 通知中心「保養」類別的圖示底。
  static const Color maintenanceIcon = Color(0xFF5E5CE6);

  /// 近黑但不是純黑的文字：類別磚標籤、池內「做到哪」備註。
  static const Color labelSoft = Color(0xFF1C1C1E);

  // ── 連線狀態列 ──────────────────────────────────────────────────────

  /// 離線列底色。刻意用深色跟白底的「即時連線異常」列區隔：兩列會出現在同一
  /// 個位置，底色一樣的話員工看不出狀態已經換了一種。
  static const Color offlineBarBackground = Color(0xFF2C2C2E);

  /// 離線列上的主文色。
  static const Color offlineBarLabel = Color(0xFFFFFFFF);

  /// 離線列上的次要文字（說明與待同步筆數）。
  static const Color offlineBarSecondaryLabel = Color(0xBFEBEBF5);

  // ── 毛玻璃 ──────────────────────────────────────────────────────────
  //
  // 這幾個值都帶 alpha，疊在 [TmsGlass] 模糊過的背景上才是最後看到的顏色，
  // 單獨拿去當實心底色會太淡。

  /// 浮動分頁列的玻璃底色。Main.dc.html:1126 — rgba(255,255,255,.55)
  static const Color glassSurface = Color(0x8CFFFFFF);

  /// 玻璃底板的外框。Main.dc.html:1126 — rgba(255,255,255,.7)
  static const Color glassBorder = Color(0xB3FFFFFF);

  /// 玻璃底板上緣那條高光。Main.dc.html:1126 —
  /// inset 0 1px 0 rgba(255,255,255,.8)
  static const Color glassHighlight = Color(0xCCFFFFFF);

  /// 玻璃底板的外陰影。Main.dc.html:1126 — 0 4px 16px rgba(0,0,0,.12)
  static const Color glassShadow = Color(0x1F000000);

  /// 分頁列選中那一格的膠囊底。Main.dc.html:1128 — rgba(118,118,128,.16)
  static const Color tabBarSelectedPill = Color(0x29767680);

  /// 分頁列底下那層 soft scroll edge 的底色。
  /// Main.dc.html:1122 — rgba(242,242,247,.72)
  static const Color bottomScrollEdge = Color(0xB8F2F2F7);

  // ── 疊層 ────────────────────────────────────────────────────────────

  /// 慶祝頁遮罩。
  static const Color scrimCelebrate = Color(0x73000000);

  /// 底部面板遮罩。
  static const Color scrimSheet = Color(0x52000000);

  /// iOS Alert 遮罩。
  static const Color scrimAlert = Color(0x33000000);

  /// 按下時疊在元件上的壓色。
  static const Color pressedOverlay = Color(0x1A000000);
}

/// 字級 token。每一個都對應一個 iOS 文字樣式（Text Style）。
///
/// 畫面一律取這裡的樣式，**不要自己寫 `TextStyle(fontSize: 17)`**。理由有兩
/// 個：一是同一種角色的文字在不同畫面上才會長得一樣；二是「我的 › 文字大小」
/// 走 Dynamic Type，Flutter 會把 `fontSize` 乘上系統的 textScaler，字級散落在
/// 畫面裡的話，哪幾處跟著放大、哪幾處不會，沒有人說得準。
///
/// 相對地，**間距、圖示尺寸、觸控目標一律寫死 logical pixel**，不乘
/// textScaler——放大的是字，不是版面。
///
/// 17pt 是主幹：[headline]（600）與 [body]（400）只差字重，用來區分「這一列的
/// 主文」與「一般內容」。字重收斂成 400／500／600／700 四級，只有大型展示數字
/// 用 800。500 只用在「主角旁邊的配角」，也就是統計數字後的單位字
/// （[footnoteMedium]）與步驟條還沒走到的那幾步（[subheadlineMedium]）。
abstract final class TmsText {
  /// 中日韓字形的字型遞補順序。
  ///
  /// 拉丁字母與數字交給平台預設的系統字型（iOS 上是 SF Pro Text）；系統字型沒
  /// 有的字（主要是中文）才往下找這份清單。不寫死遞補順序的話，同一個中文字
  /// 在不同語系設定的機器上會落到不同字型，字寬與筆畫粗細跟著變，版面就排不準。
  static const List<String> fontFamilyStack = <String>[
    'PingFang TC',
    'Noto Sans TC',
  ];

  /// 等寬數字。房號與計時器要用，否則秒數每跳一次，整串數字就左右抖一次。
  static const List<FontFeature> tabularNumbers = <FontFeature>[
    FontFeature.enable('tnum'),
  ];

  /// Large Title。分頁大標（待認領、通知）。
  static const TextStyle largeTitle = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 34,
    height: 41 / 34,
    fontWeight: FontWeight.w700,
    letterSpacing: 0.4,
    color: TmsColors.label,
  );

  /// Title 1。首頁問候語、登入頁標題。
  static const TextStyle title1 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 28,
    height: 34 / 28,
    fontWeight: FontWeight.w700,
    letterSpacing: 0.36,
    color: TmsColors.label,
  );

  /// Title 2。空狀態標題。
  static const TextStyle title2 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 22,
    height: 28 / 22,
    fontWeight: FontWeight.w700,
    color: TmsColors.label,
  );

  /// Title 3。區段標題（我的任務、清潔 SOP）、身分卡姓名、統計數字。
  static const TextStyle title3 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 20,
    height: 25 / 20,
    fontWeight: FontWeight.w700,
    color: TmsColors.label,
  );

  /// Title 3 的常規字重版。iOS 動作選單（「拍照」「從相簿選擇」）的選項字。
  /// 行高不宣告，由選項列自己的 57pt 最小高度把文字撐在垂直中央。
  static const TextStyle title3Regular = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 20,
    fontWeight: FontWeight.w400,
    color: TmsColors.label,
  );

  /// Title 3 的中等字重版。iOS 動作選單的「取消」。字重 600 比 [title3] 的
  /// 700 輕一階，和 [subheadlineEmphasized]、[footnoteEmphasized] 同一級。
  static const TextStyle title3Emphasized = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 20,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  /// Headline。導覽列標題、列主文、狀態列標題、按鈕文字。
  static const TextStyle headline = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 17,
    height: 22 / 17,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  /// Body。分組列表的一般列、SOP 項目、輸入框、交辦內文。
  static const TextStyle body = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 17,
    height: 22 / 17,
    fontWeight: FontWeight.w400,
    color: TmsColors.label,
  );

  /// Callout。多行輸入框的內文。
  static const TextStyle callout = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 16,
    height: 21 / 16,
    fontWeight: FontWeight.w400,
    color: TmsColors.label,
  );

  /// Subheadline。副標、說明文、次要資訊、空狀態說明。
  ///
  /// 這一級是現場資訊的下限：日期、班別、樓層、列表副標都在這裡，不再往下掉到
  /// 13——房務邊走邊看、走廊燈光偏暗，13pt 灰字辨識度不夠。
  static const TextStyle subheadline = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 15,
    height: 20 / 15,
    fontWeight: FontWeight.w400,
    color: TmsColors.label,
  );

  /// Subheadline 的強調版。班別、文字按鈕。
  static const TextStyle subheadlineEmphasized = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 15,
    height: 20 / 15,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  /// Subheadline 的中等字重版。步驟條上「還沒走到」那幾步的標籤；目前那一步
  /// 用 700，兩者的粗細差就是「走到哪裡」的視覺線索。
  static const TextStyle subheadlineMedium = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 15,
    height: 20 / 15,
    fontWeight: FontWeight.w500,
    color: TmsColors.label,
  );

  /// Footnote。分組標題、提示文、通知時間、統計標籤。
  static const TextStyle footnote = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 13,
    height: 18 / 13,
    fontWeight: FontWeight.w400,
    color: TmsColors.secondaryLabel,
  );

  /// Footnote 的強調版。卡片上的小分類標籤（「下一間」）。
  static const TextStyle footnoteEmphasized = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 13,
    height: 18 / 13,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  /// Footnote 的中等字重版。緊跟在大號統計數字後面的單位字（「分」「張工單」）
  /// 與離線列的說明文。字重 500 讓單位字不跟數字搶，又不像 400 那樣在小字級
  /// 上糊掉。
  static const TextStyle footnoteMedium = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 13,
    height: 18 / 13,
    fontWeight: FontWeight.w500,
    color: TmsColors.label,
  );

  /// Caption 1。狀態 chip、徽章數字、照片張數。
  static const TextStyle caption1 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 12,
    height: 16 / 12,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  /// Caption 1 的常規字重版。只有一處用得上，就是「查一下」建議問題列底下那
  /// 行範圍字（`Main.dc.html:1603`）。它比同列主文小 5pt，靠位置而不是字級被
  /// 讀到。主文已經把問題講完，範圍只是補一句這題答的是哪一間房。
  ///
  /// 原型沒有宣告行高，兩行的間距由列的 `gap: 2` 決定，所以這裡也不宣告。
  static const TextStyle caption1Regular = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 12,
    fontWeight: FontWeight.w400,
    color: TmsColors.secondaryLabel,
  );

  /// Caption 2。類別磚小字、待認領卡上的類別名。
  static const TextStyle caption2 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 11,
    height: 13 / 11,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  /// 全 App 最小的字級。只有兩處用得上：分頁列的分頁名，以及照片縮圖底部那條
  /// 顯示檔案大小的字條。行高不宣告，兩處都靠所在容器的高度把文字撐在中央。
  ///
  /// 這一級不該再往別處擴散。10pt 已經低於現場邊走邊看讀得清的下限，它成立是
  /// 因為這兩處的文字旁邊都有圖示或縮圖可以辨認，字只是補充。
  static const TextStyle caption3 = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 10,
    fontWeight: FontWeight.w600,
    color: TmsColors.label,
  );

  // ── 展示數字 ────────────────────────────────────────────────────────
  //
  // iOS 沒有對應的文字樣式，這四級是 App 自己的展示層級：房號與計時是員工隔著
  // 一條走廊也要認得出來的主角，所以字重用到 800，並且一律等寬數字。

  /// 任務頁當主角的房號或計時。
  static const TextStyle displayHero = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 56,
    height: 1,
    fontWeight: FontWeight.w800,
    fontFeatures: tabularNumbers,
    color: TmsColors.label,
  );

  /// 首頁進行中卡與下一間卡的房號。
  static const TextStyle displayRoomNo = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 44,
    height: 1,
    fontWeight: FontWeight.w800,
    fontFeatures: tabularNumbers,
    color: TmsColors.label,
  );

  /// 進度環中央的數字。
  static const TextStyle displayMetric = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 30,
    height: 1,
    fontWeight: FontWeight.w700,
    fontFeatures: tabularNumbers,
    color: TmsColors.label,
  );

  /// 房號當主角時退居副位的計時。
  static const TextStyle displayTimer = TextStyle(
    fontFamilyFallback: fontFamilyStack,
    fontSize: 26,
    height: 1,
    fontWeight: FontWeight.w700,
    fontFeatures: tabularNumbers,
    color: TmsColors.label,
  );
}

/// 間距 token，單位是 logical pixel，一律寫死不跟著字級放大。
///
/// [screenEdge] 是全域規則：所有捲動容器的左右邊距都是 16，列表群組內的列再縮
/// 16，所以列文字離畫面邊緣共 32。
abstract final class TmsSpacing {
  /// 同一欄位內上下兩行文字之間。
  static const double xxs = 2;

  /// 緊貼的元素：步驟條標籤、chip 內的上下留白。
  static const double xs = 4;

  /// 分組標題與群組之間、chip 與 chip 之間。
  static const double xs2 = 6;

  /// 列表項之間、按鈕群內的上下間距、照片之間。
  static const double sm = 8;

  /// 分類磚的網格間距。
  static const double sm2 = 10;

  /// 卡片內主要區塊之間、頭像與文字之間、雙欄磚之間。
  static const double md = 12;

  /// 任務頁捲動內容的區塊之間、卡片內段落之間。
  static const double md2 = 14;

  /// 首頁區塊之間、卡片內 padding。
  static const double lg = 16;

  /// 進度環卡的 padding。
  static const double lg2 = 18;

  /// 「我的」各區塊之間、任務頁標題區的左右 padding。
  static const double xl = 20;

  /// 慶祝頁遮罩 padding、登入頁區塊間距。
  static const double xl2 = 24;

  /// 空狀態卡的上下 padding、慶祝頁上 padding。
  static const double xxl = 28;

  /// 畫面左右邊距。值跟 [lg] 一樣，獨立命名是因為它是一條規則、不是巧合：
  /// 改版面時這個值要一起動，其他 16 不一定。
  static const double screenEdge = 16;

  /// 分組清單一般列的上下內距。配上 17pt 的列文字剛好湊到 44 的最小觸控高度。
  static const double groupedRowVertical = 11;

  /// 浮動分頁列離畫面左右兩邊的距離。
  static const double floatingTabBarSide = 20;

  /// 浮動分頁列下緣離畫面底部的距離，依裝置底部安全區算出。
  ///
  /// 安全區扣掉 13，是因為 home indicator 本身已經佔掉一條空白，分頁列貼著它
  /// 下緣看起來才和兩側的 20 等距。最少留 10，否則沒有 home indicator 的機型
  /// 會把分頁列壓在畫面最底邊。
  static double floatingTabBarBottom(double bottomSafeAreaInset) {
    return math.max(bottomSafeAreaInset - 13, 10);
  }

  /// 捲動內容底部要留的空白，依裝置底部安全區算出。
  ///
  /// 浮動分頁列是浮在內容上方的，不像不透明分頁列會把內容往上推。捲到底時最後
  /// 一列會剛好被它蓋住，所以捲動容器要自己讓開分頁列的整個高度與下緣距離，
  /// 再加 20 當呼吸空間。
  ///
  /// 不需要讓開分頁列的畫面（表單頁、明細頁）不走這個值，它們自己留 24。
  static double scrollPadBottom(double bottomSafeAreaInset) {
    return TmsSize.floatingTabBarHeight +
        floatingTabBarBottom(bottomSafeAreaInset) +
        20;
  }

  /// 分頁列底下那層 soft scroll edge 的高度，依裝置底部安全區算出。
  ///
  /// 它蓋住的正好是浮動分頁列佔到的那一段，所以是分頁列的高度加上它的下緣
  /// 距離。捲到底的最後一列從這一層底下透出來，員工看得出清單還沒走完。
  static double bottomScrollEdgeHeight(double bottomSafeAreaInset) {
    return TmsSize.floatingTabBarHeight +
        floatingTabBarBottom(bottomSafeAreaInset);
  }
}

/// 圓角 token。
///
/// 卡片分兩級，不要全部套同一個值：主視覺卡（首頁、待認領的單卡）用
/// [cardHero]，分組列表／狀態列／工作磚用 [card]，底部面板內的白卡用
/// [cardInSheet]。
abstract final class TmsRadius {
  /// 照片縮圖、加照片的虛線框。
  static const double sm = 10;

  /// 交辦引言、池內「做到哪」備註列、查一下的出處卡。
  static const double md = 12;

  /// 底部面板內的白卡、類別磚的圖示方塊、重複報修卡。
  static const double cardInSheet = 14;

  /// 分組列表群組、狀態列、工作磚、身分卡。
  static const double card = 16;

  /// 分類磚本體。
  static const double tile = 18;

  /// 首頁與待認領的主卡片。
  static const double cardHero = 20;

  /// 任務頁標題區的下緣、慶祝頁的勾勾方塊。
  static const double xl = 28;

  /// Alert 對話框。`Main.dc.html:4860` 的 `al.r`。
  static const double alert = 34;

  /// 蓋住整個畫面的對話卡，目前只有慶祝卡。`Main.dc.html:4463` 的 `ui.dialogR`。
  /// 它比 [alert] 再大一階，因為這張卡佔的面積也大一截。
  static const double dialog = 36;

  /// 底部面板上緣（`Main.dc.html:4932`）。
  static const double sheet = 38;

  /// 膠囊：所有主按鈕、chip、標籤、徽章、頭像、未讀點、進度條。
  static const double pill = 999;
}

/// 固定尺寸 token，單位是 logical pixel。
abstract final class TmsSize {
  /// iOS 的最小觸控目標。任何可點的東西不得低於這個高度。
  static const double minTouchTarget = 44;

  /// 導覽列高度。
  static const double navBarHeight = 44;

  /// 分隔線粗細。
  static const double hairline = 0.5;

  /// 狀態列、分組列、一般列的最小高度。
  static const double rowMinHeight = 44;

  /// 狀態列與提示區塊內的圖示尺寸。
  static const double statusIcon = 24;

  /// 主按鈕高度（任務頁、登入頁）。
  static const double primaryButtonHeight = 54;

  /// 列尾 chevron 的寬與高（`Main.dc.html:421`）。原型是 8 × 13 的描邊箭頭，
  /// 高比寬多一半；系統圖示的字框是正方形，拿它畫出來的箭頭會矮而胖。
  static const double chevronWidth = 8;
  static const double chevronHeight = 13;

  /// 列尾 chevron 的筆畫粗細（`Main.dc.html:421`）。
  static const double chevronStroke = 2;

  /// 選項列尾端那個勾的邊長與筆畫粗細（`Main.dc.html:1463`）。原型的勾在
  /// 24 格的畫布上畫 stroke-width 3，縮到 16 之後筆畫實際是 2。
  static const double optionCheck = 16;
  static const double optionCheckStroke = 2;

  /// 「我的」身分卡上的大頭像。首頁問候列那顆小的是 [minTouchTarget]。
  static const double avatarLarge = 60;

  /// 通知中心每一列的圓形分類圖示。
  static const double notificationKindIcon = 36;

  /// 狀態列左側那顆填色圓形圖示（`Main.dc.html:1227`）。
  static const double calloutIcon = 26;

  /// 固定教學左側那個燈泡（`Main.dc.html:1253`）。
  static const double calloutTeachingIcon = 28;

  /// 固定教學右上角那個關閉符號（`Main.dc.html:1259`）。點擊範圍是
  /// [minTouchTarget]，符號本身只有這麼大。
  static const double calloutDismissGlyph = 13;

  /// 交辦引言標註左側那條色條的寬度，也是它右側圓角的半徑
  /// （`Main.dc.html:1270`）。
  static const double quoteBar = 4;

  /// 交辦引言標註標題列的訊息框圖示（`Main.dc.html:1272`）。
  static const double quoteIcon = 18;

  /// 狀態摘要卡左側那顆圓形圖示（`Main.dc.html:1771`）。
  static const double statusSummaryIcon = 36;

  /// 未讀點的直徑。
  static const double unreadDot = 8;

  /// 空狀態的描邊圖示邊長。
  static const double emptyStateIcon = 56;

  /// 浮動分頁列的高度。
  static const double floatingTabBarHeight = 62;

  /// 浮動分頁列的圓角，剛好是高度的一半，所以兩端是半圓。
  static const double floatingTabBarRadius = 31;

  /// 浮動分頁列玻璃底板與四格之間的內距。Main.dc.html:1126 — padding: 4
  static const double floatingTabBarPadding = 4;

  /// 浮動分頁列相鄰兩格之間。Main.dc.html:1126 — gap: 2
  static const double floatingTabBarGap = 2;

  /// 選中那一格的膠囊底圓角。Main.dc.html:1128 — border-radius: 27
  static const double floatingTabBarPillRadius = 27;

  /// 浮動分頁列的圖示邊長。貼底不透明那版是 26，浮動這版小一號。
  /// Main.dc.html:1129
  static const double floatingTabBarIcon = 24;
}

/// Cupertino 側的主題。App 殼是 `CupertinoApp`，可點文字、游標與選取色都吃這裡
/// 的 `primaryColor`，所以品牌色得從這裡進去，不能只靠畫面各自寫。
CupertinoThemeData buildTmsCupertinoTheme() {
  // 這裡刻意不設 scaffoldBackgroundColor：原型的畫面底是
  // TmsColors.groupedBackground，但現有畫面都還是白底直排的版面，一次把底色
  // 換掉會讓每一支畫面同時變樣卻沒人看過。底色跟著各畫面改版時一支一支換。
  return const CupertinoThemeData(
    brightness: Brightness.light,
    primaryColor: TmsColors.brandPrimary,
  );
}

/// Material 側的主題。畫面本身走 Cupertino，但少數共用元件仍會讀
/// `Theme.of(context)`，所以在 App 外層墊一層。
///
/// 這裡**不用** `ColorScheme.fromSeed`：從品牌色推出來的 container 不是原型的
/// `#C8E8ED`，一旦有畫面讀到推算值，整套配色就會跟原型分岔。色票全部照
/// [TmsColors] 明寫。
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

/// 舊名保留：`main.dart` 與既有畫面還用這個常數指品牌主色。新程式碼請直接用
/// [TmsColors.brandPrimary]。
const Color tmsBrandColor = TmsColors.brandPrimary;
