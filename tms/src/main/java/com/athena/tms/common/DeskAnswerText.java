package com.athena.tms.common;

import com.athena.tms.common.codes.ErrorCode;
import com.athena.tms.common.error.UnprocessableEntityException;
import java.util.Map;

/**
 * 櫃檯當場回答給住客的回覆，修剪與長度的規則只在這一處定義。
 *
 * <p>回覆先去掉頭尾空白，剩下的字必須是 1 到 120 字；存進 {@code tasks.answer}、
 * 寫進 answered 事件與住客看到的，都是去掉空白後的那段字。長度以字（Unicode code point）計，
 * 中文字與表情符號都算一個字，和櫃檯畫面上的字數一致。
 *
 * <p>同一個上限也寫在櫃檯 Web 的當場回答面板（最多 120 字、送出前去掉頭尾空白），
 * 以及 API 契約 {@code deskAnswerTask} 與 {@code answer_invalid} 的說明。要改上限得幾處一起改。
 */
public final class DeskAnswerText {

    public static final int MAX_LENGTH = 120;

    private DeskAnswerText() {
    }

    /**
     * 要把回覆寫進單裡的地方都先經過這裡，拿回來的字才是可以存、可以給住客看的版本。
     *
     * <p>沒帶回覆的請求和只有空白的回覆一樣，會被擋下來，不會結案一張住客收不到任何字的單。
     * 擋下來時 {@code context} 帶 {@code maxLength}，畫面可以照它提示上限。
     */
    public static String require(String raw) {
        String answer = raw == null ? "" : raw.strip();
        int length = answer.codePointCount(0, answer.length());
        if (length < 1 || length > MAX_LENGTH) {
            throw new UnprocessableEntityException(ErrorCode.ANSWER_INVALID,
                    "回覆去掉頭尾空白後必須是 1 到 " + MAX_LENGTH + " 字，收到 " + length + " 字",
                    Map.of("maxLength", MAX_LENGTH));
        }
        return answer;
    }
}
