package com.ecommerce.productservice.service;

import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product();
        sampleProduct.setId("prod-101");
        sampleProduct.setName("Wireless Noise-Cancelling Headphones");
        sampleProduct.setDescription("Premium over-ear headphones with active noise cancellation");
        sampleProduct.setPrice(new BigDecimal("199.99"));
        sampleProduct.setStockQuantity(25);
        sampleProduct.setCategory("Electronics");
    }

    @Test
    @DisplayName("getAll returns list of all products from repository")
    void testGetAll() {
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct));

        List<Product> products = productService.getAll();

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getName()).isEqualTo("Wireless Noise-Cancelling Headphones");
        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getById returns product when found in repository")
    void testGetById_Found() {
        when(productRepository.findById("prod-101")).thenReturn(Optional.of(sampleProduct));

        Product product = productService.getById("prod-101");

        assertThat(product).isNotNull();
        assertThat(product.getId()).isEqualTo("prod-101");
        assertThat(product.getPrice()).isEqualByComparingTo("199.99");
        verify(productRepository, times(1)).findById("prod-101");
    }

    @Test
    @DisplayName("getById throws IllegalArgumentException when product not found")
    void testGetById_NotFound() {
        when(productRepository.findById("prod-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById("prod-999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found: prod-999");

        verify(productRepository, times(1)).findById("prod-999");
    }

    @Test
    @DisplayName("create saves and returns the new product")
    void testCreate() {
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        Product created = productService.create(sampleProduct);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo("prod-101");
        verify(productRepository, times(1)).save(sampleProduct);
    }

    @Test
    @DisplayName("search calls repository findByNameContainingIgnoreCase")
    void testSearch() {
        when(productRepository.findByNameContainingIgnoreCase("Headphones"))
                .thenReturn(List.of(sampleProduct));

        List<Product> results = productService.search("Headphones");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).contains("Headphones");
        verify(productRepository, times(1)).findByNameContainingIgnoreCase("Headphones");
    }

    @Test
    @DisplayName("decrementStock succeeds and decrements quantity when stock is sufficient")
    void testDecrementStock_SufficientStock() {
        when(productRepository.findById("prod-101")).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        boolean success = productService.decrementStock("prod-101", 5);

        assertThat(success).isTrue();
        assertThat(sampleProduct.getStockQuantity()).isEqualTo(20);
        verify(productRepository, times(1)).save(sampleProduct);
    }

    @Test
    @DisplayName("decrementStock returns false and does not update when stock is insufficient")
    void testDecrementStock_InsufficientStock() {
        when(productRepository.findById("prod-101")).thenReturn(Optional.of(sampleProduct));

        boolean success = productService.decrementStock("prod-101", 50);

        assertThat(success).isFalse();
        assertThat(sampleProduct.getStockQuantity()).isEqualTo(25);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("decrementStock throws exception when product does not exist")
    void testDecrementStock_ProductNotFound() {
        when(productRepository.findById("prod-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.decrementStock("prod-unknown", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");

        verify(productRepository, never()).save(any(Product.class));
    }
}
