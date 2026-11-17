# Collapp Backend — Plan de mejoras post-MVP

Estado de partida: MVP funcional (Java 25, Spring Boot 3.5.16, JPA, Security + JWT, PostgreSQL).
Arquitectura en capas correcta (controller → service interface + impl → repository), DTOs como
records, mappers manuales, excepciones de dominio con `@RestControllerAdvice`. La base es sólida;
lo que sigue son mejoras ordenadas por impacto.

Cada tarea lleva una etiqueta de origen:
- **[FB]** viene del feedback de la presentación final.
- **[REV]** ha salido de la revisión del código.

---

## Fase 0 — Feedback de la presentación (rápido, cierra el ciclo del bootcamp)

Todo esto es de bajo riesgo y se puede hacer en una o dos sesiones.

### 0.1 Mover `SecurityConfig` a `security/` **[FB]**
`config/SecurityConfig.java` → `security/SecurityConfig.java`. El paquete `config` queda vacío;
se puede eliminar o reservarlo para configuración transversal no relacionada con seguridad
(CORS properties, Jackson, OpenAPI). Es solo cambiar el `package` y el import en quien lo use.

### 0.2 Validar el rango de fechas de la oferta **[FB]**
Hoy `OfferRequest` valida `@FutureOrPresent` en `startDate` pero `endDate` no valida nada: se puede
crear una oferta que termina en 2020 o antes de empezar.

Solución: una anotación de clase `@ValidDateRange` con su `ConstraintValidator`, aplicada al record
completo (las validaciones entre campos no se pueden expresar campo a campo):

```java
@ValidDateRange(start = "startDate", end = "endDate")
public record OfferRequest(...) {}
```

El validador comprueba `end != null && !end.isBefore(start)`. Añadir además `@FutureOrPresent`
en `endDate`. Los errores caen solos en el handler de `MethodArgumentNotValidException`, pero
ojo: los errores de clase son `getGlobalErrors()`, no `getFieldErrors()` — hay que añadirlos al
mapa de respuesta (ver 0.6).

### 0.3 Usar los mappers en `ApplicationServiceImpl.create` y `AuthServiceImpl.register` **[FB]**
Ahora mismo esos dos métodos construyen la entidad a mano dentro del servicio, mientras que el
resto del código pasa por el mapper. Añadir a cada mapper el sentido inverso:

- `ApplicationMapper.toEntity(ApplicationRequest, Offer, User)` → devuelve `Application` con
  estado `PENDING`.
- `UserMapper.toEntity(RegisterRequest, String passwordHash)` → devuelve `User` con
  `systemRole = ROLE_CREATIVE`.

`OfferServiceImpl.create` tiene exactamente el mismo problema aunque no se mencionara en el
feedback: también construye el `Offer` a mano. Conviene arreglar los tres a la vez para que la
regla sea "los servicios nunca construyen entidades" y no una excepción puntual.

Cuando los tres estén hechos, valorar migrar a **MapStruct**: genera los mappers en tiempo de
compilación, elimina ~150 líneas de código repetitivo y es lo que se usa en proyectos reales.
Convive bien con Lombok si se ordena el `annotationProcessorPaths` (lombok → lombok-mapstruct-binding
→ mapstruct-processor).

### 0.4 Normalizar `location` **[FB]**
Hoy es un `String` libre: "Madrid", "madrid", "Madrid, España" y "MAD" son cuatro ubicaciones
distintas, lo que hace imposible filtrar bien.

Dos opciones, y la recomendación depende del alcance que quieras darle:

| Opción | Cuándo | Coste |
|---|---|---|
| `enum Location` (provincias españolas, o ciudades principales) | Si el ámbito es España y cerrado | Bajo — igual que `Specialty`, y el frontend obtiene un desplegable gratis |
| Entidad `Location` con tabla propia | Si quieres varios países, alta de ubicaciones nuevas, o luego geolocalización | Medio — tabla, repositorio, seeder, endpoint `GET /api/locations` |

