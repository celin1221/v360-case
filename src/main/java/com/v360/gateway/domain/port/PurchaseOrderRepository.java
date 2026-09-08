package com.v360.gateway.domain.port;

import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository {

    PurchaseOrder save(PurchaseOrder order);

    Optional<PurchaseOrder> findById(Long id);

    Optional<PurchaseOrder> findByClientIdAndPoNumber(String clientId, String poNumber);

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    List<PurchaseOrder> findAll();

    Page<PurchaseOrder> findAll(Pageable pageable);

    Page<PurchaseOrder> findWithFilters(
            String clientId,
            String vendorTaxId,
            OrderStatus status,
            Boolean onlyPendingBalance,
            Pageable pageable
    );

    long count();

    void deleteAll();
}
