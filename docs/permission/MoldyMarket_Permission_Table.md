# MoldyMarket — Permission Table (RBAC)

> Nguồn: `Danh_sách_UseCase_chợ_đồ_cũ.docx` (Actor, danh sách UC, đặc tả NU / AP / SPE / SO / SS / AD / GEN).
> File này là bảng Permission đã được suy ra từ tài liệu UseCase, dùng làm đầu vào cho AI agent (Antigravity) hoặc dev để seed DB và cấu hình Spring Security.

## 0. Hướng dẫn cho AI agent

1. **Nguồn sự thật** là bảng ở §4 (Permission Matrix) và CSV ở §9. Không tự thêm permission ngoài các dòng này.
2. Ký hiệu: ✅ có quyền · ❌ không · ⚠️ có quyền nhưng kèm điều kiện (xem cột Ghi chú và §6) · ⚙️ Store Owner cấp cho Staff theo nhóm quyền (§5) · ❓ tài liệu chưa nói rõ.
3. **Không seed** các ô ❓. Chờ chủ dự án quyết định (xem §11).
4. Permission được gắn theo **(role, workspace)**, không chỉ theo role: Appraiser và Store Owner phải chuyển về Normal workspace mới mua hàng được (§2).
5. Role chỉ là điều kiện cần. Các ràng buộc ở §6 (ownership, Legit Points, xung đột lợi ích, OTP…) phải kiểm tra thêm ở tầng service.

## 1. Ký hiệu cột

`G` = GUEST · `NU` = NORMAL_USER · `AP` = APPRAISER (workspace) · `SO` = STORE_OWNER (workspace) · `SS` = STORE_STAFF · `AD` = ADMIN

## 2. Roles & Workspace

| Role | Actor trong doc | Kế thừa | Cách có được role | Ghi chú |
| --- | --- | --- | --- | --- |
| `GUEST` | Khách viếng thăm | — | Chưa đăng nhập | Chỉ duyệt/tìm kiếm/xem giá. |
| `NORMAL_USER` | Normal User | — | Mặc định khi đăng ký (NU-UC01) | Vừa mua vừa bán cá nhân. |
| `APPRAISER` | Appraiser | Normal User | Nộp hồ sơ (AP-UC01) → Admin duyệt (AD-UC04) | Giữ nguyên NORMAL_USER. Có Appraisal Workspace riêng. Bị tước quyền tự động nếu Legit Points dưới ngưỡng. |
| `STORE_OWNER` | Store Owner | Normal User | Tự động khi mở gian hàng (SO-UC01) | Có Store Owner Workspace. Muốn mua đồ cá nhân phải chuyển về Normal workspace. |
| `STORE_STAFF` | Store Staff | **Không** kế thừa Normal User | Store Owner thêm (SO-UC04) | Gắn với 1 gian hàng. Không mua hàng, không rút tiền, không chuyển sang Normal Account. Quyền theo nhóm do Owner cấp. |
| `ADMIN` | Admin | — | Hệ thống cấp, không tự đăng ký | Cổng đăng nhập riêng, bắt buộc 2FA. |
| `SYSTEM` | System Pricing Engine | — | Không phải role đăng nhập | Xem §7. |

**Mô hình workspace** (GEN-UC01): một user có thể giữ đồng thời `NORMAL_USER` + `APPRAISER` + `STORE_OWNER`. Token/session cần có `activeWorkspace`. Trong `APPRAISER` workspace chỉ có quyền M4, WALLET và profile. Trong `STORE_OWNER` workspace chỉ có quyền bán hàng và quản lý shop. Quyền mua/offer/chat cá nhân chỉ có ở `NORMAL_USER` workspace.

## 3. Public endpoints (permitAll)

Không cần permission: đăng ký, đăng nhập email/Google OAuth, quên mật khẩu (mở rộng), tìm kiếm/lọc, xem chi tiết sản phẩm, xem biểu đồ giá. Cổng đăng nhập Admin là URL riêng, chỉ tài khoản `ADMIN` + 2FA mới qua được.

## 4. Permission Matrix

### M1. Duyệt & tìm kiếm (public)

