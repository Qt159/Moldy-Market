# Moldy Market — Requirements Document

---

## Mục lục

1. [Tổng quan dự án](#1-tổng-quan-dự-án)
2. [Actors](#2-actors)
3. [Functional Requirements](#3-functional-requirements)
   - 3.1 [Authentication & Authorization](#31-authentication--authorization)
   - 3.2 [User Profile Management](#32-user-profile-management)
   - 3.3 [Product Listing](#33-product-listing)
   - 3.4 [Search & Discovery](#34-search--discovery)
   - 3.5 [Pricing Engine](#35-pricing-engine)
   - 3.6 [Offer & Negotiation](#36-offer--negotiation)
   - 3.7 [Order & Checkout](#37-order--checkout)
   - 3.8 [Payment & Escrow](#38-payment--escrow)
   - 3.9 [Shipping](#39-shipping)
   - 3.10 [Review & Rating](#310-review--rating)
   - 3.11 [Voucher & Promotion](#311-voucher--promotion)
   - 3.12 [Real-time Chat](#312-real-time-chat)
   - 3.13 [Notification](#313-notification)
   - 3.14 [Dispute & Complaint](#314-dispute--complaint)
   - 3.15 [Wallet & Withdrawal](#315-wallet--withdrawal)
   - 3.16 [Appraiser Workflow](#316-appraiser-workflow)
   - 3.17 [Store Owner & Staff](#317-store-owner--staff)
   - 3.18 [Admin Portal](#318-admin-portal)
   - 3.19 [Workspace Switching](#319-workspace-switching)
4. [Non-Functional Requirements](#4-non-functional-requirements)
5. [Constraints & Assumptions](#5-constraints--assumptions)

---

## 1. Tổng quan dự án

**Moldy Market** là nền tảng thương mại điện tử chuyên dụng cho mua bán hàng hóa đã qua sử dụng (secondhand). Hệ thống cho phép người dùng cá nhân đăng bán đồ cũ, thương lượng giá, mua sắm an toàn thông qua cơ chế Escrow, và được hỗ trợ bởi hệ thống định giá thông minh.

### Mục tiêu cốt lõi

- Tạo sân chơi minh bạch, an toàn cho các giao dịch hàng đã qua sử dụng.
- Giảm rủi ro lừa đảo bằng cơ chế thanh toán Escrow.
- Cung cấp giá tham chiếu tin cậy qua hệ thống định giá tự động và thẩm định chuyên gia.
- Hỗ trợ cả người bán cá nhân lẫn cửa hàng kinh doanh nhỏ.

---

## 2. Actors

| Actor | Vai trò | Mô tả |
|---|---|---|
| **Guest** | Khách vãng lai | Xem sản phẩm, tìm kiếm, xem giá — không cần đăng nhập |
| **Normal User** | Người dùng thường | Mua và bán hàng cá nhân. Đóng cả hai vai trò buyer và seller |
| **Appraiser** | Chuyên gia thẩm định | Kế thừa từ Normal User. Thẩm định và định giá hàng phi điện tử |
| **System Pricing Engine** | Hệ thống định giá tự động | Tự động định giá thiết bị điện tử bằng công thức khấu hao |
| **Store Owner** | Chủ cửa hàng | Kế thừa từ Normal User. Quản lý gian hàng, nhân viên, doanh thu |
| **Store Staff** | Nhân viên cửa hàng | Vận hành gian hàng: đăng sản phẩm, xử lý đơn, chat với khách |
| **Admin** | Quản trị viên | Quản lý toàn bộ nền tảng: user, danh mục, voucher, tranh chấp |

---

## 3. Functional Requirements

---

### 3.1 Authentication & Authorization

**NU-UC01 — Đăng ký / Đăng nhập**

| ID | Yêu cầu |
|---|---|
| AUTH-01 | Hệ thống cho phép đăng ký tài khoản bằng Email + Mật khẩu |
| AUTH-02 | Hệ thống hỗ trợ đăng nhập bằng Google OAuth |
| AUTH-03 | Hệ thống gửi OTP qua email để xác minh tài khoản khi đăng ký |
| AUTH-04 | Mật khẩu phải được hash bằng BCrypt trước khi lưu |
| AUTH-05 | Hệ thống trả về JWT sau khi đăng nhập thành công |
| AUTH-06 | JWT được sử dụng để xác thực các request tiếp theo |
| AUTH-07 | Tài khoản bị khóa (Legit Points = 0) không thể đăng nhập |
| AUTH-08 | API đăng nhập phải phản hồi trong vòng 2 giây |
| AUTH-09 | Hỗ trợ chức năng quên mật khẩu — gửi link reset qua email |
| AUTH-10 | Xác thực 2FA bằng OTP khi thực hiện rút tiền |

---

### 3.2 User Profile Management

**NU-UC02 — Quản lý hồ sơ cá nhân**

| ID | Yêu cầu |
|---|---|
| PROFILE-01 | Người dùng có thể xem và chỉnh sửa: họ tên, avatar, số điện thoại, địa chỉ giao hàng |
| PROFILE-02 | Người dùng có thể thêm hoặc thay đổi thông tin tài khoản ngân hàng liên kết |
| PROFILE-03 | Avatar sau khi upload phải được nén và lưu trên Cloud Storage (S3); không lưu trực tiếp trên server |
| PROFILE-04 | Thông tin tài khoản ngân hàng phải được mã hóa tại tầng database |
| PROFILE-05 | Thay đổi số điện thoại hoặc tài khoản ngân hàng yêu cầu xác thực OTP về email/SĐT cũ |
| PROFILE-06 | Số điện thoại phải là duy nhất trong hệ thống |

---

### 3.3 Product Listing

**NU-UC08 — Đăng bán sản phẩm**

| ID | Yêu cầu |
|---|---|
| LISTING-01 | Người bán có thể tạo bài đăng với thông tin: tiêu đề, mô tả, danh mục, tình trạng, hình ảnh |
| LISTING-02 | Khi đăng sản phẩm thuộc danh mục **điện tử**, hệ thống tự động kích hoạt Auto-Pricing Engine để gợi ý giá |
| LISTING-03 | Khi đăng sản phẩm thuộc danh mục **phi điện tử cần thẩm định**, hệ thống chuyển yêu cầu tới Appraiser |
| LISTING-04 | Người bán có thể tự thiết lập giá mà không cần định giá |
| LISTING-05 | Hình ảnh phải được nén và lưu trên S3 trước khi đăng |
| LISTING-06 | Hệ thống chặn nội dung chứa từ khóa cấm hoặc hình ảnh sai định dạng |
| LISTING-07 | Người bán phải có Legit Points đạt ngưỡng tối thiểu để đăng sản phẩm |
| LISTING-08 | Sau khi đăng, bài viết hiển thị công khai trên sàn ngay lập tức |

**NU-UC09 — Quản lý sản phẩm cá nhân**

| ID | Yêu cầu |
|---|---|
| LISTING-09 | Người bán có thể chỉnh sửa, ẩn hoặc xóa bài đăng của chính mình |
| LISTING-10 | Không thể ẩn hoặc xóa sản phẩm đang nằm trong đơn hàng chưa hoàn tất |
| LISTING-11 | Hệ thống sử dụng soft delete để giữ toàn vẹn dữ liệu lịch sử tài chính |

---

### 3.4 Search & Discovery

**NU-UC03 — Tìm kiếm và lọc sản phẩm**

| ID | Yêu cầu |
|---|---|
| SEARCH-01 | Tất cả vai trò kể cả Guest đều có thể tìm kiếm sản phẩm |
| SEARCH-02 | Hệ thống hỗ trợ tìm kiếm theo: từ khóa, danh mục, khoảng giá, tình trạng sản phẩm, độ uy tín người bán |
| SEARCH-03 | Kết quả trả về dạng lưới, có phân trang, sắp xếp mặc định theo độ liên quan hoặc mới nhất |
| SEARCH-04 | Thời gian phản hồi kết quả tìm kiếm phải dưới 1 giây |
| SEARCH-05 | Các trường tìm kiếm văn bản phải được đánh index hoặc sử dụng full-text search |
| SEARCH-06 | Hỗ trợ auto-complete / gợi ý từ khóa khi người dùng đang gõ |
| SEARCH-07 | Lưu lịch sử tìm kiếm cho người dùng đã đăng nhập |
| SEARCH-08 | Hỗ trợ sắp xếp kết quả: giá tăng dần, giá giảm dần, mới nhất |

**NU-UC04 — Xem biểu đồ giá thị trường**

| ID | Yêu cầu |
|---|---|
| SEARCH-09 | Người dùng xem biểu đồ lịch sử giá thị trường cho các thiết bị điện tử trên trang chi tiết sản phẩm |
| SEARCH-10 | Biểu đồ hiển thị dưới dạng Line Chart, mức giá bán trung bình theo từng tháng trong 6 tháng gần nhất |
| SEARCH-11 | Dữ liệu biểu đồ được tính toán định kỳ và cache (Redis) để đảm bảo phản hồi dưới 1 giây |
| SEARCH-12 | Nếu chưa đủ dữ liệu, hệ thống ẩn biểu đồ và hiển thị thông báo phù hợp |

---

### 3.5 Pricing Engine

**SPE-UC01 — Định giá tự động (Auto-Pricing)**

| ID | Yêu cầu |
|---|---|
| PRICING-01 | Engine tự động kích hoạt khi sản phẩm được đăng thuộc danh mục điện tử |
| PRICING-02 | Engine truy vấn giá tham chiếu từ lịch sử giao dịch nội bộ hoặc dữ liệu thị trường giả lập |
| PRICING-03 | Engine tính khấu hao dựa trên: tình trạng bảo hành, tình trạng hư hỏng, thời gian sử dụng |
| PRICING-04 | Engine trả về giá đề xuất trong vòng 2 giây |
| PRICING-05 | Người bán có thể điều chỉnh giá đề xuất trước khi đăng |
| PRICING-06 | Kết quả định giá được ghi log để phục vụ tối ưu công thức sau này |
| PRICING-07 | Khi không đủ dữ liệu tham chiếu, hệ thống trả về giá mặc định theo phần trăm khấu hao cơ bản kèm cảnh báo |

**AP-UC01/02/03 — Thẩm định bởi Appraiser**

| ID | Yêu cầu |
|---|---|
| PRICING-08 | Appraiser đăng ký bằng cách nộp hồ sơ chuyên môn; Admin duyệt thủ công |
| PRICING-09 | Hệ thống hiển thị danh sách yêu cầu thẩm định theo đúng chuyên môn của Appraiser |
| PRICING-10 | Appraiser nhận nhiệm vụ — nhiệm vụ đó được khóa khỏi Appraiser khác |
| PRICING-11 | Appraiser không được thẩm định sản phẩm do chính mình đăng bán |
| PRICING-12 | Sau khi gửi kết quả, phí dịch vụ được chuyển vào ví Escrow của Appraiser — độc lập với quyết định của người bán |
| PRICING-13 | Nếu Appraiser không phản hồi trong 48 giờ, nhiệm vụ bị thu hồi và trả lại hàng đợi |
| PRICING-14 | Appraiser có thể yêu cầu người bán bổ sung ảnh nếu không đủ thông tin |

---

### 3.6 Offer & Negotiation

**NU-UC05 — Gửi Offer / NU-UC10 — Xử lý Offer**

| ID | Yêu cầu |
|---|---|
| OFFER-01 | Người mua có thể gửi mức giá đề nghị lên sản phẩm hỗ trợ tính năng trả giá |
| OFFER-02 | Hệ thống chặn Offer có giá thấp hơn 50% giá niêm yết |
| OFFER-03 | Nếu sản phẩm được mua bởi người khác trong khi đang soạn Offer, hệ thống thông báo "sản phẩm không còn khả dụng" |
| OFFER-04 | Hệ thống giới hạn số lần gửi Offer tối đa mỗi sản phẩm để tránh spam |
| OFFER-05 | Người bán có thể: Chấp nhận, Từ chối, hoặc Counter-offer |
| OFFER-06 | Khi sản phẩm được mua ngay (Buy Now), các Offer đang chờ tự động bị hủy |
| OFFER-07 | Hệ thống sử dụng Optimistic Locking để tránh xung đột ghi đè |
| OFFER-08 | Offer có thời hạn hiệu lực — sau thời hạn tự chuyển sang trạng thái EXPIRED |
| OFFER-09 | Người bán có thể thiết lập Auto-reject cho Offer dưới mức giá sàn |

---

### 3.7 Order & Checkout

**NU-UC06/07/12/15 — Đặt hàng và Xử lý**

| ID | Yêu cầu |
|---|---|
| ORDER-01 | Người mua có thể thanh toán trực tiếp từ giỏ hàng hoặc từ Offer đã được chấp nhận |
| ORDER-02 | Người mua có thể theo dõi trạng thái đơn hàng theo thời gian thực |
| ORDER-03 | Người mua xác nhận đã nhận hàng — kích hoạt đếm ngược thời gian đổi trả |
| ORDER-04 | Nếu người mua không bấm xác nhận sau X ngày kể từ khi vận chuyển báo "giao thành công", hệ thống tự động chuyển trạng thái |
| ORDER-05 | Người bán xác nhận đơn hàng, đóng gói, và tạo mã vận đơn qua đối tác vận chuyển |
| ORDER-06 | Nếu hết hàng ngoài ý muốn, người bán hủy đơn — hệ thống hoàn tiền qua Escrow |
| ORDER-07 | Người mua hoặc người bán có thể hủy đơn khi chưa phát sinh vận chuyển |
| ORDER-08 | Không cho phép hủy đơn sau khi đã bàn giao hàng cho bưu tá |
| ORDER-09 | Tạo mã vận đơn và in PDF xử lý bất đồng bộ |
| ORDER-10 | Người bán tự ý hủy đơn nhiều lần sẽ bị trừ Legit Points |
| ORDER-11 | Hệ thống gửi Push Notification qua AWS SNS khi đơn hàng đổi trạng thái "Đang giao" hoặc "Đã giao" |

---

### 3.8 Payment & Escrow

**NU-UC06 — Thanh toán Escrow**

| ID | Yêu cầu |
|---|---|
| PAYMENT-01 | Hệ thống sử dụng SePay để nhận thanh toán qua chuyển khoản ngân hàng |
| PAYMENT-02 | Khi tạo đơn hàng, hệ thống sinh một mã nội dung chuyển khoản unique (VD: `MM-ORDER-123`) và hiển thị thông tin tài khoản ngân hàng + QR code cho Buyer |
| PAYMENT-03 | SePay phát hiện tiền vào tài khoản ngân hàng của sàn và gọi webhook về backend để xác nhận thanh toán |
| PAYMENT-04 | Backend verify webhook bằng cách đối chiếu `transferCode` và `amount` — nếu khớp mới cập nhật trạng thái đơn hàng |
| PAYMENT-05 | Tiền thanh toán được giữ trong ví Escrow — không giải ngân cho người bán ngay lập tức |
| PAYMENT-06 | Tiền được giải ngân cho người bán sau khi hết thời gian đổi trả tính từ thời điểm người mua nhận hàng |
| PAYMENT-07 | Đơn hàng quá thời gian chờ thanh toán tự động bị hủy, giải phóng kho |
| PAYMENT-08 | Hệ thống chống xử lý trùng webhook bằng cách dùng `bankTxnId` từ SePay làm unique key (Idempotency) |
| PAYMENT-09 | Nếu không nhận được webhook sau thời gian quy định, hệ thống tự động hủy đơn |

---

### 3.9 Shipping

**NU-UC07A/07B — Vận chuyển**

| ID | Yêu cầu |
|---|---|
| SHIPPING-01 | Hệ thống tích hợp API đối tác vận chuyển để tạo mã vận đơn và tra cứu trạng thái |
| SHIPPING-02 | Người mua có thể tra cứu trạng thái vận chuyển theo thời gian thực |
| SHIPPING-03 | Nếu API đối tác không phản hồi, hệ thống hiển thị trạng thái gần nhất từ database nội bộ |
| SHIPPING-04 | API đối tác vận chuyển phải có cơ chế Timeout và Fallback để tránh giao diện bị treo |

---

### 3.10 Review & Rating

**NU-UC14 — Đánh giá uy tín**

| ID | Yêu cầu |
|---|---|
| REVIEW-01 | Người mua có thể đánh giá sau khi đơn hàng hoàn tất: chọn sao (1–5), nhập nhận xét, tải lên ảnh/video |
| REVIEW-02 | Mỗi người dùng chỉ tạo được một review cho một order |
| REVIEW-03 | Chức năng đánh giá đóng sau 30 ngày kể từ khi đơn hàng hoàn tất |
| REVIEW-04 | Điểm uy tín trung bình của người bán được tính lại bất đồng bộ sau mỗi review |
| REVIEW-05 | Đánh giá hiển thị công khai trên trang sản phẩm và hồ sơ người bán |
| REVIEW-06 | Người bán có thể phản hồi lại nhận xét của người mua |

---

### 3.11 Voucher & Promotion

**NU-UC11 / SO-UC03 / AD-UC05 — Voucher**

| ID | Yêu cầu |
|---|---|
| VOUCHER-01 | Normal User có thể tạo voucher cá nhân áp dụng cho sản phẩm của mình |
| VOUCHER-02 | Store Owner có thể tạo voucher cho gian hàng |
| VOUCHER-03 | Admin có thể tạo voucher toàn sàn (freeship, sale lễ/tết) |
| VOUCHER-04 | Voucher cá nhân của Admin = `seller_id IS NULL`; voucher của seller có `seller_id` cụ thể |
| VOUCHER-05 | Hệ thống sử dụng hàng đợi (Queue) để tránh Race Condition khi nhiều user cùng áp mã trong flash sale |
| VOUCHER-06 | Khi hết số lượng, mã tự động ẩn khỏi giao diện người mua |
| VOUCHER-07 | Mỗi user chỉ sử dụng cùng một voucher một lần (unique constraint) |
| VOUCHER-08 | Khi ngân sách tài trợ hết trước thời hạn, hệ thống tự ẩn mã và thông báo cho Admin |

---

### 3.12 Real-time Chat

**NU-UC13 — Nhắn tin trực tiếp**

| ID | Yêu cầu |
|---|---|
| CHAT-01 | Hỗ trợ chat 1-1 giữa người mua và người bán |
| CHAT-02 | Tin nhắn được gửi theo thời gian thực qua WebSocket |
| CHAT-03 | Độ trễ tin nhắn tối đa 500ms |
| CHAT-04 | Nếu mất kết nối, tin nhắn chuyển sang trạng thái chờ gửi và tự động thử lại khi có mạng |
| CHAT-05 | Hỗ trợ đính kèm hình ảnh và link sản phẩm |
| CHAT-06 | Lịch sử chat được lưu trữ bảo mật |
| CHAT-07 | Tích hợp bộ lọc từ khóa nhạy cảm / thông tin liên hệ ngoài sàn |

---

### 3.13 Notification

| ID | Yêu cầu |
|---|---|
| NOTIF-01 | Hệ thống gửi thông báo khi: Offer được gửi/chấp nhận/từ chối, Đơn hàng thay đổi trạng thái, Tiền được giải ngân, Có tranh chấp mới |
| NOTIF-02 | Notification được lưu trên DynamoDB, tách biệt khỏi PostgreSQL |
| NOTIF-03 | Người dùng có thể xem danh sách thông báo và đánh dấu đã đọc |
| NOTIF-04 | Notification có thời gian hết hạn (TTL) |
| NOTIF-05 | Hệ thống hỗ trợ Push Notification qua AWS SNS trên thiết bị di động |

---

### 3.14 Dispute & Complaint

**NU-UC16/17 / AD-UC06 — Đổi trả và Tranh chấp**

| ID | Yêu cầu |
|---|---|
| DISPUTE-01 | Người mua gửi yêu cầu đổi/trả hàng trong thời hạn quy định sau khi nhận hàng |
| DISPUTE-02 | Người bán có 24–48 giờ để chấp nhận hoặc từ chối yêu cầu đổi trả |
| DISPUTE-03 | Nếu người bán từ chối, hệ thống tự động chuyển case sang khiếu nại cho Admin xử lý |
| DISPUTE-04 | Người mua có thể mở khiếu nại: chọn lý do, tải lên bằng chứng (hình ảnh, video) |
| DISPUTE-05 | Khi khiếu nại được tạo, tiền trong Escrow bị đóng băng |
| DISPUTE-06 | Bằng chứng media được lưu trên S3 với Signed URL |
| DISPUTE-07 | Admin xem xét bằng chứng và ra quyết định: hoàn tiền toàn bộ/một phần cho người mua, hoặc nhả tiền cho người bán |
| DISPUTE-08 | Mọi hành động của Admin trong tranh chấp đều được ghi log chi tiết |
| DISPUTE-09 | Cho phép hai bên thương lượng trực tiếp trước khi Admin can thiệp chính thức |

---

### 3.15 Wallet & Withdrawal

**NU-UC18/19 / SO-UC05 / AP-UC04 — Ví và Rút tiền**

| ID | Yêu cầu |
|---|---|
| WALLET-01 | Người dùng có thể xem lịch sử biến động số dư ví Escrow |
| WALLET-02 | Hỗ trợ lọc lịch sử theo: khoảng thời gian, loại giao dịch (mua/bán/rút/nạp) |
| WALLET-03 | Người bán rút tiền từ ví về tài khoản ngân hàng liên kết — bắt buộc xác thực OTP (2FA) |
| WALLET-04 | Nếu nhập sai OTP quá 3 lần: yêu cầu bị hủy, giao diện khóa rút tiền 15 phút |
| WALLET-05 | Lệnh rút tiền xử lý bất đồng bộ, có đối soát với ngân hàng |
| WALLET-06 | Tiền hoàn trả Escrow khi hủy đơn phải thực hiện trong vòng tối đa 1 phút |
| WALLET-07 | Hỗ trợ đặt lịch rút tiền tự động theo chu kỳ |

---

### 3.16 Appraiser Workflow

**AP-UC01/02/03/04 — Nghiệp vụ Appraiser**

| ID | Yêu cầu |
|---|---|
| APPRAISER-01 | Normal User đăng ký trở thành Appraiser bằng cách nộp hồ sơ/chứng chỉ chuyên môn |
| APPRAISER-02 | Admin duyệt hồ sơ thủ công; hồ sơ được lưu trên S3 với kiểm soát truy cập |
| APPRAISER-03 | Tài khoản đang vi phạm (Legit Points thấp) không được đăng ký Appraiser |
| APPRAISER-04 | Appraiser không được thẩm định sản phẩm do chính mình đăng bán |
| APPRAISER-05 | Appraiser không được mua lại sản phẩm mà mình đã thẩm định |
| APPRAISER-06 | Appraiser có thể Switch về Normal Account để mua/bán cá nhân |
| APPRAISER-07 | Phí dịch vụ được trả ngay khi Appraiser gửi kết quả — không phụ thuộc vào quyết định của người bán |
| APPRAISER-08 | Appraiser rút phí dịch vụ từ ví Escrow về ngân hàng — yêu cầu OTP |

---

### 3.17 Store Owner & Staff

**SO-UC01/02/03/04/05/06 — Chủ cửa hàng**

| ID | Yêu cầu |
|---|---|
| STORE-01 | Normal User đăng ký mở gian hàng — được phê duyệt tự động (không qua Admin) |
| STORE-02 | Tên gian hàng phải là duy nhất trong hệ thống |
| STORE-03 | Store Owner quản lý sản phẩm: đăng bán, chỉnh sửa, ẩn, xóa |
| STORE-04 | Store Owner thêm/xóa Store Staff và gán nhóm quyền |
| STORE-05 | Store Owner xem thống kê doanh số qua Dashboard: số đơn, mặt hàng bán chạy, tỷ lệ hủy |
| STORE-06 | Store Owner rút tiền từ ví Escrow của shop — bắt buộc OTP |
| STORE-07 | Store Owner muốn mua hàng cá nhân phải Switch về Normal Account |
| STORE-08 | Hỗ trợ bulk upload sản phẩm qua file Excel/CSV |
| STORE-09 | Hỗ trợ cấu hình rút tiền tự động định kỳ |

**SS-UC01/02/03/04/05/06 — Nhân viên cửa hàng**

| ID | Yêu cầu |
|---|---|
| STORE-10 | Store Staff đăng nhập và truy cập vào Store Workspace theo quyền được gán |
| STORE-11 | Store Staff không có chức năng Mua hàng và không thể Switch sang Normal Account |
| STORE-12 | Store Staff không có quyền Rút tiền |
| STORE-13 | Mọi thao tác của Staff được ghi log (audit trail) để Store Owner đối soát |
| STORE-14 | Bài đăng của Staff được gắn nhãn "Đăng bởi nhân viên [tên]" để truy vết |
| STORE-15 | Staff xử lý Offer trong giới hạn giá được Store Owner cho phép; nếu vượt mức cần xin duyệt |

---

### 3.18 Admin Portal

**AD-UC01/02/03/04/05/06/07/08 — Quản trị hệ thống**

| ID | Yêu cầu |
|---|---|
| ADMIN-01 | Admin đăng nhập qua cổng riêng biệt với xác thực 2FA bắt buộc |
| ADMIN-02 | Tất cả phiên đăng nhập của Admin được ghi log (IP, thời gian) |
| ADMIN-03 | Admin quản lý danh mục và thuộc tính sản phẩm (Category Master Data) |
| ADMIN-04 | Xóa danh mục đang có sản phẩm hoạt động bị chặn — phải chuyển sản phẩm trước |
| ADMIN-05 | Admin tìm kiếm, khóa/mở khóa tài khoản người dùng và gian hàng |
| ADMIN-06 | Khi tài khoản bị khóa giữa chừng, các nghĩa vụ tài chính đang treo vẫn được hoàn tất trước khi khóa hoàn toàn |
| ADMIN-07 | Admin phê duyệt hoặc từ chối hồ sơ Appraiser |
| ADMIN-08 | Admin tạo và quản lý voucher toàn sàn |
| ADMIN-09 | Admin phân xử tranh chấp: xem bằng chứng, ra quyết định hoàn tiền/nhả tiền |
| ADMIN-10 | Admin quản lý Legit Points: trừ điểm vi phạm, phục hồi điểm, theo dõi lịch sử biến động |
| ADMIN-11 | Khi Legit Points xuống dưới ngưỡng, hệ thống tự động khóa quyền tương ứng |
| ADMIN-12 | Admin xem thống kê toàn sàn: dòng tiền, tổng phí giao dịch, số user hoạt động |
| ADMIN-13 | Dữ liệu tổng hợp được tính toán định kỳ (batch/ETL) và lưu vào data warehouse riêng |
| ADMIN-14 | Hỗ trợ tự động gắn cờ tài khoản có dấu hiệu bất thường để Admin ưu tiên xem xét |

---

### 3.19 Workspace Switching

**GEN-UC01 — Chuyển đổi không gian làm việc**

| ID | Yêu cầu |
|---|---|
| WORKSPACE-01 | Store Owner và Appraiser có thể chuyển đổi giữa các Workspace (Store / Appraisal / Normal Account) |
| WORKSPACE-02 | Hệ thống hiển thị danh sách Workspace khả dụng dựa trên vai trò của tài khoản |
| WORKSPACE-03 | Chuyển đổi Workspace phải phản hồi dưới 1 giây |
| WORKSPACE-04 | Nếu có nhiệm vụ dở dang chưa lưu, hệ thống hiển thị cảnh báo trước khi chuyển |

---

## 4. Non-Functional Requirements

### 4.1 Performance

| ID | Yêu cầu |
|---|---|
| PERF-01 | API đăng nhập phản hồi < 2 giây |
| PERF-02 | API tìm kiếm sản phẩm phản hồi < 1 giây |
| PERF-03 | API Auto-Pricing Engine phản hồi < 2 giây |
| PERF-04 | API biểu đồ giá thị trường phản hồi < 1 giây (dùng cache) |
| PERF-05 | Tin nhắn Chat độ trễ < 500ms |
| PERF-06 | Chuyển đổi Workspace phản hồi < 1 giây |
| PERF-07 | Hoàn tiền Escrow khi hủy đơn < 1 phút |

### 4.2 Security

| ID | Yêu cầu |
|---|---|
| SEC-01 | Mật khẩu được hash bằng BCrypt |
| SEC-02 | Thông tin ngân hàng mã hóa tại tầng database |
| SEC-03 | Hồ sơ Appraiser trên S3 chỉ Admin truy cập được |
| SEC-04 | Bằng chứng tranh chấp trên S3 dùng Signed URL có thời hạn |
| SEC-05 | Admin Portal bắt buộc 2FA |
| SEC-06 | Mọi lệnh rút tiền bắt buộc OTP |
| SEC-07 | API Escrow tích hợp cơ chế Idempotency chống thanh toán trùng lặp |
| SEC-08 | JWT xác thực cho tất cả API được bảo vệ |

### 4.3 Availability & Reliability

| ID | Yêu cầu |
|---|---|
| AVAIL-01 | Cập nhật trạng thái đơn hàng sử dụng Transaction an toàn để tránh kích hoạt giải ngân nhầm |
| AVAIL-02 | WebSocket chat có cơ chế reconnect và retry |
| AVAIL-03 | API vận chuyển có Timeout và Fallback |
| AVAIL-04 | Voucher flash sale dùng Queue chống Race Condition |
| AVAIL-05 | Offer dùng Optimistic Locking chống xung đột ghi đè |
| AVAIL-06 | Tạo mã vận đơn và PDF xử lý bất đồng bộ (async) |
| AVAIL-07 | Dashboard thống kê dùng dữ liệu tính sẵn (cache/batch) để không ảnh hưởng hệ thống chính |

### 4.4 Scalability

| ID | Yêu cầu |
|---|---|
| SCALE-01 | DynamoDB Notification có thể scale độc lập với PostgreSQL |
| SCALE-02 | S3 xử lý lưu trữ ảnh không giới hạn mà không làm phình database |
| SCALE-03 | Data warehouse Analytics tách biệt với database giao dịch chính |
| SCALE-04 | Tạo vận đơn và các tác vụ nặng xử lý qua hàng đợi bất đồng bộ |

### 4.5 Usability

| ID | Yêu cầu |
|---|---|
| UX-01 | Biểu đồ giá thị trường render bằng thư viện đồ họa (Recharts/Chart.js), responsive trên mobile |
| UX-02 | Dashboard thống kê doanh số hiển thị biểu đồ trực quan |
| UX-03 | Giao diện chat mượt mà, hỗ trợ badge "chưa đọc" |

---

## 5. Constraints & Assumptions

### Constraints

| ID | Ràng buộc |
|---|---|
| CON-01 | Hệ thống định giá tự động hiện tại dùng dữ liệu giả lập; chưa tích hợp API giá thị trường thật |
| CON-02 | Appraiser chỉ thẩm định hàng phi điện tử; thiết bị điện tử do Auto-Pricing Engine xử lý |
| CON-03 | Store Staff không thể mua hàng và không có Normal Account riêng |
| CON-04 | Mỗi user chỉ có thể mở tối đa một gian hàng |
| CON-05 | Gian hàng được phê duyệt tự động (không qua Admin); Appraiser phải qua Admin duyệt thủ công |

### Assumptions

| ID | Giả định |
|---|---|
| ASS-01 | Tích hợp thanh toán qua SePay — nhận webhook khi có biến động số dư tài khoản ngân hàng của sàn |
| ASS-02 | Đối tác vận chuyển cung cấp API tra cứu trạng thái và tạo vận đơn |
| ASS-03 | Dữ liệu giá thị trường giả lập đủ để khởi động hệ thống; sẽ được thay thế bằng dữ liệu thật trong tương lai |
| ASS-04 | Notification được gửi qua cả push notification (mobile) và in-app notification (web) |
| ASS-05 | Admin được tạo thủ công bởi bên vận hành — không có luồng tự đăng ký |
