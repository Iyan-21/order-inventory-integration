# LegacySupply Integration (Lab 3)

All LegacySupply knowledge lives in `edu.cit.Abesia.supplier`. Only `SupplierGateway`, `SupplierOrder`,
`SupplierOrderResult` and `SupplierOrderStatus` are public. XML classes, HTTP client, session, mappings and jobs are package-private in `supplier.internal`.
Inventory and Order never import them. Configuration comes from env vars: `LS_CLIENT_ID`, `LS_API_KEY`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

## 1. Product mapping

| Product ID | Name | SupplierSku | PackSize |
|---|---|---|---|
| P100 | Wireless Mouse | ZZA-1307 | 24 |
| P200 | Mechanical Keyboard | ZZA-2133 | 12 |
| P300 | USB-C Hub | ZZA-4556 | 10 |

> TODO before submitting: re-check these three rows against your own `GET /catalog` response (each student's catalog differs).

## 2. Sessions

1. `POST /auth/token` with `ClientId` + `ApiKey` returns a `SessionToken`.
2. Every other call sends it in `X-LS-Session`.
3. The manual only says sessions are "short-lived". The adapter logs `LegacySupply rejected the session after N s` each time a 401 arrives.
4. **Measured lifetime: `<<N>>` seconds** (TODO: copy the smallest/typical N from your application log).
5. The adapter does not guess a lifetime. It signs in lazily on first use, and on any 401 it drops the token, signs in again and repeats the call once. No manual token pasting.

## 3. Errors seen

| Code | HTTP | Real cause in my traffic | Adapter behaviour |
|---|---|---|---|
| E-AUTH-03 / E-AUTH-07 | 401 | Session expired | Sign in again, repeat once |
| E-SYS-50 / E-SYS-99 | 503 | Injected outage/chaos; sometimes **after** the PO was already created | Retry (max 3, 0.5s/1s backoff) with the **same** X-Request-Id; else leave PENDING |
| E-RATE-03 | 429 | Too many requests | Not retried immediately; job stops its cycle, tries next run |
| E-REF-05 | 400 | Bad BuyerRef (e.g. "RO-null" or over 40 chars) | Not retried |
| E-QTY-11 / E-SKU-02 | 422 | Qty outside 1-99 or unknown SKU | Not retried |
| E-IDEM-04 | 409 | Same request id with different content | Never happens: request id is stored with the row |

> TODO: delete rows you did not actually receive and add any others from your log.

## 4. Qty and Uom

`Qty` is a number of **cases** (`Uom = CS`), not units. One case holds `PackSize` units.
Example (P200, PackSize 12): Inventory needs 17 units, so cases = ceil(17 / 12) = 2. I send `Qty=2`. On delivery Inventory receives 2 x 12 = **24 units**.
`supplier_orders` stores both `cases` and `units`; the delivery event carries `units`.

## 5. Resilience

- 3 s connect and read timeout; max 3 attempts per call with exponential backoff.
- Row is saved in `supplier_orders` (PENDING, `RO-<id>`, `REQ-<id>`) **before** any network call, so the same X-Request-Id is reused across retries and restarts.
- `SupplierOrderRetryJob` (`@Scheduled`, 60 s) re-sends PENDING rows with the stored request id. It stops its cycle on the first failure to save quota.

## 6. Delivery tracking

`SupplierOrderTrackingJob` (`@Scheduled`, 90 s, max 5 status checks per cycle) polls ACCEPTED/PICKING/SHIPPED/UNKNOWN orders and maps 10/20/30/40 to ACCEPTED/PICKING/SHIPPED/DELIVERED.
On DELIVERED it publishes `SupplierOrderDeliveredEvent(productId, units, poNumber)`; Inventory's `SupplierDeliveryListener` restocks. Status change and event share one transaction, so a PO restocks only once.

**Unexpected status codes**

- **90 (not in the manual):** PO-103951 (RO-4) reported StatusCode 90 and never progressed to 40. LegacySupply's self-check page has a "Noticed a cancelled order" item that counted it. Together with the fact that the order never moved forward again, I treat 90 as **cancelled**: the goods will never arrive. It maps to `CANCELLED`, leaves the polling set, adds **no** stock, publishes `SupplierOrderCancelledEvent` (shows in the activity feed), and the tracking job places **one replacement order** for the same units with a new BuyerRef and X-Request-Id.
- **Any other unknown code:** stored as `UNKNOWN`, logged as a warning, **no restock**, re-polled each cycle so it self-heals if LegacySupply later reports a known code.
