/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonInclude
 *  com.fasterxml.jackson.annotation.JsonInclude$Include
 */
package com.athena.tms.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(value=JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, List<Detail> details) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public record Detail(String field, String issue) {
    }
}
