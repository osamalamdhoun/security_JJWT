# Spring Boot security example: JJWT, roles and authorities

A small product catalog API for learning **Spring Security + JJWT**, without OAuth2.
It uses Java 21, Maven, Spring Boot 4.1.2, Spring Data JPA, PostgreSQL and Lombok.

Start here: create the PostgreSQL database, edit `application.properties`, run the Java application, then import the Postman collection. No Docker, PowerShell scripts, environment-variable setup or external identity provider is needed.

## 1. Understand the idea

**Authentication** asks: "Who are you?" Login checks your email and password.

**Authorization** asks: "What are you allowed to do?" A logged-in USER can read products, but cannot delete them.

**JWT** means JSON Web Token. **JJWT** is the Java library that creates and verifies these tokens. After login, the server gives Postman a signed token. Postman sends it with later requests:

```http
Authorization: Bearer eyJ...
```

The token is signed using HS256. Its signature lets the server detect changes. A signed JWT is readable, not encrypted. This example puts only the email (`sub`), issuer (`iss`), issue time (`iat`) and expiration (`exp`) in it. Never put a password in a token.

This is a custom email/password login API. It does **not** implement OAuth2, OpenID Connect, Google login, authorization codes or refresh tokens.

### Role versus authority

A **role** is a group of permissions, such as MANAGER.
An **authority** is a specific permission, such as `product:write`.

| Role stored in PostgreSQL | Authorities given to Spring Security | Can do |
| --- | --- | --- |
| USER | `ROLE_USER`, `product:read` | Read products and own profile |
| MANAGER | `ROLE_MANAGER`, `product:read`, `product:write` | Read, create and update products |
| ADMIN | `ROLE_ADMIN`, `product:read`, `product:write`, `product:delete` | All product actions, list users and change roles |

Role-to-permission mapping is in `user/Role.java`. Each user has one role stored as text in `app_users.role`. Permissions are defined in Java; there are no separate role/permission tables in this small example.

```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAuthority('product:write')")
```

`hasRole("ADMIN")` checks for `ROLE_ADMIN`; Spring adds the prefix.
`hasAuthority("product:write")` checks that exact string.
Roles are also represented as authorities internally. ADMIN does not automatically inherit ROLE_USER: this project explicitly grants the needed product permissions to each role. See the [Spring Security method-security reference](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).

### What happens during a request?

```text
Login:
Postman -> AuthController -> AuthenticationManager
        -> DatabaseUserDetailsService -> PostgreSQL
        -> BCrypt checks the password -> JwtService returns a signed JWT

Protected request:
Postman + Bearer token -> JwtAuthenticationFilter
        -> JJWT validates signature, HS256, issuer and expiration
        -> load current user and authorities from PostgreSQL
        -> put authentication in Spring Security's SecurityContext
        -> @PreAuthorize checks role/authority
        -> Controller -> Service -> Repository -> PostgreSQL
        -> Mapper -> response DTO -> JSON
```

There is no login session and no JSESSIONID cookie. Each request needs its token.
Stateless authentication does not mean "no database queries": this example deliberately reads the user on every authenticated request. A role change takes effect with the **same token on the next request**. Deleting a user also makes their tokens unusable.

## 2. DTO, JPA entity, repository, service and Lombok

| Part | Purpose | Example |
| --- | --- | --- |
| Request DTO | Fields the caller is allowed to send; validates input | `RegisterRequest`, `ProductRequest` |
| JPA entity | Object mapped to a database table | `AppUser` -> `app_users`, `Product` -> `products` |
| Repository | Reads and writes entities using Spring Data JPA | `UserRepository`, `ProductRepository` |
| Service | Implements business rules and transaction boundaries | `AuthService`, `ProductService` |
| Mapper | Copies selected fields between entities and DTOs | `UserMapper`, `ProductMapper` |
| Response DTO | Safe JSON returned to the caller | `UserResponse`, `ProductResponse` |
| Controller | Defines REST URLs, validation and permissions | `ProductController` |

