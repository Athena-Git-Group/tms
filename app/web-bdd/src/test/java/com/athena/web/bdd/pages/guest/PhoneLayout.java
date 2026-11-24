package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 住客每一頁在手機直向上的版面量測：橫向捲動、欄位、可點範圍、長標題、釘在頁底的按鈕。
 *
 * <p>量的都是瀏覽器排版之後的方框（{@code getBoundingClientRect}），因為這幾件事只有排版完才看得出來；
 * 元素一樣用角色與看得到的字找。
 */
public class PhoneLayout extends PageObject {

    /** 「可以點的東西」：按鈕、連結、選單選項、單選的星星與膠囊、勾選框、分頁。 */
    private static final List<AriaRole> PRESSABLE_ROLES = List.of(AriaRole.BUTTON, AriaRole.LINK, AriaRole.MENUITEM,
            AriaRole.MENUITEMRADIO, AriaRole.RADIO, AriaRole.CHECKBOX, AriaRole.TAB, AriaRole.OPTION);

    public PhoneLayout(WebWorld world) {
        super(world);
    }

    /** 頁面可以捲動的寬度與視窗寬度。 */
    public Map<String, Object> scrollWidth() {
        @SuppressWarnings("unchecked")
        Map<String, Object> widths = (Map<String, Object>) page().evaluate("""
                () => ({ scroll: document.scrollingElement.scrollWidth, viewport: window.innerWidth })
                """);
        return widths;
    }

    /**
     * 右緣超出視窗的元素，每一個寫成「標籤名.class：右緣」。
     * 祖先把超出的部分裁掉（{@code overflow} 不是 visible）時以裁切後的右緣算，因為裝飾用的大圓圈會被卡片裁掉，住客看不到。
     */
    public List<String> elementsPastRightEdge() {
        @SuppressWarnings("unchecked")
        List<String> found = (List<String>) page().evaluate("""
                () => {
                  const vw = window.innerWidth;
                  const out = [];
                  for (const el of document.body.querySelectorAll('*')) {
                    const s = getComputedStyle(el);
                    if (s.display === 'none' || s.visibility === 'hidden') continue;
                    const r = el.getBoundingClientRect();
                    if (r.width === 0 || r.height === 0) continue;
                    let right = r.right;
                    for (let a = el.parentElement; a && a !== document.documentElement; a = a.parentElement) {
                      if (getComputedStyle(a).overflowX !== 'visible') right = Math.min(right, a.getBoundingClientRect().right);
                    }
                    if (right > vw + 0.5) out.push(el.tagName.toLowerCase() + '.' + el.className + '：' + Math.round(right));
                  }
                  return out;
                }
                """);
        return found;
    }

