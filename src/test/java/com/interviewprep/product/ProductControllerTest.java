package com.interviewprep.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import com.interviewprep.common.config.ClockConfig;
import com.interviewprep.common.exception.ResourceNotFoundException;
import com.interviewprep.common.security.SecurityConfig;
import com.interviewprep.product.dto.ProductResponse;
import com.interviewprep.product.dto.UpdateProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Web layer: HTTP mapping, validation and access rules (real SecurityConfig). The service is mocked.
 */
@Import({SecurityConfig.class, ClockConfig.class})
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final String VALID_UPDATE = """
            {"name": "Desk Lamp", "category": "Home", "price": 19.99, "stock": 5, "rating": 4.2}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void getProductIsPublic() throws Exception {
        when(productService.findById(1L)).thenReturn(new ProductResponse(
                1L, "Desk Lamp", "Home", new BigDecimal("19.99"), 5, new BigDecimal("4.2"), Instant.EPOCH));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Desk Lamp"))
                .andExpect(jsonPath("$.price").value(19.99));
    }

    @Test
    void getUnknownProductReturns404() throws Exception {
        when(productService.findById(99L)).thenThrow(new ResourceNotFoundException("Product 99 not found"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product 99 not found"));
    }

    @Test
    void anonymousCannotUpdateOrDelete() throws Exception {
        mockMvc.perform(put("/api/products/1").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(productService);
    }

    @Test
    void userCannotUpdateOrDelete() throws Exception {
        mockMvc.perform(put("/api/products/1").with(withRole("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/products/1").with(withRole("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productService);
    }

    @Test
    void adminCanUpdateAndDelete() throws Exception {
        when(productService.update(eq(1L), any(UpdateProductRequest.class))).thenReturn(new ProductResponse(
                1L, "Desk Lamp", "Home", new BigDecimal("19.99"), 5, new BigDecimal("4.2"), Instant.EPOCH));

        mockMvc.perform(put("/api/products/1").with(withRole("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Desk Lamp"));
        mockMvc.perform(delete("/api/products/1").with(withRole("ADMIN")))
                .andExpect(status().isNoContent());
        verify(productService).delete(1L);
    }

    @Test
    void updateValidatesEveryField() throws Exception {
        mockMvc.perform(put("/api/products/1").with(withRole("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "category": "", "price": -1, "stock": -5, "rating": 5.5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(5));
        verifyNoInteractions(productService);
    }

    private static RequestPostProcessor withRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
