package org.example.orderservice.models.services.impl;

import org.example.orderservice.clients.ProductClient;
import org.example.orderservice.exceptions.ProductServiceException;
import org.example.orderservice.models.dto.responses.ProductResponse;
import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductGatewayService {

    private final ProductClient productClient;

    @CircuitBreaker(name = "productService", fallbackMethod = "fallbackGetProduct")
    public ProductResponse getProductById(Long productId) {
        return productClient.getProductById(productId);
    }

    public ProductResponse fallbackGetProduct(Long productId, Throwable throwable) {
        throw new ProductServiceException("Product service is unavailable or unreachable for product id: " + productId, throwable);
    }
}