| Permission code | Mô tả | UC nguồn | G | NU | AP | SO | SS | AD | Ghi chú |
| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| `PRODUCT_SEARCH` | Tìm kiếm & lọc sản phẩm | NU-UC03 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Public endpoint (permitAll). Mọi role đều dùng được. |
| `PRODUCT_VIEW_DETAIL` | Xem chi tiết sản phẩm | (suy ra từ NU-UC04/05) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Public endpoint. UC không ghi thành UC riêng. |
| `MARKET_PRICE_CHART_VIEW` | Xem biểu đồ giá thị trường | NU-UC04 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Chỉ áp dụng danh mục Thiết bị điện tử. Doc: 'bất kỳ người dùng nào'. |

### M2. Hồ sơ & không gian làm việc

| Permission code | Mô tả | UC nguồn | G | NU | AP | SO | SS | AD | Ghi chú |
| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| `PROFILE_MANAGE` | Xem/sửa hồ sơ (avatar, SĐT, địa chỉ) | NU-UC02 | ❌ | ✅ | ✅ | ✅ | ❓ | ❌ | NU-UC02 không liệt kê Store Staff. Đổi SĐT/ngân hàng cần OTP (mở rộng). |
| `BANK_ACCOUNT_MANAGE` | Liên kết/sửa tài khoản ngân hàng | NU-UC02, SO-UC05 | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | Staff không có rút tiền nên không cần. Nên bắt OTP khi đổi. |
| `WORKSPACE_SWITCH` | Chuyển workspace (Normal ↔ Appraiser ↔ Store Owner) | GEN-UC01 | ❌ | ⚠️ | ✅ | ✅ | ❌ | ❌ | NU chỉ thấy nút này nếu user có thêm role APPRAISER/STORE_OWNER. Staff bị cấm chuyển sang Normal. |

### M3. Đăng bán & quản lý sản phẩm

| Permission code | Mô tả | UC nguồn | G | NU | AP | SO | SS | AD | Ghi chú |
| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| `PRODUCT_CREATE` | Đăng bán sản phẩm (cá nhân / gian hàng) | NU-UC08, SO-UC02, SS-UC02 | ❌ | ⚠️ | ❌ | ✅ | ⚙️ | ❌ | NU: cần Legit Points đạt chuẩn. Staff: nhóm 'Quản lý sản phẩm'. Ở Appraiser workspace phải chuyển về Normal để đăng. |
| `PRODUCT_MANAGE_OWN` | Sửa/ẩn/xóa mềm sản phẩm của mình | NU-UC09, SO-UC02 | ❌ | ✅ | ❌ | ✅ | ❓ | ❌ | Không ẩn/xóa khi SP nằm trong đơn chưa hoàn tất. Soft delete. SS-UC02 chỉ có 'Đăng bán' nên Staff chưa rõ. |
| `APPRAISAL_REQUEST_CREATE` | Gửi yêu cầu thẩm định (SP phi điện tử) | NU-UC08 (nhánh 2), SO-UC02 | ❌ | ✅ | ❌ | ✅ | ⚙️ | ❌ | Suy ra từ luồng đăng bán. Staff đi theo SO-UC02. |
| `VOUCHER_SELLER_MANAGE` | Tạo/quản lý voucher cá nhân hoặc gian hàng | NU-UC11, SO-UC03 | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | NU: phải đang có SP đăng bán. Staff mặc định ❌, chỉ là mở rộng của SO-UC03. |

### M4. Thẩm định (Appraiser)

| Permission code | Mô tả | UC nguồn | G | NU | AP | SO | SS | AD | Ghi chú |
| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| `APPRAISER_APPLY` | Nộp hồ sơ xin làm Appraiser | AP-UC01 | ❌ | ⚠️ | ❌ | ❌ | ❌ | ❌ | Cần Legit Points ≥ ngưỡng, chưa là Appraiser. Nộp ở Normal workspace. |
| `APPRAISAL_QUEUE_VIEW` | Xem danh sách chờ định giá (đúng chuyên môn) | AP-UC02 | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | Lọc theo chuyên môn đã đăng ký. |
| `APPRAISAL_TASK_ACCEPT` | Nhận nhiệm vụ định giá | AP-UC02 | ❌ | ❌ | ⚠️ | ❌ | ❌ | ❌ | Không nhận SP do chính mình đăng; không bị tước quyền do điểm thấp; lock chống 2 người cùng nhận. |
| `APPRAISAL_SUBMIT` | Gửi kết quả thẩm định + giá đề xuất | AP-UC03 | ❌ | ❌ | ⚠️ | ❌ | ❌ | ❌ | Chỉ với task đã nhận, SP không phải của mình. Phí vào Escrow Appraiser độc lập với việc người bán đồng ý giá. |

