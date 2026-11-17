package com.collapp.project.service.impl;

import com.collapp.project.dto.offer.OfferRequest;
import com.collapp.project.dto.offer.OfferResponse;
import com.collapp.project.entity.Offer;
import com.collapp.project.entity.User;
import com.collapp.project.entity.enums.CompensationType;
import com.collapp.project.entity.enums.Location;
import com.collapp.project.entity.enums.OfferStatus;
import com.collapp.project.entity.enums.Specialty;
import com.collapp.project.exception.ForbiddenOperationException;
import com.collapp.project.exception.ResourceNotFoundException;
import com.collapp.project.mapper.OfferMapper;
import com.collapp.project.repository.OfferRepository;
import com.collapp.project.repository.UserRepository;
import com.collapp.project.util.TestObjectFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferServiceImplTest {

    @Mock
    private OfferRepository offerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OfferMapper offerMapper;

    @InjectMocks
    private OfferServiceImpl offerService;

    private User creator;
    private User otherUser;
    private User adminUser;
    private Offer offer;
    private OfferRequest offerRequest;
    private OfferResponse offerResponse;

    @BeforeEach
    void setUp() {
        creator = TestObjectFactory.createUser();
        otherUser = TestObjectFactory.createUser2();
        adminUser = TestObjectFactory.createAdminUser();
        offer = TestObjectFactory.createOffer(creator);
        offerRequest = TestObjectFactory.createOfferRequest();
        offerResponse = new OfferResponse(
                1L, creator.getId(), creator.getUsername(), creator.getAvatarUrl(),
                "Test Offer", "A test offer description", Specialty.MAKEUP, Location.BARCELONA,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                CompensationType.PAID, OfferStatus.OPEN, null
        );
    }

    @Test
    void crea_una_oferta_y_devuelve_la_respuesta() {
        when(userRepository.findByEmail(creator.getEmail())).thenReturn(Optional.of(creator));
        when(offerMapper.toEntity(offerRequest, creator)).thenReturn(offer);
        when(offerRepository.save(any(Offer.class))).thenReturn(offer);
        when(offerMapper.toResponse(offer)).thenReturn(offerResponse);

        OfferResponse result = offerService.create(offerRequest, creator.getEmail());

        assertNotNull(result);
        assertEquals("Test Offer", result.title());
        verify(offerMapper).toEntity(offerRequest, creator);
        verify(offerRepository).save(any(Offer.class));
        verify(offerMapper).toResponse(offer);
    }

    @Test
    void lanza_not_found_si_el_creador_no_existe() {
        when(userRepository.findByEmail("nonexistent@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> offerService.create(offerRequest, "nonexistent@test.com"));

        verify(offerRepository, never()).save(any());
    }

    @Test
    void actualiza_una_oferta_propia() {
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(offerRepository.save(any(Offer.class))).thenReturn(offer);
        when(offerMapper.toResponse(offer)).thenReturn(offerResponse);

        OfferResponse result = offerService.update(offer.getId(), offerRequest, creator.getEmail());

        assertNotNull(result);
        verify(offerMapper).updateEntity(offer, offerRequest);
        verify(offerRepository).save(offer);
    }

    @Test
    void lanza_forbidden_si_el_solicitante_no_es_el_dueno() {
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThrows(ForbiddenOperationException.class,
                () -> offerService.update(offer.getId(), offerRequest, otherUser.getEmail()));

        verify(offerRepository, never()).save(any());
    }

    @Test
    void lanza_not_found_si_la_oferta_no_existe() {
        when(offerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> offerService.update(999L, offerRequest, creator.getEmail()));
    }

    @Test
    void elimina_la_oferta_siendo_el_dueno() {
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        offerService.delete(offer.getId(), creator.getEmail(), false);

        verify(offerRepository).delete((Offer) any());
    }

    @Test
    void elimina_cualquier_oferta_siendo_admin() {
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        offerService.delete(offer.getId(), adminUser.getEmail(), true);

        verify(offerRepository).delete((Offer) any());
    }

    @Test
    void lanza_forbidden_si_no_es_dueno_ni_admin() {
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThrows(ForbiddenOperationException.class,
                () -> offerService.delete(offer.getId(), otherUser.getEmail(), false));

        verify(offerRepository, never()).delete((Offer) any());
    }

    @Test
    void devuelve_una_oferta_por_su_id() {
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(offerMapper.toResponse(offer)).thenReturn(offerResponse);

        OfferResponse result = offerService.getById(offer.getId());

        assertNotNull(result);
        assertEquals(offer.getId(), result.id());
    }

    @Test
    void lanza_not_found_si_la_oferta_no_existe() {
        when(offerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> offerService.getById(999L));
    }

    @Test
    void lista_las_ofertas_abiertas_de_una_categoria() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Offer> offerPage = new PageImpl<>(List.of(offer), pageable, 1);

        when(offerRepository.findByStatusAndCategory(OfferStatus.OPEN, Specialty.MAKEUP, pageable))
                .thenReturn(offerPage);
        when(offerMapper.toResponse(offer)).thenReturn(offerResponse);

        Page<OfferResponse> result = offerService.list(Specialty.MAKEUP, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(offerRepository).findByStatusAndCategory(OfferStatus.OPEN, Specialty.MAKEUP, pageable);
    }

    @Test
    void lista_todas_las_ofertas_abiertas() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Offer> offerPage = new PageImpl<>(List.of(offer), pageable, 1);

        when(offerRepository.findByStatus(OfferStatus.OPEN, pageable)).thenReturn(offerPage);
        when(offerMapper.toResponse(offer)).thenReturn(offerResponse);

        Page<OfferResponse> result = offerService.list(null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(offerRepository).findByStatus(OfferStatus.OPEN, pageable);
    }

    @Test
    void cambia_el_estado_de_una_oferta_propia() {
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(offerRepository.save(any(Offer.class))).thenReturn(offer);
        when(offerMapper.toResponse(offer)).thenReturn(offerResponse);

        OfferResponse result = offerService.updateStatus(offer.getId(), OfferStatus.PAUSED, creator.getEmail());

        assertNotNull(result);
        verify(offerRepository).save(offer);
    }

    @Test
    void lista_las_ofertas_del_usuario_autenticado() {
        when(userRepository.findByEmail(creator.getEmail())).thenReturn(Optional.of(creator));
        when(offerRepository.findByCreatorId(creator.getId())).thenReturn(List.of(offer));
        when(offerMapper.toResponseList(List.of(offer))).thenReturn(List.of(offerResponse));

        List<OfferResponse> result = offerService.listMine(creator.getEmail());

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(offerRepository).findByCreatorId(creator.getId());
    }

    @Test
    void lista_todas_las_ofertas_para_el_admin() {
        when(offerRepository.findAll()).thenReturn(List.of(offer));
        when(offerMapper.toResponseList(List.of(offer))).thenReturn(List.of(offerResponse));

        List<OfferResponse> result = offerService.listAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(offerRepository).findAll();
    }
}