JPA entities and DTOs are different objects. For example, `AppUser` contains a password hash; `UserResponse` has no password field at all. Controllers never return entities directly.

DTOs use Java records, which provide an immutable data structure with accessors and a constructor. Lombok reduces boilerplate elsewhere:
- `@Getter` / `@Setter` on entities and configuration classes.
- `@NoArgsConstructor` supplies the empty constructor JPA requires.
- `@RequiredArgsConstructor` supplies constructors for Spring dependency injection.

Passwords are stored as **BCrypt hashes**, not plain text and not reversible encryption.

## 3. Create PostgreSQL locally with pgAdmin

Install Java **21**, Maven, PostgreSQL and pgAdmin. PostgreSQL must be running.

1. Open pgAdmin and connect to your local PostgreSQL server.
2. Right-click **Databases -> Create -> Database**.
3. Enter `security_demo` as the database name and save.
4. Alternatively, run this in pgAdmin's Query Tool while connected to the existing `postgres` database:

```sql
CREATE DATABASE security_demo;
```

Use either the dialog or the SQL command, not both. The application creates its tables on first startup.

Open [src/main/resources/application.properties](src/main/resources/application.properties):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/security_demo
spring.datasource.username=postgres
spring.datasource.password=postgres
```

Replace the username and password with **your local PostgreSQL credentials**. The example password `postgres` is not necessarily your PostgreSQL password. If your port is different, change the URL.

All application database and JWT settings are in this file:

| Property | Meaning |
| --- | --- |
| `spring.datasource.url` | PostgreSQL host, port and database |
| `spring.datasource.username` / `password` | Database login |
| `spring.datasource.driver-class-name` | PostgreSQL JDBC driver |
| `spring.jpa.hibernate.ddl-auto=update` | Creates/updates demo tables; keeps data between runs |
| `spring.jpa.open-in-view=false` | Database access stays inside service transactions |
| `spring.jpa.show-sql=false` | Controls SQL logging |
| `app.jwt.secret` | Base64-encoded signing key, at least 32 decoded bytes |
| `app.jwt.issuer` | Issuer written into and required on every token |
| `app.jwt.expiration=1h` | Token lifetime; Spring duration format, e.g. `15m`, `30s` |
| `app.demo.enabled` | Creates missing learning accounts at startup |
| `app.demo.*-email` / `*-password` | Initial learning-account credentials |
| `server.port` | HTTP port, default 8080 |

The signing algorithm is deliberately fixed to **HS256** in `JwtService`; the verifier rejects other algorithms. The issuer, key and token lifetime come from properties, not hardcoded Java values.

The included JWT key and demo accounts are public teaching values. Use this setup locally. For private use, replace the key with a cryptographically random Base64 key, replace credentials, turn off demo seeding and keep the edited properties file private. You can generate a key by evaluating this Java expression in your IDE:

```java
io.jsonwebtoken.io.Encoders.BASE64.encode(
    io.jsonwebtoken.Jwts.SIG.HS256.key().build().getEncoded())
