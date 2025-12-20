# RayoAI - Guía de Implementación del Modelo Freemium
**Versión de Producción 1.0**

## 0. Introducción y Alcance

Este documento es la guía de diseño e implementación para la migración de RayoAI a un modelo de negocio Freemium. Su propósito es servir como la única fuente de verdad para el desarrollo del backend, las modificaciones en los clientes de Android y Windows, y la estrategia de monetización.

### 0.1. Alcance del Documento
*   **Cubre:** El diseño de la arquitectura, la especificación de la API, los flujos de usuario, la estrategia de monetización y el roadmap de desarrollo.
*   **No Cubre (Operaciones en Producción):** Este documento establece los *requisitos* para la operación, pero los procedimientos detallados de CI/CD, planes de backup/restauración, configuración de dashboards de monitoreo y rotación de llaves de API se manejarán en documentación operativa separada.

### 0.2. Principios Guía
1.  **Seguridad Primero:** La arquitectura está diseñada para ser resistente a ataques comunes.
2.  **Experiencia de Usuario Coherente y Accesible:** El sistema es predecible y proporciona feedback claro.
3.  **Implementación por Fases Validada:** El roadmap comienza con una Prueba de Concepto (PoC) para mitigar riesgos.

---
## 1. Arquitectura General y Flujo de Usuario

El ecosistema se compondrá de tres partes: la App de Android, la App de Windows y un Backend central que orquesta todo.

#### Flujo de un Nuevo Usuario
1.  El usuario descarga la app (Android o Windows).
2.  Se le presenta una pantalla de "Iniciar sesión con Google". Es el único método de entrada.
3.  Tras un login exitoso, el cliente (Android/Windows) recibe un ID Token de Google.
4.  El cliente envía este ID Token a nuestro Backend.
5.  El Backend lo verifica, crea una cuenta de usuario en nuestra base de datos y devuelve un **token de sesión propio (Access Token y Refresh Token)**.
6.  El cliente almacena estos tokens de forma segura y los usará para todas las futuras comunicaciones con nuestro Backend.

#### Flujo de Monetización
*   **Usuario Gratuito (Android):** Verá anuncios y tendrá un límite diario de peticiones a Gemini.
*   **Compra Premium (Android):** Usa Google Play Billing. La app gestiona la compra y envía el purchaseToken al Backend para su validación.
*   **Compra Premium (Windows):** El usuario es redirigido a una página de pago de Stripe. El Backend recibe la confirmación vía webhook.
*   **Acceso Premium Unificado:** Una vez que el Backend confirma una suscripción (desde cualquier plataforma), la cuenta del usuario se marca como "premium". Ambas aplicaciones reconocerán este estado.

---
## 2. Diseño Detallado del Backend

### 2.1. Tecnología y Configuración
*   **Framework:** Node.js con Express.js y TypeScript.
*   **Base de Datos:** PostgreSQL.
*   **Gestión de Configuración:** Uso de variables de entorno (.env) para GEMINI_API_KEY, DATABASE_URL, JWT_SECRET, STRIPE_SECRET_KEY, etc.

### 2.2. Esquema de la Base de Datos
**Tabla users**
`sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    google_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    profile_picture_url TEXT,
    subscription_provider VARCHAR(20) CHECK (subscription_provider IN ('google_play', 'stripe')),
    subscription_status VARCHAR(20) NOT NULL DEFAULT 'free' CHECK (subscription_status IN ('free', 'active', 'cancelled', 'expired')),
    subscription_id TEXT,
    subscription_expires_at TIMESTAMPTZ,
    gemini_api_calls_today INT NOT NULL DEFAULT 0,
    last_api_call_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
`
*   **Regla de Negocio:** Un usuario no puede tener suscripciones activas de dos proveedores a la vez. El backend debe impedir la activación de una nueva suscripción si ya existe una activa de otro proveedor.

**Tabla efresh_tokens**
`sql
CREATE TABLE refresh_tokens (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    jti VARCHAR(255) NOT NULL UNIQUE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
`

**Tabla payment_history**
`sql
CREATE TABLE payment_history (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id),
    provider VARCHAR(20) NOT NULL,
    provider_payment_id TEXT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    amount_decimal DECIMAL(10, 2),
    currency VARCHAR(3),
    status VARCHAR(30) NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
`
*   **Idempotencia de Webhooks:** Esta tabla (o una dedicada processed_webhook_events) se usará para garantizar la idempotencia, almacenando el id del evento del proveedor. Antes de procesar un webhook, se verificará si el id ya existe.

### 2.3. Especificación de Endpoints de la API

#### Política de Seguridad de Refresh Tokens
*   **Hash:** Se usará **Bcrypt**.
*   **Revocación:** Se implementará búsqueda por jti para revocación O(1).
*   **Rotación:** Se implementará **rotación de refresh tokens**. Al usar un refresh token, este se invalida y se emite uno nuevo junto al nuevo access token. El cliente debe estar preparado para almacenar el nuevo refresh token.

#### Endpoints de Autenticación
*   POST /api/auth/google/verify: Intercambia un google_id_token por un par de ccess_token y efresh_token de nuestra app.
*   POST /api/auth/token/refresh: Usa un efresh_token para obtener un nuevo ccess_token (y un nuevo efresh_token debido a la rotación).
*   POST /api/auth/logout: Revoca el efresh_token actual (buscando por jti).
*   POST /api/auth/logout-all: Revoca **todos** los efresh_tokens de un usuario.

#### Endpoints de Usuario y Suscripción
*   GET /api/users/me: Obtiene el perfil y estado de suscripción del usuario.
    *   **Contrato del Cliente:** Los clientes deben implementar una lógica de reintento con refresco de token si reciben un 401 Unauthorized.
