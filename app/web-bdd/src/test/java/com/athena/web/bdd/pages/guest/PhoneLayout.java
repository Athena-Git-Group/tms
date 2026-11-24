package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.support.JsResult;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PhoneLayout extends PageObject {

    private static final List<AriaRole> PRESSABLE_ROLES = List.of(AriaRole.BUTTON, AriaRole.LINK, AriaRole.MENUITEM,
            AriaRole.MENUITEMRADIO, AriaRole.RADIO, AriaRole.CHECKBOX, AriaRole.TAB, AriaRole.OPTION);

    public PhoneLayout(WebWorld world) {
        super(world);
    }

    public Map<String, Object> scrollWidth() {
        return JsResult.object(page().evaluate("""
                () => ({ scroll: document.scrollingElement.scrollWidth, viewport: window.innerWidth })
                """));
    }

    public List<String> elementsPastRightEdge() {
        return JsResult.strings(page().evaluate("""
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
                """));
    }

    public Map<String, Object> contentColumns() {
        return JsResult.object(page().evaluate("""
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
                """));
    }

    public int pressableCount() {
        int count = 0;
        for (AriaRole role : PRESSABLE_ROLES) {
            count += page().getByRole(role).filter(new Locator.FilterOptions().setVisible(true)).count();
        }
        return count;
    }

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

    public Map<String, Object> textBox(Locator text) {
        return JsResult.object(text.evaluate("""
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
                """));
    }

    public static boolean overlaps(Map<String, Object> a, Map<String, Object> b) {
        return num(a, "left") < num(b, "right") && num(b, "left") < num(a, "right")
                && num(a, "top") < num(b, "bottom") && num(b, "top") < num(a, "bottom");
    }

    public static boolean inside(Map<String, Object> inner, Map<String, Object> outer) {
        return num(inner, "left") >= num(outer, "left") - 0.5 && num(inner, "right") <= num(outer, "right") + 0.5
                && num(inner, "top") >= num(outer, "top") - 0.5 && num(inner, "bottom") <= num(outer, "bottom") + 0.5;
    }

    public Map<String, Object> box(Locator element) {
        return JsResult.object(element.evaluate("""
                el => { const r = el.getBoundingClientRect(); return { left: r.left, top: r.top, right: r.right, bottom: r.bottom }; }
                """));
    }

    public Map<String, Object> pinnedButton(Locator button) {
        return JsResult.object(button.evaluate("""
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
                """));
    }

    public static double num(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }
}
