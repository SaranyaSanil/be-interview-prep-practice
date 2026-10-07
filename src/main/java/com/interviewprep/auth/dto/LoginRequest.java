package com.interviewprep.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    /** Hides the password if the record is ever logged. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****]";
    }
}
