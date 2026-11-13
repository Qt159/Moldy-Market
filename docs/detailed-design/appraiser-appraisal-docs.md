# Tài liệu nghiệp vụ & thiết kế Backend --- Appraiser / Appraisal

> **Dự án:** MoldyMarket\
> **Phạm vi:** Quản lý chuyên gia thẩm định (Appraiser) và toàn bộ quy
> trình thẩm định sản phẩm (Appraisal)\
> **Phiên bản:** 1.0\
> **Trạng thái:** Tài liệu thiết kế nghiệp vụ và định hướng triển khai
> backend\
> **Ngày cập nhật:** 13/11/2026

------------------------------------------------------------------------

## 1. Mục tiêu

Tài liệu mô tả thống nhất nghiệp vụ Appraiser và Appraisal của
MoldyMarket, từ lúc người bán tạo yêu cầu thẩm định, kết nối với chuyên
gia, trao đổi, gửi báo cáo, phản hồi báo cáo đến khi hoàn tất và đánh
giá chuyên gia.

Mục tiêu của quy trình:

-   Tăng độ tin cậy của thông tin sản phẩm trên marketplace đồ cũ.
-   Cho phép người bán chủ động lựa chọn chuyên gia phù hợp.
-   Hỗ trợ hai cách kết nối Seller --- Appraiser nhưng dùng chung một
    quy trình thẩm định.
-   Lưu được lịch sử báo cáo và các lần người bán từ chối, không ghi đè
    dữ liệu cũ.
-   Tách biệt hồ sơ chuyên gia, nghiệp vụ thẩm định, chat và lưu trữ tệp
    để dễ bảo trì, mở rộng.

### 1.1. Phạm vi

Bao gồm:

1.  Appraiser profile và trạng thái hoạt động.
2.  Appraisal Request.
3.  Appraiser Application.
4.  Appraisal Invitation.
5.  Appraisal Assignment.
6.  Conversation liên kết giữa Seller và Appraiser.
7.  Appraisal Report và lịch sử chỉnh sửa.
8.  Seller Review dành cho Appraiser.

Không bao gồm trong phiên bản này:

-   Tính hoa hồng, chia doanh thu và payout cho Appraiser.
-   Quy trình thanh toán phí thẩm định.
-   Quy trình khiếu nại/tranh chấp độc lập.
-   Tích hợp upload S3 chi tiết. Report hiện lưu URL tệp; URL sẽ được
    cung cấp bởi module Storage sau khi tích hợp.
-   Tự động định giá bằng AI hoặc mô hình định giá.

------------------------------------------------------------------------

## 2. Thuật ngữ và vai trò

  -----------------------------------------------------------------------
  Thuật ngữ                           Ý nghĩa
  ----------------------------------- -----------------------------------
  User                                Tài khoản người dùng trong hệ thống

  Seller                              Người bán, tạo yêu cầu thẩm định và
                                      là người quyết định Appraiser trong
                                      luồng ứng tuyển

  Appraiser                           Chuyên gia đã được hệ thống xét
                                      duyệt và có quyền nhận công việc
                                      thẩm định

  Appraisal Request                   Yêu cầu thẩm định do Seller tạo cho
                                      một Listing

  Appraiser Application               Hồ sơ Appraiser gửi để ứng tuyển
                                      vào một Request đang mở

  Appraisal Invitation                Lời mời Seller gửi trực tiếp đến
                                      một Appraiser cụ thể

  Appraisal Assignment                Bản ghi công việc thẩm định đã được
                                      hai bên xác nhận

  Appraisal Report                    Báo cáo kết quả thẩm định do
                                      Appraiser gửi

  Appraisal Review                    Đánh giá Seller gửi cho Appraiser
                                      sau khi hoàn tất thẩm định

  Conversation                        Cuộc hội thoại 1--1 giữa Seller và
                                      Appraiser, thuộc module Chat
  -----------------------------------------------------------------------

### 2.1. Nguyên tắc định danh

Các trường `sellerId` và `appraiserId` trong tài liệu được hiểu là định
danh được module nghiệp vụ sử dụng để liên kết với User. Nếu hệ thống
đang dùng `Appraiser.id` riêng biệt thay vì `User.id`, cần thống nhất rõ
quy tắc chuyển đổi giữa hai ID ở Service/Repository; không được mặc định
hai ID luôn giống nhau.

------------------------------------------------------------------------

## 3. Tổng quan kiến trúc module

Đề xuất tách module theo trách nhiệm:

``` text
appraiser/
  controller/
  dto/
  entity/
  enums/
  mapper/
  repository/
  service/

appraisal/
  request/
    controller/
    dto/
    entity/
    enums/
    mapper/
    repository/
    service/
  application/
    controller/
    dto/
    entity/
    enums/
    mapper/
    repository/
    service/
  invitation/
    controller/
    dto/
    entity/
    enums/
    mapper/
    repository/
    service/
  assignment/
    controller/
    dto/
    entity/
    enums/
    mapper/
    repository/
    service/
  report/
    controller/
    dto/
    entity/
    enums/
    mapper/
    repository/
    service/
  review/
    controller/
    dto/
    entity/
    mapper/
    repository/
    service/

chat/
storage/
```

### 3.1. Trách nhiệm

  -----------------------------------------------------------------------
  Module                              Trách nhiệm
  ----------------------------------- -----------------------------------
  `appraiser`                         Hồ sơ chuyên gia, chuyên môn, thông
                                      tin giới thiệu, trạng thái xét
                                      duyệt/hoạt động

  `appraisal.request`                 Tạo và quản lý yêu cầu thẩm định
                                      của Seller

  `appraisal.application`             Appraiser ứng tuyển vào Request

  `appraisal.invitation`              Seller mời trực tiếp Appraiser

  `appraisal.assignment`              Chốt quan hệ công việc Seller ---
                                      Appraiser, trạng thái thực hiện và
                                      Conversation ID

  `appraisal.report`                  Báo cáo thẩm định, phiên bản báo
                                      cáo, chấp nhận/từ chối

  `appraisal.review`                  Đánh giá Appraiser sau khi hoàn tất

  `chat`                              Tạo và quản lý cuộc hội thoại

  `storage`                           Upload và quản lý tệp, trả về URL
                                      hoặc file key
  -----------------------------------------------------------------------

### 3.2. Nguyên tắc phụ thuộc

-   Appraisal có thể gọi Chat để tạo Conversation, nhưng không tự triển
    khai logic nhắn tin.
-   Appraisal Report chỉ lưu tham chiếu đến tệp/URL; không tự xử lý
    upload.
-   Appraiser là hồ sơ chuyên gia; Appraisal là một nghiệp vụ phát sinh
    từ Listing.
-   Không đưa logic hoa hồng vào Assignment ở giai đoạn hiện tại.
-   Controller chỉ nhận request, lấy người dùng hiện tại, gọi Service và
    trả response.
-   Business validation phải nằm trong Service/domain method, không đặt
    toàn bộ ở Controller.

------------------------------------------------------------------------

## 4. Nghiệp vụ Appraiser

### 4.1. Mục đích

Appraiser là hồ sơ chuyên gia có khả năng thẩm định sản phẩm thuộc một
hoặc nhiều danh mục. Hồ sơ này giúp Seller đánh giá mức độ phù hợp trước
khi gửi lời mời hoặc lựa chọn ứng viên.

Thông tin hồ sơ nên có:

-   Thông tin User liên kết.
-   Giới thiệu bản thân.
-   Kinh nghiệm thẩm định.
-   Danh sách chuyên môn/danh mục sản phẩm.
-   Tài liệu chứng minh năng lực (nếu hệ thống yêu cầu).
-   Trạng thái xét duyệt.
-   Trạng thái hoạt động.
-   Thông tin đánh giá tổng hợp (có thể tính từ Review, không nhất thiết
    lưu trùng).

