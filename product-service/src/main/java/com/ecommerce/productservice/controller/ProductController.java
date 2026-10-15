package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.security.UserContext;
import com.ecommerce.productservice.security.UserContextHolder;
import com.ecommerce.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<Product> getAll() {
        return productService.getAll();
    }

    @GetMapping("/{id}")
    public Product getById(@PathVariable String id) {
        return productService.getById(id);
    }

    @GetMapping("/search")
    public List<Product> search(@RequestParam String q) {
        return productService.search(q);
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to create products");
        }
        if (!ctx.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: ADMIN role required");
        }
        return ResponseEntity.ok(productService.create(product));
    }

    @PutMapping("/{id}/decrement-stock")
    public ResponseEntity<Boolean> decrementStock(@PathVariable String id, @RequestParam int quantity) {
        return ResponseEntity.ok(productService.decrementStock(id, quantity));
    }
}
