package com.interviewprep.user;

import java.util.List;
import java.util.Locale;

import com.interviewprep.common.exception.ResourceNotFoundException;
import com.interviewprep.user.dto.UserResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Emails are compared case-insensitively by always storing and looking them up in this form. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public UserResponse getProfile(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
    }
}