### 4.2. Trạng thái Appraiser

Tên enum cụ thể cần khớp với code hiện tại. Về mặt nghiệp vụ, nên phân
biệt hai nhóm trạng thái:

**Trạng thái xét duyệt hồ sơ:**

-   `PENDING`: Chờ quản trị viên xét duyệt.
-   `APPROVED`: Hồ sơ đã được duyệt.
-   `REJECTED`: Hồ sơ bị từ chối.
-   `SUSPENDED`: Hồ sơ bị đình chỉ.

**Trạng thái hoạt động (nếu tách riêng):**

-   `ACTIVE`: Được phép nhận lời mời/ứng tuyển.
-   `INACTIVE`: Tạm ngưng nhận công việc.

Không nên gộp trạng thái xét duyệt và trạng thái hoạt động nếu hệ thống
cần hỗ trợ trường hợp Appraiser đã được duyệt nhưng chủ động tạm ngưng
nhận việc.

### 4.3. Quy tắc nghiệp vụ

1.  Chỉ Appraiser đã được duyệt và đang hoạt động mới được ứng tuyển
    hoặc nhận lời mời.
2.  Appraiser chỉ được thao tác trên hồ sơ của chính mình, trừ quyền
    quản trị.
3.  Seller có thể xem hồ sơ công khai của Appraiser để lựa chọn.
4.  Chuyên môn của Appraiser nên được dùng để lọc/tìm kiếm Request phù
    hợp.
5.  Review được tạo sau khi Assignment hoàn tất; điểm đánh giá có thể
    được tổng hợp để hiển thị trên hồ sơ.
6.  Không cho Appraiser đã bị đình chỉ nhận công việc mới. Việc xử lý
    Assignment đang thực hiện cần có chính sách riêng, không tự động xóa
    hoặc hủy dữ liệu lịch sử.

------------------------------------------------------------------------

## 5. Tổng quan luồng Appraisal

MoldyMarket hỗ trợ hai cách kết nối Seller và Appraiser:

### Luồng A --- Seller mời Appraiser cụ thể

1.  Seller tạo Appraisal Request cho Listing.
2.  Seller xem hồ sơ Appraiser.
3.  Seller gửi Invitation đến Appraiser được chọn.
4.  Appraiser chấp nhận hoặc từ chối Invitation.
5.  Nếu chấp nhận, hệ thống tạo Assignment.
6.  Hệ thống tạo Conversation 1--1 cho Seller và Appraiser.
7.  Appraiser thực hiện thẩm định và gửi Report.
8.  Seller chấp nhận hoặc từ chối Report.
9.  Nếu Seller chấp nhận, Assignment và Request hoàn tất.
10. Seller tạo Review cho Appraiser.

### Luồng B --- Appraiser ứng tuyển vào Request

1.  Seller tạo Appraisal Request.
2.  Request xuất hiện trong danh sách Request đang mở (Appraiser
    Market).
3.  Appraiser xem thông tin và gửi Application.
4.  Seller xem các Application và hồ sơ Appraiser.
5.  Seller chọn một Application.
6.  Hệ thống tạo Assignment.
7.  Hệ thống tạo Conversation 1--1 cho Seller và Appraiser.
8.  Appraiser thực hiện thẩm định và gửi Report.
9.  Seller chấp nhận hoặc từ chối Report.
10. Nếu Seller chấp nhận, Assignment và Request hoàn tất.
11. Seller tạo Review cho Appraiser.

### 5.1. Điểm hội tụ

Hai luồng chỉ khác nhau ở bước tìm và xác nhận Appraiser:

-   Luồng A dùng `AppraisalInvitation`.
-   Luồng B dùng `AppraiserApplication`.

Sau khi xác nhận, cả hai đều tạo đúng một `AppraisalAssignment` và đi
qua cùng một quy trình Report/Review. Không nên tạo hai loại Assignment
riêng biệt.

### 5.2. Sơ đồ luồng

``` mermaid
flowchart TD
    A[Seller tạo Listing] --> B[Seller tạo Appraisal Request]
    B --> C{Cách kết nối Appraiser}
    C -->|Seller mời trực tiếp| D[Tạo Appraisal Invitation]
    D --> E{Appraiser phản hồi}
    E -->|Từ chối| F[Invitation REJECTED]
    E -->|Chấp nhận| J[Tạo Assignment]
    C -->|Appraiser ứng tuyển| G[Request xuất hiện trên Market]
    G --> H[Appraiser gửi Application]
    H --> I[Seller chọn Application]
    I --> J
    J --> K[Tạo Conversation 1-1]
    K --> L[Appraiser thực hiện thẩm định]
    L --> M[Appraiser gửi Report]
    M --> N{Seller xem Report}
    N -->|Từ chối, còn lượt| O[Lưu lý do từ chối]
    O --> L
    N -->|Chấp nhận| P[Assignment và Request COMPLETED]
    P --> Q[Seller Review Appraiser]
```

------------------------------------------------------------------------

## 6. Appraisal Request

### 6.1. Mục đích

`AppraisalRequest` biểu diễn nhu cầu thẩm định một Listing do Seller
tạo. Đây là điểm bắt đầu chung của cả hai cách kết nối Appraiser.

Không cần thêm `AppraisalRequestType` để phân biệt hai luồng. Việc kết
nối được biểu diễn bằng Invitation hoặc Application; nguồn tạo
Assignment được lưu ở `AppraisalAssignment.sourceType`.

### 6.2. Dữ liệu chính

  -----------------------------------------------------------------------
  Field                               Ý nghĩa
  ----------------------------------- -----------------------------------
  `id`                                UUID của Request

  `listingId`                         Listing cần thẩm định

  `sellerId`                          Seller tạo Request

  `description`                       Mô tả yêu cầu/thông tin cần chuyên
                                      gia lưu ý

  `expectedPrice`                     Giá Seller kỳ vọng hoặc giá tham
                                      chiếu

  `status`                            Trạng thái Request

  `deadline`                          Hạn nhận ứng tuyển/lời mời hoặc hạn
                                      xử lý theo quy ước hệ thống

  `createdAt`                         Thời điểm tạo

  `updatedAt`                         Thời điểm cập nhật

  `version`                           Optimistic locking nếu entity sử
                                      dụng `@Version`
  -----------------------------------------------------------------------

`expectedPrice` chỉ là thông tin tham khảo do Seller cung cấp, không
phải kết quả thẩm định và không được hiển thị như giá trị đã xác minh.

### 6.3. Request Status

``` java
public enum AppraisalRequestStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
```

  Status          Ý nghĩa
  --------------- ----------------------------------------------------------
  `OPEN`          Request đang mở, có thể nhận Application hoặc Invitation
  `ASSIGNED`      Đã xác nhận Appraiser và tạo Assignment
  `IN_PROGRESS`   Appraiser đã bắt đầu thực hiện
  `COMPLETED`     Seller đã chấp nhận Report
  `CANCELLED`     Seller hoặc quy trình hợp lệ đã hủy Request
  `EXPIRED`       Request hết hạn khi chưa được xác nhận

Không cần `SELECTING_APPRAISER`: trong lúc Seller đang xem nhiều
Application, Request vẫn có thể ở trạng thái `OPEN`.

Không dùng `SUBMITTED` cho Request vì đây là trạng thái của Report.

### 6.4. Quy tắc nghiệp vụ

