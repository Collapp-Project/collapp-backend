package com.collapp.project.integration;

import com.collapp.project.dto.application.ApplicationRequest;
import com.collapp.project.dto.auth.LoginRequest;
import com.collapp.project.dto.auth.RegisterRequest;
import com.collapp.project.dto.offer.OfferRequest;
import com.collapp.project.entity.enums.CompensationType;
import com.collapp.project.entity.enums.Location;
import com.collapp.project.entity.enums.OfferStatus;
import com.collapp.project.entity.enums.Specialty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas end-to-end contra un PostgreSQL real de Testcontainers: validan enrutado,
 * seguridad y serialización, que los tests unitarios con mocks no cubren.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ApiIntegrationTest {

    private static final String AUTHORIZATION = "Authorization";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void el_contexto_usa_el_postgresql_del_contenedor() {
        String version = jdbcTemplate.queryForObject("select version()", String.class);

        assertTrue(version.startsWith("PostgreSQL"),
                "Los tests de integración deben correr contra PostgreSQL real");
    }

    @Test
    void el_registro_y_el_login_devuelven_token() throws Exception {
        String suffix = uniqueSuffix();
        RegisterRequest register = new RegisterRequest(
                "login_" + suffix, "Login Test", "login_" + suffix + "@test.com",
                "password123", Specialty.MAKEUP, null, null, null, null);

        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_CREATIVE"))
                .andReturn();

        assertFalse(readToken(registered).isBlank());

        LoginRequest login = new LoginRequest(register.email(), register.password());
        MvcResult loggedIn = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        assertFalse(readToken(loggedIn).isBlank());
    }

    @Test
    void una_peticion_sin_token_devuelve_401_con_el_contrato_api_error() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/users/me"));
    }

    @Test
    void un_body_invalido_devuelve_400_con_errores_por_campo() throws Exception {
        String invalidBody = """
                {
                  "username": "a",
                  "fullName": "",
                  "email": "no-es-un-email",
                  "password": "123",
                  "specialty": "MAKEUP"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.username").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void un_enum_invalido_devuelve_400() throws Exception {
        TestUser user = registerUser();

        String invalidBody = """
                {
                  "title": "Sesión",
                  "description": "Descripción",
                  "category": "COCINA",
                  "location": "MADRID",
                  "startDate": "2030-01-01",
                  "endDate": "2030-01-03",
                  "compensationType": "PAID",
                  "status": "OPEN"
                }
                """;

        mockMvc.perform(post("/api/offers")
                        .header(AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void un_rango_de_fechas_invertido_devuelve_400_en_form() throws Exception {
        TestUser user = registerUser();
        OfferRequest request = offerRequest(
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/offers")
                        .header(AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields._form").exists());
    }

    @Test
    void el_listado_y_el_detalle_de_ofertas_son_publicos_y_paginados() throws Exception {
        long offerId = createOffer(registerUser());

        mockMvc.perform(get("/api/offers").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/offers/" + offerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Sesión de prueba"))
                .andExpect(jsonPath("$.location").value("MADRID"));
    }

    @Test
    void editar_la_oferta_de_otro_usuario_devuelve_403() throws Exception {
        long offerId = createOffer(registerUser());
        TestUser intruder = registerUser();
        OfferRequest edit = offerRequest(
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(10));

        mockMvc.perform(put("/api/offers/" + offerId)
                        .header(AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void el_listado_de_admin_devuelve_403_a_un_creativo() throws Exception {
        TestUser creative = registerUser();

        mockMvc.perform(get("/api/offers/all").header(AUTHORIZATION, bearer(creative)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void inscribirse_a_la_propia_oferta_devuelve_400() throws Exception {
        TestUser owner = registerUser();
        long offerId = createOffer(owner);
        ApplicationRequest application = new ApplicationRequest(offerId, "Me interesa");

        mockMvc.perform(post("/api/applications")
                        .header(AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(application)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void inscribirse_dos_veces_a_la_misma_oferta_devuelve_409() throws Exception {
        long offerId = createOffer(registerUser());
        TestUser applicant = registerUser();
        ApplicationRequest application = new ApplicationRequest(offerId, "Me interesa");

        mockMvc.perform(post("/api/applications")
                        .header(AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(application)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/applications")
                        .header(AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(application)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void flujo_completo_oferta_candidatura_y_aceptacion() throws Exception {
        TestUser owner = registerUser();
        TestUser applicant = registerUser();
        long offerId = createOffer(owner);

        MvcResult applied = mockMvc.perform(post("/api/applications")
                        .header(AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ApplicationRequest(offerId, "Me interesa el proyecto"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long applicationId = readId(applied);

        mockMvc.perform(get("/api/offers/" + offerId + "/applications")
                        .header(AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].applicantId").isNumber());

        mockMvc.perform(patch("/api/applications/" + applicationId + "/status")
                        .header(AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Al aceptar la candidatura se revela el contacto del creador al candidato.
        mockMvc.perform(get("/api/applications/mine").header(AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].creatorEmail").value(owner.email()));
    }

    // --- Helpers ---

    private TestUser registerUser() throws Exception {
        String suffix = uniqueSuffix();
        String username = "user_" + suffix;
        String email = username + "@test.com";
        RegisterRequest request = new RegisterRequest(
                username, "Test " + suffix, email, "password123",
                Specialty.MAKEUP, null, null, null, null);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return new TestUser(username, email, readToken(result));
    }

    private long createOffer(TestUser creator) throws Exception {
        OfferRequest request = offerRequest(
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(10));

        MvcResult result = mockMvc.perform(post("/api/offers")
                        .header(AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return readId(result);
    }

    private OfferRequest offerRequest(LocalDate start, LocalDate end) {
        return new OfferRequest("Sesión de prueba", "Descripción de prueba",
                Specialty.PHOTOGRAPHY_VIDEO, Location.MADRID, start, end,
                CompensationType.PAID, OfferStatus.OPEN);
    }

    private String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    private String readToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record TestUser(String username, String email, String token) {}
}
