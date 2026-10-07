package com.interviewprep.auth.dto;

/**
 * @param expiresIn token lifetime in seconds, so clients know when to log in again
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