### M5. Đàm phán giá (Offer)

| Permission code | Mô tả | UC nguồn | G | NU | AP | SO | SS | AD | Ghi chú |
| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| `OFFER_CREATE` | Gửi offer đàm phán giá (người mua) | NU-UC05 | ❌ | ⚠️ | ❌ | ❌ | ❌ | ❌ | Cần Legit Points > ngưỡng; offer ≥ 50% giá niêm yết; SP hỗ trợ trả giá. Owner/Appraiser phải chuyển Normal workspace; Staff không mua. |
| `OFFER_HANDLE` | Chấp nhận/từ chối/counter-offer | NU-UC10, SS-UC04 | ❌ | ✅ | ❌ | ✅ | ⚙️ | ❌ | Staff: nhóm 'Xử lý đàm phán giá', trong giới hạn giá sàn shop. Optimistic locking. |
| `OFFER_OVERRIDE_FLOOR` | Duyệt counter-offer thấp hơn giá sàn shop | SS-UC04 (luồng thay thế) | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | Staff phải xin Store Owner duyệt. |

### M6. Đơn hàng, đổi trả, khiếu nại, chat

| Permission code | Mô tả | UC nguồn | G | NU | AP | SO | SS | AD | Ghi chú |
| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| `ORDER_PAY` | Thanh toán đơn hàng (Escrow) | NU-UC06 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | Chỉ Normal workspace. Idempotency chống trả trùng. |
| `ORDER_TRACK` | Theo dõi trạng thái đơn mua | NU-UC07A | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | Chỉ người mua của đơn đó. |
| `ORDER_CONFIRM_RECEIVED` | Xác nhận đã nhận hàng | NU-UC07B | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | Kích hoạt đếm ngược đổi trả/khiếu nại. Tự động sau X ngày (SYSTEM). |
| `ORDER_PROCESS` | Xác nhận đơn, tạo vận đơn, xác nhận bàn giao vận chuyển | NU-UC12, SS-UC05, SS-UC06 | ❌ | ✅ | ❌ | ✅ | ⚙️ | ❌ | Staff: nhóm 'Xử lý đơn hàng'. Tạo vận đơn bất đồng bộ. |
| `ORDER_CANCEL` | Hủy đơn (trước khi giao) | NU-UC15, SS-UC05 | ❌ | ✅ | ❌ | ✅ | ⚠️ | ❌ | Người mua hoặc người bán. Staff hủy khi hết hàng có thể cần Owner xác nhận. Sau khi 'Đang giao' phải đi đường Đổi trả/Khiếu nại. |
| `ORDER_RETURN_REQUEST` | Yêu cầu đổi/trả hàng (người mua) | NU-UC16 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | Trong thời hạn đổi trả. |
| `ORDER_RETURN_HANDLE` | Người bán chấp nhận/từ chối đổi trả (24–48h) | NU-UC16 (bước 4) | ❌ | ✅ | ❌ | ✅ | ❓ | ❌ | GAP: không có UC riêng cho phía người bán. Từ chối → tự chuyển thành khiếu nại. |
| `DISPUTE_CREATE` | Gửi khiếu nại / mở tranh chấp | NU-UC17 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | Đóng băng tiền Escrow. Trong thời hạn khiếu nại. |
| `DISPUTE_RESPOND` | Người bán phản hồi tranh chấp | AD-UC06 (bước 2) | ❌ | ✅ | ❌ | ✅ | ❓ | ❌ | GAP: không có UC riêng. |
| `REVIEW_CREATE` | Đánh giá sao & nhận xét | NU-UC14 | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | Đơn đã hoàn tất, trong 30 ngày. |
| `CHAT_SEND` | Nhắn tin (WebSocket) | NU-UC13, SS-UC03 | ❌ | ✅ | ❌ | ✅ | ⚙️ | ❌ | Staff: nhóm 'Chăm sóc khách hàng'. Appraiser workspace không có chat. |

