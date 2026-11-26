# Payments
* **Base URL:** `http://localhost:8085/api`
* **Payment object**
```json
{
  "id": "string",
  "orderId": "string",
  "amount": "float(2)",
  "method": "string",
  "status": "string (SUCCESS | FAILED | PENDING | REFUNDED)",
  "createdAt": "datetime(iso 8601)"
}
```

**GET /payments**
----
  Returns all payments in the system.
* **URL Params**  
  None
* **Data Params**  
  None
* **Headers**  
  `Content-Type: application/json`  
  `X-API-KEY: <API Key>`
* **Success Response:**  
  * **Code:** 200  
  **Content:**  
```json
[
  { "<payment_object>" },
  { "<payment_object>" }
]
```
* **Error Response:**  
  * **Code:** 401  
  **Content:** `{ "error": "Unauthorized: Invalid or missing API key." }`

**GET /payments/:id**
----
  Returns the specified payment by its ID.
* **URL Params**  
  *Required:* `id=[string]`
* **Data Params**  
  None
* **Headers**  
  `Content-Type: application/json`  
  `X-API-KEY: <API Key>`
* **Success Response:**  
  * **Code:** 200  
  **Content:** `{ "<payment_object>" }`
* **Error Response:**  
  * **Code:** 404  
  **Content:** `{ "error": "Payment not found" }`  
  OR  
  * **Code:** 401  
  **Content:** `{ "error": "Unauthorized: Invalid or missing API key." }`

**GET /payments/order/:orderId**
----
  Returns the payment associated with the specified order ID.
* **URL Params**  
  *Required:* `orderId=[string]`
* **Data Params**  
  None
* **Headers**  
  `Content-Type: application/json`  
  `X-API-KEY: <API Key>`
* **Success Response:**  
  * **Code:** 200  
  **Content:** `{ "<payment_object>" }`
* **Error Response:**  
  * **Code:** 404  
  **Content:** `{ "error": "Payment not found for order" }`  
  OR  
  * **Code:** 401  
  **Content:** `{ "error": "Unauthorized: Invalid or missing API key." }`

**POST /payments**
----
  Processes a new payment for an order (validates `PENDING_PAYMENT` status with the Orders service, enforces idempotency per `orderId`, persists the record, and updates the order status to `PAID`).
* **URL Params**  
  None
* **Headers**  
  `Content-Type: application/json`  
  `X-API-KEY: <API Key>`
* **Data Params**  
```json
{
  "orderId": "string",
  "amount": "float(2)",
  "method": "string"
}
```
* **Success Response:**  
  * **Code:** 201 (Created) or 200 (if idempotent duplicate `orderId` already succeeded)  
  **Content:** `{ "<payment_object>" }`
* **Error Response:**  
  * **Code:** 400  
  **Content:** `{ "error": "Invalid payment request or order not in PENDING_PAYMENT state" }`  
  OR  
  * **Code:** 404  
  **Content:** `{ "error": "Order doesn't exist" }`  
  OR  
  * **Code:** 401  
  **Content:** `{ "error": "Unauthorized: Invalid or missing API key." }`

**POST /payments/:id/refund**
----
  Refunds a completed payment and updates its status to `REFUNDED`.
* **URL Params**  
  *Required:* `id=[string]`
* **Data Params**  
  None
* **Headers**  
  `Content-Type: application/json`  
  `X-API-KEY: <API Key>`
* **Success Response:**  
  * **Code:** 200  
  **Content:** `{ "<payment_object>" }`
* **Error Response:**  
  * **Code:** 404  
  **Content:** `{ "error": "Payment not found" }`  
  OR  
  * **Code:** 400  
  **Content:** `{ "error": "Payment cannot be refunded" }`  
  OR  
  * **Code:** 401  
  **Content:** `{ "error": "Unauthorized: Invalid or missing API key." }`