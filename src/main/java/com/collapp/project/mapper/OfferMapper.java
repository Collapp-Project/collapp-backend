package com.collapp.project.mapper;

import com.collapp.project.dto.offer.OfferRequest;
import com.collapp.project.dto.offer.OfferResponse;
import com.collapp.project.entity.Offer;
import com.collapp.project.entity.User;
import com.collapp.project.entity.enums.OfferStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OfferMapper {

    /** Una oferta nace siempre abierta; los demás estados se asignan con el PATCH de estado. */
    public Offer toEntity(OfferRequest request, User creator) {
        return Offer.builder()
                .creator(creator)
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .location(request.location())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .compensationType(request.compensationType())
                .status(OfferStatus.OPEN)
                .build();
    }

    public void updateEntity(Offer offer, OfferRequest request) {
        offer.setTitle(request.title());
        offer.setDescription(request.description());
        offer.setCategory(request.category());
        offer.setLocation(request.location());
        offer.setStartDate(request.startDate());
        offer.setEndDate(request.endDate());
        offer.setCompensationType(request.compensationType());
        offer.setStatus(request.status());
    }

    public OfferResponse toResponse(Offer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getCreator().getId(),
                offer.getCreator().getUsername(),
                offer.getCreator().getAvatarUrl(),
                offer.getTitle(),
                offer.getDescription(),
                offer.getCategory(),
                offer.getLocation(),
                offer.getStartDate(),
                offer.getEndDate(),
                offer.getCompensationType(),
                offer.getStatus(),
                offer.getCreatedAt()
        );
    }

    public List<OfferResponse> toResponseList(List<Offer> offers) {
        return offers.stream()
                .map(this::toResponse)
                .toList();
    }
}