```

Paste its result into `app.jwt.secret`. Changing the key invalidates existing tokens; log in again. See the [JJWT documentation](https://github.com/jwtk/jjwt) for key creation and signature verification.

## 4. Run the backend

### Easiest: IntelliJ IDEA

1. Open this folder as a Maven project and reload Maven dependencies.
2. Set the project SDK to Java 21.
3. Ensure PostgreSQL is running and properties contain the correct credentials.
4. Run `SecurityDemo2Application.main()`.
5. Wait for `Started SecurityDemo2Application` in the application log.

If Lombok-generated methods are not recognized, enable annotation processing in IntelliJ settings and reload Maven.

### Maven

In a terminal in the project directory (Windows Command Prompt is fine):

```text
mvn spring-boot:run
```

Or use IntelliJ's Maven tool window to run the `spring-boot:run` goal.
To test, package and run the executable JAR:

```text
mvn clean verify
java -jar target/security-demo2-0.0.1-SNAPSHOT.jar
```

The base URL is **http://localhost:8080**.
Try **http://localhost:8080/api/public/hello** in your browser.
Optional generated API docs are at **http://localhost:8080/swagger-ui/index.html** and **http://localhost:8080/v3/api-docs**. Use the supplied Postman collection for the token workflow.

### Learning accounts

On startup, the initializer creates these accounts if their email is absent:

| Email | Password | Role |
| --- | --- | --- |
| `user@example.com` | `User12345!` | USER |
| `manager@example.com` | `Manager12345!` | MANAGER |
| `admin@example.com` | `Admin12345!` | ADMIN |

Creating the accounts is controlled by `app.demo.enabled=true`.
Existing users are **not** reset when the application restarts. Changing the seed password property does not change an existing database user's password. Setting seeding to false does not delete existing accounts.

## 5. Try everything in Postman

### Automatic collection

1. Start the application.
2. In Postman select **Import**.
3. Import [postman/security-demo2.postman_collection.json](postman/security-demo2.postman_collection.json).
4. Open the imported collection. Its `baseUrl` variable is `http://localhost:8080`; edit it if you changed the port.
5. Run requests **01 through 26 in order**, either manually using Send or with the Collection Runner.
6. Inspect the response body, status and test results.

No Postman environment is required. Collection scripts automatically save separate USER, MANAGER, ADMIN and learner tokens, plus created IDs. Login requests explicitly use **No Auth**. Other requests specify the correct Bearer token.

Expected `400`, `401`, `403`, `404` and `409` responses demonstrate error handling; their assertions should pass. The sequence also promotes a learner, uses their existing token, and demotes them again.

Each full run creates one uniquely named learner account and deletes the products it created. Learner accounts remain in the database. Run against your local learning database. If interrupted, created products and a promoted learner may remain; continue the remaining requests or inspect them using the admin account.

The collection uses the demo emails/passwords above. If you customize those properties, also edit the three login request bodies. Tokens are saved locally in collection variables for convenience; clear them before exporting or sharing the collection.

### Manual example: login, then create a product

**A. Login as MANAGER**

- Method: **POST**
- URL: `http://localhost:8080/api/auth/login`
- Authorization: **No Auth**
- Body: **raw -> JSON**

```json
{
  "email": "manager@example.com",
  "password": "Manager12345!"
}
```

The `200 OK` response looks like this (IDs and token vary):

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 2,
    "name": "Demo Manager",
    "email": "manager@example.com",
    "role": "MANAGER",
    "authorities": ["ROLE_MANAGER", "product:read", "product:write"]
  }
}
```

`expiresIn` is in **seconds**. Copy the complete `accessToken` value.

**B. Create a product**

- Method: **POST**
- URL: `http://localhost:8080/api/products`
- Authorization: **Bearer Token**
- Token: paste the token only, without typing `Bearer` again.
- Body: **raw -> JSON**

```json
{
  "name": "Keyboard",
  "description": "USB keyboard",
  "price": 49.90
}
```

Expected: `201 Created`, a `Location: /api/products/<id>` header and:

```json
{
  "id": 1,
  "name": "Keyboard",
  "description": "USB keyboard",
  "price": 49.90
}
```

Use the returned ID; it is not guaranteed to be 1.

**C. See the authorization difference**

Send the same POST with a USER token: **403 Forbidden**.
Send it with a MANAGER token: **201 Created**.
Delete a product with a MANAGER token: **403 Forbidden**.
Delete it with an ADMIN token: **204 No Content**.

## 6. Every REST endpoint

Use `Content-Type: application/json` for requests with JSON bodies.
All protected endpoints require `Authorization: Bearer <accessToken>`.

