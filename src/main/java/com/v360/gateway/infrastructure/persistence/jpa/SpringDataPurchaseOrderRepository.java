package com.v360.gateway.infrastructure.persistence.jpa;

import com.v360.gateway.domain.model.PurchaseOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataPurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    @EntityGraph(attributePaths = {"items"})
    Optional<PurchaseOrder> findByClientIdAndPoNumber(String clientId, String poNumber);

    @EntityGraph(attributePaths = {"items"})
    Optional<PurchaseOrder> findFirstByPoNumber(String poNumber);

    @Override
    @NonNull
    @EntityGraph(attributePaths = {"items"})
    Optional<PurchaseOrder> findById(@NonNull Long id);
}