### M7. Ví & tài chính

| Permission code | Mô tả | UC nguồn | G | NU | AP | SO | SS | AD | Ghi chú |
| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| `WALLET_VIEW` | Xem số dư & lịch sử giao dịch của ví mình | NU-UC18, AP-UC04, SO-UC05 | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | Mỗi role có ví riêng: cá nhân / Appraiser / shop. Staff bị chặn (Owner nắm tài chính). |
| `WALLET_WITHDRAW` | Rút tiền về ngân hàng | NU-UC19, AP-UC04, SO-UC05 | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | Bắt buộc OTP 2FA; đã liên kết ngân hàng; số dư khả dụng > 0; chỉ tiền đã hết hạn đổi trả. Staff bị khóa rõ ràng. |
| `STORE_STATS_VIEW` | Xem thống kê doanh số gian hàng | SO-UC06 | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | Dashboard shop. |

### M8. Gian hàng

| Permission code | Mô tả | UC nguồn | G | NU | AP | SO | SS | AD | Ghi chú |
| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| `STORE_REGISTER` | Đăng ký mở gian hàng (tự động duyệt) | SO-UC01 | ❌ | ⚠️ | ❌ | ❌ | ❌ | ❌ | Chưa sở hữu shop nào, tài khoản không bị khóa. |
| `STORE_STAFF_MANAGE` | Thêm/xóa Staff, cấp/thu hồi nhóm quyền | SO-UC04 | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | Ghi audit log mọi thay đổi quyền. |
| `STORE_WORKSPACE_ACCESS` | Vào Store Staff Workspace | SS-UC01 | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | Chỉ thấy phân hệ được cấp; nếu chưa thuộc shop nào thì không có gì. |

### M9. Quản trị (Admin)

| Permission code | Mô tả | UC nguồn | G | NU | AP | SO | SS | AD | Ghi chú |
| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | --- |
| `ADMIN_PORTAL_ACCESS` | Đăng nhập cổng quản trị | AD-UC01 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | Cổng riêng, 2FA bắt buộc, không tự đăng ký. Log IP/thời gian. |
| `CATEGORY_MANAGE` | Quản lý danh mục & thuộc tính | AD-UC02 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | Không xóa danh mục đang có SP hoạt động. |
| `USER_STORE_MANAGE` | Xem, khóa/mở khóa user & gian hàng | AD-UC03 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | Ghi log người khóa, lý do. |
| `APPRAISER_APPROVE` | Duyệt/từ chối hồ sơ Appraiser | AD-UC04 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | Có thể yêu cầu bổ sung giấy tờ. |
| `VOUCHER_SYSTEM_MANAGE` | Quản lý voucher toàn sàn | AD-UC05 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | Có ngân sách tài trợ tối đa. |
| `DISPUTE_ADJUDICATE` | Phân xử tranh chấp (refund / release) | AD-UC06 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | Ghi log căn cứ quyết định. |
| `LEGIT_POINTS_MANAGE` | Trừ/phục hồi điểm tín nhiệm | AD-UC07 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | Điểm dưới ngưỡng → hệ thống tự khóa quyền. |
| `ANALYTICS_SYSTEM_VIEW` | Xem thống kê toàn hệ thống | AD-UC08 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | Dữ liệu tổng hợp batch/cache. |

## 5. Nhóm quyền của Store Staff (Owner cấp ở SO-UC04)

| Nhóm quyền (group) | Tên trong doc | Permission bao gồm |
| --- | --- | --- |
| `PRODUCT_MANAGEMENT` | Quản lý sản phẩm | `PRODUCT_CREATE`, `APPRAISAL_REQUEST_CREATE` |
| `CUSTOMER_CARE` | Chăm sóc khách hàng | `CHAT_SEND` |
| `ORDER_PROCESSING` | Xử lý đơn hàng | `ORDER_PROCESS`, `ORDER_CANCEL` (⚠️ có thể cần Owner xác nhận) |
| `PRICE_NEGOTIATION` | Xử lý đàm phán giá | `OFFER_HANDLE` (trong giới hạn giá sàn) |

