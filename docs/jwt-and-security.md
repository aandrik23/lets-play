# JWT Authentication & Spring Security — How It Works Here

This document explains, from first principles, everything built in this session: password hashing, JWT
authentication, Spring Security wiring, and global error handling. It's meant to be read top to bottom if
this is your first time building this kind of system.

---

## 1. The big picture

This API is **stateless**. That one word drives almost every design decision below.

In a traditional "session-based" web app, the server remembers who you are: you log in once, the server
creates a session and gives your browser a cookie (`JSESSIONID`), and every future request sends that
cookie so the server can look the session up in memory/DB. The server holds state about you.

A stateless JWT API works differently: the server remembers **nothing** about who's logged in. Instead,
every single request must carry its own proof of identity — a signed token — in the `Authorization` header.
The server verifies that token fresh, on every request, and then forgets about you again. No sessions, no
server-side session storage, nothing to scale or invalidate across multiple server instances.

This matters for a REST API because REST is supposed to be stateless by definition, and because it's what
lets you scale horizontally (any server instance can validate any token, since validation only needs the
shared secret key, not a shared session store).

The trade-off: once a JWT is issued, the server can't easily revoke it before it expires (there's no session
to delete). That's why tokens are short-lived (`jwt.expiration` = 3,600,000 ms = 1 hour in this project).

---

## 2. Password hashing (BCrypt)

### Why you never store plain-text passwords

If your database is ever leaked (backup exposed, insider threat, misconfigured access), every plain-text
password in it is immediately usable — for this app and for every other site where users reused that
password. Hashing exists specifically to make a database leak *not* be a password leak.

### Hashing vs. encryption — a critical distinction

- **Encryption** is two-way: you encrypt with a key, and can decrypt back to the original with that same
  key (or a paired key). If a hashing scheme could be "decrypted," it would be worthless — anyone who
  steals the key/algorithm could recover every password.
- **Hashing** is one-way: you can turn a password into a hash, but you cannot mathematically reverse a hash
  back into the password. There is no "decrypt" operation. This is why password verification later works
  by *re-hashing* the input and comparing hashes, never by "decrypting" anything.

### What "salting" solves

If everyone's password went through the exact same hash function with no variation, two users with the
same password (`"password123"`) would produce the *identical* hash in your database. That's a problem:
an attacker with a precomputed table of common-password → hash pairs (a "rainbow table") could crack every
matching account in your DB in one lookup, and could instantly spot which users share a password.

A **salt** is a random value mixed into the hash computation, unique per password. It guarantees that even
two identical passwords hash to two completely different strings. BCrypt generates this salt for you
automatically and stores it *embedded inside* the output hash string — so you never manage salts yourself,
and there's no separate "salt column" to maintain.

### Where this lives in the code

`SecurityConfig.java` declares one bean:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

`PasswordEncoder` is Spring Security's interface; `BCryptPasswordEncoder` is the concrete implementation
using the BCrypt algorithm. It has exactly two operations you'll ever call:

- **`encode(rawPassword)`** — hashes and salts a plain-text password. Used once, in `AuthService.register()`:
  ```java
  user.setPassword(passwordEncoder.encode(request.getPassword()));
  ```
- **`matches(rawPassword, encodedPassword)`** — used at login. It re-derives the hash from the raw input
  using the salt embedded in the stored hash, and compares the results:
  ```java
  if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new InvalidCredentialsException();
  }
  ```

Note this is why the `User` entity's `password` field has `@JsonIgnore` — even though it's a *hash*, not
plain text, there's still no reason to ever expose it over the API. Defense in depth.

---

## 3. JWT theory — what a token actually is

A JWT (JSON Web Token) is a string made of **three parts separated by dots**:

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiI2YTVmYjc4Zi...  . xz0vOC4vueTArxnAEPjVq2Js50dMP83pKLIuuMx9N_Q
     HEADER                   PAYLOAD                          SIGNATURE