**Recomendación:** empezar por el enum. Es el paso que arregla el filtrado hoy, y migrar de enum
a tabla más adelante es una migración de datos sencilla (los valores ya están normalizados).
Si vas a añadir "buscar ofertas cerca de mí", ve directo a la tabla con `latitude`/`longitude`.

### 0.5 `@Data` en las entidades **[FB]** — con matiz
El feedback propone sustituir `@Getter @Setter @EqualsAndHashCode @ToString` por `@Data`.
Es cierto que queda más limpio, pero conviene saber el porqué de lo que hay ahora, porque
`@Data` en entidades JPA es un antipatrón conocido:

- `@Data` genera `equals`/`hashCode` sobre **todos** los campos. En una entidad con `@Id`
  autogenerado, dos entidades nuevas sin persistir son "iguales", y el `hashCode` de una entidad
  cambia al persistirla — lo que la rompe dentro de un `HashSet` o como clave de un `Map`.
- `@Data` genera `toString` sobre todos los campos, incluidas las colecciones `LAZY`
  (`User.offers`, `Offer.applications`). Un `log.info("{}", user)` fuera de transacción lanza
  `LazyInitializationException`, y con relaciones bidireccionales puede provocar recursión infinita.
- `@Data` implica `@RequiredArgsConstructor`, que choca con el `@NoArgsConstructor` que JPA exige.

El código actual ya evita las tres trampas a mano (`onlyExplicitlyIncluded = true`,
`@ToString(exclude = ...)`). **Recomendación:** dejarlo como está y añadir un comentario de una
línea explicando el motivo — así en la próxima revisión queda claro que es deliberado y no
desconocimiento. Si aun así quieres la limpieza visual, la alternativa segura es crear una
anotación propia `@JpaEntity` que agrupe el conjunto correcto, o pasar a
`@Getter @Setter @ToString(onlyExplicitlyIncluded = true)` y un `equals`/`hashCode` escrito a mano
basado solo en el `id`.

### 0.6 Que las validaciones lleguen al frontend en un formato usable **[FB, frontend]**
El feedback del frontend dice que las validaciones "deberían venir directamente del backend".
Eso es trabajo de backend: hoy las respuestas de error son `Map<String, Object>` construidos
ad-hoc, con dos formas distintas (unas con `error`, otras con `error` + `fields`), lo que obliga
al frontend a adivinar.

Definir un contrato único como record:

```java
public record ApiError(
        Instant timestamp,
        int status,
        String error,        // etiqueta corta y estable: "VALIDATION_ERROR", "NOT_FOUND"
        String message,      // mensaje legible para el usuario
        String path,
        Map<String, String> fields   // null salvo en errores de validación
) {}
```

Con `fields` siempre presente (aunque vacío) en los 400 de validación, el frontend puede pintar el
error debajo de cada input sin lógica extra. Añadir también los `globalErrors` (los de `@ValidDateRange`)
bajo una clave convenida, p. ej. `fields.put("_form", mensaje)`.

---

## Fase 1 — Errores y agujeros que hay que tapar antes de seguir creciendo

Esto no salió en la presentación pero es lo que más impacto tiene en la calidad real del proyecto.

### 1.1 Un token inválido o caducado provoca un 500 **[REV]** — *prioridad alta*
`JwtAuthFilter.doFilterInternal` llama a `jwtService.extractEmail(jwt)` sin protección. Con un
token caducado, manipulado o simplemente basura, JJWT lanza `ExpiredJwtException` /
`SignatureException` / `MalformedJwtException` desde dentro del filtro. Como se lanza en la cadena
de filtros y no en el controlador, el `@RestControllerAdvice` **no** la captura: el usuario recibe
un 500 en vez de un 401. Es el caso más habitual del mundo real — el token caduca a las 24 h.

