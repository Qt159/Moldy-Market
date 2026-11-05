package com.moldy.moldymarket.order.repository;

import com.moldy.moldymarket.order.entity.CheckoutGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckoutGroupRepository extends JpaRepository<CheckoutGroup, String> {

}
