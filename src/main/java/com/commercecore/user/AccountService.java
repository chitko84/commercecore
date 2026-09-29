package com.commercecore.user;

import com.commercecore.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AccountService {
    private final UserAccountRepository users;
    public AccountService(UserAccountRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public UserDtos.ProfileResponse get(UUID userId) { return response(find(userId)); }

    @Transactional
    public UserDtos.ProfileResponse update(UUID userId, UserDtos.UpdateProfileRequest request) {
        UserAccount user = find(userId);
        String fullName = request.fullName() == null ? user.getFullName() : request.fullName();
        String phone = request.phone() == null ? user.getPhone() : request.phone();
        user.updateProfile(fullName, phone);
        return response(user);
    }

    private UserAccount find(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND", "The account was not found."));
    }
    private static UserDtos.ProfileResponse response(UserAccount user) {
        return new UserDtos.ProfileResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getRole(), user.isEmailVerified(), user.getAccountStatus(), user.getCreatedAt());
    }
}
