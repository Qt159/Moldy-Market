package com.moldy.moldymarket.store.repository;

import com.moldy.moldymarket.store.entity.StoreStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreStaffRepository extends JpaRepository<StoreStaff, UUID> {

    // Tìm tất cả nhân viên của 1 cửa hàng
    List<StoreStaff> findAllByStoreId(UUID storeId);

    // Kiểm tra xem user này đã là staff của store này chưa
    boolean existsByStoreIdAndUserId(UUID storeId, UUID userId);

    // Tìm nhân viên theo id của store và id của record staff
    Optional<StoreStaff> findByIdAndStoreId(UUID id, UUID storeId);

    // Tìm thông tin staff theo userId (để nạp quyền ủy quyền lúc đăng nhập & xem quyền của mình)
    Optional<StoreStaff> findByUserId(UUID userId);

    long countByStoreId(UUID storeId);}
