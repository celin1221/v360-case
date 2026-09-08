package com.v360.gateway.ingestion.service;

import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import com.v360.gateway.ingestion.adapter.alfa.AlfaJsonAdapter;
import com.v360.gateway.ingestion.dto.IngestionResultResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AlfaIngestionService {

    private final AlfaJsonAdapter adapter;
    private final PurchaseOrderRepository repository;

    public AlfaIngestionService(AlfaJsonAdapter adapter, PurchaseOrderRepository repository) {
        this.adapter = adapter;
        this.repository = repository;
    }

    public IngestionResultResponse ingest(String jsonContent) {
        List<PurchaseOrder> orders = adapter.parse(jsonContent);

        List<String> orderNumbers = new ArrayList<>();
        int totalItems = 0;

        for (PurchaseOrder order : orders) {
            PurchaseOrder saved = repository.save(order);
            orderNumbers.add(saved.getPoNumber());
            totalItems += saved.getItems().size();
        }

        return IngestionResultResponse.success(AlfaJsonAdapter.ALFA_CLIENT_ID, orderNumbers, totalItems);
    }
}