`STORE_WORKSPACE_ACCESS` luôn có. Nếu Staff chưa/không còn được cấp nhóm nào thì không hiển thị chức năng nào (SS-UC01). Khi Owner thu hồi quyền, chặn từ phiên đăng nhập tiếp theo.

## 6. Ràng buộc ngoài role (guards ở tầng service)

| # | Quy tắc | Áp dụng cho | Nguồn |
| --- | --- | --- | --- |
| R1 | **Ownership**: chỉ thao tác trên tài nguyên của mình (sản phẩm, offer, đơn, ví). Với shop thì tài nguyên thuộc `storeId` của Owner/Staff. | `PRODUCT_MANAGE_OWN`, `OFFER_HANDLE`, `ORDER_*`, `WALLET_*` | NU-UC09, NU-UC10 |
| R2 | **Legit Points**: đăng bán yêu cầu điểm đạt chuẩn; gửi offer yêu cầu điểm > ngưỡng tối thiểu; đăng ký Appraiser yêu cầu điểm ≥ ngưỡng; điểm = 0 khóa tài khoản; Appraiser dưới ngưỡng bị tước quyền định giá tự động. | `PRODUCT_CREATE`, `OFFER_CREATE`, `APPRAISER_APPLY`, `APPRAISAL_*`, login | NU-UC01/05/08, AP-UC01/02, AD-UC07 |
| R3 | **Chống xung đột lợi ích**: Appraiser không định giá SP do chính mình đăng bán, không mua lại SP do mình thẩm định. | `APPRAISAL_TASK_ACCEPT`, `APPRAISAL_SUBMIT`, `ORDER_PAY` | Bảng Actor, AP-UC02/03 |
| R4 | **Workspace**: Appraiser/Owner chỉ mua khi ở Normal workspace. Staff không có Normal workspace. | `ORDER_PAY`, `OFFER_CREATE`, `WORKSPACE_SWITCH` | Bảng Actor, GEN-UC01 |
| R5 | **OTP 2FA** cho mọi lệnh rút tiền (NU, AP, SO). Nhập sai OTP quá 3 lần: hủy lệnh, khóa rút 15 phút. Mở rộng: OTP khi đổi SĐT/ngân hàng. | `WALLET_WITHDRAW`, `BANK_ACCOUNT_MANAGE` | NU-UC19, AP-UC04, SO-UC05 |
| R6 | **Escrow**: chỉ rút tiền đã giải ngân, tức là sau khi hết thời hạn đổi trả tính từ lúc người mua nhận hàng. Tiền bị đóng băng khi có tranh chấp. | `WALLET_WITHDRAW`, `DISPUTE_CREATE` | Bảng Actor, NU-UC17 |
| R7 | **Trạng thái shop/tài khoản**: shop bị Admin khóa thì chặn các quyền bán. Tài khoản bị khóa vẫn hoàn tất nghĩa vụ tài chính đang treo nhưng không tạo giao dịch mới. | M3, M5, M6 | SO-UC02, AD-UC03 |
| R8 | **Thời hạn**: hủy đơn chỉ khi chưa giao vận chuyển; đổi trả/khiếu nại trong hạn; đánh giá trong 30 ngày sau hoàn tất; người bán xử lý đổi trả trong 24–48h. | `ORDER_CANCEL`, `ORDER_RETURN_*`, `DISPUTE_CREATE`, `REVIEW_CREATE` | NU-UC14/15/16/17 |
| R9 | **Offer sàn**: offer < 50% giá niêm yết bị chặn; Staff không counter dưới giá sàn shop nếu chưa được Owner duyệt. | `OFFER_CREATE`, `OFFER_HANDLE` | NU-UC05, SS-UC04 |
| R10 | **Admin**: bắt buộc 2FA, cổng riêng, log mọi hành động khóa/hoàn tiền/trừ điểm. | M9 | AD-UC01/03/06/07 |

