package com.v360.gateway.ingestion.service;

import com.v360.gateway.domain.model.PurchaseOrder;
import com.v360.gateway.domain.port.PurchaseOrderRepository;
import com.v360.gateway.ingestion.adapter.beta.BetaCsvAdapter;
import com.v360.gateway.ingestion.dto.IngestionResultResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class BetaIngestionService {

    private final BetaCsvAdapter adapter;
    private final PurchaseOrderRepository repository;

    public BetaIngestionService(BetaCsvAdapter adapter, PurchaseOrderRepository repository) {
        this.adapter = adapter;
        this.repository = repository;
    }

    public IngestionResultResponse ingest(String cabecalhoCsv, String itensCsv) {
        List<PurchaseOrder> orders = adapter.parse(cabecalhoCsv, itensCsv);

        List<String> orderNumbers = new ArrayList<>();
        int totalItems = 0;

        for (PurchaseOrder order : orders) {
            PurchaseOrder saved = repository.save(order);
            orderNumbers.add(saved.getPoNumber());
            totalItems += saved.getItems().size();
        }

        return IngestionResultResponse.success(BetaCsvAdapter.BETA_CLIENT_ID, orderNumbers, totalItems);
    }
}