```

Each part is Base64URL-encoded (**not encrypted** — anyone can decode and read a JWT's contents, so never
put secrets like raw passwords inside one):

1. **Header** — metadata, mainly which signing algorithm was used (here, `HS256`).
2. **Payload** — the actual **claims**: pieces of data about the token/user. In this project:
   - `sub` (subject) = the user's MongoDB `id`
   - `email` = the user's email (a custom claim)
   - `role` = `"ADMIN"` or `"USER"` (a custom claim, used later for authorization)
   - `iat` (issued-at) and `exp` (expiration) timestamps
3. **Signature** — this is the part that makes the token trustworthy. It's a cryptographic signature
   computed over the header + payload, using a **secret key that only the server knows**
   (`jwt.secret` in `application.properties`).

### Why the signature is the whole point

Anyone can *read* a JWT's payload (it's just Base64, not encryption) — but nobody can *forge or modify* one
without knowing the secret key. If an attacker changes the payload (say, `"role":"USER"` → `"role":"ADMIN"`)
even by one character, the signature no longer matches, and the server's validation will reject it.

This project uses **HS256** (HMAC-SHA256), a *symmetric* algorithm: the same secret key both signs the
token (at login) and verifies it (on every subsequent request). This is simpler than asymmetric schemes
(like RS256, which use a private/public key pair) and is perfectly appropriate here since the same
application both issues and validates its own tokens.

---

## 4. `JwtService` — generating and validating tokens

```java
public String generateToken(User user) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
            .setSubject(user.getId())
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
}
```

This is called exactly once, in `AuthService.login()`, right after the password check succeeds. It builds
the three JWT parts described above and signs them with the key derived from `jwt.secret`.

```java
public Claims extractClaims(String token) {
    return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
}
```

`parseClaimsJws` does two things at once: it **verifies the signature** (throws if it doesn't match — e.g.
tampered token, or signed with a different secret) and **decodes the payload** into a `Claims` object you
can read values from. This single method call is where a forged/tampered token gets rejected.

```java
public boolean isTokenValid(String token) {
    try {
        return extractClaims(token).getExpiration().after(new Date());
    } catch (Exception e) {
        return false;
    }
}
```

Wraps the above in a boolean check: is the signature valid (didn't throw), *and* has it not expired yet?
Any exception (bad signature, malformed token, expired — jjwt actually throws a specific
`ExpiredJwtException` for that last one) is treated as "not valid," fail closed.

---

## 5. `JwtAuthFilter` — where tokens get checked on every request

Spring's web layer processes every incoming HTTP request through a **filter chain** before it ever reaches
your `@RestController`. Think of it as a pipeline: request → Filter 1 → Filter 2 → ... → Filter N →
`DispatcherServlet` → your controller method. Each filter can inspect/modify the request, reject it
outright, or pass it along by calling `filterChain.doFilter(request, response)`.

`JwtAuthFilter` is a custom filter we inserted into that pipeline:

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
    String authHeader = request.getHeader("Authorization");

    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);

        if (jwtService.isTokenValid(token)) {
            Claims claims = jwtService.extractClaims(token);
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            var authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    filterChain.doFilter(request, response);
}
```

Step by step, on **every single request**:

1. Look for an `Authorization: Bearer <token>` header. If it's missing, do nothing special — just let the
   request continue unauthenticated (this is fine; public endpoints don't need one, and protected ones will
   get rejected later, by `authorizeHttpRequests`, precisely *because* nobody authenticated them).
2. If a token is present and passes validation, pull the `userId` and `role` out of its claims.
3. Build an `Authentication` object and drop it into Spring Security's `SecurityContextHolder` — this is
   the mechanism the rest of Spring Security (and your own code, later, via
   `SecurityContextHolder.getContext().getAuthentication()`) uses to know "who is making this request."
   The authority `"ROLE_" + role` (e.g. `"ROLE_ADMIN"`) is what lets you later write
   `hasRole("ADMIN")` checks.
4. Regardless of what happened, call `filterChain.doFilter(...)` to let the request continue to the next
   filter (and eventually the controller). This filter never itself rejects a request — it only optionally
   *authenticates* it. Rejection is a separate concern, handled by the authorization rules below.

`extends OncePerRequestFilter` guarantees this logic runs exactly once per request even in edge cases
(like internal servlet forwards) that could otherwise cause a filter to run twice.

Notice we didn't need a full `UserDetailsService` / database lookup here — the `role` claim is already
embedded and signed inside the token itself, so trusting it doesn't require hitting MongoDB again on every
request. That's a deliberate stateless-JWT design choice: fewer DB round trips, at the cost of "if you
change a user's role mid-token-lifetime, they'll still have the old role until their token expires."