*   POST /api/billing/google-play/verify: Valida un purchaseToken de Google Play al momento de la compra.
*   POST /api/billing/stripe/create-checkout-session: Crea y devuelve una URL de pago de Stripe.

#### Webhooks (Entradas Asíncronas al Backend)
*   POST /api/webhooks/google-play: Recibe Notificaciones para Desarrolladores en Tiempo Real (RTDN).
*   POST /api/webhooks/stripe: Recibe eventos de Stripe.
    *   **Seguridad:** Verificación de firma obligatoria.
    *   **Eventos Soportados:** checkout.session.completed, invoice.payment_failed, customer.subscription.updated, customer.subscription.deleted.

### 2.4. Estrategia de Sincronización de Suscripciones

#### Para Google Play (Estrategia Híbrida)
1.  **Verificación Inmediata:** erify activa la suscripción al instante.
2.  **Notificaciones RTDN:** Actualizaciones rápidas pero no garantizadas.
3.  **Job Programado Diario (Fuente de Verdad):** Un cron job diario (ej. 04:00 UTC) consultará la **Google Play Developer API v3 (purchases.subscriptions.get)** para cada suscripción activa, reconciliando el estado con nuestra base de datos. Este job manejará los estados intermedios:
    *   SUBSCRIPTION_STATE_IN_GRACE_PERIOD: El usuario sigue ctive, pero se registra el estado para posible notificación.
    *   SUBSCRIPTION_STATE_ON_HOLD: El estado del usuario pasa a cancelled (o un estado on_hold si se desea más granularidad), suspendiendo el acceso premium.
    *   SUBSCRIPTION_STATE_CANCELED: El estado del usuario sigue ctive pero se actualiza subscription_expires_at para que coincida con la fecha de fin de ciclo.
    *   **Alertas:** Si el cron job falla, debe enviar una alerta a un administrador para investigación.

### 2.5. Modelo de Límites y Anti-Abuso
*   **Definición de Límites (Configurable):**
    *   GEMINI_FREE_TIER_DAILY_LIMIT=50 (configurable vía .env).
    *   GEMINI_PREMIUM_TIER_DAILY_LIMIT=1500 (configurable vía .env).
    *   **Regla de Reset:** Todos los límites diarios se resetean a las **00:00 UTC**.
*   **Estrategia Anti-Abuso:**
    *   **Rate Limiting por IP:** Umbrales configurables (ej. 10 peticiones/minuto para /auth/*).
    *   **Rate Limiting por Usuario:** Umbrales configurables (ej. 60 peticiones/minuto para /gemini/generate).
    *   **Decisión Explícita:** No se implementará un sistema complejo de "fingerprinting" de dispositivo en la v1, pero se registrará el User-Agent para análisis de patrones de abuso.

---
## 3. Plan Detallado para la App Android

### 3.1. Gestión de Dependencias y Configuración
*   Añadir play-services-auth, AdMob, Billing, Retrofit en pp/build.gradle.kts.
*   Configurar AndroidManifest.xml con ID de AdMob y permisos.

### 3.2. Flujo de Autenticación
*   Implementar AuthActivity y AuthViewModel para orquestar el login con Google, la llamada al backend y el almacenamiento seguro de tokens en EncryptedSharedPreferences.
*   Implementar un AuthInterceptor en OkHttp para la rotación y refresco automático de tokens.

### 3.3. Requisitos de Experiencia de Usuario y Accesibilidad
*   La UI debe incluir feedback claro y accesible (no solo visual) para todos los estados (login, errores, carga).
*   Se deben usar contentDescription y anuncios para lectores de pantalla (TalkBack).

---
## 4. Plan Detallado para la App Windows

### 4.1. Gestión de Dependencias
*   Añadir google-auth-oauthlib, equests, stripe a equirements.txt.

### 4.2. Flujo de Autenticación (OAuth 2.0 en Escritorio)
*   Implementar flujo con servidor local temporal para recibir el callback del navegador de forma segura.

### 4.3. Lógica de Suscripción (Stripe)
*   Implementar la llamada al backend para crear la sesión de Stripe y abrir la URL en el navegador.
*   Implementar un sondeo (polling) al endpoint GET /api/users/me para verificar el cambio de estado tras el pago.

### 4.4. Manejo de Errores y Flujos de Contingencia (OAuth)
*   La UI debe mostrar estados explícitos (esperando en navegador, error, timeout) y ofrecer un botón de reintento.

---
## 5. Roadmap de Desarrollo

**Fase 0: Preparación y Cuentas**
*   Configurar proyectos en Google Cloud, AdMob y Stripe.
*   Crear repositorio Git para el Backend.

**Fase 0.5: Prueba de Concepto (PoC) End-to-End**
*   **Objetivo:** Validar los flujos de autenticación de Android y Windows contra un backend mínimo.
*   **Criterio de Éxito:** Un usuario puede iniciar sesión en ambas plataformas y obtener un JWT válido.

**Fase 1-4: Desarrollo por Fases**
*   Implementación incremental del Backend, monetización de Android y monetización de Windows, siguiendo las especificaciones de este documento.

**NUEVA Fase 6: Preparación para Operaciones en Producción**
*   **Despliegue:** Configurar un pipeline de CI/CD (ej. GitHub Actions) para despliegue automatizado a staging y producción.
*   **Migraciones de BD:** Usar una herramienta de migraciones (ej. 
ode-pg-migrate) para gestionar cambios de esquema.
*   **Backups:** Configurar backups automáticos y periódicos de la base de datos PostgreSQL. Definir y probar un plan de restauración.
*   **Monitoreo y Alertas:** Integrar un servicio de monitoreo de salud del backend (ej. UptimeRobot), logging centralizado (ej. ELK) y alertas para fallos críticos (ej. el cron job de Google Play).
