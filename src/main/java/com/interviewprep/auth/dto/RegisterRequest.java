package com.interviewprep.auth.dto;

import com.interviewprep.user.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * There is deliberately no {@code role}: self-registration always creates a USER, so nobody can make themselves
 * ADMIN. The 72 limit is BCrypt's maximum input length.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = AppUser.EMAIL_MAX_LENGTH) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    /** Hides the password if the record is ever logged. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=****]";
    }
}
