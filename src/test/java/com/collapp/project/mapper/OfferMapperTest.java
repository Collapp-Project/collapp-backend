package com.collapp.project.mapper;

import com.collapp.project.dto.offer.OfferRequest;
import com.collapp.project.dto.offer.OfferResponse;
import com.collapp.project.entity.Offer;
import com.collapp.project.entity.User;
import com.collapp.project.entity.enums.OfferStatus;
import com.collapp.project.util.TestObjectFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OfferMapperTest {

    private OfferMapper offerMapper;
    private Offer offer;
    private User creator;

    @BeforeEach
    void setUp() {
        offerMapper = new OfferMapper();
        creator = TestObjectFactory.createUser();
        offer = TestObjectFactory.createOffer(creator);
    }

    @Test
    void toResponse_mapsAllFields() {
        OfferResponse response = offerMapper.toResponse(offer);

        assertNotNull(response);
        assertEquals(offer.getId(), response.id());
        assertEquals(offer.getCreator().getId(), response.creatorId());
        assertEquals(offer.getCreator().getUsername(), response.creatorUsername());
        assertEquals(offer.getCreator().getAvatarUrl(), response.creatorAvatarUrl());
        assertEquals(offer.getTitle(), response.title());
        assertEquals(offer.getDescription(), response.description());
        assertEquals(offer.getCategory(), response.category());
        assertEquals(offer.getLocation(), response.location());
        assertEquals(offer.getStartDate(), response.startDate());
        assertEquals(offer.getEndDate(), response.endDate());
        assertEquals(offer.getCompensationType(), response.compensationType());
        assertEquals(offer.getStatus(), response.status());
    }

    @Test
    void toEntity_mapeaTodosLosCamposYFijaEstadoInicial() {
        OfferRequest request = TestObjectFactory.createOfferRequest();

        Offer entity = offerMapper.toEntity(request, creator);

        assertNotNull(entity);
        assertEquals(creator, entity.getCreator());
        assertEquals(request.title(), entity.getTitle());
        assertEquals(request.description(), entity.getDescription());
        assertEquals(request.category(), entity.getCategory());
        assertEquals(request.location(), entity.getLocation());
        assertEquals(request.startDate(), entity.getStartDate());
        assertEquals(request.endDate(), entity.getEndDate());
        assertEquals(request.compensationType(), entity.getCompensationType());
        assertEquals(OfferStatus.OPEN, entity.getStatus());
    }

    @Test
    void updateEntity_aplicaTodosLosCambiosSobreLaEntidadExistente() {
        OfferRequest request = TestObjectFactory.createOfferRequest("Título actualizado");

        offerMapper.updateEntity(offer, request);

        assertEquals(request.title(), offer.getTitle());
        assertEquals(request.description(), offer.getDescription());
        assertEquals(request.category(), offer.getCategory());
        assertEquals(request.location(), offer.getLocation());
        assertEquals(request.startDate(), offer.getStartDate());
        assertEquals(request.endDate(), offer.getEndDate());
        assertEquals(request.compensationType(), offer.getCompensationType());
        assertEquals(request.status(), offer.getStatus());
    }
}
