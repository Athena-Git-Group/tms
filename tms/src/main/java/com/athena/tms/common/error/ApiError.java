package com.athena.tms.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(value=JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, List<Detail> details, Map<String, Object> context) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, null);
    }

    public record Detail(String field, String issue) {
    }
}
