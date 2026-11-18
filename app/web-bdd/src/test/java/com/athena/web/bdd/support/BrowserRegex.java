package com.athena.web.bdd.support;

/**
 * 把一段字照字面放進要交給瀏覽器比對的正規表示式。
 *
 * <p>Playwright 把 Java 的 {@code Pattern} 原樣交給瀏覽器的 JavaScript 去比對。{@code Pattern.quote} 產生的
 * {@code \Q…\E} 是 Java 才有的寫法，JavaScript 會把它讀成字母 Q 與 E，於是任何名稱都比對不到。
 * 這裡在每個特殊字元前加反斜線，Java 與 JavaScript 讀起來都是同一段字面。
 * 斜線也要跳開，因為 Playwright 把表示式寫成 {@code /…/} 交給瀏覽器。
 */
public final class BrowserRegex {

    private static final String SPECIAL = "\\^$.|?*+()[]{}/";

    private BrowserRegex() {
    }

    public static String literal(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (SPECIAL.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