## 7. Quyền nội bộ (SYSTEM, không gán cho role người dùng)

| Code | Mô tả | Nguồn |
| --- | --- | --- |
| `PRICING_AUTO_RUN` | Tự định giá cho Thiết bị điện tử (giá bán trên sàn → giá giả lập → khấu hao), ghi log, < 2 giây | SPE-UC01 (include từ NU-UC08/SO-UC02/SS-UC02) |
| `ESCROW_HOLD` | Tạm giữ tiền khi thanh toán thành công | NU-UC06 |
| `ESCROW_RELEASE` | Giải ngân cho người bán khi hết hạn đổi trả | Bảng Actor, NU-UC07B |
| `ESCROW_REFUND` | Hoàn tiền khi hủy đơn / Admin quyết định | NU-UC15, AD-UC06 |
| `ESCROW_FREEZE` | Đóng băng tiền khi có khiếu nại | NU-UC17 |
| `APPRAISER_FEE_TRANSFER` | Chuyển phí thẩm định vào ví Escrow của Appraiser | AP-UC03 |
| `ORDER_AUTO_CONFIRM_RECEIVED` | Tự chuyển 'Đã nhận hàng' sau X ngày nếu người mua không bấm | NU-UC07B |
| `APPRAISAL_TASK_REVOKE` | Thu hồi task Appraiser quá hạn (ví dụ 48h) | AP-UC03 |
| `LEGIT_POINTS_AUTO_LOCK` | Tự khóa quyền khi điểm dưới ngưỡng; tự trừ điểm khi người bán hủy đơn nhiều lần | AD-UC07, NU-UC15 |

## 8. Permission mở rộng (tùy chọn, chưa đưa vào matrix)

Chỉ thêm khi chủ dự án chốt làm: `REVIEW_REPLY` (người bán phản hồi đánh giá, NU-UC14) · `APPRAISAL_DISPUTE` (người bán khiếu nại giá thẩm định → Admin xem lại, AP-UC03) · `STORE_PRODUCT_BULK_UPLOAD` (SO-UC02) · `VOUCHER_SELLER_MANAGE` cho Staff (SO-UC03) · phân quyền Staff chi tiết hơn, ví dụ chỉ xem (SO-UC04) · `REPORT_EXPORT` (NU-UC18, SO-UC06, AD-UC08) · `WITHDRAW_SCHEDULE` (NU-UC19, SO-UC05) · `OFFER_AUTO_REJECT_CONFIG` (NU-UC10).

## 9. Seed data (CSV: role → permission)

Chỉ gồm ô ✅ (`FULL`), ⚠️ (`CONDITIONAL`), ⚙️ (`DELEGATED`). Bỏ ❌ và ❓. `staff_group` chỉ có với STORE_STAFF.

