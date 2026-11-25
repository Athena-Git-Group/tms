package com.athena.web.bdd.pages;

import com.athena.web.bdd.pages.UiWords.Language;
import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CreateRequestPanel extends PageObject {

    public static final List<String> ITEMS = List.of(
            "毛巾", "浴巾", "礦泉水", "拖鞋", "牙刷組", "嬰兒床", "加床", "冰塊", "冷氣", "熱水", "燈具", "電視");

    public static final List<String> ENGINEERING_ITEMS = List.of("冷氣", "熱水", "燈具", "電視");

    private static final double WAIT_MS = 10_000;

    static final Map<String, List<String>> WORDS = Map.ofEntries(
            Map.entry("代開單", List.of("New Request", "代理作成", "Tạo hộ")),
            Map.entry("取消", List.of("Cancel", "キャンセル", "Hủy")),
            Map.entry("關閉", List.of("Close", "閉じる", "Đóng")),
            Map.entry("房號", List.of("Room", "部屋番号", "Số phòng")),
            Map.entry("輸入房號，例如 305", List.of("Enter room, e.g. 305", "部屋番号を入力（例：305）",
                    "Nhập số phòng, ví dụ 305")),
            Map.entry("常用品項（點一下加一份，會依品項自動分到房務或工務）", List.of(
                    "Common items (tap to add one; each goes to Housekeeping or Engineering automatically)",
                    "よく使う品目（タップで1つ追加。品目ごとに客室係か設備係へ自動で振り分け）",
                    "Vật phẩm thường dùng (chạm để thêm 1, tự chia cho buồng phòng hoặc kỹ thuật)")),
            Map.entry("其他需求（選填）", List.of("Other request (optional)", "その他の依頼（任意）",
                    "Yêu cầu khác (không bắt buộc)")),
            Map.entry("其他需求", List.of("Other request", "その他の依頼", "Yêu cầu khác")),
            Map.entry("例如：住客明早 6 點要叫車、浴室門卡住", List.of(
                    "e.g. Taxi at 6 a.m. tomorrow; bathroom door sticks", "例：明朝6時にタクシー手配、浴室のドアが固い",
                    "Ví dụ: khách cần gọi xe lúc 6 giờ sáng mai, cửa phòng tắm bị kẹt")),
            Map.entry("移除", List.of("Remove", "削除", "Bỏ")),
            Map.entry("這段要給", List.of("Send to", "依頼先", "Giao cho")),
            Map.entry("送出", List.of("Send", "送信", "Gửi")),
            Map.entry("房務", List.of("Housekeeping", "客室清掃", "Buồng phòng")),
            Map.entry("工務", List.of("Engineering", "施設管理", "Kỹ thuật")),
            Map.entry("毛巾", List.of("Towels", "タオル", "Khăn tắm")),
            Map.entry("浴巾", List.of("Bath towels", "バスタオル", "Khăn tắm lớn")),
            Map.entry("礦泉水", List.of("Bottled water", "ミネラルウォーター", "Nước suối")),
            Map.entry("拖鞋", List.of("Slippers", "スリッパ", "Dép")),
            Map.entry("牙刷組", List.of("Toothbrush kit", "歯ブラシセット", "Bộ bàn chải")),
            Map.entry("嬰兒床", List.of("Crib", "ベビーベッド", "Nôi em bé")),
            Map.entry("加床", List.of("Extra bed", "エキストラベッド", "Giường phụ")),
            Map.entry("冰塊", List.of("Ice", "氷", "Đá")),
            Map.entry("冷氣", List.of("A/C", "エアコン", "Điều hòa")),
            Map.entry("熱水", List.of("Hot water", "お湯", "Nước nóng")),
            Map.entry("燈具", List.of("Lights", "照明", "Đèn")),
            Map.entry("電視", List.of("TV", "テレビ", "TV")));

    public CreateRequestPanel(WebWorld world) {
        super(world);
    }

    public static String word(Language language, String traditionalChinese) {
        List<String> others = WORDS.get(traditionalChinese);
        if (others == null) {
            throw new IllegalArgumentException("代開單面板的文字表沒有「" + traditionalChinese + "」");
        }
        return switch (language) {
            case TRADITIONAL_CHINESE -> traditionalChinese;
            case ENGLISH -> others.get(0);
            case JAPANESE -> others.get(1);
            case VIETNAMESE -> others.get(2);
        };
    }

    static Pattern anyLanguage(String traditionalChinese) {
        String alternatives = Arrays.stream(Language.values())
                .map(language -> BrowserRegex.literal(word(language, traditionalChinese)))
                .distinct()
                .collect(Collectors.joining("|"));
        return Pattern.compile("^(?:" + alternatives + ")$");
    }

    public static String submitLabel(Language language, int count) {
        if (count == 0) {
            return word(language, "送出");
        }
        return switch (language) {
            case TRADITIONAL_CHINESE -> "送出 " + count + " 張單";
            case ENGLISH -> "Send " + count + " requests";
            case JAPANESE -> count + " 件送信";
            case VIETNAMESE -> "Gửi " + count + " phiếu";
        };
    }

    public static String summaryText(Language language, int count, String housekeeping, String engineering) {
        return switch (language) {
            case TRADITIONAL_CHINESE -> "會開 " + count + " 張單："
                    + joinSegments("；", housekeeping == null ? null : housekeeping + " 給房務",
                            engineering == null ? null : engineering + " 給工務")
                    + "。直接進部門待認領，不經派單。";
            case ENGLISH -> "Creates " + count + " requests: "
                    + joinSegments("; ", housekeeping == null ? null : housekeeping + " to Housekeeping",
                            engineering == null ? null : engineering + " to Engineering")
                    + ". They go straight to each department's pickup queue, no dispatch needed.";
            case JAPANESE -> count + " 件作成："
                    + joinSegments("／", housekeeping == null ? null : housekeeping + " を客室係へ",
                            engineering == null ? null : engineering + " を設備係へ")
                    + "。部署の受付待ちへ直接入り、振り分けは不要です。";
            case VIETNAMESE -> "Sẽ tạo " + count + " phiếu: "
                    + joinSegments("; ", housekeeping == null ? null : housekeeping + " giao Buồng phòng",
                            engineering == null ? null : engineering + " giao Kỹ thuật")
                    + ". Vào thẳng hàng chờ nhận của bộ phận, không qua phân việc.";
        };
    }

    public static String listSeparator(Language language) {
        return language == Language.ENGLISH || language == Language.VIETNAMESE ? ", " : "、";
    }

    private static String joinSegments(String separator, String first, String second) {
        List<String> segments = new ArrayList<>();
        if (first != null) {
            segments.add(first);
        }
        if (second != null) {
            segments.add(second);
        }
        return String.join(separator, segments);
    }

    public Locator toolbarButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(anyLanguage("代開單")));
    }

    public Locator panel() {
        return page().getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName(anyLanguage("代開單")));
    }

    public Locator backdrop() {
        return panel().locator("xpath=..").getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(anyLanguage("關閉")));
    }

    public Locator cancelButton() {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(anyLanguage("取消")));
    }

    public Locator title() {
        return panel().getByRole(AriaRole.HEADING).first();
    }

    public Locator roomInput() {
        return panel().getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions().setName(anyLanguage("房號")));
    }

    public Locator suggestion(String roomNo) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(roomNo.strip()) + "(?!\\d)")));
    }

    public Locator suggestions() {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^\\d{1,4}(?!\\d)")));
    }

    public Locator busy() {
        return panel().locator("[aria-busy='true']");
    }

    public Locator guestCardStayLine() {
        return panel().getByText(Pattern.compile("^\\d{1,4} · \\d{1,2}/\\d{1,2}–\\d{1,2}/\\d{1,2}$"));
    }

    public Locator vacantNotice() {
        return panel().getByText(Pattern.compile("目前是空房，沒有住客。"));
    }

    public Locator sectionTitle(String traditionalChinese) {
        return panel().getByText(anyLanguage(traditionalChinese));
    }

    public Locator itemTile(String item) {
        return panel().getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions()
                .setHas(page().getByText(anyLanguage(item))));
    }

    public Locator itemCard(String item) {
        return itemTile(item).locator("xpath=..");
    }

    public Locator decreaseButton(String item) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName("減一份" + item).setExact(true));
    }

    public Locator increaseButton(String item) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName("加一份" + item).setExact(true)).last();
    }

    public Locator removeButton(String item) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName("移除" + item).setExact(true));
    }

    public Locator buttonNamed(String name) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator otherRequestBox() {
        return panel().getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions().setName(anyLanguage("其他需求")));
    }

    public Locator otherRequestDeptGroup() {
        return panel().getByRole(AriaRole.GROUP).filter(new Locator.FilterOptions()
                .setHas(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(anyLanguage("工務")))));
    }

    public Locator otherRequestDept(String dept) {
        return otherRequestDeptGroup().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(anyLanguage(dept.strip())));
    }

    public Locator sendToLabel() {
        return panel().getByText(anyLanguage("這段要給"));
    }

    public Locator similarTasksNotice() {
        return panel().getByRole(AriaRole.STATUS);
    }

    public Locator summary() {
        return panel().getByText(Pattern.compile("^(會開 \\d+ 張單：|Creates \\d+ requests?: |\\d+ 件作成：|Sẽ tạo \\d+ phiếu: )"));
    }

    public Locator submitButton() {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile(
                "^(送出( \\d+ 張單)?|Send( \\d+ requests?)?|送信|\\d+ 件送信|Gửi( \\d+ phiếu)?)$")));
    }

    public Locator createdHighlights() {
        return page().locator("[data-highlight='created']");
    }

    public boolean isOpen() {
        return world.hasPage() && panel().count() > 0 && panel().first().isVisible();
    }

    public void waitUntilReady() {
        panel().waitFor(new Locator.WaitForOptions().setTimeout(WAIT_MS));
        page().waitForCondition(() -> busy().count() == 0, new Page.WaitForConditionOptions().setTimeout(WAIT_MS));
    }

    public void waitUntilClosed() {
        panel().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(WAIT_MS));
    }

    public String roomValue() {
        return roomInput().inputValue();
    }

    public int quantityOf(String item) {
        Locator tile = itemTile(item);
        String name = String.valueOf(tile.evaluate(
                "el => el.getAttribute('aria-label') ?? ''"));
        Matcher current = Pattern.compile("，目前 (\\d+)$").matcher(name);
        if (current.find()) {
            return Integer.parseInt(current.group(1));
        }
        String badge = badgeOf(item);
        return badge == null ? 0 : Integer.parseInt(badge);
    }

    public String badgeOf(String item) {
        for (String line : ShellFrame.linesOf(itemTile(item).innerText())) {
            if (line.matches("\\d+")) {
                return line;
            }
        }
        return null;
    }

    public boolean looksSelected(String item) {
        Object selected = itemTile(item).evaluate("""
                tile => {
                  const brand = 'rgb(12, 118, 128)';
                  for (const el of [tile, tile.parentElement]) {
                    if (!el) continue;
                    if (el.getAttribute('aria-pressed') === 'true' || el.getAttribute('aria-selected') === 'true') return true;
                    const style = getComputedStyle(el);
                    if (style.boxShadow.includes(brand) || style.outlineColor === brand && style.outlineStyle !== 'none'
                        || (style.borderTopColor === brand && parseFloat(style.borderTopWidth) >= 2)) return true;
                  }
                  return false;
                }
                """);
        return Boolean.TRUE.equals(selected);
    }

    public List<String> underRowOf(String item) {
        Object lines = itemTile(item).evaluate("""
                tile => {
                  const card = tile.parentElement;
                  if (!card) return [];
                  const out = [];
                  for (const child of card.children) {
                    if (child === tile || child.contains(tile)) continue;
                    if (!child.offsetParent && getComputedStyle(child).position !== 'fixed') continue;
                    for (const line of child.innerText.split('\\n')) {
                      const text = line.replace(/\\s+/g, ' ').trim();
                      if (text) out.push(text);
                    }
                  }
                  return out;
                }
                """);
        List<String> result = new ArrayList<>();
        for (Object line : (List<?>) lines) {
            result.add(String.valueOf(line));
        }
        return result;
    }

    public List<List<String>> tilesInOrder(Language language) {
        List<List<String>> tiles = new ArrayList<>();
        for (String item : ITEMS) {
            Locator tile = itemTile(item);
            if (tile.count() != 1) {
                tiles.add(List.of("（找不到「" + item + "」這一格，或不只一格）"));
                continue;
            }
            List<String> lines = ShellFrame.linesOf(tile.innerText()).stream()
                    .filter(line -> !line.matches("\\d+"))
                    .toList();
            tiles.add(lines);
        }
        return tiles;
    }

    public List<double[]> tilePositions() {
        List<double[]> positions = new ArrayList<>();
        for (String item : ITEMS) {
            var box = itemTile(item).boundingBox();
            positions.add(box == null ? null : new double[] {box.x, box.y});
        }
        return positions;
    }

    public List<List<String>> readSuggestions() {
        List<List<String>> rows = new ArrayList<>();
        Pattern roomAndGuest = Pattern.compile("^(\\d{1,4})\\s*(.*)$");
        for (String text : suggestions().allInnerTexts()) {
            String collapsed = ShellFrame.normalizeSpaces(text.replace('\n', ' '));
            Matcher matcher = roomAndGuest.matcher(collapsed);
            rows.add(matcher.matches() ? List.of(matcher.group(1), matcher.group(2).strip()) : List.of(collapsed));
        }
        return rows;
    }

    public List<String> guestCardLines() {
        Locator stay = guestCardStayLine();
        if (stay.count() == 0) {
            return List.of();
        }
        Object lines = stay.first().evaluate("""
                line => {
                  let card = line;
                  while (card.parentElement && card.innerText.split('\\n').filter(t => t.trim()).length < 3) {
                    card = card.parentElement;
                  }
                  return card.innerText.split('\\n').map(t => t.replace(/\\s+/g, ' ').trim()).filter(Boolean);
                }
                """);
        List<String> result = new ArrayList<>();
        for (Object line : (List<?>) lines) {
            result.add(String.valueOf(line));
        }
        return result;
    }

    public List<String> similarTasksLines() {
        Locator notice = similarTasksNotice().filter(new Locator.FilterOptions().setVisible(true));
        if (notice.count() == 0) {
            return List.of();
        }
        return ShellFrame.linesOf(notice.first().innerText());
    }

    public boolean submitLooksDisabled() {
        Object disabled = submitButton().evaluate(
                "el => el.disabled || el.getAttribute('aria-disabled') === 'true' || el.getAttribute('aria-busy') === 'true'");
        return Boolean.TRUE.equals(disabled);
    }

    public boolean submitBusy() {
        Object busy = submitButton().evaluate("el => el.getAttribute('aria-busy') === 'true'");
        return Boolean.TRUE.equals(busy);
    }

    public Map<String, Object> readContent() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("roomNo", roomValue());
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (String item : ITEMS) {
            int quantity = quantityOf(item);
            if (quantity > 0) {
                quantities.put(item, quantity);
            }
        }
        content.put("quantities", quantities);
        String text = otherRequestBox().inputValue();
        content.put("otherText", text.isBlank() ? null : text);
        content.put("otherDept", text.isBlank() ? null : selectedDept());
        return content;
    }

    public String selectedDept() {
        for (String dept : List.of("房務", "工務")) {
            Locator option = otherRequestDept(dept);
            if (option.count() == 1 && "true".equals(option.getAttribute("aria-pressed"))) {
                return dept;
            }
        }
        return null;
    }

    private ShellFrame shell() {
        return new ShellFrame(world);
    }

    public void settle() {
        page().evaluate("() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))");
        try {
            shell().waitForLayoutSettled();
        } catch (com.microsoft.playwright.PlaywrightException stillMoving) {
        }
    }

    public void openFromToolbar() {
        shell().press(toolbarButton());
        waitUntilReady();
        settle();
    }

    public void pressEvenIfDisabled(Locator target) {
        if (world.profile().touch()) {
            target.tap(new Locator.TapOptions().setForce(true));
        } else {
            target.click(new Locator.ClickOptions().setForce(true));
        }
    }

    private void focus(Locator box) {
        shell().press(box);
    }

    private void typeEachChar(String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            if (world.profile().touch()) {
                page().keyboard().insertText(ch);
            } else {
                page().keyboard().type(ch);
            }
            page().evaluate("() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))");
            i += Character.charCount(codePoint);
        }
    }

    public void typeRoomChars(String chars) {
        focus(roomInput());
        roomInput().press("End");
        typeEachChar(chars);
        settle();
    }

    public void enterRoom(String roomNo) {
        focus(roomInput());
        roomInput().fill("");
        typeEachChar(roomNo.strip());
        settle();
    }

    public void deleteLastRoomChar() {
        focus(roomInput());
        roomInput().press("End");
        roomInput().press("Backspace");
        settle();
    }

    public void pickSuggestion(String roomNo) {
        shell().press(suggestion(roomNo));
        settle();
    }

    public void tapItem(String item, int times) {
        for (int i = 0; i < times; i++) {
            pressEvenIfDisabled(itemTile(item));
            page().evaluate("() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))");
        }
        settle();
    }

    public void pressUnder(String item, String button, int times) {
        for (int i = 0; i < times; i++) {
            Locator target = switch (button.strip()) {
                case "−", "-" -> decreaseButton(item);
                case "+" -> increaseButton(item);
                case "移除" -> removeButton(item);
                default -> throw new IllegalArgumentException("品項底下的鈕只有 −、+、移除，收到「" + button + "」");
            };
            pressEvenIfDisabled(target);
            page().evaluate("() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))");
        }
        settle();
    }

    public void typeOtherRequest(String text) {
        focus(otherRequestBox());
        otherRequestBox().fill("");
        typeEachChar(text);
        settle();
    }

    public void clearOtherRequest() {
        focus(otherRequestBox());
        otherRequestBox().fill("");
        settle();
    }

    public void pasteIntoOtherRequest(String text) {
        panel().evaluate("""
                (panel, text) => {
                  const scratch = document.createElement('textarea');
                  scratch.id = '__webBddClipboard';
                  scratch.value = text;
                  scratch.style.position = 'fixed';
                  scratch.style.left = '0';
                  scratch.style.top = '0';
                  scratch.style.opacity = '0';
                  panel.appendChild(scratch);
                  scratch.focus();
                  scratch.select();
                }
                """, text);
        page().keyboard().press("ControlOrMeta+C");
        panel().evaluate("panel => panel.querySelector('#__webBddClipboard')?.remove()");
        focus(otherRequestBox());
        otherRequestBox().press("End");
        page().keyboard().press("ControlOrMeta+V");
        settle();
    }

    public void chooseDept(String dept) {
        shell().press(otherRequestDept(dept));
        settle();
    }

    public void submit() {
        shell().pressAndSettle(submitButton());
        settle();
    }

    public void clickBackdrop() {
        var box = backdrop().boundingBox();
        var panelBox = panel().boundingBox();
        if (box == null || panelBox == null) {
            throw new AssertionError("畫面上找不到代開單面板或它外面的遮罩");
        }
        double x = box.x + 8;
        double y = box.y + 8;
        if (x >= panelBox.x && x <= panelBox.x + panelBox.width && y >= panelBox.y && y <= panelBox.y + panelBox.height) {
            x = box.x + box.width - 8;
            y = box.y + box.height - 8;
        }
        if (world.profile().touch()) {
            page().touchscreen().tap(x, y);
        } else {
            page().mouse().click(x, y);
        }
        settle();
    }
}
