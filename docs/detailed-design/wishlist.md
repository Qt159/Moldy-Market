WISHLIST MODULE 
Tài liệu mô tả thiết kế và hành vi của Wishlist module trong Moldy
Market.
Trạng thái: Proposed / cần đối chiếu với implementation hiện tại
trước khi merge.

1. Tổng quan
Wishlist cho phép người dùng đã đăng nhập lưu sản phẩm vào danh sách yêu
thích để xem lại sau.

Module quản lý quan hệ giữa User và Product. Wishlist không sở
hữu dữ liệu sản phẩm; thông tin sản phẩm vẫn thuộc Product module.
Mục tiêu
- Thêm sản phẩm vào wishlist.
- Xóa một sản phẩm khỏi wishlist.
- Lấy danh sách wishlist có phân trang.
- Kiểm tra một sản phẩm có nằm trong wishlist của người dùng hay
  không.
- Đếm số sản phẩm trong wishlist của người dùng.
- Đếm số lượt wishlist của một sản phẩm khi nghiệp vụ cần thống kê.
Nguyên tắc thiết kế
- Mỗi bản ghi wishlist liên kết một userId với một productId.
- Một người dùng không thể lưu trùng cùng một sản phẩm.
- Database unique constraint là lớp bảo vệ cuối cùng cho tính duy
  nhất.
- Wishlist lưu ID tham chiếu, không sao chép toàn bộ dữ liệu Product.
- Danh sách được sắp xếp mới nhất trước và hỗ trợ pagination.
- Các thao tác thay đổi dữ liệu phải chạy trong transaction.
2. Phạm vi và trách nhiệm
Wishlist module chịu trách nhiệm
- Quản lý bản ghi wishlist.
- Kiểm tra trùng lặp theo user và product.
- Truy vấn danh sách wishlist theo người dùng.
- Cung cấp các thao tác kiểm tra và đếm phục vụ các module khác.
Không thuộc trách nhiệm của Wishlist
- Quản lý thông tin, giá, hình ảnh hoặc trạng thái sản phẩm.
- Quản lý tài khoản và xác thực người dùng.
- Quyết định sản phẩm có được phép hiển thị trên marketplace hay
  không.
- Xử lý đặt hàng, thanh toán hoặc tồn kho.
Các quy tắc về sản phẩm bị ẩn, bị xóa mềm hoặc không còn khả dụng cần
thống nhất với Product module. Ví dụ, sản phẩm đã bán có thể vẫn xuất
hiện trong wishlist nhưng được hiển thị trạng thái không khả dụng; sản
phẩm bị xóa hoặc ẩn có thể cần được loại khỏi kết quả tùy chính sách của
hệ thống.
3. Kiến trúc và thành phần
Luồng xử lý tổng quát:
Client
  |
  v
WishlistController
  |
  v
WishlistService
  |----------------------|
  v                      v
WishlistRepository   ProductRepository
  |
  v
PostgreSQL
  Thành phần                          Trách nhiệm
  WishlistController                Nhận HTTP request, lấy user hiện
tại, validate tham số và trả
response

  WishlistService                   Thực thi nghiệp vụ wishlist và quản
lý transaction

  WishlistRepository                Truy vấn, thêm và xóa dữ liệu
wishlist

  WishlistMapper                    Chuyển WishlistItem thành
response DTO

  WishlistItem                      Entity ánh xạ bảng wishlist_items
ProductRepository                 Kiểm tra sản phẩm có tồn tại theo
quy tắc tích hợp với Product module
Wishlist sử dụng userId và productId làm dữ liệu tham chiếu. Nếu hệ
thống không khai báo quan hệ JPA trực tiếp tới User và Product, việc lưu
ID giúp giảm coupling giữa các feature module.
4. Data model
4.1. Bảng wishlist_items
  Column            Type              Constraint        Mô tả
  id              UUID              Primary key, not  ID của wishlist
null              item

  user_id         UUID              Not null          Người dùng sở hữu
wishlist item

  product_id      UUID              Not null          Sản phẩm được lưu
