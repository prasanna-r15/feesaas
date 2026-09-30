# FeeSaaS

Multi-tenant fee & customer management platform. See `docs/architecture.md` for the approved design.

## Run the backend locally (step 1.1)

Requirements: JDK 21 (the Gradle toolchain can download it), Docker.

```bash
docker compose -f infra/docker-compose.yml up -d     # Postgres 16 + Redis (creates the restricted app role)
cd backend
./gradlew bootRun                                     # Flyway runs migrations as feesaas_owner
curl localhost:8085/actuator/health                   # {"status":"UP"}
```

On Windows PowerShell use `.\gradlew.bat bootRun`.

## Run the tests

```bash
cd backend
./gradlew check      # unit tests + TenantIsolationIT (needs Docker for Testcontainers)
```

## Auth API (step 1.2)

```bash
# owner login (email or phone + password)
curl -s localhost:8085/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"identifier":"owner@demo.local","password":"welcome123","deviceId":"dev-1"}'

curl -s localhost:8085/api/v1/auth/me -H "Authorization: Bearer $ACCESS"
curl -s localhost:8085/api/v1/auth/refresh -H 'Content-Type: application/json' \
  -d '{"refreshToken":"...","deviceId":"dev-1"}'
```

Access JWT is 15 minutes (RS256). Refresh tokens are opaque, hashed at rest, rotated on use, and a reused refresh token revokes its whole family. Password reset email is stubbed (logged only); tests can return the token when `feesaas.auth.expose-reset-token=true`. Platform TOTP is not enforced yet.

## Platform tenants and staff (step 1.3)

Platform super-admin (`ROLE_PLATFORM_SUPER_ADMIN`):

```
POST /api/v1/platform/tenants          # create tenant from a business-type preset + owner
GET  /api/v1/platform/tenants
GET  /api/v1/platform/tenants/{id}
PATCH /api/v1/platform/tenants/{id}
POST /api/v1/platform/tenants/{id}/suspend
POST /api/v1/platform/tenants/{id}/activate
```

Tenant owner / staff with `staff.manage`:

```
GET/POST /api/v1/staff
GET/PATCH/DELETE /api/v1/staff/{id}    # delete disables the account
PUT /api/v1/staff/{id}/permissions
GET /api/v1/roles                      # permission catalogue for the picker
```

Staff cannot grant a permission they do not have. Presets (`GYM`, `ACADEMY`, `TUITION`, `GENERIC`) copy labels and enabled modules at tenant creation.

## Customers (owner app)

Tenant owner / staff with customer permissions:

```
GET    /api/v1/customers?q=&status=
POST   /api/v1/customers
GET    /api/v1/customers/{id}
PATCH  /api/v1/customers/{id}
DELETE /api/v1/customers/{id}    # soft delete
```

Customer codes come from `next_counter('customer')` (C0001, C0002, …). Phone and email are unique per tenant among active rows.

`GET /api/v1/me/bootstrap` returns the signed-in user, permissions, tenant labels/modules, and dashboard widgets (Flutter renders from this).

## Flutter apps (step 1.4)

SDK on this machine: `D:\projects\flutter_windows_3.47.5-stable\flutter` (user PATH + Cursor `terminal.integrated.env.windows`).

Open a **new** terminal after restarting Cursor. If `flutter` is still unknown, call it by full path:

```bat
D:\projects\flutter_windows_3.47.5-stable\flutter\bin\flutter.bat run --dart-define=API_BASE_URL=http://127.0.0.1:8085
```

On Windows, **Developer Mode** must be on (symlinks for plugins): Settings → Privacy & security → For developers.

Android emulator (HTTP API on the host):

```bash
cd apps/mobile
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8085
```

Windows / Chrome against local API:

```bash
flutter run -d windows --dart-define=API_BASE_URL=http://127.0.0.1:8085
cd ../admin_web
flutter run -d chrome --dart-define=API_BASE_URL=http://127.0.0.1:8085
```

| App | Path | What it does |
|---|---|---|
| Owner/staff | `apps/mobile` | Login, session restore, pending-fees home, customers CRUD, dynamic bottom nav, staff list/add |
| Platform admin | `apps/admin_web` | Login, tenant list/create, suspend/activate |

Shared code lives in `packages/core` (Dio, single-flight refresh, secure tokens, theme, `ModuleGate` / `PermissionGate`) and `packages/api_client`.

## Two database roles (important)

| Role | Used by | Notes |
|---|---|---|
| `feesaas_owner` | Flyway migrations only | owns the tables; bypasses RLS. Must NOT be used at runtime. In production use a non-superuser owner |
| `feesaas_app`   | The running application | not owner, not superuser, not BYPASSRLS, so RLS always applies |

## How tenant isolation works (step 1.1)

1. `TenantContextFilter` builds a `TenantScope` from the verified JWT (never from request input).
2. `TenantAwareTransactionManager` runs `set_config('app.tenant_id', ..., true)` at the start of every transaction and enables the Hibernate tenant filter.
3. PostgreSQL RLS policies on every tenant table compare `tenant_id` to `app_tenant_id()`. No scope = no rows.
4. Composite foreign keys (added with each business table) make cross-tenant references impossible.
