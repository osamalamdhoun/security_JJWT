package os.project.securitydemo2;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import os.project.securitydemo2.product.ProductRepository;
import os.project.securitydemo2.security.JwtProperties;
import os.project.securitydemo2.user.*;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtProperties properties;
    private static final String PRODUCT = """
            {"name":"Keyboard","description":"USB keyboard","price":49.90}
            """;

    @BeforeEach
    void setup() {
        products.deleteAll();
        users.deleteAll();
        for (Role role : Role.values()) {
            AppUser user = new AppUser();
            user.setName(role.name());
            user.setEmail(role.name().toLowerCase(java.util.Locale.ROOT) + "@example.com");
            user.setPasswordHash(encoder.encode("Password123!"));
            user.setRole(role);
            users.save(user);
        }
    }

    @Test
    void publicWorksAndProtectedReturnsJson401() throws Exception {
        mvc.perform(get("/api/public/hello")).andExpect(status().isOk());
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void registrationHashesPasswordNormalizesEmailAndUsesSafeDto() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"Alice@Example.com","password":"Alice12345!"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        var saved = users.findByEmail("alice@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("Alice12345!");
        assertThat(encoder.matches("Alice12345!", saved.getPasswordHash())).isTrue();
        mvc.perform(get("/api/users/me").header("Authorization",
                        "Bearer " + json.readTree(body).get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void cannotRegisterAsAdminAndDuplicateEmailReturns409() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Attacker","email":"attacker@example.com","password":"Password123!","role":"ADMIN"}
                                """)).andExpect(status().isBadRequest());
        assertThat(users.existsByEmail("attacker@example.com")).isFalse();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Duplicate","email":"USER@EXAMPLE.COM","password":"Password123!"}
                                """)).andExpect(status().isConflict());
    }

    @Test
    void invalidFieldsMalformedJsonAndOverlongUtf8PasswordReturn400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"bad","password":"short"}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Unicode",
                                "email", "unicode@example.com", "password", "é".repeat(40)))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsUsableJwtAndWrongOrUnknownCredentialsReturnSame401() throws Exception {
        String token = login("USER@EXAMPLE.COM");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities", hasItem("ROLE_USER")))
                .andExpect(jsonPath("$.authorities", hasItem("product:read")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        for (String email : new String[]{"user@example.com", "unknown@example.com"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email, "password", "Wrong12345!"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }
    }

    @Test
    void malformedExpiredWrongIssuerUnsignedMissingExpiryAndWrongSignatureReturn401() throws Exception {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
        Instant now = Instant.now();
        String expired = Jwts.builder().subject("user@example.com").issuer(properties.getIssuer())
                .issuedAt(Date.from(now.minusSeconds(120))).expiration(Date.from(now.minusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256).compact();
        String wrongIssuer = Jwts.builder().subject("user@example.com").issuer("wrong")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256).compact();
        String missingExpiry = Jwts.builder().subject("user@example.com").issuer(properties.getIssuer())
                .issuedAt(Date.from(now)).signWith(key, Jwts.SIG.HS256).compact();
        String unsigned = Jwts.builder().subject("user@example.com").compact();
        String wrongSignature = Jwts.builder().subject("user@example.com").issuer(properties.getIssuer())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(Jwts.SIG.HS256.key().build()).compact();
        String wrongAlgorithm = Jwts.builder().subject("user@example.com").issuer(properties.getIssuer())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(key, Jwts.SIG.HS384).compact();
        for (String token : new String[]{"garbage", expired, wrongIssuer, missingExpiry,
                unsigned, wrongSignature, wrongAlgorithm}) {
            mvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void deletedUserCannotUseOldToken() throws Exception {
        String token = login("user@example.com");
        users.delete(users.findByEmail("user@example.com").orElseThrow());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanReadButCannotWriteDeleteOrManageUsers() throws Exception {
        String token = login("user@example.com");
        mvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        assertCannotWrite(token);
        mvc.perform(put("/api/products/1").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT))
                .andExpect(status().isForbidden());
        assertCannotAdmin(token);
    }

    @Test
    void managerCanCreateAndUpdateButCannotDeleteOrManageUsers() throws Exception {
        String token = login("manager@example.com");
        long id = createProduct(token);
        mvc.perform(put("/api/products/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Updated keyboard","description":"Wireless","price":59.90}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated keyboard"));
        mvc.perform(get("/api/products/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(59.90));
        assertCannotAdmin(token);
    }

    @Test
    void adminCanDeleteAndMissingProductReturns404() throws Exception {
        String token = login("admin@example.com");
        long id = createProduct(token);
        mvc.perform(delete("/api/products/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/products/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void roleChangesApplyToExistingTokensOnNextRequest() throws Exception {
        String admin = login("admin@example.com");
        String user = login("user@example.com");
        long id = users.findByEmail("user@example.com").orElseThrow().getId();
        changeRole(admin, id, "MANAGER");
        createProduct(user);
        changeRole(admin, id, "USER");
        assertCannotWrite(user);
    }

    @Test
    void badProductPaginationAndRoleReturn400AndMissingUserReturns404() throws Exception {
        String token = login("admin@example.com");
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"","description":"","price":-1}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.price").exists());
        for (String path : new String[]{"/api/products?page=-1", "/api/products?size=101",
                "/api/users?size=0", "/api/products/not-a-number"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/users/1/role").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/users/999999/role").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiDoesNotCreateSessionOrAcceptBasicAuthentication() throws Exception {
        String token = login("user@example.com");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(cookie().doesNotExist("JSESSIONID"));
        mvc.perform(get("/api/users/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    private void assertCannotWrite(String token) throws Exception {
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT))
                .andExpect(status().isForbidden());
    }

    private void assertCannotAdmin(String token) throws Exception {
        mvc.perform(delete("/api/products/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/users/1/role").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    private void changeRole(String token, long id, String role) throws Exception {
        mvc.perform(patch("/api/users/" + id + "/role").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("role", role))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Password123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private long createProduct(String token) throws Exception {
        String body = mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}

