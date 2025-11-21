package com.mega.warrantymanagementsystem.service.v2;

import com.mega.warrantymanagementsystem.entity.ClaimPartCheck;
import com.mega.warrantymanagementsystem.entity.Part;
import com.mega.warrantymanagementsystem.model.response.PartStatisticResponse;
import com.mega.warrantymanagementsystem.repository.ClaimPartCheckRepository;
import com.mega.warrantymanagementsystem.repository.PartRepository;
import com.mega.warrantymanagementsystem.repository.WarehousePartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class PartStatisticService {

    @Autowired
    private PartRepository partRepository;

    @Autowired
    private WarehousePartRepository warehousePartRepository;

    @Autowired
    private ClaimPartCheckRepository claimPartCheckRepository;

    // Bộ nhớ tạm lưu totalQuantity và isUsedExtra
    private final Map<String, Integer> totalQuantityMemory = new HashMap<>();
    private final Map<String, Integer> usedQuantityMemory = new HashMap<>();


    // ==================== TOOLS ====================

    private int getUsedExtra(String partNumber) {
        return usedQuantityMemory.getOrDefault(partNumber, 0);
    }

    private int getClaimUsed(String partNumber, List<ClaimPartCheck> checks) {
        return checks.stream()
                .filter(c -> partNumber.equals(c.getPartNumber()))
                .filter(c -> Boolean.TRUE.equals(c.getIsRepair()))
                .mapToInt(ClaimPartCheck::getQuantity)
                .sum();
    }


    // ==================== 1. THỐNG KÊ TỔNG ====================
    public List<PartStatisticResponse> getAllStatistic() {

        List<Part> parts = partRepository.findAll();
        List<ClaimPartCheck> checks = claimPartCheckRepository.findAll();

        List<PartStatisticResponse> result = new ArrayList<>();

        for (Part p : parts) {
            String partNumber = p.getPartNumber();

            // Khởi tạo totalQuantity nếu chưa có
            totalQuantityMemory.putIfAbsent(
                    partNumber,
                    warehousePartRepository.findByPart_PartNumber(partNumber)
                            .stream()
                            .mapToInt(wp -> wp.getQuantity())
                            .sum()
            );

            usedQuantityMemory.putIfAbsent(partNumber, 0);

            int totalQuantity = totalQuantityMemory.get(partNumber);
            int claimUsed = getClaimUsed(partNumber, checks);
            int finalUsed = claimUsed + getUsedExtra(partNumber);

            result.add(new PartStatisticResponse(
                    partNumber,
                    p.getNamePart(),
                    totalQuantity,
                    finalUsed
            ));
        }

        return result;
    }


    // ==================== 2. THEO NGÀY ====================
    public List<PartStatisticResponse> getStatisticByDate(LocalDate date) {

        List<PartStatisticResponse> base = getAllStatistic();

        List<ClaimPartCheck> checks = claimPartCheckRepository.findAll().stream()
                .filter(c -> c.getWarrantyClaim() != null)
                .filter(c -> date.equals(c.getWarrantyClaim().getClaimDate()))
                .toList();

        for (PartStatisticResponse r : base) {
            int claimUsed = getClaimUsed(r.getPartId(), checks);
            r.setIsUsed(claimUsed + getUsedExtra(r.getPartId()));
        }

        return base;
    }


    // ==================== 3. THEO THÁNG ====================
    public List<PartStatisticResponse> getStatisticByMonth(int year, int month) {

        List<PartStatisticResponse> base = getAllStatistic();
        YearMonth target = YearMonth.of(year, month);

        List<ClaimPartCheck> checks = claimPartCheckRepository.findAll().stream()
                .filter(c -> c.getWarrantyClaim() != null)
                .filter(c -> c.getWarrantyClaim().getClaimDate() != null)
                .filter(c -> YearMonth.from(c.getWarrantyClaim().getClaimDate()).equals(target))
                .toList();

        for (PartStatisticResponse r : base) {
            int claimUsed = getClaimUsed(r.getPartId(), checks);
            r.setIsUsed(claimUsed + getUsedExtra(r.getPartId()));
        }

        return base;
    }


    // ==================== 4. THEO NĂM ====================
    public List<PartStatisticResponse> getStatisticByYear(int year) {

        List<PartStatisticResponse> base = getAllStatistic();

        List<ClaimPartCheck> checks = claimPartCheckRepository.findAll().stream()
                .filter(c -> c.getWarrantyClaim() != null)
                .filter(c -> c.getWarrantyClaim().getClaimDate() != null)
                .filter(c -> c.getWarrantyClaim().getClaimDate().getYear() == year)
                .toList();

        for (PartStatisticResponse r : base) {
            int claimUsed = getClaimUsed(r.getPartId(), checks);
            r.setIsUsed(claimUsed + getUsedExtra(r.getPartId()));
        }

        return base;
    }


    // ==================== 5. ÁP DỤNG ADDQUANTITY ====================
    public void applyAddQuantityToStatistic(String partNumber, int quantity) {

        // Khởi tạo nếu chưa có
        totalQuantityMemory.putIfAbsent(
                partNumber,
                warehousePartRepository.findByPart_PartNumber(partNumber)
                        .stream()
                        .mapToInt(wp -> wp.getQuantity())
                        .sum()
        );

        usedQuantityMemory.putIfAbsent(partNumber, 0);

        if (quantity > 0) {
            // quantity dương → tăng total
            totalQuantityMemory.put(partNumber, totalQuantityMemory.get(partNumber) + quantity);
        }

        if (quantity < 0) {
            // quantity âm → tăng isUsed
            usedQuantityMemory.put(partNumber, usedQuantityMemory.get(partNumber) + Math.abs(quantity));
        }
    }


    // ==================== 6. XỬ LÝ ISREPAIR ====================
    public void applyRepairUsed(String partNumber, int qty) {
        usedQuantityMemory.put(
                partNumber,
                usedQuantityMemory.getOrDefault(partNumber, 0) + qty
        );
    }
}