Arreglo: envolver la extracción en `try/catch`, y en caso de fallo dejar el `SecurityContext`
vacío y continuar la cadena. Al no haber autenticación, el `ExceptionTranslationFilter` devolverá
el 401 correcto.

### 1.2 Faltan `AuthenticationEntryPoint` y `AccessDeniedHandler` **[REV]**
Cuando una petición sin token llega a un endpoint protegido, Spring responde con su formato por
defecto, distinto del `ApiError` del resto de la API. Registrar ambos handlers en `SecurityConfig`
para que escriban el mismo JSON. Va de la mano con 0.6.

### 1.3 Un enum inválido en el body o en el query param devuelve 500 **[REV]**
`@ExceptionHandler(Exception.class)` se evalúa antes que los resolvers por defecto de Spring, así
que se está tragando excepciones que Spring ya sabía convertir en 400:

- `POST /api/offers` con `"category": "COCINA"` → `HttpMessageNotReadableException` → 500 (debería ser 400).
- `GET /api/offers?category=COCINA` → `MethodArgumentTypeMismatchException` → 500 (debería ser 400).

Añadir handlers explícitos para ambas, y para `HttpRequestMethodNotSupportedException`,
`NoResourceFoundException` y `AuthorizationDeniedException` (esta última será necesaria en cuanto
uses `@PreAuthorize`, o los 403 se convertirán en 500). Además, el handler genérico debe **loguear**
la excepción — ahora mismo la descarta en silencio y no queda rastro de los fallos en producción.

### 1.4 Falta `@Transactional` y hay que decidir sobre `open-in-view` **[REV]** — *prioridad alta*
Ningún servicio está anotado. Ahora mismo funciona por accidente: Spring Boot activa
`spring.jpa.open-in-view=true` por defecto, que mantiene la sesión de Hibernate abierta durante
toda la petición y permite que los mappers naveguen `offer.getCreator().getEmail()` fuera de
transacción. Es una muleta que ya está avisando en el log al arrancar.

Plan:
1. `@Transactional` en los métodos de escritura, `@Transactional(readOnly = true)` en las lecturas.
2. `spring.jpa.open-in-view=false` en `application.properties`.
3. Arreglar los `LazyInitializationException` que aparezcan con fetch joins (punto siguiente).

Este orden importa: si desactivas OSIV antes de los pasos 1 y 3, se rompe media API.

Efecto secundario positivo: hoy `ApplicationServiceImpl.create` hace tres queries y un `save` sin
transacción, así que una caída a medias deja datos inconsistentes.

### 1.5 N+1 de consultas en todos los listados **[REV]**
`OfferMapper.toResponse` accede a `offer.getCreator()`, que es `LAZY`. Al mapear una página de 20
ofertas se lanzan 21 consultas. En `ApplicationMapper` es peor: toca `offer`, `offer.creator` y
`applicant`, o sea 4 consultas por fila.

Arreglo: `@EntityGraph(attributePaths = {"creator"})` en los métodos de `OfferRepository`, y
`@EntityGraph(attributePaths = {"offer", "offer.creator", "applicant"})` en `ApplicationRepository`.
Para verificarlo, añadir en el perfil de desarrollo un contador de consultas
(`spring.jpa.properties.hibernate.generate_statistics=true`) o la librería `datasource-proxy`.

### 1.6 El `DataSeeder` crea un admin con contraseña conocida en todos los perfiles **[REV]** — *prioridad alta*
`admin@collapp.com` / `12345678` se crea también en producción si la base está vacía. Anotar la
clase con `@Profile("dev")` (o `@ConditionalOnProperty(name = "collapp.seed.enabled")`) y activarla
solo en local y en el entorno de demo.

### 1.7 `ddl-auto=update` → migraciones con Flyway **[REV]** — *prioridad alta*
`update` nunca borra ni modifica columnas, no versiona nada y en producción es una fuente clásica
de sorpresas. Migrar a **Flyway**:

