# Store chat entry — one Backend file to copy

Status: IMPLEMENTED_LOCAL, 2026-11-21. Chu copied the DTO and confirmed both entry buttons appeared. Both target the store owner, including staff-posted products. Existing POST /api/v1/conversations accepts targetUserId and returns the existing or new user-pair conversation. No endpoint is added. The later direct business-policy update supersedes the staff-owner allowance described in this historical guide: current staff and their owner cannot use customer chat with each other. See CHAT-NAMES-AND-POLICY.md.

The User Frontend buttons have been implemented with guards. The main Backend DTO has not been changed: Chu copies the one file below. Until ownerId is returned, authenticated users see a disabled button with a reload action; the frontend never falls back to the listing staff's sellerId.

Replace src/main/java/com/moldy/moldymarket/store/dto/response/PublicStoreResponse.java with:
```java
package com.moldy.moldymarket.store.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PublicStoreResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        UUID ownerId
) {
}
```

StoreMapper already maps fields by name; Store has getOwnerId(), so it maps ownerId automatically after recompiling. Do not edit generated StoreMapperImpl. No controller, service, schema, database credentials or properties changes are needed.

Rebuild the Backend to regenerate the mapper, restart it and reload the frontend. GET /api/v1/stores/{storeId} must now include data.ownerId as the actual owner's UUID. The response still does not expose phone, email or pickup address. Report “xong” so the assistant can check the copied DTO and install the owner HTTP regression test; Chu does not need to copy tests.

The existing StoreServiceTest fixture in the main repo was adapted by the assistant to use the real mapper rather than a four-argument DTO constructor, with ID/name assertions retained and strengthened. It compiles and passes against the old DTO, and will work with the added field. Reference tests are in store-chat-tests/ for the assistant to install after copying.

Review verification: a regression HTTP test failed on missing ownerId before the patch. Full Maven verify/package passed 301 tests (300 passed, one pre-existing disabled QA-010, zero failures/errors) on an isolated copy of the current dirty Backend. The public HTTP test checks the owner's UUID and absence of email/phone/pickupAddress. The main application DTO remains unchanged; this is not a claim that the new field is already deployed or integrated manually.

Frontend behavior: both buttons use the same action; guests go to login with a return URL and click Chat again after login. Owners cannot chat with themselves, while staff can chat with their owner. A double-click lock and retry:false prevent automatic duplicate POSTs. The returned pair/status is validated, and callbacks after logout/unmount do not navigate or restore an old inbox. No productId/message text is invented in the conversation request. Existing CLOSED conversations stay CLOSED/read-only.

No commit or push. UI verification remains with Chu after copying/restarting.
