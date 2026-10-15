package com.ecommerce.productservice.service;

import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> getAll() {
        return productRepository.findAll();
    }

    public Product getById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    public Product create(Product product) {
        return productRepository.save(product);
    }

    public List<Product> search(String keyword) {
        return productRepository.findByNameContainingIgnoreCase(keyword);
    }

    // Called by Order Service (via REST) to check/decrement stock before order confirmation
    public boolean decrementStock(String productId, int quantity) {
        Product product = getById(productId);
        if (product.getStockQuantity() < quantity) return false;
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);
        return true;
    }
}