| Method | URL | Required access | Success |
| --- | --- | --- | --- |
| GET | `/api/public/hello` | Public | 200 message |
| POST | `/api/auth/register` | Public | 201 auth response |
| POST | `/api/auth/login` | Public | 200 auth response |
| GET | `/api/users/me` | Any authenticated user | 200 user DTO |
| GET | `/api/users?page=0&size=10` | ADMIN role | 200 page of user DTOs |
| PATCH | `/api/users/{id}/role` | ADMIN role | 200 updated user DTO |
| GET | `/api/products?page=0&size=10` | `product:read` | 200 page of product DTOs |
| GET | `/api/products/{id}` | `product:read` | 200 product DTO |
| POST | `/api/products` | `product:write` | 201 product DTO and Location |
| PUT | `/api/products/{id}` | `product:write` | 200 updated product DTO |
| DELETE | `/api/products/{id}` | `product:delete` | 204, empty body |

### GET /api/public/hello

No body and no authentication required:

```json
{"message": "Hello! This endpoint does not need a token."}
```

### POST /api/auth/register

```json
{
  "name": "Alice",
  "email": "alice@example.com",
  "password": "Alice12345!"
}
```

Returns the same auth response structure as login, with status **201** and role **USER**.
Name is required and at most 80 characters. Email must be valid, at most 254 characters, and is stored in lowercase. Password must be 8–72 characters and at most 72 UTF-8 bytes because BCrypt has a byte limit. Passwords are not trimmed. Duplicate email returns **409**. Invalid fields return **400**.

Do not send `role`, `authorities`, `id` or `passwordHash`: unknown fields are rejected with **400**. Registration always assigns USER on the server.

### POST /api/auth/login

Send `email` and `password`, as shown above. Email lookup ignores case. Valid credentials return **200** with an access token. A wrong password or unknown account returns **401** with the same message. Missing/invalid fields return **400**.

### GET /api/users/me

No body. Returns the current user's ID, name, email, role and authorities. The shape is the `user` object in the login example, without the surrounding auth response.

### GET /api/users

ADMIN only. No body. Accepts optional `page` and `size`. Returns the page shape below, with user DTOs in `content`. Never returns password hashes.

### PATCH /api/users/{id}/role

ADMIN only. Use a user ID from registration or the user list.

```json
{"role": "MANAGER"}
```

Allowed values are `USER`, `MANAGER`, `ADMIN` (case-sensitive).
Returns the updated user DTO. Unknown ID: **404**. Missing or invalid role: **400**.
The change affects the user's next request, even with an existing JWT.

For the exercise, change the new learner account, then restore it to USER.
This simple example allows admins to change their own role too; demoting your only admin removes your ability to manage roles until you restore an admin through the database.

### GET /api/products and GET /api/products/{id}

All three roles can read. No body.
The single-product endpoint returns the product shape shown above; missing ID returns **404**.

Both product and user lists use this page shape:

