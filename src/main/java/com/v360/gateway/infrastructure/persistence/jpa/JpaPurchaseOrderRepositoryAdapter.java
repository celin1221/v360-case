package com.v360.gateway.infrastructure.persistence.jpa;

import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.PurchaseOrderItem;
import com.v360.gateway.domain.model.Vendor;
import com.v360.gateway.domain.port.PurchaseOrderFilter;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
    public Page<PurchaseOrder> findWithFilters(PurchaseOrderFilter filter, Pageable pageable) {
        Specification<PurchaseOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (filter.clientId() != null && !filter.clientId().isBlank()) {
                    predicates.add(cb.equal(root.get("clientId"), filter.clientId().trim()));
                }

                if (filter.vendorTaxId() != null && !filter.vendorTaxId().isBlank()) {
                    String cleanTaxId = Vendor.normalizeTaxId(filter.vendorTaxId());
                    predicates.add(cb.equal(root.get("vendor").get("taxId"), cleanTaxId));
                }

                if (filter.status() != null) {
                    predicates.add(cb.equal(root.get("status"), filter.status()));
                }

                if (filter.onlyPendingBalance() == Boolean.TRUE) {
                    Subquery<Long> subquery = query.subquery(Long.class);
                    Root<PurchaseOrderItem> itemRoot = subquery.from(PurchaseOrderItem.class);
                    subquery.select(cb.literal(1L))
                            .where(
                                    cb.equal(itemRoot.get("order"), root),
                                    cb.greaterThan(
                                            cb.diff(itemRoot.get("quantityOrdered"), itemRoot.get("quantityReceived")),
                                            BigDecimal.ZERO
                                    )
                            );
                    predicates.add(cb.exists(subquery));
                }
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
