package com.collapp.project.controller;

import com.collapp.project.dto.error.ApiError;
import com.collapp.project.dto.user.UserResponse;
import com.collapp.project.dto.user.UserUpdateRequest;
import com.collapp.project.security.CustomUserDetails;
import com.collapp.project.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Perfil del usuario autenticado")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Obtener el perfil propio")
    @ApiResponse(responseCode = "200", description = "Perfil del usuario autenticado")
    @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(Authentication authentication) {
        String email = ((CustomUserDetails) authentication.getPrincipal()).getUser().getEmail();
        return ResponseEntity.ok(userService.getCurrentUser(email));
    }

    @Operation(summary = "Actualizar el perfil propio",
            description = "Los campos omitidos conservan su valor actual")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado")
    @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            Authentication authentication,
            @Valid @RequestBody UserUpdateRequest request) {
        String email = ((CustomUserDetails) authentication.getPrincipal()).getUser().getEmail();
        return ResponseEntity.ok(userService.updateCurrentUser(email, request));
    }
}