```json
{
  "content": [
    {"id": 1, "name": "Keyboard", "description": "USB keyboard", "price": 49.90}
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

Pages start at **0**. Default page is 0, default size is 10, maximum size is 100, minimum size is 1. Results are sorted by ID ascending. An empty page has `content: []`. Invalid page/size returns **400**.

### POST /api/products

MANAGER or ADMIN. Send `name`, `description`, and `price` using the example above.
Name must be nonblank and at most 100 characters. Description is required, may be empty, and is at most 500 characters. Price must be at least 0.01, with at most 10 integer digits and 2 decimal places. Decimal prices use `BigDecimal`.

Returns **201**, the saved product DTO, and a Location header.
Invalid input returns **400**.

### PUT /api/products/{id}

MANAGER or ADMIN. Send all three fields; this is a full update:

```json
{
  "name": "Wireless keyboard",
  "description": "Updated product",
  "price": 59.90
}
```

Returns **200** with the updated product. Validation is the same as creation.
Missing ID returns **404**. The product ID is taken from the URL, never the body.

### DELETE /api/products/{id}

ADMIN only. No body. Returns **204** with no response body after deletion.
Missing ID returns **404**. A USER or MANAGER receives **403**.

## 7. Understand errors

| Status | Meaning in this API | What to do |
| --- | --- | --- |
| 400 | Invalid JSON, unknown field, invalid input or pagination | Fix the request body/parameters |
| 401 | No valid token, expired/invalid token, or incorrect login | Log in and use the new Bearer token |
| 403 | Valid authentication, insufficient role/authority | Use an account with the required permission |
| 404 | Product or user ID does not exist | Check the ID |
| 409 | Email already registered or database constraint conflict | Use another email or inspect the conflicting data |

Example security error:

```json
{
  "timestamp": "2026-11-08T12:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission for this action",
  "path": "/api/products",
  "fieldErrors": {}
}
```

Validation errors use the same format with `status: 400`, `message: "Validation failed"` and entries in `fieldErrors`, such as `{"email":"must be a well-formed email address"}`.

A public endpoint does not need a token, but sending a malformed Bearer token still returns 401 because the JWT filter validates supplied tokens. Select **No Auth** for public and login requests.

### Token expiration and logout

There is no refresh endpoint. When the token expires, log in again.
There is no server logout endpoint or token denylist: deleting the token in Postman only removes your local copy. A copied token remains valid until expiration, a key change, or user deletion. Production authentication needs additional lifecycle controls appropriate to the application.

CSRF protection is disabled because this API accepts authentication through explicit Authorization headers and does not use authentication cookies or Basic Auth. If you later switch to cookies or add a browser frontend, review CSRF and CORS for that design.

## 8. Project map and tests

```text
src/main/java/os/project/securitydemo2/
  SecurityDemo2Application.java
  auth/         registration, login, authentication DTOs
  security/     JWT generation/validation, filter, Spring Security configuration
  user/         JPA user, roles, authorities, repository, mapper, service, controller
  product/      JPA product, repository, mapper, service, controller and DTOs
  common/       error responses, exception handlers, pagination, public endpoint
  config/       initial demo accounts
src/main/resources/application.properties
src/test/resources/application-test.properties
postman/security-demo2.postman_collection.json
```

To follow the code, start with `Role.java`, then `SecurityConfig.java`, `AuthService.java`, `JwtService.java`, `JwtAuthenticationFilter.java`, and `ProductController.java`.

Run:

```text
mvn test
```

Tests use **H2 in PostgreSQL compatibility mode**, with its database settings in the test properties file. H2 is a test-only dependency; the normal application uses PostgreSQL. Tests do not require or alter your local PostgreSQL database.

The integration tests send real JSON requests through Spring's MockMvc filter/controller pipeline, using real JJWT signatures, BCrypt and JPA repositories. They cover registration, duplicate emails, safe DTOs, login failures, missing/invalid/expired tokens, wrong signatures/issuer/algorithm, USER/MANAGER/ADMIN permissions, product CRUD, validation, pagination and immediate role changes.

H2 tests verify application behavior; they do not prove complete PostgreSQL compatibility. The Postman collection exercises the running application against your configured PostgreSQL database.

## 9. Troubleshooting

- **Connection refused on 5432:** start your PostgreSQL service and check the host/port.
- **Password authentication failed:** put your actual PostgreSQL username/password in `application.properties`.
- **Database security_demo does not exist:** create it in pgAdmin first.
- **Port 8080 already in use:** stop the other application or change `server.port` and the Postman `baseUrl`.
- **Invalid target release 21:** select JDK 21 for both the IDE project and Maven runner.
- **401 after restarting with a new JWT key:** log in again.
- **403 after login:** check `/api/users/me` and the endpoint's required authority.
- **Demo password stopped working after editing properties:** existing accounts are not overwritten by the seed initializer.
- **Empty product list:** create a product using the MANAGER or ADMIN request.
- **Invalid Base64/weak key on startup:** use a Base64-encoded random key with at least 32 decoded bytes.
- **Postman variables are empty:** run the numbered login/create requests before requests that use their values.

This is a learning project. Its public demo key/accounts, automatic schema updates, open API docs, and absence of rate limiting, password reset, email verification and refresh/revocation flows are deliberate scope limits. Use private configuration, migrations, HTTPS and appropriate account/token lifecycle controls before adapting it for real users.

