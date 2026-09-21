package com.hic.service;

import com.hic.model.User;
import com.hic.repository.UserRepository;
import com.hic.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserScopeService {

    private final UserRepository userRepository;

    public Long resolveBranchScope(Long requestedBranchId) {
        String username = TenantContext.getUsername();
        if (username == null || username.isBlank()) {
            return requestedBranchId;
        }

        return userRepository.findByUsername(username)
                .map(user -> {
                    if (isBranchScopedUser(user)) {
                        return user.getBranchId();
                    }
                    return requestedBranchId;
                })
                .orElse(requestedBranchId);
    }

    private boolean isBranchScopedUser(User user) {
        return (user.getUserType() == User.UserType.OFFICE_HR
                || user.getUserType() == User.UserType.DEPARTMENT_HR)
                && user.getBranchId() != null;
    }
}