1.  Chỉ Seller sở hữu Listing mới được tạo Request cho Listing đó.
2.  Một Listing không nên có nhiều Request đang hoạt động đồng thời, trừ
    khi nghiệp vụ cho phép tạo nhiều đợt thẩm định.
3.  Request mới có trạng thái `OPEN`.
4.  Chỉ Request `OPEN` mới nhận Application hoặc Invitation mới.
5.  Request hết hạn không được nhận thêm Application/Invitation.
6.  Khi Assignment được tạo thành công, Request chuyển sang `ASSIGNED`.
7.  Khi Appraiser bắt đầu công việc, Request chuyển sang `IN_PROGRESS`.
8.  Khi Seller chấp nhận Report, Request chuyển sang `COMPLETED`.
9.  Request `CANCELLED`, `EXPIRED`, `COMPLETED` là các trạng thái kết
    thúc; không được quay lại `OPEN` bằng cập nhật tùy ý.
10. Khi Request bị hủy hoặc hết hạn, các Application/Invitation đang chờ
    cần được chuyển sang trạng thái phù hợp.

### 6.5. API đề xuất

  -------------------------------------------------------------------------------------------------------
  Method            Endpoint                                          Quyền             Mô tả
  ----------------- ------------------------------------------------- ----------------- -----------------
  `POST`            `/api/v1/appraisal-requests`                      Seller            Tạo Request

  `GET`             `/api/v1/appraisal-requests/{requestId}`          Người liên quan   Xem chi tiết

  `GET`             `/api/v1/appraisal-requests/my-requests`          Seller            Danh sách Request
                                                                                        của Seller

  `GET`             `/api/v1/appraisal-requests/market/open`          Appraiser         Danh sách Request
                                                                                        đang mở

  `PATCH`           `/api/v1/appraisal-requests/{requestId}/cancel`   Seller            Hủy Request
  -------------------------------------------------------------------------------------------------------

Danh sách nên hỗ trợ pagination, sort và lọc theo danh mục Listing, thời
hạn, khu vực hoặc trạng thái phù hợp với quyền truy cập.

------------------------------------------------------------------------

## 7. Appraiser Application

### 7.1. Mục đích

`AppraiserApplication` là hồ sơ ứng tuyển do Appraiser gửi vào một
Request đang mở. Seller là người xem và quyết định chọn Appraiser.

### 7.2. Dữ liệu chính

  Field                        Ý nghĩa
  ---------------------------- ------------------------------------------------------
  `id`                         UUID Application
  `appraisalRequestId`         Request được ứng tuyển
  `appraiserId`                Appraiser ứng tuyển
  `introduction`               Lời giới thiệu
  `experienceDescription`      Mô tả kinh nghiệm liên quan
  `proposedFee`                Mức phí đề xuất; chưa thực hiện tính hoa hồng/payout
  `estimatedCompletionHours`   Thời gian dự kiến hoàn thành
  `status`                     Trạng thái Application
  `appliedAt`                  Thời điểm ứng tuyển
  `respondedAt`                Thời điểm Seller phản hồi nếu có
  `createdAt`, `updatedAt`     Thời điểm tạo/cập nhật

### 7.3. Application Status

``` java
public enum AppraiserApplicationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN,
    EXPIRED
}
```

  Status        Ý nghĩa
  ------------- -------------------------------------------------------
  `PENDING`     Chờ Seller quyết định
  `ACCEPTED`    Application được Seller chọn
  `REJECTED`    Seller không chọn
  `WITHDRAWN`   Appraiser tự rút Application
  `EXPIRED`     Request hết hạn hoặc đóng trước khi Seller quyết định

### 7.4. Quy tắc nghiệp vụ

1.  Chỉ Appraiser đã được duyệt và đang hoạt động mới được ứng tuyển.
2.  Chỉ ứng tuyển vào Request `OPEN` và chưa hết hạn.
3.  Appraiser không được ứng tuyển vào Request của chính mình.
4.  Mỗi Appraiser chỉ có một Application hợp lệ cho một Request, theo
    chính sách hiện tại.
5.  Chỉ chủ Request được xem danh sách Application.
6.  Appraiser chỉ được xem và rút Application của chính mình.
7.  Chỉ Application `PENDING` mới được rút hoặc được Seller chọn.
8.  Khi Seller chọn một Application, Application đó chuyển `ACCEPTED`;
    các Application `PENDING` khác chuyển `REJECTED` hoặc trạng thái kết
    thúc tương ứng.
9.  Khi Request đóng/hủy/hết hạn, Application đang chờ chuyển `EXPIRED`
    hoặc trạng thái đóng được quy định.

### 7.5. API đề xuất

  ------------------------------------------------------------------------------------------------------------------
  Method            Endpoint                                                    Quyền              Mô tả
  ----------------- ----------------------------------------------------------- ------------------ -----------------
  `POST`            `/api/v1/appraisal-requests/{requestId}/applications`       Appraiser          Ứng tuyển

  `GET`             `/api/v1/appraisal-requests/{requestId}/applications`       Seller sở hữu      Xem ứng viên
                                                                                Request            

  `GET`             `/api/v1/appraisal-applications/my-applications`            Appraiser          Xem Application
                                                                                                   của mình

  `GET`             `/api/v1/appraisal-applications/{applicationId}`            Appraiser/Seller   Xem chi tiết
                                                                                liên quan          

  `PATCH`           `/api/v1/appraisal-applications/{applicationId}/withdraw`   Appraiser sở hữu   Rút Application
  ------------------------------------------------------------------------------------------------------------------

Việc Seller chọn Application được thực hiện qua API Assignment, không
nên để Application tự tạo Assignment trong Controller của Application.

------------------------------------------------------------------------

## 8. Appraisal Invitation

### 8.1. Mục đích

`AppraisalInvitation` biểu diễn lời mời do Seller chủ động gửi cho một
Appraiser cụ thể.

### 8.2. Dữ liệu chính

  Field                      Ý nghĩa
  -------------------------- ------------------------
  `id`                       UUID Invitation
  `appraisalRequestId`       Request liên quan
  `appraiserId`              Appraiser được mời
  `proposedFee`              Mức phí Seller đề xuất
  `message`                  Nội dung lời mời
  `deadline`                 Hạn Appraiser phản hồi
  `status`                   Trạng thái Invitation
  `respondedAt`              Thời điểm phản hồi
  `createdAt`, `updatedAt`   Thời điểm tạo/cập nhật

Seller có thể được suy ra từ `AppraisalRequest.sellerId`, tránh lưu
trùng dữ liệu nếu không có yêu cầu truy vấn đặc biệt.

### 8.3. Invitation Status

``` java
public enum AppraisalInvitationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
```

### 8.4. Quy tắc nghiệp vụ

1.  Chỉ Seller sở hữu Request mới được tạo Invitation.
2.  Request phải đang `OPEN`.
3.  Appraiser được mời phải tồn tại, đã được duyệt và đang hoạt động.
4.  Seller không được tự mời chính mình.
5.  Deadline Invitation phải nằm trong thời hạn hợp lệ của Request.
6.  Appraiser chỉ được chấp nhận/từ chối Invitation gửi cho mình.
7.  Seller chỉ được hủy Invitation do mình tạo.
8.  Chỉ Invitation `PENDING` mới được chấp nhận, từ chối hoặc hủy.
9.  Khi Appraiser chấp nhận, hệ thống tạo Assignment trong cùng
    transaction logic.
10. Khi Invitation bị từ chối/hủy/hết hạn, không tạo Assignment.
11. Nếu Request đã có Assignment, các Invitation còn `PENDING` phải được
    đóng/hủy/hết hạn.

### 8.5. Chính sách mời lại

