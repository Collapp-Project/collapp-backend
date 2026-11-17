package com.collapp.project.controller;

import com.collapp.project.dto.application.ApplicationRequest;
import com.collapp.project.dto.application.ApplicationResponse;
import com.collapp.project.dto.application.ApplicationStatusRequest;
import com.collapp.project.dto.error.ApiError;
import com.collapp.project.security.AuthHelper;
import com.collapp.project.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Applications", description = "Candidaturas a ofertas")
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final AuthHelper authHelper;

    @Operation(summary = "Inscribirse a una oferta")
    @ApiResponse(responseCode = "201", description = "Candidatura creada en estado PENDING")
    @ApiResponse(responseCode = "400", description = "No puedes inscribirte a tu propia oferta",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Oferta no encontrada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Ya te has inscrito a esta oferta",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody ApplicationRequest request,
            Authentication authentication) {
        ApplicationResponse response = applicationService.create(request, authHelper.extractEmail(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Listar mis candidaturas")
    @ApiResponse(responseCode = "200", description = "Candidaturas del usuario autenticado")
    @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/mine")
    public ResponseEntity<List<ApplicationResponse>> listMine(Authentication authentication) {
        return ResponseEntity.ok(applicationService.listMine(authHelper.extractEmail(authentication)));
    }

    @Operation(summary = "Aceptar o rechazar una candidatura",
            description = "Solo el dueño de la oferta puede cambiar el estado")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "400", description = "Estado inválido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "La oferta pertenece a otro usuario",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Candidatura no encontrada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                applicationService.updateStatus(id, request, authHelper.extractEmail(authentication))
        );
    }
}
