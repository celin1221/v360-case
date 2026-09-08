package com.v360.gateway.infrastructure.persistence.jpa;

import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.domain.model.Vendor;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!in-memory")
@Transactional
public class JpaPurchaseOrderRepositoryAdapter implements PurchaseOrderRepository {

    private final SpringDataPurchaseOrderRepository springDataRepo;

    public JpaPurchaseOrderRepositoryAdapter(SpringDataPurchaseOrderRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public PurchaseOrder save(PurchaseOrder order) {
        Optional<PurchaseOrder> existingOpt = springDataRepo.findByClientIdAndPoNumber(
                order.getClientId(),
                order.getPoNumber()
        );

        if (existingOpt.isPresent()) {
            PurchaseOrder existing = existingOpt.get();
            existing.setCreatedAt(order.getCreatedAt());
            existing.setStatus(order.getStatus());
            existing.setCurrency(order.getCurrency());
            existing.setVendor(order.getVendor());

            existing.getItems().clear();
            for (PurchaseOrderItem item : order.getItems()) {
                existing.addItem(item);
            }
            return springDataRepo.save(existing);
        }

        return springDataRepo.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseOrder> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseOrder> findByClientIdAndPoNumber(String clientId, String poNumber) {
        return springDataRepo.findByClientIdAndPoNumber(clientId, poNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseOrder> findByPoNumber(String poNumber) {
        return springDataRepo.findFirstByPoNumber(poNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrder> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrder> findAll(Pageable pageable) {
        return springDataRepo.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrder> findWithFilters(
            String clientId,
            String vendorTaxId,
            OrderStatus status,
            Boolean onlyPendingBalance,
            Pageable pageable
    ) {
        Specification<PurchaseOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (clientId != null && !clientId.isBlank()) {
                predicates.add(cb.equal(root.get("clientId"), clientId.trim()));
            }

            if (vendorTaxId != null && !vendorTaxId.isBlank()) {
                String cleanTaxId = Vendor.normalizeTaxId(vendorTaxId);
                predicates.add(cb.equal(root.get("vendor").get("taxId"), cleanTaxId));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (onlyPendingBalance == Boolean.TRUE) {
                query.distinct(true);
                Join<PurchaseOrder, PurchaseOrderItem> itemJoin = root.join("items");
                predicates.add(cb.greaterThan(
                        cb.diff(itemJoin.get("quantityOrdered"), itemJoin.get("quantityReceived")),
                        BigDecimal.ZERO
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return springDataRepo.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return springDataRepo.count();
    }

    @Override
    public void deleteAll() {
        springDataRepo.deleteAll();
    }
}