1. Levantar el esquema actual con `update`, volcarlo con `pg_dump --schema-only`.
2. Guardarlo como `src/main/resources/db/migration/V1__initial_schema.sql`.
3. Cambiar a `spring.jpa.hibernate.ddl-auto=validate`.
4. A partir de ahí, cada cambio de entidad lleva su `V2__...sql`.

Bonus: los tests con H2 pasan a ejecutar las mismas migraciones, así que dejan de validar un
esquema distinto del de producción. (Alternativa aún mejor para los tests: **Testcontainers** con
PostgreSQL real — ver 3.2.)

### 1.8 Endurecer el JWT **[REV]**
- **Validar la longitud del secreto al arrancar.** `Keys.hmacShaKeyFor()` exige 32 bytes para
  HS256; con un `JWT_SECRET` corto la aplicación falla en la primera petición, no al arrancar.
  Validarlo en un `@PostConstruct` con un mensaje claro.
- **Meter `userId` y `role` como claims** para no tener que ir a base de datos en cada petición
  (`JwtAuthFilter` hace hoy un `SELECT` por request).
- **Refresh tokens**: el access token dura 24 h, que es mucho para un token que no se puede
  revocar. El objetivo es access token corto (15 min) + refresh token de larga duración guardado
  en base de datos y revocable. Habilita también un logout real.

### 1.9 Coherencia de permisos en las ofertas **[REV]**
`GET /api/offers` es público pero `GET /api/offers/{id}` exige autenticación. Un visitante puede
ver el listado y recibe un 401 al pulsar en una oferta. Hacer público el detalle
(`.requestMatchers(HttpMethod.GET, "/api/offers/*").permitAll()`) o proteger ambos, pero no mezclar.

### 1.10 `OfferRequest.status` es obligatorio pero se ignora al crear **[REV]**
`create` fuerza siempre `OfferStatus.OPEN`, así que el cliente está obligado a enviar un campo que
no se usa. Y en `update` ese campo duplica el `PATCH /{id}/status`. Separar en dos records:
`OfferCreateRequest` (sin `status`) y `OfferUpdateRequest` (con `status`), o quitar `status` de
ambos y dejar el `PATCH` como única vía de cambio de estado.

### 1.11 Transiciones de estado sin reglas **[REV]**
`ApplicationServiceImpl.updateStatus` acepta cualquier cambio: una candidatura `ACCEPTED` puede
volver a `PENDING`, y se pueden aceptar candidaturas de una oferta ya `COVERED`. Definir las
transiciones válidas (p. ej. `PENDING → ACCEPTED | REJECTED`, y nada sale de un estado final) y
lanzar una `InvalidStateTransitionException` (409) en el resto de casos. Lo mismo para `OfferStatus`.

### 1.12 CORS configurable **[REV]**
`http://localhost:5173` está escrito a fuego. Sacarlo a
`collapp.cors.allowed-origins` en `application.properties`, leído con `@ConfigurationProperties`.
Sin esto no se puede desplegar.

---

## Fase 2 — Funcionalidad nueva

### 2.1 Subida de imagen de perfil desde el ordenador **[FB, frontend]** — *el más visible*
El feedback pide sustituir el `avatarUrl` por una subida real. Es la mejora con más impacto
demostrable y hoy el backend no tiene nada.

Diseño propuesto:
- `POST /api/users/me/avatar`, `multipart/form-data`, campo `file`.
- Validar en servidor: tipo MIME real (no la extensión), tamaño máximo (`spring.servlet.multipart.max-file-size=5MB`)
  y dimensiones. Recortar/redimensionar a un cuadrado de 512 px con **Thumbnailator** o `imgscalr`.
- Almacenamiento: empezar con disco local tras una interfaz `StorageService` (`store`, `delete`, `url`),
  y añadir después una implementación **Cloudinary** o **S3** sin tocar el servicio. Cloudinary tiene
  plan gratuito generoso y resuelve el CDN, que es lo que necesitas si despliegas en Render o Railway.
