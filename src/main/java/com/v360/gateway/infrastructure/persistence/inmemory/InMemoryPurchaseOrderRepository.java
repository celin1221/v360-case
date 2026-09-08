package com.v360.gateway.infrastructure.persistence.inmemory;

import com.v360.gateway.domain.model.OrderStatus;
import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.Vendor;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("in-memory")
public class InMemoryPurchaseOrderRepository implements PurchaseOrderRepository {

    private final Map<Long, PurchaseOrder> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public PurchaseOrder save(PurchaseOrder order) {
        Optional<PurchaseOrder> existingOpt = findByClientIdAndPoNumber(order.getClientId(), order.getPoNumber());

        if (existingOpt.isPresent()) {
            PurchaseOrder existing = existingOpt.get();
            existing.setCreatedAt(order.getCreatedAt());
            existing.setStatus(order.getStatus());
            existing.setCurrency(order.getCurrency());
            existing.setVendor(order.getVendor());
            existing.clearItems();
            order.getItems().forEach(existing::addItem);
            return existing;
        }

        if (order.getId() == null) {
            order.setId(idGenerator.getAndIncrement());
        }
        storage.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<PurchaseOrder> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<PurchaseOrder> findByClientIdAndPoNumber(String clientId, String poNumber) {
        return storage.values().stream()
                .filter(o -> o.getClientId().equalsIgnoreCase(clientId) && o.getPoNumber().equalsIgnoreCase(poNumber))
                .findFirst();
    }

    @Override
    public Optional<PurchaseOrder> findByPoNumber(String poNumber) {
        return storage.values().stream()
                .filter(o -> o.getPoNumber().equalsIgnoreCase(poNumber))
                .findFirst();
    }

    @Override
    public List<PurchaseOrder> findAll() {
        return List.copyOf(storage.values());
    }

    @Override
    public Page<PurchaseOrder> findAll(Pageable pageable) {
        List<PurchaseOrder> all = findAll();
        return toPage(all, pageable);
    }

    @Override
    public Page<PurchaseOrder> findWithFilters(
            String clientId,
            String vendorTaxId,
            OrderStatus status,
            Boolean onlyPendingBalance,
            Pageable pageable
    ) {
        String cleanTaxId = vendorTaxId != null ? Vendor.normalizeTaxId(vendorTaxId) : null;

        List<PurchaseOrder> filtered = storage.values().stream()
                .filter(o -> clientId == null || clientId.isBlank() || o.getClientId().equalsIgnoreCase(clientId.trim()))
                .filter(o -> cleanTaxId == null || cleanTaxId.isBlank() || (o.getVendor() != null && o.getVendor().taxId().equals(cleanTaxId)))
                .filter(o -> status == null || o.getStatus() == status)
                .filter(o -> onlyPendingBalance != Boolean.TRUE || o.hasPendingBalance())
                .toList();

        return toPage(filtered, pageable);
    }

    private Page<PurchaseOrder> toPage(List<PurchaseOrder> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= list.size()) {
            return new PageImpl<>(List.of(), pageable, list.size());
        }
        int end = Math.min(start + pageable.getPageSize(), list.size());
        return new PageImpl<>(list.subList(start, end), pageable, list.size());
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }
}
