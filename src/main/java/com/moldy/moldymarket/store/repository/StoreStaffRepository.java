package com.moldy.moldymarket.store.repository;

import com.moldy.moldymarket.store.entity.StoreStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface StoreStaffRepository extends JpaRepository<StoreStaff, UUID> {

    @EntityGraph(attributePaths = "store")
    List<StoreStaff> findAllByUserIdIn(Collection<UUID> userIds);

    // Tìm tất cả nhân viên của 1 cửa hàng
    List<StoreStaff> findAllByStoreId(UUID storeId);

    // Kiểm tra xem user này đã là staff của store này chưa
    boolean existsByStoreIdAndUserId(UUID storeId, UUID userId);

    // Tìm nhân viên theo id của store và id của record staff
    Optional<StoreStaff> findByIdAndStoreId(UUID id, UUID storeId);

    // Tìm thông tin staff theo userId (để nạp quyền ủy quyền lúc đăng nhập & xem quyền của mình)
    Optional<StoreStaff> findByUserId(UUID userId);

    long countByStoreId(UUID storeId);}
