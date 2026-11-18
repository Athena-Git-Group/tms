package com.athena.tms.common;

import com.athena.tms.common.codes.ErrorCode;
import com.athena.tms.common.error.UnprocessableEntityException;
import java.util.Map;

public final class DeskAnswerText {

    public static final int MAX_LENGTH = 120;

    private DeskAnswerText() {
    }

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
