package com.collapp.project.mapper;

import com.collapp.project.dto.auth.RegisterRequest;
import com.collapp.project.dto.user.UserResponse;
import com.collapp.project.entity.User;
import com.collapp.project.entity.enums.SystemRole;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    /** El registro público solo crea creativos; los demás roles se asignan por otra vía. */
    public User toEntity(RegisterRequest request, String passwordHash) {
        return User.builder()
                .username(request.username())
                .fullName(request.fullName())
                .email(request.email())
                .passwordHash(passwordHash)
                .specialty(request.specialty())
                .systemRole(SystemRole.ROLE_CREATIVE)
                .avatarUrl(request.avatarUrl())
                .portfolioUrl(request.portfolioUrl())
                .instagramUrl(request.instagramUrl())
                .bio(request.bio())
                .build();
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getSpecialty(),
                user.getBio(),
                user.getAvatarUrl(),
                user.getPortfolioUrl(),
                user.getInstagramUrl()
        );
    }
}