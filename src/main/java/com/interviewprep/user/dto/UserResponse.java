package com.interviewprep.user.dto;

import java.time.Instant;

import com.interviewprep.user.AppUser;
import com.interviewprep.user.Role;

/** Public view of a user. Deliberately has no password hash. */
public record UserResponse(Long id, String email, Role role, Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
