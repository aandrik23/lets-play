



## Dependency injection

### @Autowired field injection:
- ❌ Dependencies can be null at construction time
- ❌ Harder to unit test (can't inject mocks easily)
- ❌ Hides dependencies - hard to see what class needs
- ❌ Allows creating object without required dependencies

### Constructor injection:
- ✅ Dependencies guaranteed at construction time
- ✅ Easy to unit test (just pass mocks to constructor)
- ✅ Dependencies are explicit and visible
- ✅ Fields can be final (immutable)
- ✅ Spring team officially recommends it

HTTP to HTTPS redirects should be handled at the infrastructure level
by a load balancer or reverse proxy like Nginx or AWS ALB — not in the
application code. This keeps the Spring Boot app focused on business
logic and makes SSL management centralized.