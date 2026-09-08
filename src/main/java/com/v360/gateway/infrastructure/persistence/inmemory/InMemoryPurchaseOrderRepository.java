package com.v360.gateway.infrastructure.persistence.inmemory;

import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.model.Vendor;
import com.v360.gateway.domain.port.PurchaseOrderFilter;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.*;
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
    public List<PurchaseOrder> findAll() {
        return List.copyOf(storage.values());
    }

    @Override
    public Page<PurchaseOrder> findAll(Pageable pageable) {
        List<PurchaseOrder> all = findAll();
        return toPage(all, pageable);
    }

    @Override
    public Page<PurchaseOrder> findWithFilters(PurchaseOrderFilter filter, Pageable pageable) {
        String clientId = (filter != null) ? filter.clientId() : null;
        String rawTaxId = (filter != null) ? filter.vendorTaxId() : null;
        String cleanTaxId = (rawTaxId != null && !rawTaxId.isBlank()) ? Vendor.normalizeTaxId(rawTaxId) : null;

        List<PurchaseOrder> filtered = storage.values().stream()
                .filter(o -> clientId == null || clientId.isBlank() || o.getClientId().equalsIgnoreCase(clientId.trim()))
                .filter(o -> cleanTaxId == null || (o.getVendor() != null && o.getVendor().taxId().equals(cleanTaxId)))
                .filter(o -> filter == null || filter.status() == null || o.getStatus() == filter.status())
                .filter(o -> filter == null || filter.onlyPendingBalance() != Boolean.TRUE || o.hasPendingBalance())
                .toList();

        return toPage(filtered, pageable);
    }

    private Page<PurchaseOrder> toPage(List<PurchaseOrder> list, Pageable pageable) {
        List<PurchaseOrder> sorted = applySort(list, pageable.getSort());
        int start = (int) pageable.getOffset();
        if (start >= sorted.size()) {
            return new PageImpl<>(List.of(), pageable, sorted.size());
        }
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(start, end), pageable, sorted.size());
    }

    private List<PurchaseOrder> applySort(List<PurchaseOrder> list, Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return list;
        }

        List<PurchaseOrder> sorted = new ArrayList<>(list);
        Comparator<PurchaseOrder> comparator = null;

        for (Sort.Order order : sort) {
            Comparator<PurchaseOrder> current = switch (order.getProperty()) {
                case "createdAt" -> Comparator.comparing(PurchaseOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
                case "poNumber" -> Comparator.comparing(PurchaseOrder::getPoNumber, Comparator.nullsLast(Comparator.naturalOrder()));
                case "clientId" -> Comparator.comparing(PurchaseOrder::getClientId, Comparator.nullsLast(Comparator.naturalOrder()));
                default -> Comparator.comparing(PurchaseOrder::getId, Comparator.nullsLast(Comparator.naturalOrder()));
            };

            if (order.isDescending()) {
                current = current.reversed();
            }

            comparator = (comparator == null) ? current : comparator.thenComparing(current);
        }

        if (comparator != null) {
            sorted.sort(comparator);
        }

        return sorted;
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
