package com.athena.web.bdd.pages.guest;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 評分卡上一顆星畫成哪一種圖形。每顆星是一個 {@code svg}，裡面一條 {@code path}，{@code d} 照原型
 * {@code ui/guest.js} 圖示表的 {@code star_fill}（實心）與 {@code star}（空心）。產品在 Phase 4 照這兩個圖形畫。
 */
public enum StarShape {
    FILLED("實心"),
    HOLLOW("空心"),
    UNRECOGNISED("認不出");

    private static final String FILL_PATH = "M6.14 20.91Q5.66 20.51 5.94 19.59L7.74 14.28L3.15 10.97Q2.31 10.41 2.55 9.78Q2.75 9.18 3.75 9.18L9.33 9.22L11.05 3.87Q11.37 2.92 12.01 2.92Q12.64 2.92 12.96 3.87L14.68 9.22L20.26 9.18Q21.26 9.18 21.46 9.78Q21.66 10.45 20.86 10.97L16.27 14.28L18.07 19.59Q18.35 20.51 17.87 20.91Q17.31 21.34 16.51 20.71L12.01 17.4L7.5 20.71Q6.7 21.34 6.14 20.91Z";
    private static final String HOLLOW_PATH = FILL_PATH
            + "M7.38 19.23Q7.42 19.23 7.46 19.19L11.57 16.08Q12.01 15.76 12.45 16.08L16.55 19.19Q16.63 19.27 16.63 19.23V19.15L14.92 14.24Q14.72 13.73 15.16 13.45L19.43 10.49L19.47 10.45Q19.47 10.41 19.39 10.41L14.24 10.49Q13.68 10.49 13.52 10.02L12.05 5.07Q12.05 4.99 12.01 4.99Q11.97 5.03 11.97 5.07L10.49 10.02Q10.33 10.49 9.77 10.49L4.63 10.41Q4.55 10.41 4.55 10.45L4.59 10.49L8.86 13.45Q9.29 13.73 9.09 14.24L7.38 19.15Z";

    private final String label;

    StarShape(String label) {
        this.label = label;
    }

    /** 一顆星裡所有 {@code path} 的 {@code d}；剛好一條且是兩種圖形之一才認得出。 */
    public static StarShape of(List<?> paths) {
        if (paths.size() != 1) {
            return UNRECOGNISED;
        }
        String d = String.valueOf(paths.get(0)).strip();
        if (d.equals(FILL_PATH)) {
            return FILLED;
        }
        return d.equals(HOLLOW_PATH) ? HOLLOW : UNRECOGNISED;
    }

    /** 前 {@code filled} 顆實心、其餘空心，共五顆。 */
    public static List<StarShape> pattern(int filled) {
        return IntStream.rangeClosed(1, 5).mapToObj(n -> n <= filled ? FILLED : HOLLOW).toList();
    }

    @Override
    public String toString() {
        return label;
    }
}
