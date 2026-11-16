# Kiến trúc Payment Module 

---

## 1. Tổng quan
Module payment trong Moldy Market là module đảm nhiệm vai trò chính trong luồng thực
hiện thanh toán của ứng dụng.
Dưới đây các luồng hoạt động sẽ ứng dụng payment module:
+ Người dùng (Users) khi chọn phương thức thanh toán là ***online banking***. 
+ Hệ thống chuyển tiền:
  + Giải ngân cho sellers nếu giao hàng thành công và không có bất kì khiếu nại gì trong 
  thời gian quy định.
  + Hoàn tiền cho buyers nếu như hệ thống xác thực khiếu nại của người mua là chính xác, ngược lại 
  thì giải ngân cho sellers.
+ Người dùng (all users) thực hiện rút tiền về tài khoản của họ.

**Các module liên quan:**

| Module | Trách nhiệm |
|---|---|
| Order | Quản lý đơn hàng, số tiền phải thanh toán và vòng đời giao hàng.  |
| Payment  | Quản lý giao dịch thanh toán, tích hợp provider, webhook và trạng thái thanh toán.  |
| Wallet  | Quản lý số dư, sổ cái, và yêu cầu rút tiền.  |
| Settlement  |Quản lý điều kiện và tiến trình giải ngân cho seller. |
|Dispute | Quản lý khiếu nại và kết quả giải quyết tranh chấp. |
|Notification | Gửi thông báo đến người dùng.|
|Admin | Giám sát và xử lý các giao dịch cần can thiệp thủ công.|

***Chú ý: Payment không quản lý toàn bộ nghiệp vụ tài chính*** Payment quản lý các giao dịch; Wallet quản lý sổ cái; Settlement quản lý giải ngân; Dispute quản lý quyết định khiếu nại; Order quản lý vòng đời đơn hàng.