```csv
role,permission_code,grant_type,staff_group
GUEST,PRODUCT_SEARCH,FULL,
GUEST,PRODUCT_VIEW_DETAIL,FULL,
GUEST,MARKET_PRICE_CHART_VIEW,FULL,
NORMAL_USER,PRODUCT_SEARCH,FULL,
NORMAL_USER,PRODUCT_VIEW_DETAIL,FULL,
NORMAL_USER,MARKET_PRICE_CHART_VIEW,FULL,
NORMAL_USER,PROFILE_MANAGE,FULL,
NORMAL_USER,BANK_ACCOUNT_MANAGE,FULL,
NORMAL_USER,WORKSPACE_SWITCH,CONDITIONAL,
NORMAL_USER,PRODUCT_CREATE,CONDITIONAL,
NORMAL_USER,PRODUCT_MANAGE_OWN,FULL,
NORMAL_USER,APPRAISAL_REQUEST_CREATE,FULL,
NORMAL_USER,VOUCHER_SELLER_MANAGE,FULL,
NORMAL_USER,APPRAISER_APPLY,CONDITIONAL,
NORMAL_USER,OFFER_CREATE,CONDITIONAL,
NORMAL_USER,OFFER_HANDLE,FULL,
NORMAL_USER,ORDER_PAY,FULL,
NORMAL_USER,ORDER_TRACK,FULL,
NORMAL_USER,ORDER_CONFIRM_RECEIVED,FULL,
NORMAL_USER,ORDER_PROCESS,FULL,
NORMAL_USER,ORDER_CANCEL,FULL,
NORMAL_USER,ORDER_RETURN_REQUEST,FULL,
NORMAL_USER,ORDER_RETURN_HANDLE,FULL,
NORMAL_USER,DISPUTE_CREATE,FULL,
NORMAL_USER,DISPUTE_RESPOND,FULL,
NORMAL_USER,REVIEW_CREATE,FULL,
NORMAL_USER,CHAT_SEND,FULL,
NORMAL_USER,WALLET_VIEW,FULL,
NORMAL_USER,WALLET_WITHDRAW,FULL,
NORMAL_USER,STORE_REGISTER,CONDITIONAL,
APPRAISER,PRODUCT_SEARCH,FULL,
APPRAISER,PRODUCT_VIEW_DETAIL,FULL,
APPRAISER,MARKET_PRICE_CHART_VIEW,FULL,
APPRAISER,PROFILE_MANAGE,FULL,
APPRAISER,BANK_ACCOUNT_MANAGE,FULL,
APPRAISER,WORKSPACE_SWITCH,FULL,
APPRAISER,APPRAISAL_QUEUE_VIEW,FULL,
APPRAISER,APPRAISAL_TASK_ACCEPT,CONDITIONAL,
APPRAISER,APPRAISAL_SUBMIT,CONDITIONAL,
APPRAISER,WALLET_VIEW,FULL,
APPRAISER,WALLET_WITHDRAW,FULL,
STORE_OWNER,PRODUCT_SEARCH,FULL,
STORE_OWNER,PRODUCT_VIEW_DETAIL,FULL,
STORE_OWNER,MARKET_PRICE_CHART_VIEW,FULL,
STORE_OWNER,PROFILE_MANAGE,FULL,
STORE_OWNER,BANK_ACCOUNT_MANAGE,FULL,
STORE_OWNER,WORKSPACE_SWITCH,FULL,
STORE_OWNER,PRODUCT_CREATE,FULL,
STORE_OWNER,PRODUCT_MANAGE_OWN,FULL,
STORE_OWNER,APPRAISAL_REQUEST_CREATE,FULL,
STORE_OWNER,VOUCHER_SELLER_MANAGE,FULL,
STORE_OWNER,OFFER_HANDLE,FULL,
STORE_OWNER,OFFER_OVERRIDE_FLOOR,FULL,
STORE_OWNER,ORDER_PROCESS,FULL,
STORE_OWNER,ORDER_CANCEL,FULL,
STORE_OWNER,ORDER_RETURN_HANDLE,FULL,
STORE_OWNER,DISPUTE_RESPOND,FULL,
STORE_OWNER,CHAT_SEND,FULL,
STORE_OWNER,WALLET_VIEW,FULL,
STORE_OWNER,WALLET_WITHDRAW,FULL,
STORE_OWNER,STORE_STATS_VIEW,FULL,
STORE_OWNER,STORE_STAFF_MANAGE,FULL,
STORE_STAFF,PRODUCT_SEARCH,FULL,
STORE_STAFF,PRODUCT_VIEW_DETAIL,FULL,
STORE_STAFF,MARKET_PRICE_CHART_VIEW,FULL,
STORE_STAFF,PRODUCT_CREATE,DELEGATED,PRODUCT_MANAGEMENT
STORE_STAFF,APPRAISAL_REQUEST_CREATE,DELEGATED,PRODUCT_MANAGEMENT
STORE_STAFF,OFFER_HANDLE,DELEGATED,PRICE_NEGOTIATION
STORE_STAFF,ORDER_PROCESS,DELEGATED,ORDER_PROCESSING
STORE_STAFF,ORDER_CANCEL,CONDITIONAL,ORDER_PROCESSING
STORE_STAFF,CHAT_SEND,DELEGATED,CUSTOMER_CARE
STORE_STAFF,STORE_WORKSPACE_ACCESS,FULL,
ADMIN,PRODUCT_SEARCH,FULL,
ADMIN,PRODUCT_VIEW_DETAIL,FULL,
ADMIN,MARKET_PRICE_CHART_VIEW,FULL,
ADMIN,ADMIN_PORTAL_ACCESS,FULL,
ADMIN,CATEGORY_MANAGE,FULL,
ADMIN,USER_STORE_MANAGE,FULL,
ADMIN,APPRAISER_APPROVE,FULL,
ADMIN,VOUCHER_SYSTEM_MANAGE,FULL,
ADMIN,DISPUTE_ADJUDICATE,FULL,
ADMIN,LEGIT_POINTS_MANAGE,FULL,
ADMIN,ANALYTICS_SYSTEM_VIEW,FULL,
```