Nếu muốn cho phép Seller mời lại cùng một Appraiser sau khi lời mời
trước bị từ chối hoặc hủy, không nên dùng unique constraint cố định trên
`(appraisal_request_id, appraiser_id)` cho toàn bộ lịch sử. Có thể chọn
một trong hai hướng:

-   **Đơn giản:** chỉ cho phép một Invitation cho mỗi cặp
    Request/Appraiser; không hỗ trợ mời lại.
-   **Có lịch sử:** cho phép nhiều Invitation, mỗi lần mời là một bản
    ghi mới; cần kiểm soát chỉ có tối đa một Invitation `PENDING` cho
    cùng cặp.

Cần chọn chính sách này trước khi chốt constraint ở database.

### 8.6. API đề xuất

  -------------------------------------------------------------------------------------------------------------
  Method            Endpoint                                                Quyền             Mô tả
  ----------------- ------------------------------------------------------- ----------------- -----------------
  `POST`            `/api/v1/appraisal-requests/{requestId}/invitations`    Seller            Gửi lời mời

  `GET`             `/api/v1/appraisal-requests/{requestId}/invitations`    Seller sở hữu     Xem lời mời đã
                                                                            Request           gửi

  `GET`             `/api/v1/appraisal-invitations/my-invitations`          Appraiser         Xem lời mời nhận
                                                                                              được

  `GET`             `/api/v1/appraisal-invitations/{invitationId}`          Người liên quan   Xem chi tiết

  `PATCH`           `/api/v1/appraisal-invitations/{invitationId}/accept`   Appraiser được    Chấp nhận
                                                                            mời               

  `PATCH`           `/api/v1/appraisal-invitations/{invitationId}/reject`   Appraiser được    Từ chối
                                                                            mời               

  `PATCH`           `/api/v1/appraisal-invitations/{invitationId}/cancel`   Seller sở hữu     Hủy lời mời
                                                                            Request           
  -------------------------------------------------------------------------------------------------------------

------------------------------------------------------------------------

## 9. Appraisal Assignment

### 9.1. Mục đích

`AppraisalAssignment` là bản ghi xác nhận công việc thẩm định giữa một
Seller và một Appraiser. Đây là điểm hội tụ của Invitation và
Application.

Assignment là nguồn dữ liệu chính để quản lý quá trình thực hiện công
việc sau khi hai bên đã được xác nhận.

### 9.2. Source Type

``` java
public enum AppraisalSourceType {
    SELLER_INVITATION,
    APPRAISER_APPLICATION
}
```

  Source Type               Nguồn tạo Assignment
  ------------------------- -------------------------------------------
  `SELLER_INVITATION`       Appraiser chấp nhận Invitation của Seller
  `APPRAISER_APPLICATION`   Seller chọn Application của Appraiser

### 9.3. Dữ liệu chính

  -----------------------------------------------------------------------
  Field                               Ý nghĩa
  ----------------------------------- -----------------------------------
  `id`                                UUID Assignment

  `appraisalRequestId`                Request được thực hiện

  `appraiserId`                       Appraiser thực hiện

  `sellerId`                          Seller sở hữu Request

  `applicationId`                     Application được chọn, nullable nếu
                                      từ Invitation

  `invitationId`                      Invitation được chấp nhận, nullable
                                      nếu từ Application

  `sourceType`                        Nguồn tạo Assignment

  `agreedFee`                         Mức phí hai bên thống nhất

  `conversationId`                    ID Conversation ở module Chat

  `status`                            Trạng thái Assignment

  `rejectionCount`                    Số lần Seller từ chối Report

  `assignedAt`                        Thời điểm xác nhận

  `startedAt`                         Thời điểm bắt đầu

  `completedAt`                       Thời điểm hoàn tất

  `createdAt`, `updatedAt`            Thời điểm tạo/cập nhật

  `version`                           Optimistic locking nếu sử dụng
  -----------------------------------------------------------------------

`applicationId` và `invitationId` nên được kiểm soát để chỉ một nguồn
được sử dụng cho một Assignment. Nếu dùng database constraint, cần bảo
đảm biểu diễn được quy tắc XOR này.

### 9.4. Assignment Status

Có thể dùng bộ trạng thái cơ bản:

``` java
public enum AppraisalAssignmentStatus {
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
```

Nếu nghiệp vụ xác định trường hợp Seller từ chối đủ số lần sẽ kết thúc
công việc nhưng không hoàn tất, nên bổ sung trạng thái kết thúc riêng,
ví dụ `FAILED` hoặc `CLOSED_AFTER_REJECTIONS`. Không nên dùng
`COMPLETED` cho trường hợp không có Report được Seller chấp nhận.

  -----------------------------------------------------------------------
  Status                              Ý nghĩa
  ----------------------------------- -----------------------------------
  `ASSIGNED`                          Hai bên đã xác nhận, chờ bắt đầu

  `IN_PROGRESS`                       Appraiser đang thực hiện

  `COMPLETED`                         Seller đã chấp nhận Report

  `CANCELLED`                         Assignment bị hủy hợp lệ

  `FAILED` (nếu áp dụng)              Quy trình không thể hoàn tất theo
                                      chính sách sau số lần từ chối tối
                                      đa
  -----------------------------------------------------------------------

### 9.5. Quy tắc nghiệp vụ

1.  Một Request chỉ được có tối đa một Assignment đang/đã xác nhận theo
    thiết kế hiện tại.
2.  Assignment chỉ được tạo từ Invitation được chấp nhận hoặc
    Application được Seller chọn.
3.  Request phải còn `OPEN` tại thời điểm tạo Assignment.
4.  Assignment phải lưu nguồn tạo để phục vụ truy vết.
5.  Assignment lưu `conversationId`, không lưu toàn bộ nội dung chat.
6.  Sau khi Assignment được tạo, Request chuyển `ASSIGNED`.
7.  Khi Appraiser bắt đầu, Assignment chuyển `IN_PROGRESS`, Request cũng
    chuyển `IN_PROGRESS`.
8.  Khi Seller chấp nhận Report, Assignment chuyển `COMPLETED`.
9.  Khi Assignment được tạo thành công, các Application/Invitation đang
    chờ khác phải được đóng.
10. Các thao tác tạo Assignment phải chống race condition: hai Appraiser
    không thể đồng thời được gán vào cùng Request.
11. Việc tạo Assignment, cập nhật Request, cập nhật
    Application/Invitation và tạo Conversation cần có chiến lược
    transaction nhất quán.
12. Không tạo Assignment mới chỉ vì Appraiser gửi lại Report sau khi
    Seller từ chối; các lần sửa Report thuộc cùng Assignment.

### 9.6. Đồng bộ Chat

Khi tạo Assignment:

1.  Xác định `sellerId` và `appraiserId`.
2.  Gọi Chat Service để tạo hoặc lấy Conversation 1--1 phù hợp.
3.  Lưu `conversationId` vào Assignment.
4.  Trả Assignment Response có `conversationId`.

Nếu Chat là service/module cùng ứng dụng và cùng database, có thể thực
hiện trong cùng transaction. Nếu Chat tách service hoặc dùng cơ chế bất
đồng bộ, cần thiết kế retry/idempotency và trạng thái tạo Conversation;
không được giả định transaction database bao trùm được hệ thống bên
ngoài.

