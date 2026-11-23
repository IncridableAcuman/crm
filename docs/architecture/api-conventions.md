# SAVDOCRM — REST API Conventions & Standards

## 1. RESTful Design Principles

All APIs in SAVDOCRM adhere to strict RESTful standards:
- **Base URI Path**: `/api/v1/`
- **Resource Naming**: Plural nouns representing domain resources (e.g., `/api/v1/products`, `/api/v1/branches`, `/api/v1/inventory/stocks`).
- **Sub-Resources**: Hierarchical relationships represented intuitively (e.g., `/api/v1/products/{id}/variants`, `/api/v1/sales/{id}/items`).
- **Action Endpoints**: When an operation represents a complex business workflow rather than pure CRUD, use specific verb actions (e.g., `POST /api/v1/sales/checkout`, `POST /api/v1/inventory/transfers/{id}/approve`).

---

## 2. HTTP Verbs & Status Code Semantics

| HTTP Verb | Action | Success Code | Idempotent |
| :--- | :--- | :--- | :--- |
| `GET` | Retrieve resource or list | `200 OK` | Yes |
| `POST` | Create a new resource or execute an action | `201 Created` or `200 OK` | No (requires `Idempotency-Key` for financial ops) |
| `PUT` | Complete replacement of a resource | `200 OK` | Yes |
| `PATCH` | Partial update of specific fields | `200 OK` | No |
| `DELETE` | Soft-delete or archive a resource | `204 No Content` | Yes |

### Error Status Codes:
- `400 Bad Request`: Validation failure or malformed JSON syntax.
- `401 Unauthorized`: Missing, invalid, or expired JWT token.
- `403 Forbidden`: Authenticated user lacks required permission or branch scope.
- `404 Not Found`: Resource does not exist within the active tenant partition.
- `409 Conflict`: Business invariant collision (e.g., duplicate barcode, insufficient stock, optimistic lock failure).
- `422 Unprocessable Entity`: Semantic business logic violation (e.g., customer credit limit exceeded).
- `500 Internal Server Error`: Unexpected server fault (traceId returned to client for support tracking).

---

## 3. Standard Request & Response Envelopes

### 3.1. Standard Single Resource Response:
```json
{
  "success": true,
  "data": {
    "id": "e3b0c442-98fc-11ef-bd8f-0242ac120002",
    "name": "Coca-Cola 1.5L",
    "sku": "BEV-COCA-15L",
    "sellingPrice": 1.5000,
    "costPrice": 0.9500,
    "unit": {
      "id": "7a1b2c3d-...",
      "symbol": "pcs"
    },
    "isActive": true
  },
  "timestamp": "2026-11-23T14:52:00.123Z"
}
```

### 3.2. Standard Paginated Response (`PageResponse<T>`):
```json
{
  "success": true,
  "data": [
    { "id": "...", "name": "Item A" },
    { "id": "...", "name": "Item B" }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 142,
    "totalPages": 8,
    "isFirst": true,
    "isLast": false
  },
  "timestamp": "2026-11-23T14:52:00.123Z"
}
```

### 3.3. Standard Error Envelope (RFC 7807 Compatible):
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Input validation failed on 2 fields",
    "details": [
      {
        "field": "sellingPrice",
        "rejectedValue": -5.00,
        "message": "Selling price cannot be negative"
      },
      {
        "field": "name",
        "rejectedValue": "",
        "message": "Product name is required"
      }
    ]
  },
  "traceId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "timestamp": "2026-11-23T14:52:00.123Z"
}
```

---

## 4. Query Parameters: Filtering, Sorting, and Search

1. **Pagination**:
   - `page`: 0-indexed page number (`?page=0`).
   - `size`: Items per page (`?size=25`, default: 20, max: 100).
2. **Sorting**:
   - Format: `sort=fieldName,asc` or `sort=fieldName,desc`.
   - Multiple sorts: `?sort=createdAt,desc&sort=name,asc`.
3. **Filtering & Full-Text Search**:
   - Search term: `?search=coca` (searches across product name, SKU, and barcode).
   - Category filtering: `?categoryId=e3b0c442-...`
   - Active status: `?isActive=true`
   - Date range: `?startDate=2026-11-01T00:00:00Z&endDate=2026-11-23T23:59:59Z`.

---

## 5. Idempotency for High-Impact Operations

Retail operations must prevent double billing or duplicate inventory deductions in unstable network environments.

### 5.1. Idempotency Flow & Protocol:
Endpoints performing financial settlement or stock movement accept a mandatory `Idempotency-Key` HTTP header:
```http
POST /api/v1/sales/checkout
Authorization: Bearer <token>
Idempotency-Key: 7b84f291-a641-4b72-a0d4-a957b98fbc14
Content-Type: application/json

{
  "posSessionId": "...",
  "items": [ ... ],
  "payments": [ ... ]
}
```

### 5.2. Atomic Verification Lifecycle:
1. **Request Hashing**: The backend hashes the raw request payload (SHA-256).
2. **Atomic Record Check**: The backend queries `idempotency_records WHERE tenant_id = :tenantId AND idempotency_key = :key`.
   - **Case A (Completed)**: If a record exists with status `COMPLETED`:
     - If the payload hash matches, the previously generated response JSON is immediately returned with `200 OK` (or `201 Created`) without re-executing inventory deduction or financial charges.
     - If the payload hash differs, returns `422 Unprocessable Entity` ("Idempotency key reuse with mismatched payload").
   - **Case B (In-Progress)**: If a record exists with status `IN_PROGRESS`, returns `409 Conflict` ("Request with this key is currently being processed. Please retry shortly.").
   - **Case C (New Request)**: Inserts an `idempotency_records` row with status `IN_PROGRESS`.
3. **Transactional Execution**: The main business logic (Sale creation, stock deduction, payment, customer debt) executes inside an ACID transaction.
4. **Finalization**: Upon successful commit, the `idempotency_records` status is updated to `COMPLETED` along with the serialized JSON response body.
5. **Rollback on Failure**: If the business transaction fails or rolls back, the idempotency record is either removed or marked `FAILED`, allowing the cashier to retry.

---

## 6. OpenAPI / Swagger 3.0 Documentation

All API contracts are automatically documented using **Springdoc OpenAPI**:
- UI accessible at: `/swagger-ui/index.html`.
- Machine-readable specification at: `/v3/api-docs`.
- Every controller specifies domain tags, operation descriptions, error responses, and JWT bearer authentication schemes.
