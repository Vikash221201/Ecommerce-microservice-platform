package com.ecommerce.productservice;

import com.ecommerce.productservice.controller.ProductController;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.security.JwtValidator;
import com.ecommerce.productservice.security.SecurityFilter;
import com.ecommerce.productservice.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
@Import({SecurityFilter.class, JwtValidator.class})
@AutoConfigureMockMvc(addFilters = true)
class ProductSecurityTest {

    private static final String SECRET = "change-this-super-secret-key-min-32-chars-long";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private String generateToken(String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .subject("user@example.com")
                .claim("userId", UUID.randomUUID().toString())
                .claim("email", "user@example.com")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    void publicCatalogueReadAllowedWithoutAuth() throws Exception {
        when(productService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    void postProductWithoutAuthReturns401() throws Exception {
        Product p = new Product();
        p.setName("Tablet");
        p.setPrice(new BigDecimal("299.99"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postProductWithCustomerTokenReturns403() throws Exception {
        String token = generateToken("ROLE_CUSTOMER");

        Product p = new Product();
        p.setName("Tablet");
        p.setPrice(new BigDecimal("299.99"));

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postProductWithAdminTokenSucceeds() throws Exception {
        String token = generateToken("ROLE_ADMIN");

        Product p = new Product();
        p.setId("prod-1");
        p.setName("Tablet");
        p.setPrice(new BigDecimal("299.99"));

        when(productService.create(any(Product.class))).thenReturn(p);

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("prod-1"))
                .andExpect(jsonPath("$.name").value("Tablet"));
    }

    @Test
    void postProductWithGatewayAdminHeaderSucceeds() throws Exception {
        Product p = new Product();
        p.setId("prod-2");
        p.setName("Laptop");
        p.setPrice(new BigDecimal("1299.99"));

        when(productService.create(any(Product.class))).thenReturn(p);

        mockMvc.perform(post("/api/products")
                        .header("X-User-Id", "admin-id")
                        .header("X-User-Email", "admin@store.com")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("prod-2"));
    }
}