### 9.7. API đề xuất

  -----------------------------------------------------------------------------------------------------------------------
  Method            Endpoint                                                          Quyền             Mô tả
  ----------------- ----------------------------------------------------------------- ----------------- -----------------
  `POST`            `/api/v1/appraisal-assignments/select-appraiser?requestId={id}`   Seller            Chọn Application

  `GET`             `/api/v1/appraisal-assignments/{assignmentId}`                    Người tham gia    Xem Assignment

  `GET`             `/api/v1/appraisal-assignments/my-assignments`                    Appraiser         Assignment của
                                                                                                        Appraiser

  `GET`             `/api/v1/appraisal-assignments/seller/my-assignments`             Seller            Assignment của
                                                                                                        Seller

  `PATCH`           `/api/v1/appraisal-assignments/{assignmentId}/start`              Appraiser         Bắt đầu thẩm định

  `PATCH`           `/api/v1/appraisal-assignments/{assignmentId}/cancel`             Người có quyền    Hủy Assignment
                                                                                      theo chính sách   
  -----------------------------------------------------------------------------------------------------------------------

Chấp nhận Invitation có thể gọi Assignment Service nội bộ; không cần để
client gọi một endpoint tạo Assignment riêng sau khi Appraiser đã chấp
nhận Invitation.

------------------------------------------------------------------------

## 10. Appraisal Report

### 10.1. Mục đích

`AppraisalReport` là kết quả thẩm định do Appraiser gửi cho Seller.
Report phải lưu lịch sử từng lần gửi để bảo đảm khả năng truy vết.

Không cập nhật đè nội dung của Report đã bị từ chối. Mỗi lần Appraiser
gửi lại là một bản ghi Report mới với `revisionNumber` tăng dần.

### 10.2. Dữ liệu chính

  Field                   Ý nghĩa
  ----------------------- --------------------------------
  `id`                    UUID Report
  `assignmentId`          Assignment liên quan
  `appraiserId`           Appraiser gửi Report
  `revisionNumber`        Số thứ tự phiên bản
  `description`           Nhận xét tổng quan
  `conditionAssessment`   Đánh giá tình trạng sản phẩm
  `assessedValue`         Giá trị Appraiser đề xuất
  `imageUrls`             Ảnh minh chứng
  `documentUrls`          Tài liệu minh chứng
  `status`                Trạng thái Report
  `rejectionReason`       Lý do Seller từ chối
  `submittedAt`           Thời điểm gửi
  `reviewedAt`            Thời điểm Seller phản hồi
  `version`               Optimistic locking nếu sử dụng

### 10.3. Report Status

``` java
public enum AppraisalReportStatus {
    SUBMITTED,
    ACCEPTED,
    REJECTED
}
```

  Status        Ý nghĩa
  ------------- -----------------------------
  `SUBMITTED`   Đã gửi, chờ Seller phản hồi
  `ACCEPTED`    Seller chấp nhận
  `REJECTED`    Seller từ chối và lưu lý do

### 10.4. Quy tắc nghiệp vụ

1.  Chỉ Appraiser thuộc Assignment mới được gửi Report.
2.  Assignment phải ở trạng thái `IN_PROGRESS`.
3.  Không cho gửi Report mới khi Report hiện tại đang `SUBMITTED`.
4.  Mỗi lần gửi mới tạo một Report record mới.
5.  `revisionNumber` tăng tuần tự trong phạm vi Assignment.
6.  Seller chỉ được chấp nhận/từ chối Report thuộc Assignment của mình.
7.  Chỉ Report `SUBMITTED` mới được chấp nhận hoặc từ chối.
8.  Khi chấp nhận Report:
    -   Report chuyển `ACCEPTED`.
    -   Assignment chuyển `COMPLETED`.
    -   Request chuyển `COMPLETED`.
    -   Ghi nhận `completedAt`.
9.  Khi từ chối Report:
    -   Report chuyển `REJECTED`.
    -   Lưu `rejectionReason` và `reviewedAt`.
    -   Tăng `Assignment.rejectionCount`.
    -   Appraiser có thể gửi phiên bản tiếp theo nếu còn lượt.
10. Không cho phép chỉnh sửa nội dung Report đã gửi. Nếu cần sửa, phải
    tạo revision mới sau khi Report trước bị từ chối.

### 10.5. Chính sách tối đa 3 lần từ chối

Theo nghiệp vụ đã thống nhất, Seller có tối đa 3 lần từ chối Report. Cần
phân biệt rõ:

-   `rejectionCount` là số lần Seller đã từ chối Report.
-   `revisionNumber` là số phiên bản Report đã được gửi.
-   Hai giá trị này không đồng nghĩa nếu có trường hợp Report bị hủy
    hoặc gửi lại theo chính sách khác.

Sau lần từ chối thứ ba, hệ thống phải có kết quả nghiệp vụ rõ ràng.
Không nên chỉ chặn Appraiser gửi tiếp và để Assignment `IN_PROGRESS` vô
thời hạn.

Các phương án cần chốt:

-   Đóng Assignment với trạng thái `FAILED`/`CLOSED_AFTER_REJECTIONS`,
    sau đó cho phép Seller review Appraiser.
-   Chuyển sang quy trình hỗ trợ/đối soát bởi Admin.
-   Cho phép mở Dispute nếu Seller và Appraiser không thống nhất.

Tài liệu hiện ghi nhận giới hạn 3 lần từ chối, nhưng trạng thái kết thúc
sau lần thứ ba cần được xác nhận trước khi khóa implementation.

### 10.6. API đề xuất

  ----------------------------------------------------------------------------------------------------------------------
  Method            Endpoint                                                         Quyền             Mô tả
  ----------------- ---------------------------------------------------------------- ----------------- -----------------
  `POST`            `/api/v1/appraisal-reports/assignments/{assignmentId}`           Appraiser được    Gửi Report mới
                                                                                     gán               

  `GET`             `/api/v1/appraisal-reports/{reportId}`                           Người tham gia    Xem Report

  `GET`             `/api/v1/appraisal-reports/assignments/{assignmentId}/history`   Người tham gia    Xem lịch sử
                                                                                                       Report

  `PATCH`           `/api/v1/appraisal-reports/{reportId}/accept`                    Seller sở hữu     Chấp nhận Report
                                                                                     Assignment        

  `PATCH`           `/api/v1/appraisal-reports/{reportId}/reject`                    Seller sở hữu     Từ chối Report
                                                                                     Assignment        
  ----------------------------------------------------------------------------------------------------------------------

------------------------------------------------------------------------

## 11. Appraisal Review

### 11.1. Mục đích

`AppraisalReview` là đánh giá của Seller dành cho Appraiser sau khi công
việc thẩm định kết thúc. Review giúp Seller khác tham khảo chất lượng và
độ tin cậy của chuyên gia.

### 11.2. Dữ liệu chính

  Field                    Ý nghĩa
  ------------------------ -----------------------------------------------
  `id`                     UUID Review
  `assignmentId`           Assignment được đánh giá
  `sellerId`               Seller tạo Review
  `appraiserId`            Appraiser được đánh giá
  `rating`                 Điểm tổng thể
  `accuracyScore`          Độ chính xác
  `professionalismScore`   Tính chuyên nghiệp
  `communicationScore`     Khả năng giao tiếp
  `timelinessScore`        Đúng tiến độ
  `comment`                Nhận xét
  `recommend`              Có đề xuất Appraiser cho người khác hay không
  `createdAt`              Thời điểm tạo

Các điểm đánh giá nên giới hạn trong khoảng 1--5.

### 11.3. Quy tắc nghiệp vụ

1.  Chỉ Seller của Assignment mới được tạo Review.
2.  Assignment phải đạt trạng thái đủ điều kiện review theo chính sách.
3.  Nếu chỉ cho review sau khi Seller chấp nhận Report, Assignment phải
    `COMPLETED`.
