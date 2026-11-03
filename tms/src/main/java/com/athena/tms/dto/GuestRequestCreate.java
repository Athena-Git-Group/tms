package com.athena.tms.dto;

public record GuestRequestCreate(String itemId, Integer quantity, String text, String lang) {
}
