package com.collapp.project.controller;

import com.collapp.project.dto.application.ApplicationResponse;
import com.collapp.project.dto.error.ApiError;
import com.collapp.project.dto.offer.OfferRequest;
import com.collapp.project.dto.offer.OfferResponse;
import com.collapp.project.dto.offer.OfferStatusRequest;
import com.collapp.project.entity.enums.Specialty;
import com.collapp.project.security.AuthHelper;
import com.collapp.project.service.ApplicationService;
import com.collapp.project.service.OfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Offers", description = "Ofertas de colaboración")
@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;
    private final ApplicationService applicationService;
    private final AuthHelper authHelper;

    @Operation(summary = "Listar ofertas abiertas",
            description = "Listado público y paginado, con filtro opcional por categoría")
    @ApiResponse(responseCode = "200", description = "Página de ofertas abiertas")
    @ApiResponse(responseCode = "400", description = "Parámetros de consulta inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<Page<OfferResponse>> list(
            @RequestParam(required = false) Specialty category,
            Pageable pageable) {
        return ResponseEntity.ok(offerService.list(category, pageable));
    }

    @Operation(summary = "Obtener una oferta por id")
    @ApiResponse(responseCode = "200", description = "Oferta encontrada")
    @ApiResponse(responseCode = "404", description = "Oferta no encontrada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @SecurityRequirements
    @GetMapping("/{id}")
    public ResponseEntity<OfferResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(offerService.getById(id));
    }

    @Operation(summary = "Listar las ofertas del usuario autenticado")
    @ApiResponse(responseCode = "200", description = "Ofertas creadas por el usuario")
    @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/mine")
    public ResponseEntity<List<OfferResponse>> listMyOffers(Authentication authentication) {
        return ResponseEntity.ok(offerService.listMine(authHelper.extractEmail(authentication)));
    }

    @Operation(summary = "Listar todas las ofertas (solo admin)")
    @ApiResponse(responseCode = "200", description = "Todas las ofertas")
    @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Se requiere rol de administrador",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/all")
    public ResponseEntity<List<OfferResponse>> listAll() {
        return ResponseEntity.ok(offerService.listAll());
    }

    @Operation(summary = "Listar las candidaturas recibidas en una oferta")
    @ApiResponse(responseCode = "200", description = "Candidaturas de la oferta")
    @ApiResponse(responseCode = "403", description = "La oferta pertenece a otro usuario",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Oferta no encontrada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{id}/applications")
    public ResponseEntity<List<ApplicationResponse>> listApplicationsForOffer(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(
                applicationService.listByOffer(id, authHelper.extractEmail(authentication)));
    }

    @Operation(summary = "Crear una oferta")
    @ApiResponse(responseCode = "201", description = "Oferta creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o rango de fechas incorrecto",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    public ResponseEntity<OfferResponse> create(
            @Valid @RequestBody OfferRequest request,
            Authentication authentication) {
        OfferResponse response = offerService.create(request, authHelper.extractEmail(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Editar una oferta propia")
    @ApiResponse(responseCode = "200", description = "Oferta actualizada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o rango de fechas incorrecto",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "La oferta pertenece a otro usuario",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Oferta no encontrada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PutMapping("/{id}")
    public ResponseEntity<OfferResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody OfferRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(offerService.update(id, request, authHelper.extractEmail(authentication)));
    }

    @Operation(summary = "Eliminar una oferta propia (o cualquiera siendo admin)")
    @ApiResponse(responseCode = "204", description = "Oferta eliminada")
    @ApiResponse(responseCode = "403", description = "Sin permiso para eliminarla",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Oferta no encontrada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        offerService.delete(id, authHelper.extractEmail(authentication), authHelper.isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cambiar el estado de una oferta propia")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "400", description = "Estado inválido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "La oferta pertenece a otro usuario",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Oferta no encontrada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PatchMapping("/{id}/status")
    public ResponseEntity<OfferResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody OfferStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(offerService.updateStatus(id, request.status(), authHelper.extractEmail(authentication)));
    }
}