4.  Mỗi Assignment chỉ có một Review.
5.  Seller không được review Assignment của người khác.
6.  Appraiser không được tự review chính mình.
7.  Review đã tạo không được chỉnh sửa tùy ý; nếu cần xử lý nội dung vi
    phạm, nên có cơ chế moderation riêng.
8.  Review có thể được hiển thị công khai trên hồ sơ Appraiser theo
    chính sách quyền riêng tư.

### 11.4. API đề xuất

  -------------------------------------------------------------------------------------------------------------------
  Method            Endpoint                                                 Quyền                  Mô tả
  ----------------- -------------------------------------------------------- ---------------------- -----------------
  `POST`            `/api/v1/appraisal-reviews/assignments/{assignmentId}`   Seller                 Tạo Review

  `GET`             `/api/v1/appraisal-reviews/assignments/{assignmentId}`   Người tham gia         Xem Review của
                                                                                                    Assignment

  `GET`             `/api/v1/appraisal-reviews/appraisers/{appraiserId}`     Public/Authenticated   Xem Review của
                                                                             theo chính sách        Appraiser

  `GET`             `/api/v1/appraisal-reviews/my-reviews`                   Seller                 Xem Review đã tạo
  -------------------------------------------------------------------------------------------------------------------

------------------------------------------------------------------------

## 12. State transition tổng hợp

### 12.1. Request

``` text
OPEN
  ├──> ASSIGNED
  │      └──> IN_PROGRESS
  │              └──> COMPLETED
  ├──> CANCELLED
  └──> EXPIRED
```

### 12.2. Application

``` text
PENDING
  ├──> ACCEPTED
  ├──> REJECTED
  ├──> WITHDRAWN
  └──> EXPIRED
```

### 12.3. Invitation

``` text
PENDING
  ├──> ACCEPTED
  ├──> REJECTED
  ├──> CANCELLED
  └──> EXPIRED
```

### 12.4. Assignment

``` text
ASSIGNED
  ├──> IN_PROGRESS
  │      ├──> COMPLETED
  │      └──> FAILED (nếu áp dụng chính sách giới hạn từ chối)
  └──> CANCELLED
```

### 12.5. Report

``` text
SUBMITTED
  ├──> ACCEPTED
  └──> REJECTED
          └──> Appraiser gửi Report revision tiếp theo
```

Report cũ giữ nguyên trạng thái `REJECTED`; Report mới có
`revisionNumber` tăng lên.

------------------------------------------------------------------------

## 13. Quan hệ dữ liệu

Quan hệ khái niệm:

``` mermaid
erDiagram
    USER ||--o| APPRAISER : owns
    LISTING ||--o{ APPRAISAL_REQUEST : has
    USER ||--o{ APPRAISAL_REQUEST : creates
    APPRAISAL_REQUEST ||--o{ APPRAISER_APPLICATION : receives
    APPRAISER ||--o{ APPRAISER_APPLICATION : submits
    APPRAISAL_REQUEST ||--o{ APPRAISAL_INVITATION : sends
    APPRAISER ||--o{ APPRAISAL_INVITATION : receives
    APPRAISAL_REQUEST ||--o| APPRAISAL_ASSIGNMENT : results_in
    APPRAISER ||--o{ APPRAISAL_ASSIGNMENT : performs
    APPRAISAL_ASSIGNMENT ||--o{ APPRAISAL_REPORT : has
    APPRAISAL_ASSIGNMENT ||--o| APPRAISAL_REVIEW : receives
    APPRAISAL_ASSIGNMENT }o--|| CONVERSATION : references
```

Ghi chú:

-   `AppraisalRequest` có thể có nhiều Application và Invitation.
-   Theo chính sách hiện tại, một Request chỉ tạo tối đa một Assignment.
-   Assignment có nhiều Report revision.
-   Assignment có tối đa một Review.
-   Assignment chỉ lưu `conversationId`; dữ liệu Conversation thuộc Chat
    module.
-   Không nhất thiết phải tạo foreign key vật lý xuyên module/service
    nếu kiến trúc triển khai không dùng chung database. Nếu cùng
    database, nên cân nhắc FK để bảo vệ toàn vẹn dữ liệu.

------------------------------------------------------------------------

## 14. Transaction, concurrency và tính toàn vẹn

Đây là phần quan trọng vì nhiều Appraiser có thể ứng tuyển cùng một
Request và Seller có thể nhận nhiều lời mời.

### 14.1. Chống tạo nhiều Assignment cho một Request

Khi Seller chọn Application hoặc Appraiser chấp nhận Invitation:

1.  Lock Request bằng pessimistic lock hoặc cơ chế tương đương.
2.  Đọc lại trạng thái Request.
3.  Xác nhận Request vẫn `OPEN`.
4.  Kiểm tra chưa tồn tại Assignment.
5.  Xác thực Application/Invitation vẫn `PENDING`.
6.  Tạo Assignment.
7.  Chuyển Request sang `ASSIGNED`.
8.  Đóng các Application/Invitation đang chờ khác.
9.  Tạo/liên kết Conversation.
10. Commit transaction.

Nên có unique constraint trên
`appraisal_assignment.appraisal_request_id` nếu chính sách là một
Assignment cho mỗi Request. Lock ở Service giúp kiểm soát luồng; unique
constraint là lớp bảo vệ cuối cùng.

### 14.2. Chống gửi nhiều Report đồng thời

Khi Appraiser gửi Report:

1.  Lock Assignment.
2.  Kiểm tra Appraiser sở hữu Assignment.
3.  Kiểm tra Assignment `IN_PROGRESS`.
4.  Kiểm tra không có Report `SUBMITTED`.
5.  Kiểm tra số lần từ chối còn trong giới hạn.
6.  Tính revision tiếp theo.
7.  Tạo Report mới.
8.  Commit.

### 14.3. Chống Seller phản hồi Report hai lần

Khi Seller accept/reject:

1.  Lock Assignment.
2.  Kiểm tra Seller sở hữu Assignment.
3.  Kiểm tra Report thuộc Assignment.
4.  Kiểm tra Report đang `SUBMITTED`.
5.  Kiểm tra Report là bản cần được phản hồi hiện tại.
6.  Cập nhật Report và Assignment trong cùng transaction.

### 14.4. Unique constraints nên cân nhắc

  ------------------------------------------------------------------------------------------
  Bảng                      Constraint                               Mục đích
  ------------------------- ---------------------------------------- -----------------------
  `appraiser_application`   `(appraisal_request_id, appraiser_id)`   Chặn ứng tuyển trùng
                                                                     theo chính sách một lần

  `appraisal_assignment`    `appraisal_request_id`                   Một Assignment cho mỗi
                                                                     Request

  `appraisal_review`        `assignment_id`                          Một Review cho mỗi
                                                                     Assignment

  `appraisal_report`        `(assignment_id, revision_number)`       Không trùng revision
                                                                     trong một Assignment
  ------------------------------------------------------------------------------------------

Không cần tạo index thủ công ngoài yêu cầu hiện tại; tuy nhiên unique
constraint thường được database triển khai bằng unique index. Cần phân
biệt unique constraint bảo vệ nghiệp vụ với index phục vụ tối ưu truy
vấn.

------------------------------------------------------------------------

