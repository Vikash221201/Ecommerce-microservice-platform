package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.service.ProductService;
import com.ecommerce.productservice.security.JwtValidator;
import com.ecommerce.productservice.security.SecurityFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProductController.class)
@Import({SecurityFilter.class, JwtValidator.class})
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product();
        sampleProduct.setId("prod-101");
        sampleProduct.setName("Wireless Noise-Cancelling Headphones");
        sampleProduct.setDescription("Premium over-ear headphones");
        sampleProduct.setPrice(new BigDecimal("199.99"));
        sampleProduct.setStockQuantity(25);
        sampleProduct.setCategory("Electronics");
    }

    @Test
    @DisplayName("GET /api/products returns HTTP 200 with product list")
    void testGetAllProducts() throws Exception {
        when(productService.getAll()).thenReturn(List.of(sampleProduct));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("prod-101"))
                .andExpect(jsonPath("$[0].name").value("Wireless Noise-Cancelling Headphones"))
                .andExpect(jsonPath("$[0].price").value(199.99));
    }

    @Test
    @DisplayName("GET /api/products/{id} returns HTTP 200 with product details")
    void testGetProductById() throws Exception {
        when(productService.getById("prod-101")).thenReturn(sampleProduct);

        mockMvc.perform(get("/api/products/prod-101"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("prod-101"))
                .andExpect(jsonPath("$.name").value("Wireless Noise-Cancelling Headphones"))
                .andExpect(jsonPath("$.stockQuantity").value(25));
    }

    @Test
    @DisplayName("GET /api/products/search?q=... returns matching products")
    void testSearchProducts() throws Exception {
        when(productService.search("Headphones")).thenReturn(List.of(sampleProduct));

        mockMvc.perform(get("/api/products/search").param("q", "Headphones"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("prod-101"))
                .andExpect(jsonPath("$[0].name").value("Wireless Noise-Cancelling Headphones"));
    }

    @Test
    @DisplayName("PUT /api/products/{id}/decrement-stock returns true when stock decremented")
    void testDecrementStock_Success() throws Exception {
        when(productService.decrementStock(eq("prod-101"), eq(2))).thenReturn(true);

        mockMvc.perform(put("/api/products/prod-101/decrement-stock").param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("PUT /api/products/{id}/decrement-stock returns false when stock insufficient")
    void testDecrementStock_Insufficient() throws Exception {
        when(productService.decrementStock(eq("prod-101"), eq(100))).thenReturn(false);

        mockMvc.perform(put("/api/products/prod-101/decrement-stock").param("quantity", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
}
