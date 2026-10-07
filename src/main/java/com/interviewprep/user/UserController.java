package com.interviewprep.user;

import java.util.List;

import com.interviewprep.user.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access rules are declared centrally in {@code SecurityConfig}: {@code /me} needs any valid token,
 * listing all users needs the ADMIN role.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** The user id comes from the token's subject, so a caller can only ever see their own profile. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping
    public List<UserResponse> listAll() {
        return userService.findAll();
    }
}