    /**
     * 內容區（{@code main}）的左右邊距，以及它直接包著的區塊裡左右並排（上下範圍重疊）的有幾對。
     */
    public Map<String, Object> contentColumns() {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) page().evaluate("""
                () => {
                  const main = document.querySelector('main');
                  if (!main) return { found: false };
                  const r = main.getBoundingClientRect();
                  const blocks = [...main.children].map(c => c.getBoundingClientRect()).filter(b => b.width > 0 && b.height > 0);
                  let sideBySide = 0;
                  for (let i = 0; i < blocks.length; i++) {
                    for (let j = i + 1; j < blocks.length; j++) {
                      const a = blocks[i], b = blocks[j];
                      const overlapY = a.top < b.bottom - 0.5 && b.top < a.bottom - 0.5;
                      const apartX = a.right <= b.left + 0.5 || b.right <= a.left + 0.5;
                      if (overlapY && apartX) sideBySide++;
                    }
                  }
                  return { found: true, left: r.left, right: window.innerWidth - r.right, sideBySide };
                }
                """);
        return result;
    }

    /** 畫面上看得到的可以點的東西有幾個（按鈕、連結等，和 {@link #pressablesSmallerThan(double)} 同一套角色）。 */
    public int pressableCount() {
        int count = 0;
        for (AriaRole role : PRESSABLE_ROLES) {
            count += page().getByRole(role).filter(new Locator.FilterOptions().setVisible(true)).count();
        }
        return count;
    }

    /** 看得到、而且可點範圍小於 44 × 44 的東西，每一個寫成「角色 名稱：寬×高」。 */
    public List<String> pressablesSmallerThan(double side) {
        List<String> small = new ArrayList<>();
        for (AriaRole role : PRESSABLE_ROLES) {
            for (Locator item : page().getByRole(role).filter(new Locator.FilterOptions().setVisible(true)).all()) {
                var box = item.boundingBox();
                if (box == null) {
                    continue;
                }
                if (box.width < side - 0.5 || box.height < side - 0.5) {
                    String name = String.valueOf(item.evaluate(
                            "el => (el.getAttribute('aria-label') || el.innerText || '').trim().replace(/\\s+/g, ' ')"));
                    small.add(role.name().toLowerCase() + " 「" + name + "」：" + Math.round(box.width) + "×" + Math.round(box.height));
                }
            }
        }
        return small;
    }

    /** 把頁面與頁面裡每一塊可以直向捲動的區域捲到最底，等捲動停下來（連續兩個畫面位置不變）。 */
    public void scrollToBottom() {
        page().evaluate("""
                async () => {
                  const scrollables = [document.scrollingElement, ...document.body.querySelectorAll('*')].filter(el => {
                    if (el === document.scrollingElement) return true;
                    const s = getComputedStyle(el);
                    return /(auto|scroll)/.test(s.overflowY) && el.scrollHeight > el.clientHeight;
                  });
                  for (const el of scrollables) el.scrollTo({ top: el.scrollHeight, behavior: 'instant' });
                  const frame = () => new Promise(r => requestAnimationFrame(r));
                  let last = -1;
                  for (let i = 0; i < 60; i++) {
                    await frame();
                    const now = scrollables.map(el => el.scrollTop).join(',') + window.scrollY;
                    if (now === last) return;
                    last = now;
                  }
                }
                """);
    }

    /**
     * 一段字的排版：行數、有沒有被截掉、截掉時是不是以「…」收尾（{@code text-overflow: ellipsis} 或限制行數），
     * 以及它的方框。
     */
    public Map<String, Object> textBox(Locator text) {
        @SuppressWarnings("unchecked")
        Map<String, Object> box = (Map<String, Object>) text.evaluate("""
                el => {
                  const s = getComputedStyle(el);
                  const r = el.getBoundingClientRect();
                  const lineHeight = parseFloat(s.lineHeight) || parseFloat(s.fontSize) * 1.2;
                  const clipped = el.scrollHeight > el.clientHeight + 1 || el.scrollWidth > el.clientWidth + 1;
                  const clamp = s.webkitLineClamp && s.webkitLineClamp !== 'none';
                  return {
                    lines: Math.round(r.height / lineHeight),
                    clipped,
                    ellipsis: s.textOverflow === 'ellipsis' || Boolean(clamp),
                    left: r.left, top: r.top, right: r.right, bottom: r.bottom
                  };
                }
                """);
        return box;
    }

    /** 兩個元素的方框有沒有重疊。 */
    public static boolean overlaps(Map<String, Object> a, Map<String, Object> b) {
        return num(a, "left") < num(b, "right") && num(b, "left") < num(a, "right")
                && num(a, "top") < num(b, "bottom") && num(b, "top") < num(a, "bottom");
    }

    /** {@code inner} 的方框是不是整個在 {@code outer} 裡面。 */
    public static boolean inside(Map<String, Object> inner, Map<String, Object> outer) {
        return num(inner, "left") >= num(outer, "left") - 0.5 && num(inner, "right") <= num(outer, "right") + 0.5
                && num(inner, "top") >= num(outer, "top") - 0.5 && num(inner, "bottom") <= num(outer, "bottom") + 0.5;
    }

    /** 一個元素的方框，形狀和 {@link #textBox(Locator)} 的相同。 */
    public Map<String, Object> box(Locator element) {
        @SuppressWarnings("unchecked")
        Map<String, Object> box = (Map<String, Object>) element.evaluate("""
                el => { const r = el.getBoundingClientRect(); return { left: r.left, top: r.top, right: r.right, bottom: r.bottom }; }
                """);
        return box;
    }

    /**
     * 這顆按鈕在視窗裡的位置：在不在視窗裡、正中間那一點是不是它自己（沒有被別的東西蓋住）、
     * 包著它的固定或黏住的操作列（沒有時是 {@code null}）底緣離視窗底部多遠。
     * 量操作列而不量按鈕本身，因為按鈕下面還有內距、說明字與手機的安全區域。
     */
    public Map<String, Object> pinnedButton(Locator button) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) button.evaluate("""
                el => {
                  const r = el.getBoundingClientRect();
                  const hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
                  let bar = el;
                  while (bar && !['fixed', 'sticky'].includes(getComputedStyle(bar).position)) bar = bar.parentElement;
                  return {
                    inViewport: r.top >= 0 && r.bottom <= window.innerHeight + 0.5 && r.width > 0,
                    barGapToBottom: bar ? window.innerHeight - bar.getBoundingClientRect().bottom : null,
                    onTop: Boolean(hit) && (hit === el || el.contains(hit))
                  };
                }
                """);
        return result;
    }

    public static double num(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }
}