created_at      TIMESTAMP WITH    Not null          Thời điểm thêm
TIME ZONE                           vào wishlist
4.2. Ràng buộc dữ liệu
- PRIMARY KEY (id)
- NOT NULL (user_id, product_id, created_at)
- UNIQUE (user_id, product_id)
Unique constraint bảo đảm một user chỉ có tối đa một wishlist item cho
một product, kể cả khi hai request thêm cùng lúc.
4.3. Index đề xuất
  Index                               Mục đích
  Unique (user_id, product_id)      Chặn dữ liệu trùng và hỗ trợ tìm
kiếm theo user + product

  (product_id)                      Hỗ trợ đếm hoặc truy vấn wishlist
theo sản phẩm
(user_id, created_at, id)         Hỗ trợ lấy wishlist theo user, sắp
xếp mới nhất trước
Index cần được đối chiếu với schema thực tế trước khi tạo migration để
tránh tạo trùng index. Với PostgreSQL B-tree, index
(user_id, created_at, id) có thể được scan ngược để phục vụ thứ tự
created_at DESC, id DESC.
5. API specification
Base path:
/api/v1/wishlists
Các endpoint thao tác trên wishlist cá nhân yêu cầu người dùng đã đăng
nhập. User ID phải lấy từ security context (@AuthenticationPrincipal),
không nhận userId tùy ý từ request của client.
5.1. Thêm sản phẩm vào wishlist
POST /api/v1/wishlists/{productId}
Mục đích: Thêm sản phẩm vào wishlist của người dùng hiện tại.

Xử lý chính: 1. Lấy userId từ authenticated principal. 2. Kiểm tra
sản phẩm theo quy tắc tồn tại/khả dụng của Product module. 3. Kiểm tra
sản phẩm đã nằm trong wishlist hay chưa. 4. Tạo và lưu WishlistItem.
5. Trả về thông tin wishlist item vừa tạo.

Response dự kiến: 201 Created
{
  "success": true,
  "message": "Added product to wishlist",
  "data": {
    "id": "uuid",
    "productId": "uuid",
    "createdAt": "2026-11-20T10:00:00Z"
  }
}
Nếu sản phẩm đã tồn tại trong wishlist, API có thể trả lỗi conflict
(409 Conflict) theo chính sách hiện tại. Nếu muốn API có tính
idempotent, có thể thay đổi để request thêm trùng vẫn trả kết quả thành
công. Cần chọn và áp dụng thống nhất.
5.2. Xóa sản phẩm khỏi wishlist
DELETE /api/v1/wishlists/{productId}
Mục đích: Xóa sản phẩm khỏi wishlist của người dùng hiện tại.

Xử lý chính: 1. Lấy userId từ authenticated principal. 2. Xóa bản
ghi theo cặp userId và productId. 3. Nếu không có bản ghi bị xóa, xử
lý theo quy ước lỗi của API (ví dụ 404 Wishlist item not found).

Response dự kiến: 200 OK hoặc 204 No Content, tùy chuẩn response
chung của dự án.
5.3. Lấy wishlist
GET /api/v1/wishlists?page=0&size=20
Mục đích: Lấy danh sách wishlist của người dùng hiện tại.

Query parameters:

  Parameter     Default Constraint   Mô tả
  page            0 >= 0       Số trang, bắt đầu từ 0
size           20 1..50      Số phần tử mỗi trang

Sắp xếp mặc định: createdAt DESC, id DESC.

id được dùng làm tie-breaker để kết quả ổn định khi nhiều bản ghi có
cùng createdAt.

Response data: Page<WishlistItemResponse> hoặc một pagination
response DTO thống nhất của dự án.
WishlistItemResponse hiện có thể chỉ chứa id, productId,
createdAt. Nếu giao diện cần tên, ảnh, giá hoặc trạng thái sản phẩm,
cần tích hợp Product summary data. Không nên tự tạo một bản sao
Product DTO nếu dự án đã có DTO dùng chung phù hợp.

5.4. Kiểm tra sản phẩm có trong wishlist
GET /api/v1/wishlists/check/{productId}
Mục đích: Kiểm tra sản phẩm có nằm trong wishlist của người dùng
hiện tại hay không.