## 15. Bảo mật và phân quyền

  -----------------------------------------------------------------------
  Hành động                           Quyền cần kiểm tra
  ----------------------------------- -----------------------------------
  Tạo Request                         Seller sở hữu Listing

  Xem Request riêng tư                Seller sở hữu hoặc Appraiser được
                                      phép xem trên Market

  Ứng tuyển                           Appraiser đã duyệt, đang hoạt động

  Xem Application list                Seller sở hữu Request

  Rút Application                     Appraiser sở hữu Application

  Tạo Invitation                      Seller sở hữu Request

  Accept/Reject Invitation            Appraiser được mời

  Cancel Invitation                   Seller sở hữu Request

  Chọn Application                    Seller sở hữu Request

  Xem Assignment                      Seller hoặc Appraiser tham gia
                                      Assignment

  Start Assignment                    Appraiser được gán

  Submit Report                       Appraiser được gán

  Accept/Reject Report                Seller của Assignment

  Create Review                       Seller của Assignment đủ điều kiện

  Xem Review công khai                Theo chính sách
                                      public/authenticated
  -----------------------------------------------------------------------

### 15.1. Các lưu ý bảo mật

-   Không nhận `sellerId` hoặc `appraiserId` từ body nếu có thể lấy từ
    authenticated principal.
-   Không tin `requestId`, `assignmentId` chỉ vì client gửi lên; phải
    kiểm tra quyền sở hữu ở Service.
-   Không để Seller xem Application của Request khác.
-   Không để Appraiser xem Invitation của Appraiser khác.
-   Không để người ngoài xem Conversation chỉ bằng cách biết
    `conversationId`.
-   Không cho phép thay đổi status bằng API update tổng quát.
-   Dùng DTO riêng cho từng hành động; không expose trực tiếp Entity.
-   Các API list cần pagination và giới hạn page size.

------------------------------------------------------------------------

## 16. Validation dữ liệu

Các giới hạn dưới đây là baseline; cần đồng bộ với DTO thực tế:

  Dữ liệu                      Validation gợi ý
  ---------------------------- ----------------------------------------------
  Request description          Not blank, giới hạn độ dài hợp lý
  Expected price               Lớn hơn 0 nếu bắt buộc
  Request deadline             Thời điểm tương lai
  Application introduction     20--1000 ký tự
  Experience description       50--5000 ký tự
  Proposed fee                 Lớn hơn 0
  Estimated completion hours   1--480 giờ
  Invitation message           10--2000 ký tự
  Invitation deadline          Tương lai và không vượt quá deadline Request
  Report description           20--5000 ký tự
  Condition assessment         20--5000 ký tự
  Assessed value               Lớn hơn 0
  Report evidence              Giới hạn số lượng URL/tệp theo cấu hình
  Rejection reason             Bắt buộc, giới hạn độ dài hợp lý
  Review scores                1--5
  Review comment               Giới hạn độ dài hợp lý

------------------------------------------------------------------------

## 17. Error handling

Nên chuẩn hóa lỗi nghiệp vụ để Frontend có thể xử lý nhất quán.

  Tình huống                                  Ý nghĩa lỗi gợi ý
  ------------------------------------------- -----------------------------------
  Request không tồn tại                       `APPRAISAL_REQUEST_NOT_FOUND`
  Không có quyền với Request                  `APPRAISAL_REQUEST_ACCESS_DENIED`
  Request không còn mở                        `APPRAISAL_REQUEST_NOT_OPEN`
  Request hết hạn                             `APPRAISAL_REQUEST_EXPIRED`
  Đã ứng tuyển                                `APPLICATION_ALREADY_EXISTS`
  Application không tồn tại                   `APPLICATION_NOT_FOUND`
  Application không còn pending               `APPLICATION_NOT_PENDING`
  Invitation không tồn tại                    `INVITATION_NOT_FOUND`
  Invitation hết hạn                          `INVITATION_EXPIRED`
  Invitation không còn pending                `INVITATION_NOT_PENDING`
  Assignment đã tồn tại                       `ASSIGNMENT_ALREADY_EXISTS`
  Assignment không tồn tại                    `ASSIGNMENT_NOT_FOUND`
  Assignment không ở trạng thái hợp lệ        `ASSIGNMENT_INVALID_STATUS`
  Report đang chờ phản hồi                    `REPORT_ALREADY_PENDING`
  Report không còn pending                    `REPORT_NOT_PENDING`
  Đã hết lượt từ chối                         `REPORT_REJECTION_LIMIT_REACHED`
  Review đã tồn tại                           `REVIEW_ALREADY_EXISTS`
  Appraiser chưa được duyệt/không hoạt động   `APPRAISER_NOT_ELIGIBLE`

Không nên trả lỗi 500 cho các trường hợp vi phạm quy tắc nghiệp vụ có
thể dự đoán.

------------------------------------------------------------------------

## 18. File và bằng chứng thẩm định

Report có thể chứa:

-   Ảnh sản phẩm.
-   Ảnh chi tiết lỗi/hao mòn.
-   Ảnh tem, serial, mã sản phẩm hoặc chi tiết xác thực.
-   Tài liệu đính kèm.

Trong giai đoạn chưa tích hợp Storage, DTO có thể nhận URL. Khi triển
khai Storage/S3:

1.  Client yêu cầu upload hoặc upload qua API theo thiết kế Storage.
2.  Storage trả về file key/URL.
3.  Client gửi danh sách file reference khi tạo Report.
4.  Backend xác thực file thuộc user hoặc phiên upload hợp lệ.
5.  Report lưu file key/reference thay vì tự quản lý binary.

Cần cân nhắc lưu metadata gồm `fileId`, `fileKey`, `contentType`,
`size`, `uploadedBy`, `createdAt`. Không nên tin URL bất kỳ do client tự
truyền vào nếu chưa xác minh quyền sở hữu.

------------------------------------------------------------------------

## 19. Logging và audit

Các hành động nên được ghi nhận:

-   Seller tạo/hủy Request.
-   Appraiser gửi/rút Application.
-   Seller chọn Application.
-   Seller gửi Invitation.
-   Appraiser accept/reject Invitation.
-   Assignment được tạo/bắt đầu/hủy/hoàn tất.
-   Appraiser gửi Report.
-   Seller accept/reject Report.
-   Seller tạo Review.
-   Admin thay đổi trạng thái Appraiser.

Audit log nên lưu:

-   Actor ID.
-   Action.
-   Entity type và entity ID.
-   Thời điểm.
-   Giá trị trạng thái trước/sau nếu phù hợp.
-   Request/correlation ID để truy vết.

Không log nội dung nhạy cảm, token, thông tin xác thực hoặc toàn bộ dữ
liệu file.

------------------------------------------------------------------------

## 20. Các tình huống cần xử lý

  -----------------------------------------------------------------------
  Tình huống                          Cách xử lý
  ----------------------------------- -----------------------------------
  Seller hủy Request khi đang OPEN    Hủy Request; đóng
                                      Application/Invitation pending

  Request hết hạn                     Chuyển EXPIRED; đóng các hồ sơ
                                      pending

  Hai Appraiser cùng accept           Chỉ một Assignment được tạo;
  Invitation/được Seller chọn gần như request lock và unique constraint
  đồng thời                           bảo vệ

  Seller chọn Application trong khi   Transaction kiểm tra lại trạng thái
  Appraiser rút                       Application

  Appraiser accept Invitation khi     Transaction kiểm tra lại Request;
  Seller vừa hủy Request              không tạo Assignment

  Appraiser bị đình chỉ khi đang có   Chặn công việc mới; Assignment hiện
  Assignment                          tại xử lý theo chính sách hỗ trợ

  Appraiser gửi Report khi Report     Từ chối
  trước còn SUBMITTED                 

  Seller gửi accept và reject đồng    Chỉ một hành động thành công nhờ
  thời                                lock/trạng thái

  Seller từ chối Report               Lưu Report cũ, lý do và tăng
                                      rejectionCount

  Seller từ chối đủ 3 lần             Áp dụng trạng thái kết thúc/chuyển
                                      hỗ trợ đã được chốt

  Seller tạo Review hai lần           Unique constraint chặn bản ghi thứ
                                      hai

  Chat tạo thất bại sau khi           Cần transaction cùng module hoặc
  Assignment được tạo                 retry/idempotency nếu Chat độc lập
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 21. Thứ tự triển khai backend

