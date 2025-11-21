package com.mega.warrantymanagementsystem.service.v2;

import com.mega.warrantymanagementsystem.entity.*;
import com.mega.warrantymanagementsystem.repository.*;
import com.mega.warrantymanagementsystem.service.PartService;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Version chính xác của RepairPartService:
 * - Lấy ServiceCenter location từ Account (SC Staff hoặc SC Technician).
 * - Trừ part quantity theo location ưu tiên (country -> city -> province -> district).
 * - Không dựa vào WarrantyClaim.location vì không tồn tại.
 */
@Service
public class RepairPartService {

    @Autowired
    private ClaimPartCheckRepository claimPartCheckRepository;

    @Autowired
    private WarehousePartRepository warehousePartRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private WarrantyClaimRepository warrantyClaimRepository;

    @Autowired
    private PartUnderWarrantyRepository partUnderWarrantyRepository;

    @Autowired
    private PartStatisticService partStatisticService;

    @Autowired
    private PartService partService;

    @Autowired
    private ModelMapper mapper;

    // ==================== TRỪ PART KHI CLAIM REPAIR ====================
    @Transactional
    public void handleRepairParts(String warrantyId) {
        WarrantyClaim claim = warrantyClaimRepository.findById(warrantyId)
                .orElse(null);
        if (claim == null) {
            System.out.println("Không tìm thấy claim ID: " + warrantyId);
            return;
        }

        // Lấy location từ service center của SC staff hoặc technician
        String scLocation = getServiceCenterLocationFromClaim(claim);

        // Lọc tất cả ClaimPartCheck thuộc claim này và là repair
        List<ClaimPartCheck> repairParts = claimPartCheckRepository.findAll().stream()
                .filter(c -> c.getWarrantyClaim() != null && warrantyId.equals(c.getWarrantyClaim().getClaimId()))
                .filter(c -> Boolean.TRUE.equals(c.getIsRepair()))
                .collect(Collectors.toList());

        for (ClaimPartCheck check : repairParts) {
            String partNumber = check.getPartNumber();
            int needed = check.getQuantity();

            List<WarehousePart> candidates = warehousePartRepository.findByPart_PartNumber(partNumber)
                    .stream()
                    .filter(wp -> wp.getQuantity() > 0)
                    .filter(wp -> {
                        Part part = wp.getPart();
                        if (part == null) return false;
                        PartUnderWarranty puw = partUnderWarrantyRepository.findById(part.getPartNumber()).orElse(null);
                        return puw != null && Boolean.TRUE.equals(puw.getIsEnable());
                    })
                    .collect(Collectors.toList());

            if (candidates.isEmpty()) continue;

            // Sắp xếp warehouse theo độ gần location
            candidates.sort(Comparator.comparingInt((WarehousePart wp) ->
                    locationMatchScore(wp.getWarehouse().getLocation(), scLocation)).reversed());

            int remaining = needed;
            for (WarehousePart wp : candidates) {
                if (remaining <= 0) break;
                int take = Math.min(wp.getQuantity(), remaining);
                wp.setQuantity(wp.getQuantity() - take);
                warehousePartRepository.save(wp);
                partService.updateLowPartStatus(wp.getWarehouse(), wp);
                remaining -= take;

                // ======= Báo về thống kê: dùng repair -------
//                partStatisticService.applyRepairUsed(partNumber, take);
            }

            if (remaining > 0) {
                System.out.println("Thiếu part " + partNumber + ", còn thiếu " + remaining);
            }
        }
    }

    // ==================== TÍNH LOCATION SERVICE CENTER ====================
    private String getServiceCenterLocationFromClaim(WarrantyClaim claim) {
        try {
            // Ưu tiên SC staff
            Account staffId = claim.getServiceCenterStaff();
            Account techId = claim.getServiceCenterTechnician();

            if (staffId != null) {
                Account staff = accountRepository.findByAccountId(staffId.getAccountId());
                if (staff != null && staff.getServiceCenter() != null) {
                    return staff.getServiceCenter().getLocation();
                }
            }

            if (techId != null) {
                Account tech = accountRepository.findByAccountId(techId.getAccountId());
                if (tech != null && tech.getServiceCenter() != null) {
                    return tech.getServiceCenter().getLocation();
                }
            }
        } catch (Exception e) {
            System.out.println("Không thể xác định service center location cho claim " + claim.getClaimId());
        }
        return null;
    }

    // ==================== BỔ SUNG SỐ LƯỢNG PART ====================
    @Transactional
    public void addQuantity(String partNumber, int quantity, int warehouseId) {
        WarehousePartId id = new WarehousePartId(partNumber, warehouseId);
        WarehousePart wp = warehousePartRepository.findById(id).orElse(null);

        if (wp == null) throw new RuntimeException("Không tìm thấy part " + partNumber + " trong warehouse " + warehouseId);

        wp.setQuantity(wp.getQuantity() + quantity);
        warehousePartRepository.save(wp);
        partService.updateLowPartStatus(wp.getWarehouse(), wp);

        // Cập nhật thống kê tổng quantity
        partStatisticService.applyAddQuantityToStatistic(partNumber, quantity);
    }

    // ==================== LOCATION MATCH SCORE ====================
    private int locationMatchScore(String loc, String scLoc) {
        if (loc == null || scLoc == null) return 0;
        String[] a = loc.toLowerCase().split(",");
        String[] b = scLoc.toLowerCase().split(",");
        int score = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i].trim().equals(b[i].trim())) score++;
            else break;
        }
        return score;
    }
}