- `avatarUrl` en `User` se conserva: pasa a guardar la URL que devuelve el `StorageService`.
- Borrar el fichero anterior al sustituirlo.

Mismo mecanismo, más adelante, para imágenes de portada de oferta y para un portafolio con varias
imágenes por usuario.

### 2.2 Búsqueda y filtros de verdad
`OfferRepository` ya extiende `JpaSpecificationExecutor` pero no se usa. Sustituir los dos métodos
`findByStatus...` por un `OfferSpecification` que componga filtros opcionales: categoría, ubicación,
tipo de compensación, rango de fechas y búsqueda de texto en título/descripción. Un solo endpoint
`GET /api/offers?category=&location=&compensationType=&from=&to=&q=` y el frontend gana una pantalla
de búsqueda completa sin backend nuevo.

### 2.3 Paginación en todo lo que es una lista
`/api/offers/mine`, `/api/offers/all`, `/api/applications/mine` y
`/api/offers/{id}/applications` devuelven `List` sin límite. Pasarlos todos a `Page`,
con `@PageableDefault(size = 20, sort = "createdAt", direction = DESC)` para que el orden sea
estable y previsible.

### 2.4 Perfil público de usuario
`GET /api/users/{username}` devolviendo el perfil sin datos sensibles, más sus ofertas abiertas.
Hoy no hay forma de ver quién publica una oferta, lo que limita bastante el producto.

### 2.5 Notificaciones
Cuando alguien se inscribe a tu oferta, o cuando tu candidatura cambia de estado, no te enteras.
Dos niveles:
1. Entidad `Notification` + `GET /api/notifications` + contador de no leídas. Sencillo y suficiente.
2. Email con `spring-boot-starter-mail` y plantillas Thymeleaf, enviado de forma asíncrona
   (`@Async` o una cola) para no bloquear la petición.

### 2.6 Gestión de la cuenta
Cambio de contraseña (`PUT /api/users/me/password`, exigiendo la actual), recuperación por email
con token de un solo uso y caducidad, y borrado de cuenta. Cuidado con el borrado: las relaciones
son `cascade = ALL, orphanRemoval = true`, así que borrar un usuario arrastra sus ofertas y las
candidaturas de terceros a esas ofertas. Probablemente quieras borrado lógico (`deletedAt`) en
lugar de físico.

### 2.7 Panel de administración
El rol `ROLE_ADMIN` existe pero solo se usa en `/api/offers/all` y en el borrado de ofertas.
Añadir gestión de usuarios, moderación de ofertas y estadísticas básicas, protegido con
`@PreAuthorize("hasRole('ADMIN')")` a nivel de método (activando `@EnableMethodSecurity`), que es
más claro que acumular reglas de URL en `SecurityConfig`.

---

## Fase 3 — Calidad, herramientas y despliegue

### 3.1 Documentación de la API con OpenAPI/Swagger — *hazlo pronto*
Añadir `springdoc-openapi-starter-webmvc-ui`. Con la dependencia y poco más ya tienes
`/swagger-ui.html` navegable. Es lo primero que mira alguien que evalúa el proyecto, y le ahorra al
frontend tener que preguntarte el contrato de cada endpoint. Documentar los códigos de error con
`@ApiResponse` y añadir el esquema de seguridad `bearerAuth`.

### 3.2 Tests de integración
Los tests actuales son unitarios con mocks: prueban la lógica de los servicios pero **ninguno prueba
la seguridad, el enrutado ni la serialización**. Los bugs 1.1, 1.3 y 1.9 los habría cazado un test
de integración.

Añadir `@SpringBootTest` + `MockMvc` cubriendo: registro y login end-to-end, acceso sin token (401),
acceso con token de otro usuario a un recurso ajeno (403), body inválido (400), y el flujo completo
oferta → candidatura → aceptación. Usar **Testcontainers** con PostgreSQL real en vez de H2, para
que los tests validen contra el mismo motor que producción.