Đề xuất thứ tự triển khai để giảm phụ thuộc:

1.  **Appraiser:** hoàn thiện profile, category, trạng thái xét duyệt và
    điều kiện đủ tư cách nhận việc.
2.  **Request:** tạo, xem, danh sách Market, hủy và state transition.
3.  **Application:** Appraiser ứng tuyển, Seller xem danh sách,
    Appraiser rút.
4.  **Invitation:** Seller mời, Appraiser accept/reject, Seller cancel.
5.  **Assignment:** chọn Application hoặc tạo từ Invitation; xử lý
    concurrency; tạo/liên kết Conversation.
6.  **Report:** gửi Report, lưu revision, Seller accept/reject, giới hạn
    số lần từ chối.
7.  **Review:** Seller đánh giá Appraiser sau khi hoàn tất.
8.  **Storage:** tích hợp upload ảnh/tài liệu và xác thực file
    reference.
9.  **Audit & integration test:** kiểm thử toàn bộ luồng, phân quyền và
    các tình huống đồng thời.

------------------------------------------------------------------------

## 22. Test checklist

### 22.1. Appraiser

-   [ ] User chưa được duyệt không thể ứng tuyển.
-   [ ] Appraiser bị đình chỉ không thể nhận công việc mới.
-   [ ] Appraiser chỉ cập nhật hồ sơ của chính mình.
-   [ ] Seller xem được thông tin công khai cần thiết.

### 22.2. Request

-   [ ] Seller tạo Request cho Listing của mình thành công.
-   [ ] Seller không thể tạo Request cho Listing của người khác.
-   [ ] Request mới có trạng thái `OPEN`.
-   [ ] Request không hợp lệ về deadline bị từ chối.
-   [ ] Request đã đóng không xuất hiện trong Market.
-   [ ] Seller chỉ hủy Request của mình.

### 22.3. Application

-   [ ] Appraiser ứng tuyển Request đang mở thành công.
-   [ ] Appraiser không thể ứng tuyển Request đã đóng/hết hạn.
-   [ ] Không thể ứng tuyển trùng theo chính sách.
-   [ ] Appraiser không thể ứng tuyển Request của chính mình.
-   [ ] Seller chỉ xem Application thuộc Request của mình.
-   [ ] Appraiser chỉ rút Application của mình.
-   [ ] Seller chọn được một Application hợp lệ.

### 22.4. Invitation

-   [ ] Seller mời Appraiser hợp lệ thành công.
-   [ ] Seller không thể mời Appraiser không tồn tại/không hoạt động.
-   [ ] Appraiser khác không thể xem hoặc phản hồi Invitation.
-   [ ] Appraiser được mời accept thành công.
-   [ ] Appraiser được mời reject thành công.
-   [ ] Seller cancel Invitation pending thành công.
-   [ ] Invitation hết hạn không thể accept.

### 22.5. Assignment và Chat

-   [ ] Chọn Application tạo đúng một Assignment.
-   [ ] Accept Invitation tạo đúng một Assignment.
-   [ ] `sourceType` được lưu chính xác.
-   [ ] Request chuyển `ASSIGNED`.
-   [ ] Conversation được tạo/liên kết đúng hai người.
-   [ ] Người ngoài không xem được Assignment/Conversation.
-   [ ] Hai thao tác đồng thời không tạo hai Assignment.
-   [ ] Các Application/Invitation pending còn lại được đóng sau khi
    Assignment được tạo.
-   [ ] Appraiser được gán mới có thể start Assignment.

### 22.6. Report

-   [ ] Chỉ Appraiser được gán mới gửi Report.
-   [ ] Không gửi Report khi Assignment chưa `IN_PROGRESS`.
-   [ ] Không gửi Report mới khi đang có Report `SUBMITTED`.
-   [ ] Revision tăng đúng.
-   [ ] Seller chỉ xem và phản hồi Report thuộc Assignment của mình.
-   [ ] Accept Report hoàn tất Assignment và Request.
-   [ ] Reject Report lưu lý do và giữ lịch sử.
-   [ ] Không thể accept/reject Report đã được phản hồi.
-   [ ] Giới hạn 3 lần từ chối hoạt động đúng.
-   [ ] Sau lần từ chối thứ ba, Assignment được xử lý theo trạng thái
    kết thúc đã chốt.

### 22.7. Review

-   [ ] Chỉ Seller của Assignment đủ điều kiện mới tạo Review.
-   [ ] Không review Assignment chưa hoàn tất nếu chính sách yêu cầu
    completed.
-   [ ] Mỗi Assignment chỉ có một Review.
-   [ ] Điểm đánh giá ngoài khoảng 1--5 bị từ chối.
-   [ ] Review hiển thị đúng Appraiser.

------------------------------------------------------------------------

## 23. Các quyết định cần chốt trước khi khóa implementation

Đây là các điểm còn cần xác nhận với nghiệp vụ hoặc code thực tế:

1.  **ID Appraiser:** `appraiserId` có phải chính là `userId`, hay là ID
    riêng của bảng Appraiser?
2.  **Một Request có được tạo nhiều Assignment theo thời gian không?**
    Thiết kế hiện tại giả định tối đa một Assignment cho mỗi Request.
3.  **Mời lại Appraiser:** có cho phép gửi Invitation mới sau khi
    Invitation trước bị từ chối/hủy không?
4.  **Deadline Request:** là hạn nhận Application/Invitation hay
    deadline hoàn tất thẩm định? Nếu cần cả hai, nên tách thành hai
    field.
5.  **Sau lần từ chối Report thứ ba:** đóng Assignment, chuyển Admin xử
    lý hay mở Dispute?
6.  **Hủy Assignment:** Seller/Appraiser có thể hủy sau khi đã bắt đầu
    không? Có cần lý do và audit không?
7.  **Chat transaction:** Chat và Appraisal có chung
    transaction/database hay cần cơ chế retry?
8.  **Review sau thất bại:** nếu Assignment kết thúc sau ba lần từ chối
    mà không có Report được chấp nhận, Seller có được Review Appraiser
    không?
9.  **Phí thẩm định:** hiện chỉ lưu `proposedFee`/`agreedFee` làm dữ
    liệu nghiệp vụ; công thức hoa hồng, chia doanh thu và payout sẽ
    thiết kế sau.

------------------------------------------------------------------------

## 24. Kết luận

Thiết kế Appraiser/Appraisal của MoldyMarket xoay quanh một nguyên tắc:
**hai cách tìm Appraiser nhưng chỉ một quy trình thẩm định thống nhất**.

-   Appraiser quản lý hồ sơ và điều kiện hoạt động của chuyên gia.
-   Request là nhu cầu thẩm định do Seller tạo.
-   Application cho phép Appraiser chủ động ứng tuyển.
-   Invitation cho phép Seller chủ động mời chuyên gia.
-   Assignment là bản ghi xác nhận công việc và liên kết Conversation.
-   Report lưu từng phiên bản kết quả thẩm định và lịch sử phản hồi.
-   Review ghi nhận chất lượng Appraiser sau khi kết thúc.

Thiết kế này giúp phân tách trách nhiệm rõ ràng, hỗ trợ truy vết, hạn
chế race condition và có thể mở rộng thêm Storage, Dispute, Analytics
hoặc Commission/Payout mà không phải gộp các nghiệp vụ vào một module
lớn.