## 10. Gợi ý schema (PostgreSQL)

```sql
CREATE TABLE permissions (
  id          BIGSERIAL PRIMARY KEY,
  code        VARCHAR(64)  NOT NULL UNIQUE,   -- ví dụ PRODUCT_CREATE
  module      VARCHAR(32)  NOT NULL,          -- M1..M9
  name        VARCHAR(255) NOT NULL,
  source_uc   VARCHAR(128)
);

CREATE TABLE roles (
  id   BIGSERIAL PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE            -- GUEST, NORMAL_USER, APPRAISER, STORE_OWNER, STORE_STAFF, ADMIN
);

CREATE TABLE role_permissions (
  role_id       BIGINT NOT NULL REFERENCES roles(id),
  permission_id BIGINT NOT NULL REFERENCES permissions(id),
  grant_type    VARCHAR(16) NOT NULL DEFAULT 'FULL',  -- FULL | CONDITIONAL | DELEGATED
  PRIMARY KEY (role_id, permission_id)
);

-- Nhóm quyền của Staff do Owner cấp, gắn theo từng gian hàng
CREATE TABLE store_staff_grants (
  store_id    BIGINT NOT NULL,
  staff_id    BIGINT NOT NULL,
  group_code  VARCHAR(32) NOT NULL,           -- PRODUCT_MANAGEMENT | CUSTOMER_CARE | ORDER_PROCESSING | PRICE_NEGOTIATION
  granted_by  BIGINT NOT NULL,
  granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (store_id, staff_id, group_code)
);
```

Gợi ý JWT claims: `sub`, `roles[]`, `activeWorkspace` (`NORMAL` | `APPRAISER` | `STORE_OWNER` | `STORE_STAFF` | `ADMIN`), `storeId` (Owner/Staff), `staffGroups[]` (Staff).

## 11. Điểm tài liệu chưa rõ (cần chốt trước khi seed các ô ❓)

1. **`ORDER_RETURN_HANDLE`** và **`DISPUTE_RESPOND`**: doc mô tả phía người bán trong luồng NU-UC16 và AD-UC06 nhưng không có UC riêng. Mình đã cho NU/SO ✅ (suy ra), còn Staff thì chưa rõ.
2. **`PRODUCT_MANAGE_OWN` cho Staff**: SS-UC02 chỉ có 'Đăng bán'. Staff có được sửa/ẩn/xóa không?
3. **`PROFILE_MANAGE` cho Staff**: NU-UC02 không liệt kê Staff, nhưng Staff vẫn là tài khoản đăng nhập.
4. **Ngưỡng Legit Points**: doc dùng nhiều cách nói ('đạt chuẩn', '> ngưỡng tối thiểu', '= 0 bị khóa', 'dưới ngưỡng bị tước quyền định giá'). Cần con số cụ thể theo từng hành động.
5. **Appraiser workspace** hiện không có chat/khiếu nại/mua bán (phải chuyển Normal). Đúng ý bạn chưa?
6. **Một user vừa là Store Owner vừa là Appraiser**: GEN-UC01 ngụ ý có, nhưng chưa có quy tắc cấm/cho phép rõ ràng. Nếu cho phép thì cần thêm điều kiện chống xung đột lợi ích khi Appraiser định giá SP của chính shop mình.
7. **Store Staff xem doanh thu/thống kê**: hiện ❌ vì không có trong SS-UC01–06.
