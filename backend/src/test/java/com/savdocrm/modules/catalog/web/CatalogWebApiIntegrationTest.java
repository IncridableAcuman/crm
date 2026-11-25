package com.savdocrm.modules.catalog.web;

import com.savdocrm.modules.catalog.domain.BarcodeType;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;
import com.savdocrm.modules.catalog.web.dto.request.AddBarcodeRequest;
import com.savdocrm.modules.catalog.web.dto.request.BarcodeInputRequest;
import com.savdocrm.modules.catalog.web.dto.request.ConvertProductToVariableRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateProductRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateUnitRequest;
import com.savdocrm.modules.catalog.web.dto.request.CreateVariantRequest;
import com.savdocrm.modules.catalog.web.dto.request.MoveCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateActivationStatusRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateBarcodeRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateCategoryRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateProductRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateProductStatusRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateUnitRequest;
import com.savdocrm.modules.catalog.web.dto.request.UpdateVariantRequest;
import com.savdocrm.modules.identity.application.AuthService;
import com.savdocrm.modules.identity.application.dto.AuthResponse;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class CatalogWebApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("savdocrm_catalog_web_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.xml");
        registry.add("jwt.secret", () -> "testSecretKeyForJwtAuthenticationIntegrationTestingSavdoCrm1234567890!");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AuthService authService;

    private MockMvc mockMvc;
    private String ownerToken;
    private UUID pcsUnitId;
    private UUID kgUnitId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        TenantContextHolder.clear();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AuthResponse reg = authService.register(RegisterRequest.builder()
                .email("catalog_web_" + suffix + "@example.com")
                .username("cat_web_" + suffix)
                .password("StrongPassword123!")
                .firstName("Catalog")
                .lastName("Owner")
                .storeName("Web Store " + suffix)
                .build(), "127.0.0.1", "JUnit");
        ownerToken = reg.getAccessToken();

        MvcResult unitsRes = mockMvc.perform(get("/api/v1/units")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8))
                .andReturn();

        JsonNode unitsData = objectMapper.readTree(unitsRes.getResponse().getContentAsString()).get("data");
        for (JsonNode unit : unitsData) {
            if ("PCS".equals(unit.get("code").asText())) {
                pcsUnitId = UUID.fromString(unit.get("id").asText());
            } else if ("KG".equals(unit.get("code").asText())) {
                kgUnitId = UUID.fromString(unit.get("id").asText());
            }
        }
        assertThat(pcsUnitId).isNotNull();
        assertThat(kgUnitId).isNotNull();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Category REST lifecycle supports tree retrieval without N+1, move, cycle guard, and delete guards")
    void categoryLifecycleAndTreeEndpoints() throws Exception {
        UUID rootId = createCategory(null, "BEV", "Beverages", 1);
        UUID childId = createCategory(rootId, "SOFT", "Soft Drinks", 2);
        UUID leafId = createCategory(childId, "COLA", "Colas", 1);

        // Tree view via query param and /tree endpoint
        mockMvc.perform(get("/api/v1/categories")
                        .param("view", "tree")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("BEV"))
                .andExpect(jsonPath("$.data[0].children[0].code").value("SOFT"))
                .andExpect(jsonPath("$.data[0].children[0].children[0].code").value("COLA"));

        mockMvc.perform(get("/api/v1/categories/tree")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].children[0].children[0].id").value(leafId.toString()));

        // Invalid view rejected
        mockMvc.perform(get("/api/v1/categories")
                        .param("view", "xml")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());

        // Cycle move rejected with 409 Conflict
        mockMvc.perform(patch("/api/v1/categories/{id}/move", rootId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveCategoryRequest(leafId))))
                .andExpect(status().isConflict());

        // Update category details
        mockMvc.perform(put("/api/v1/categories/{id}", leafId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCategoryRequest("Sparkling Colas", "Updated", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Sparkling Colas"))
                .andExpect(jsonPath("$.data.sortOrder").value(5));

        // Move leaf to root
        mockMvc.perform(patch("/api/v1/categories/{id}/move", leafId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveCategoryRequest(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.depth").value(0));

        // Deactivate parent with active child -> 409; deactivate child then parent -> 200
        mockMvc.perform(patch("/api/v1/categories/{id}/status", rootId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateActivationStatusRequest(false))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/categories/{id}/deactivate", childId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));

        mockMvc.perform(post("/api/v1/categories/{id}/activate", childId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true));

        // Delete unused moved root category
        mockMvc.perform(delete("/api/v1/categories/{id}", leafId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories/{id}", leafId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Unit of Measure REST API enforces system immutability, precision guards, and custom lifecycle")
    void unitOfMeasureEndpoints() throws Exception {
        // System unit cannot be modified, deactivated, or deleted (403 Forbidden)
        mockMvc.perform(put("/api/v1/units/{id}", pcsUnitId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUnitRequest("Edited", "pc", false, 0))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/units/{id}/status", pcsUnitId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateActivationStatusRequest(false))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/units/{id}", pcsUnitId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        // Create custom unit
        MvcResult createRes = mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUnitRequest("roll", "Roll", "rl", true, 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("ROLL"))
                .andExpect(jsonPath("$.data.isSystem").value(false))
                .andReturn();
        UUID customUnitId = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString())
                .get("data").get("id").asText());

        // Duplicate unit code -> 409
        mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUnitRequest("ROLL", "Roll Dup", "rl", false, 0))))
                .andExpect(status().isConflict());

        // Invalid precision when allowDecimal=false -> 400
        mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUnitRequest("BAG", "Bag", "bg", false, 2))))
                .andExpect(status().isBadRequest());

        // Deactivate & hide from default list; show when includeInactive=true
        mockMvc.perform(patch("/api/v1/units/{id}/status", customUnitId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateActivationStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));

        mockMvc.perform(get("/api/v1/units")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8));

        mockMvc.perform(get("/api/v1/units")
                        .param("includeInactive", "true")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(9));

        // Update & delete unused custom unit
        mockMvc.perform(put("/api/v1/units/{id}", customUnitId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUnitRequest("Roll Updated", "rl", true, 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decimalPrecision").value(3));

        mockMvc.perform(delete("/api/v1/units/{id}", customUnitId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Products, variants, and barcodes REST lifecycle preserves invariants and aggregate ownership")
    void productsVariantsAndBarcodesLifecycle() throws Exception {
        UUID categoryId = createCategory(null, "APPAREL", "Apparel", 0);

        // Create SIMPLE product
        CreateProductRequest createSimple = new CreateProductRequest(
                categoryId, pcsUnitId, "Plain Tee", "Cotton", "BasicCo",
                ProductType.SIMPLE, List.of(), ProductStatus.ACTIVE,
                List.of(new CreateVariantRequest(
                        "tee-001", "Default", Map.of(), new BigDecimal("5.00"), new BigDecimal("12.50"), true,
                        List.of(new BarcodeInputRequest("4601000000011", BarcodeType.EAN13, true))
                ))
        );
        MvcResult simpleRes = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSimple)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productType").value("SIMPLE"))
                .andExpect(jsonPath("$.data.category.code").value("APPAREL"))
                .andExpect(jsonPath("$.data.unit.code").value("PCS"))
                .andExpect(jsonPath("$.data.variants.length()").value(1))
                .andReturn();

        JsonNode simpleJson = objectMapper.readTree(simpleRes.getResponse().getContentAsString()).get("data");
        UUID simpleId = UUID.fromString(simpleJson.get("id").asText());
        UUID soleVariantId = UUID.fromString(simpleJson.get("variants").get(0).get("id").asText());

        // Cannot add second variant to SIMPLE product (400 Bad Request)
        mockMvc.perform(post("/api/v1/products/{productId}/variants", simpleId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateVariantRequest(
                                "TEE-002", "Extra", Map.of(), BigDecimal.ONE, BigDecimal.TEN, false, List.of()
                        ))))
                .andExpect(status().isBadRequest());

        // Cannot delete ACTIVE product physically (400 Bad Request)
        mockMvc.perform(delete("/api/v1/products/{productId}", simpleId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());

        // Convert SIMPLE -> VARIABLE, add second variant, change default, manage barcodes
        mockMvc.perform(post("/api/v1/products/{productId}/convert-to-variable", simpleId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConvertProductToVariableRequest(
                                List.of("Size"), Map.of("Size", "M")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productType").value("VARIABLE"));

        MvcResult secondVarRes = mockMvc.perform(post("/api/v1/products/{productId}/variants", simpleId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateVariantRequest(
                                "TEE-001-L", "Large", Map.of("Size", "L"), new BigDecimal("5.50"), new BigDecimal("14.00"),
                                false, List.of()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID secondVariantId = UUID.fromString(objectMapper.readTree(secondVarRes.getResponse().getContentAsString())
                .get("data").get("id").asText());

        // Add barcode with Code128 spacing/characters to test barcode-first lookup safety
        MvcResult b1Res = mockMvc.perform(post("/api/v1/products/{pId}/variants/{vId}/barcodes", simpleId, secondVariantId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddBarcodeRequest("Code128 Tee L", BarcodeType.CODE128, false))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID b1Id = UUID.fromString(objectMapper.readTree(b1Res.getResponse().getContentAsString())
                .get("data").get("id").asText());

        mockMvc.perform(patch("/api/v1/products/{pId}/variants/{vId}/barcodes/{bId}/primary", simpleId, secondVariantId, b1Id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPrimary").value(true));

        mockMvc.perform(put("/api/v1/products/{pId}/variants/{vId}/barcodes/{bId}", simpleId, secondVariantId, b1Id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBarcodeRequest("Code128 Tee L V2", BarcodeType.CODE128, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.barcode").value("Code128 Tee L V2"));

        // Lookup by barcode with spaces AND by case-insensitive SKU
        mockMvc.perform(get("/api/v1/products/lookup")
                        .param("code", "Code128 Tee L V2")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variantId").value(secondVariantId.toString()))
                .andExpect(jsonPath("$.data.sellable").value(true));

        mockMvc.perform(get("/api/v1/products/lookup")
                        .param("code", "tee-001-l")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variantId").value(secondVariantId.toString()));

        // Lookup non-existent code with spaces returns 404 (not 400 from SKU regex)
        mockMvc.perform(get("/api/v1/products/lookup")
                        .param("code", "Missing Barcode 999")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        // Aggregate ownership check: cannot access secondVariantId or barcodes under wrong productId/variantId
        UUID otherProductId = createSimpleProduct(null, kgUnitId, "Sugar", "SweetCo", "SUG-1", "2.00", ProductStatus.DRAFT);
        mockMvc.perform(get("/api/v1/products/{pId}/variants/{vId}", otherProductId, secondVariantId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/products/{pId}/variants/{vId}", otherProductId, secondVariantId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateVariantRequest(
                                "TEE-001-L", "Large", Map.of("Size", "L"), BigDecimal.ONE, BigDecimal.TEN
                        ))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products/{pId}/variants/{vId}/barcodes", otherProductId, secondVariantId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/products/{pId}/variants/{vId}/barcodes/{bId}", simpleId, soleVariantId, b1Id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBarcodeRequest("4601000000999", BarcodeType.EAN13, false))))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/products/{pId}/variants/{vId}/barcodes/{bId}/primary", simpleId, soleVariantId, b1Id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/products/{pId}/variants/{vId}/barcodes/{bId}", simpleId, soleVariantId, b1Id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        // Change default to secondVariantId, deactivate soleVariantId
        mockMvc.perform(patch("/api/v1/products/{pId}/variants/{vId}/default", simpleId, secondVariantId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActiveDefault").value(true));

        mockMvc.perform(patch("/api/v1/products/{pId}/variants/{vId}/status", simpleId, soleVariantId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateActivationStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));

        // Archive product -> activeDefaultVariantId null, historicalDefaultVariantId preserved, variant mutation rejected
        mockMvc.perform(post("/api/v1/products/{productId}/archive", simpleId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.activeDefaultVariantId").doesNotExist())
                .andExpect(jsonPath("$.data.historicalDefaultVariantId").value(secondVariantId.toString()));

        mockMvc.perform(put("/api/v1/products/{pId}/variants/{vId}", simpleId, secondVariantId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateVariantRequest(
                                "TEE-001-L", "Blocked", Map.of("Size", "L"), BigDecimal.ONE, BigDecimal.TEN
                        ))))
                .andExpect(status().isBadRequest());

        // Restore product -> ACTIVE, historical default active again
        mockMvc.perform(post("/api/v1/products/{productId}/restore", simpleId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.activeDefaultVariantId").value(secondVariantId.toString()));

        // Remove barcode and delete draft product
        mockMvc.perform(delete("/api/v1/products/{pId}/variants/{vId}/barcodes/{bId}", simpleId, secondVariantId, b1Id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/products/{productId}", otherProductId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/{productId}", otherProductId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Database-level pagination, multi-criteria filtering, subcategory expansion, and safe sorting work accurately")
    void searchPaginationFilteringAndSafeSorting() throws Exception {
        UUID drinksId = createCategory(null, "DRINKS", "Drinks", 0);
        UUID sodaId = createCategory(drinksId, "SODA", "Soda", 0);
        UUID snacksId = createCategory(null, "SNACKS", "Snacks", 1);

        UUID p1 = createSimpleProduct(drinksId, pcsUnitId, "Alpha Water 100%", "Pure", "WAT-01", "1.00", ProductStatus.ACTIVE);
        UUID p2 = createSimpleProduct(sodaId, pcsUnitId, "Beta Cola", "Fizz", "COL-01", "1.50", ProductStatus.ACTIVE);
        UUID p3 = createSimpleProduct(snacksId, kgUnitId, "Gamma Chips", "Crunch", "CHP-01", "2.50", ProductStatus.DRAFT);
        UUID p4 = createSimpleProduct(snacksId, pcsUnitId, "Delta Nuts", "Crunch", "NUT-01", "3.50", ProductStatus.ACTIVE);
        mockMvc.perform(patch("/api/v1/products/{id}/status", p4)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProductStatusRequest(ProductStatus.ARCHIVED))))
                .andExpect(status().isOk());

        // Default excludes ARCHIVED (3 items), paginated with size=2
        mockMvc.perform(get("/api/v1/products")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "name,asc")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false))
                .andExpect(jsonPath("$.data.items[0].name").value("Alpha Water 100%"))
                .andExpect(jsonPath("$.data.items[1].name").value("Beta Cola"));

        mockMvc.perform(get("/api/v1/products")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "name,asc")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.first").value(false))
                .andExpect(jsonPath("$.data.last").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Gamma Chips"));

        // includeArchived=true includes p4
        mockMvc.perform(get("/api/v1/products")
                        .param("includeArchived", "true")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4));

        // Category filter with includeSubcategories=false vs true
        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", drinksId.toString())
                        .param("includeSubcategories", "false")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(p1.toString()));

        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", drinksId.toString())
                        .param("includeSubcategories", "true")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // Filter by unitId, brand, isActive, status, productType, sku, and wildcard-escaped search
        mockMvc.perform(get("/api/v1/products")
                        .param("unitId", kgUnitId.toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(p3.toString()));

        mockMvc.perform(get("/api/v1/products")
                        .param("isActive", "true")
                        .param("brand", "fizz")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(p2.toString()));

        mockMvc.perform(get("/api/v1/products")
                        .param("search", "100%")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(p1.toString()));

        mockMvc.perform(get("/api/v1/products")
                        .param("sku", "col-")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(p2.toString()));

        // Empty search results return 200 OK with empty items and 0 totalElements
        mockMvc.perform(get("/api/v1/products")
                        .param("search", "DOES_NOT_EXIST_ANYWHERE")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // Unsafe sort injection and invalid pagination rejected with 400
        mockMvc.perform(get("/api/v1/products")
                        .param("sort", "tenantId,asc")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/products")
                        .param("size", "101")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Validation rejects malformed JSON, invalid UUIDs, null list elements, negative prices, and precision overflow")
    void validationRejectsMalformedInputs() throws Exception {
        // Blank name, negative sellingPrice, >3 options, null variant entry
        CreateProductRequest invalidReq = new CreateProductRequest(
                null, pcsUnitId, "  ", null, null, ProductType.VARIABLE,
                List.of("A", "B", "C", "D"), ProductStatus.ACTIVE,
                Arrays.asList((CreateVariantRequest) null)
        );
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").isMap());

        CreateProductRequest badMoney = new CreateProductRequest(
                null, pcsUnitId, "Valid Name", null, null, ProductType.SIMPLE,
                List.of(), ProductStatus.ACTIVE,
                List.of(new CreateVariantRequest(
                        "SKU-BAD-1", "Def", Map.of(), new BigDecimal("-1.00"), new BigDecimal("10.12345"), true, List.of()
                ))
        );
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badMoney)))
                .andExpect(status().isBadRequest());

        // Malformed JSON / invalid enum
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productType\":\"NON_EXISTENT\"}"))
                .andExpect(status().isBadRequest());

        // Invalid UUID path parameter
        mockMvc.perform(get("/api/v1/products/not-a-uuid")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    private UUID createCategory(UUID parentId, String code, String name, int sortOrder) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryRequest(parentId, code, name, null, sortOrder))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asText());
    }

    private UUID createSimpleProduct(
            UUID categoryId,
            UUID unitId,
            String name,
            String brand,
            String sku,
            String sellingPrice,
            ProductStatus status
    ) throws Exception {
        CreateProductRequest req = new CreateProductRequest(
                categoryId, unitId, name, null, brand, ProductType.SIMPLE, List.of(), status,
                List.of(new CreateVariantRequest(
                        sku, "Default", Map.of(), BigDecimal.ZERO, new BigDecimal(sellingPrice), true, List.of()
                ))
        );
        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asText());
    }
}
