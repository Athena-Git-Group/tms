package com.athena.tms.kb;

import java.util.List;
import java.util.UUID;

public record KbAnswer(
        UUID answerId, boolean found, String answer, String notFoundMessage, List<KbSource> sources) {
}
