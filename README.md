# Order-Inventory App

A single Spring Boot application with two in-process modules — **Order** and **Inventory** — backed by a shared Supabase (Postgres) database, connected to a React (Vite) frontend over REST.

## Stack

- Backend: Java, Spring Boot, Spring Data JPA
- Frontend: React (Vite)
- Database: Supabase (Postgres)

## Project Structure

```
edu.cit.abesia            <- @SpringBootApplication root (scans both modules)
edu.cit.abesia.shop       <- Order module
edu.cit.abesia.inventory  <- Inventory module
```

---

## Supabase Setup

1. Create a free project at [supabase.com](https://supabase.com).
2. Open the **SQL Editor** and run `schema.sql` (included in this repo) to create and seed the `inventory` and `orders` tables.
3. Grab your database connection string, username, and password from **Project Settings → Database**.
4. Set these as environment variables (do **not** commit them to Git):

   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://<your-supabase-host>:5432/postgres
   SPRING_DATASOURCE_USERNAME=<your-username>
   SPRING_DATASOURCE_PASSWORD=<your-password>
   ```

5. Reference these in `application.properties` via `${SPRING_DATASOURCE_URL}` etc., and confirm `.gitignore` excludes any local `.env` file or hardcoded credentials.

## Running the Backend

```bash
cd backend/order-inventory
./mvnw spring-boot:run
```

Backend runs on `http://localhost:8080`.

## Running the Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`.

## API

**POST** `/api/orders`

Request:
```json
{ "productId": "P100", "quantity": 5 }
```

Response:
```json
{
  "status": "CONFIRMED",
  "reason": null,
  "inventory": { "productId": "P100", "name": "Wireless Mouse", "stock": 20 }
}
```

---

## Testing Evidence

### Confirmed order (P100, quantity 5)

_![img.png](screenshots/img.png)_![img_2.png](screenshots/img_2.png)

### Rejected order (P300, quantity 1)

_![img_1.png](screenshots/img_1.png)![Screenshot 2026-09-10 195807.png](..%2F..%2FPictures%2FScreenshots%2FScreenshot%202026-09-10%20195807.png)_

---

## Reflection

**1. In-process vs. microservice integration — what do you get for free, and what would you need to add back if split?**



Since Order and Inventory are just two Java classes talking to each other inside the same app, calling InventoryService from OrderService is basically just a normal method call. It's fast, it doesn't need the internet or any network stuff, and if something goes wrong it just throws an exception right there — no need to handle weird network errors. Also, because they're in the same app, I can use one database transaction for both the order and the inventory update, so if one fails, both get rolled back together. That's something I got automatically without even trying.

If I split them into two separate services that talk over HTTP, I'd lose all of that. The call would now go over a network, which means it could be slow or just fail randomly (timeout, service is down, etc.), so I'd need to add retry logic and handle those failures myself. I'd also lose the shared transaction — if the order service succeeds but the inventory service fails (or the other way around), the data could end up inconsistent, and I'd have to figure out some way to fix that (like sending a "cancel" event back). Basically, everything that used to be automatic because they were in one app now becomes something I have to build myself.

**2. Why does package-private visibility on `InventoryServiceImpl` matter — what breaks if it's public?**



I made InventoryServiceImpl package-private so that only classes inside the inventory package can actually use it directly. Everyone else, including the Order module, can only see and use the InventoryService interface. This basically forces Order to not know or care how Inventory actually works internally — it just knows "I can call reserve() and getItem()" and that's it.

If I made it public instead, nothing would stop the Order module (or any other part of the app) from importing InventoryServiceImpl directly and using it instead of the interface. That would kind of defeat the whole point of having an interface in the first place, because now Order is depending on Inventory's actual implementation. If I ever needed to change how Inventory works internally, I might accidentally break Order too, since it's no longer just depending on the interface anymore. Keeping it package-private is basically how Java forces me to respect the boundary instead of just trusting myself to not cheat.

**3. When would you extract Inventory into its own microservice, and what would need to change?**



I'd probably split Inventory out into its own service if it started getting way more traffic than Order, or if other apps/services also needed to check or update stock and not just my Order module. Or if a different team was going to be in charge of maintaining Inventory separately and wanted to deploy it on its own schedule instead of being tied to Order's release schedule.

To actually do that, InventoryService wouldn't be a plain Java interface anymore — it would become an HTTP API that Order calls over the network instead of just calling a method directly. I'd also need to give Inventory its own database instead of sharing the same tables, since two separate services shouldn't be reading/writing the same table directly without going through an API. And since I'd lose the shared transaction from before, I'd need some way to handle it if one part succeeds and the other fails — like adding retries or some kind of event system so they can stay in sync even without a direct transaction tying them together.