Response data:
true
hoặc
false
5.5. Đếm số sản phẩm trong wishlist của người dùng
GET /api/v1/wishlists/count
Mục đích: Trả về tổng số wishlist item thuộc người dùng hiện tại.
5.6. Đếm số lượt wishlist của sản phẩm
GET /api/v1/wishlists/products/{productId}/count
Mục đích: Trả về số người dùng đã lưu sản phẩm.

Endpoint này chỉ nên public nếu số lượt wishlist là thông tin được phép
công khai. Nếu không, cần giới hạn quyền truy cập hoặc chỉ sử dụng nội
bộ cho Analytics/Product.
6. Business flows
6.1. Add to wishlist
User
 |
 | POST /wishlists/{productId}
 v
WishlistController
 |
 | lấy userId từ principal
 v
WishlistService
 |
 | kiểm tra product
 | kiểm tra userId + productId đã tồn tại
 | tạo WishlistItem
 v
WishlistRepository
 |
 | INSERT
 v
PostgreSQL
 |
 | trả saved entity
 v
WishlistMapper
 |
 v
Response
Các điều kiện: - User phải được xác thực. - Product phải đáp ứng chính
sách tồn tại/hiển thị của Product module. - Không tạo bản ghi trùng
(user_id, product_id). - Unique constraint trong database là lớp bảo
vệ cuối cùng trước race condition.
6.2. Remove from wishlist
User
 |
 | DELETE /wishlists/{productId}
 v
WishlistController
 |
 v
WishlistService
 |
 | DELETE WHERE user_id = ? AND product_id = ?
 v
WishlistRepository
 |
 v
PostgreSQL
Nên xóa trực tiếp theo cặp userId và productId, thay vì phải query
entity trước rồi mới xóa, nếu không cần dữ liệu entity cho bước xử lý
khác.
6.3. Get wishlist
User
 |
 | GET /wishlists?page=0&size=20
 v
WishlistController
 |
 | tạo Pageable + Sort
 v
WishlistService
 |
 v
WishlistRepository
 |
 | SELECT theo user_id
 | ORDER BY created_at DESC, id DESC
 | LIMIT / OFFSET
 v
PostgreSQL
 |
 v
WishlistMapper
 |
 v
Paginated response
7. Transaction và concurrency
7.1. Transaction
- Các phương thức đọc có thể chạy với
  @Transactional(readOnly = true) ở cấp service.
- Các phương thức thêm/xóa cần transaction ghi.
- Bulk delete dùng @Modifying và phải được gọi trong transaction.
7.2. Race condition khi thêm trùng
Luồng existsByUserIdAndProductId() rồi save() không loại bỏ hoàn
toàn race condition:
1. Request A kiểm tra: chưa tồn tại.
2. Request B kiểm tra: chưa tồn tại.
3. Cả hai cùng insert.
Do đó, unique constraint (user_id, product_id) là bắt buộc nếu muốn
bảo đảm dữ liệu không trùng.

Nếu cần xử lý trường hợp cạnh tranh một cách thân thiện, có thể: - Bắt
và chuyển đổi lỗi unique constraint tại transaction boundary phù hợp;
hoặc - Dùng câu lệnh PostgreSQL INSERT ... ON CONFLICT DO NOTHING nếu
dự án chấp nhận native query.

Không nên bắt DataIntegrityViolationException rồi tiếp tục xử lý trong
cùng transaction đã bị đánh dấu rollback-only.
8. Validation và error handling
Các lỗi nghiệp vụ dự kiến:
  Trường hợp            Error code gợi ý                          HTTP status gợi ý
  Product không tồn     PRODUCT_NOT_FOUND                                     404
tại/không được phép
lưu                                                  

  Product đã có trong   PRODUCT_ALREADY_WISHLISTED                            409
wishlist                                             

  Wishlist item không   WISHLIST_ITEM_NOT_FOUND                               404
tồn tại khi xóa                                      

  Chưa đăng nhập        Theo cơ chế Security chung                              401

  Không có quyền truy   Theo cơ chế Security chung                              403