Meta razonable: cobertura de líneas por encima del 70 % medida con **JaCoCo**, con el informe
publicado en CI.

### 3.3 CI en GitHub Actions
El workflow que había se ha borrado (aparece como eliminado en el `git status` actual). Reponer uno
que en cada push y cada PR ejecute `./mvnw verify`, publique el informe de JaCoCo y falle si bajan
los tests. Añadir después análisis estático: **Spotless** (formato), **Checkstyle** o **PMD**, y
`dependency-check` para vulnerabilidades conocidas.

### 3.4 Docker y despliegue
`Dockerfile` multi-stage (build con Maven, runtime con un JRE ligero) y un `docker-compose.yml` con
la API y PostgreSQL para que cualquiera levante el proyecto con un comando. Después, desplegar en
Render, Railway o Fly.io: **un enlace a la API en vivo vale más que cualquier README** cuando enseñes
el proyecto.

Con el despliegue vienen: perfiles separados (`application-dev.properties` /
`application-prod.properties`), `spring.jpa.show-sql=false` en producción, y secretos por variables
de entorno del proveedor en vez de `.env`.

### 3.5 Observabilidad
`spring-boot-starter-actuator` con `/health` y `/metrics` (expuestos con cuidado: solo `health`
público). Logging estructurado en JSON para producción y un `traceId` por petición para poder seguir
una incidencia en los logs.

### 3.6 Rate limiting en autenticación
`POST /api/auth/login` no tiene ningún límite: se puede probar contraseñas indefinidamente.
**Bucket4j** con un filtro por IP, o bloqueo temporal de la cuenta tras N intentos fallidos.

---

## Orden sugerido

Si quieres una secuencia concreta en lugar de fases sueltas:

| Bloque | Contenido | Por qué en este orden |
|---|---|---|
| 1 | 1.1, 1.3, 1.6, 0.6, 1.2 | Bugs que devuelven 500 y una credencial por defecto en producción. Barato y urgente. |
| 2 | 0.1, 0.2, 0.3, 0.5 | El resto del feedback de la presentación. Cierra el ciclo del bootcamp. |
| 3 | 3.1, 3.2, 3.3 | Swagger y tests de integración **antes** de refactorizar: son la red de seguridad de todo lo demás. |
| 4 | 1.4, 1.5, 1.7 | Transacciones, N+1 y Flyway. Es el refactor más delicado; hazlo con los tests ya puestos. |
| 5 | 3.4 | Docker y despliegue. A partir de aquí el proyecto es enseñable con un enlace. |
| 6 | 2.1, 0.4, 2.2, 2.3 | Subida de avatar, `location` normalizada y búsqueda. Las mejoras que se ven. |
| 7 | 1.8, 1.10, 1.11, 1.12, 3.6 | Endurecimiento: refresh tokens, máquina de estados, CORS, rate limiting. |
| 8 | 2.4 – 2.7 | Producto nuevo: perfiles públicos, notificaciones, gestión de cuenta, admin. |

---

## Anexo — Feedback de frontend

Estos puntos son del repositorio `collapp-frontend`, aquí solo se anota la parte que depende del backend:

| Observación | Dependencia de backend |
|---|---|
| Layout de escritorio (hoy solo móvil) | Ninguna |
| Que las validaciones vengan del backend | **Sí** → tarea 0.6 (`ApiError` con `fields`) |
| Imagen de perfil desde el ordenador | **Sí** → tarea 2.1 (endpoint multipart + almacenamiento) |
| Componetizar `OfferDetailPage.jsx` | Ninguna |
| Evitar varios `return`, usar renderizado condicional | Ninguna |
| Página *NotFound*, validación de inscripción duplicada | Ya funcionan, mantener |
