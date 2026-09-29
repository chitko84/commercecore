package com.commercecore.product;

import com.commercecore.common.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ProductMediaService {
    private final ProductMediaRepository media;
    private final ProductService products;
    public ProductMediaService(ProductMediaRepository media, ProductService products) {
        this.media = media; this.products = products;
    }

    @Transactional
    public ProductDtos.MediaResponse create(UUID productId, ProductDtos.MediaRequest request) {
        Product product = products.required(productId);
        if (media.existsByProductIdAndDisplayOrder(productId, request.displayOrder())) throw duplicateOrder();
        if (request.primary()) media.clearPrimary(productId);
        ProductMedia entry = new ProductMedia(product, request.mediaUrl(), request.altText(),
                request.displayOrder(), request.primary());
        try { media.saveAndFlush(entry); }
        catch (DataIntegrityViolationException conflict) { throw mediaConflict(); }
        return ProductService.mediaResponse(entry);
    }

    @Transactional
    public ProductDtos.MediaResponse update(UUID productId, UUID mediaId, ProductDtos.MediaRequest request) {
        ProductMedia entry = ownMedia(productId, mediaId);
        if (media.existsByProductIdAndDisplayOrderAndIdNot(productId, request.displayOrder(), mediaId)) throw duplicateOrder();
        if (request.primary()) media.clearOtherPrimary(productId, mediaId);
        entry.update(request.mediaUrl(), request.altText(), request.displayOrder(), request.primary());
        try { media.flush(); }
        catch (DataIntegrityViolationException conflict) { throw mediaConflict(); }
        return ProductService.mediaResponse(entry);
    }

    @Transactional
    public void delete(UUID productId, UUID mediaId) { media.delete(ownMedia(productId, mediaId)); }

    private ProductMedia ownMedia(UUID productId, UUID mediaId) {
        return media.findByIdAndProductId(mediaId, productId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_MEDIA_NOT_FOUND", "The product media was not found."));
    }
    private static ApiException duplicateOrder() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_MEDIA_ORDER", "The display order is already in use for this product.");
    }
    private static ApiException mediaConflict() {
        return new ApiException(HttpStatus.CONFLICT, "PRODUCT_MEDIA_CONFLICT", "The product media conflicts with existing data.");
    }
}
