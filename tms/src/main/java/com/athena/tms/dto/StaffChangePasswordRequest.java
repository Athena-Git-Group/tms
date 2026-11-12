package com.athena.tms.dto;

public record StaffChangePasswordRequest(String currentPassword, String newPassword) {}