---

## 6. `SecurityConfig` — the rules of the road

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
    return httpSecurity
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(registry -> {
            registry.requestMatchers("/auth/**").permitAll();
            registry.requestMatchers(HttpMethod.GET, "/products/**").permitAll();
            registry.anyRequest().authenticated();
        })
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

Reading it line by line:

- **`.csrf(...disable)`** — CSRF (Cross-Site Request Forgery) protection exists to stop a malicious site
  from tricking a logged-in user's *browser* into submitting a request using their *session cookie*
  without their knowledge. It's a cookie/session-based attack. Since this API never uses cookies or
  sessions (every request must explicitly carry a JWT that a malicious page can't automatically attach),
  CSRF protection is meaningless here and would only get in the way (Spring's CSRF filter would otherwise
  reject state-changing requests without a CSRF token).

- **`.sessionManagement(STATELESS)`** — tells Spring Security: never create an `HttpSession`, never look
  one up, don't rely on one existing. This is the concrete enforcement of the "stateless" principle from
  section 1. Every request re-proves its identity via the JWT; nothing is remembered in between.

- **`.authorizeHttpRequests(...)`** — the actual access-control rules, evaluated top to bottom, first match
  wins:
  - `/auth/**` → `permitAll()`: registration and login must be reachable *without* already having a token
    (chicken-and-egg problem otherwise — you need to log in to get a token, so logging in can't require one).
  - `GET /products/**` → `permitAll()`: per the assignment spec, browsing products is public.
  - `anyRequest().authenticated()` → everything else requires a successful authentication (i.e.
    `JwtAuthFilter` must have found and validated a token and populated the `SecurityContextHolder`).

- **`.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`** — this is what actually
  splices `JwtAuthFilter` into Spring Security's internal filter chain, positioned to run *before*
  `UsernamePasswordAuthenticationFilter` (Spring's default form-login filter, which we don't use, but which
  still exists in the chain by default). Ordering matters: our filter needs to run early enough that, by
  the time `authorizeHttpRequests`'s authorization check happens later in the chain, the
  `SecurityContextHolder` is already populated if a valid token was present.

---

## 7. Tracing a request end to end

**Public request — `GET /products` with no token:**
`JwtAuthFilter` sees no `Authorization` header → does nothing → request continues unauthenticated →
`authorizeHttpRequests` checks the rule for `GET /products/**` → `permitAll()` → request reaches the
controller regardless of authentication state.

**Protected request with a valid token — e.g. `POST /products`:**
`JwtAuthFilter` finds `Authorization: Bearer <token>` → validates it → populates
`SecurityContextHolder` with `(userId, ROLE_USER)` → `authorizeHttpRequests` reaches `anyRequest()`
→ sees the request *is* authenticated → allows it through to the controller.

**Protected request with no token or an invalid one:**
`JwtAuthFilter` does nothing (or nothing usable) → `SecurityContextHolder` stays empty →
`authorizeHttpRequests` reaches `anyRequest().authenticated()` → sees *no* authentication → Spring
Security itself rejects the request (401/403) *before it ever reaches your controller code*. This is
important: your controllers and services never have to manually check "is there a valid token?" — Spring
Security's filter chain has already guaranteed that by the time your code runs.

**Login — `POST /auth/login`:**
Public path, so it always reaches `AuthController.login()` regardless of tokens. Inside,
`AuthService.login()` looks the user up by email, checks the password with `passwordEncoder.matches(...)`,
and if it succeeds, calls `jwtService.generateToken(user)` to mint a brand-new token, which is returned in
the JSON body as `{"token": "..."}`. The client is expected to store this and send it as
`Authorization: Bearer <token>` on every subsequent request.

---

## 8. Global exception handling — and a real gotcha we hit

### The problem we actually hit

Early on, testing "wrong password" and "duplicate email" in Postman returned a bare, empty
`403 Forbidden` — not the `401`/`409` the code clearly intended. The server log proved the right
exception (`InvalidCredentialsException`, `EmailAlreadyExistsException`) was being thrown in exactly the
right place — so the *logic* was correct, but nothing was translating that exception into a proper HTTP
response.

Here's the mechanism: when a `RuntimeException` escapes a controller method uncaught, Spring's default
behavior forwards the request internally to `/error` to render a fallback response. But `SecurityConfig`'s
`anyRequest().authenticated()` rule doesn't exempt `/error` — so that internal forward gets challenged by
Spring Security too, and since the *original* request never carried a token (why would it, for a login
attempt?), that forwarded request gets rejected with a generic 403. The real exception never even reached
the response — it got masked by an unrelated security rejection on the fallback path.

### The fix: catch exceptions before they ever get that far

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmailAlreadyExists(EmailAlreadyExistsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneric(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }
    // ...
}
```

`@RestControllerAdvice` registers this class as a **global interceptor for exceptions thrown by any
`@RestController`**. Each `@ExceptionHandler(SomeException.class)` method says "if *this* exception type
escapes any controller method, run *this* handler instead of letting it propagate further." Crucially, this
interception happens **inside the same request**, before Spring ever considers forwarding to `/error` — so
the whole security-rejects-the-fallback-page problem is sidestepped entirely, not just patched around.

Each handler maps one exception type to the correct HTTP status:
- `EmailAlreadyExistsException` → **409 Conflict** (the resource — email — already exists)
- `InvalidCredentialsException` → **401 Unauthorized** (bad credentials)
- `MethodArgumentNotValidException` → **400 Bad Request** — this is the exception Spring throws
  automatically when a `@Valid @RequestBody` DTO fails its Bean Validation annotations
  (`@NotBlank`, `@Email`, `@Size`, etc. — see `RegisterRequestDTO`/`LoginRequestDTO`). We extract every
  field error's message and join them into one readable string.
- A final catch-all `Exception.class` → **500**, but with a clean, safe JSON body
  (`"An unexpected error occurred"`) instead of a raw stack trace. This satisfies "never expose internals,"
  even though a truly unexpected bug is still, correctly, a 500 — the point isn't to hide that something
  went wrong, it's to never leak *how*.

All four return the same shape, `ErrorResponseDTO { status, error, message, path }`, so every error response
from this API is predictable and easy for a client (or Postman test script) to parse.

---

## 9. Why controller/service/repository are separate

You'll notice `AuthController` is intentionally thin:

```java
@PostMapping("/login")
public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
    AuthResponseDTO response = authService.login(request);
    return ResponseEntity.ok(response);
}
```

It only knows about HTTP: receive a validated DTO, delegate, wrap the result in a status code. All the
actual logic — checking the password, generating the token, deciding what counts as an error — lives in
`AuthService`. This isn't ceremony: it means the password-hashing and token-issuing code exists in exactly
one place, is easy to find for a security review, and doesn't care whether it's called from a REST
controller, a test, or (hypothetically) a CLI tool later. `UserRepository` sits below that, and only ever
talks to MongoDB — no business rules, no exceptions about "is this password right," just reads and writes.

---

## 10. Current status

**Done and verified working (register → login → token issuance → correct error codes) as of this session:**
- Password hashing via `BCryptPasswordEncoder`
- JWT generation and validation (`JwtService`)
- Stateless security filter chain with a custom `JwtAuthFilter` (`SecurityConfig`)
- `AuthController` exposing `POST /auth/register` and `POST /auth/login`
- Global JSON error handling for validation, conflict, and auth failures (`GlobalExceptionHandler`)

**Not built yet:**
- `ProductController`/`ProductService` — CRUD for products, including the owner-or-admin restriction on
  update/delete
- `UserController` — admin-only user management
- Role-based method security (`hasRole("ADMIN")` style checks) actually being *used* anywhere yet — the
  `ROLE_USER`/`ROLE_ADMIN` authority is set on every authenticated request, but nothing currently reads it
- MongoDB injection sanitization pass on user-supplied query inputs

---

## 11. Trying it yourself

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Ada","email":"ada@example.com","password":"password123"}'

# Login — copy the "token" from the response
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ada@example.com","password":"password123"}'

# Use the token on a protected endpoint (once one exists)
curl http://localhost:8080/some-protected-endpoint \
  -H "Authorization: Bearer <paste token here>"
```

In Postman: same requests, and for the protected call, set the `Authorization` tab's type to
**Bearer Token** and paste the token — Postman builds the `Authorization: Bearer <token>` header for you.