cập                                                  
page hoặc size    Validation error chung                                  400
không hợp lệ
Tên error code và response format cần đồng bộ với
GlobalExceptionHandler hiện có.
9. Security và authorization
- Không nhận userId từ request body hoặc query parameter cho các
  thao tác wishlist cá nhân.
- Luôn lấy user hiện tại từ @AuthenticationPrincipal.
- User chỉ được đọc, thêm hoặc xóa wishlist của chính mình.
- Endpoint đếm wishlist theo product cần có chính sách truy cập rõ
  ràng.
- Các endpoint cá nhân phải được cấu hình yêu cầu authentication trong
  Spring Security.
10. Performance considerations
- Sử dụng pagination thay vì tải toàn bộ wishlist khi danh sách có thể
  tăng lớn.
- Dùng existsBy... cho thao tác kiểm tra tồn tại; không dùng count
  nếu chỉ cần biết có hay không.
- Khi chỉ cần xóa dữ liệu, cân nhắc bulk delete để tránh query entity
  trước.
- Dùng index phù hợp với các truy vấn phổ biến.
- Tránh N+1 nếu sau này cần join Product data để hiển thị wishlist.
- Nếu cần lấy Product summary cho nhiều item, ưu tiên truy vấn batch
  theo danh sách productId thay vì gọi Product repository riêng cho
  từng item.
11. Testing checklist
Add wishlist
- [ ] Thêm product hợp lệ thành công.
- [ ] Product không tồn tại trả lỗi phù hợp.
- [ ] Thêm cùng product lần thứ hai xử lý đúng chính sách duplicate.
- [ ] User A và User B có thể cùng lưu một product.
- [ ] Request không có authentication bị từ chối.
- [ ] Hai request đồng thời không tạo dữ liệu trùng.
Remove wishlist
- [ ] Xóa item đang tồn tại thành công.
- [ ] User không thể xóa item thuộc user khác.
- [ ] Xóa item không tồn tại trả lỗi theo quy ước.
- [ ] Xóa một item không ảnh hưởng các item khác.
Get wishlist
- [ ] Chỉ trả về item của user hiện tại.
- [ ] Sắp xếp mới nhất trước.
- [ ] Pagination hoạt động đúng.
- [ ] page < 0, size < 1, size > 50 bị từ chối.
- [ ] Trang rỗng được xử lý đúng.
Check và count
- [ ] Check trả true khi item tồn tại.
- [ ] Check trả false khi item không tồn tại.
- [ ] Count theo user chính xác.
- [ ] Count theo product chính xác.
- [ ] Endpoint count theo product tuân thủ đúng access policy.
12. Những điểm cần xác nhận trước khi hoàn tất
- [ ] Product nào được phép thêm vào wishlist: chỉ ACTIVE hay cả
  SOLD/OUT_OF_STOCK?
- [ ] Product bị soft-delete hoặc hidden có bị xóa khỏi wishlist hay
  chỉ ẩn khỏi response?
- [ ] Add trùng trả 409 Conflict hay xử lý idempotent?
- [ ] GET /wishlists chỉ trả productId hay cần Product summary?
- [ ] Product wishlist count có public không?
- [ ] Pagination response dùng Page trực tiếp hay DTO pagination
  chung?
- [ ] Các index đề xuất đã tồn tại trong database chưa?
13. Implementation alignment
Tài liệu này là đặc tả đề xuất. Khi triển khai, cần bảo đảm tên method
giữa Repository, Service và Controller khớp nhau.

Một cách tổ chức nhất quán:
- Repository:
  Page<WishlistItem> findByUserId(UUID userId, Pageable pageable)
- Controller: tạo PageRequest với sort createdAt DESC, id DESC
- Service: gọi repository và map bằng
  Page.map(wishlistMapper::toResponse)
- Entity: tạo mới qua factory method như
  WishlistItem.create(userId, productId)
- Delete: xóa theo userId + productId, kiểm tra số dòng bị xóa nếu
  cần phân biệt not found.
Không giữ các query hoặc method chưa có use case thực tế. Khi schema
hiện tại đã có dữ liệu, kiểm tra database trước khi đổi
constraint/index; không dựa hoàn toàn vào ddl-auto=update để quản lý
migration production.