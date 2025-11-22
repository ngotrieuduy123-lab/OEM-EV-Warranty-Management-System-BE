package com.mega.warrantymanagementsystem.service.v2;

import com.mega.warrantymanagementsystem.entity.WarrantyClaim;
import com.mega.warrantymanagementsystem.entity.WarrantyClaimProgress;
import com.mega.warrantymanagementsystem.entity.entity.WarrantyClaimStatus;
import com.mega.warrantymanagementsystem.exception.exception.BusinessLogicException;
import com.mega.warrantymanagementsystem.exception.exception.ResourceNotFoundException;
import com.mega.warrantymanagementsystem.model.response.WarrantyClaimResponse;
import com.mega.warrantymanagementsystem.repository.WarrantyClaimProgressRepository;
import com.mega.warrantymanagementsystem.repository.WarrantyClaimRepository;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClaimWorkflowService {

    @Autowired
    private WarrantyClaimRepository warrantyClaimRepository;

    @Autowired
    private WarrantyClaimProgressRepository progressRepository;

    @Autowired
    private RepairPartService repairPartService;

    @Autowired
    private ModelMapper modelMapper;

    // ---------------- Technician hoàn tất REPAIR -> HANDOVER ----------------
    @Transactional
    public WarrantyClaimResponse technicianToggleDone(String claimId, String technicianId, boolean done) {
        WarrantyClaim claim = warrantyClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found: " + claimId));

        if (claim.getServiceCenterTechnician() == null ||
                !claim.getServiceCenterTechnician().getAccountId().equalsIgnoreCase(technicianId)) {
            throw new BusinessLogicException("Technician không có quyền thao tác claim này.");
        }

        if (claim.getStatus() != WarrantyClaimStatus.REPAIR) {
            throw new BusinessLogicException("Chỉ có thể hoàn tất khi claim ở trạng thái REPAIR.");
        }

        claim.setTechnicianDone(done);
        if (done) {
            claim.setStatus(WarrantyClaimStatus.HANDOVER);
            logProgress(claim, WarrantyClaimStatus.HANDOVER);
        }

        warrantyClaimRepository.save(claim);
        return mapToResponse(claim);
    }

    // ---------------- Staff xác nhận HANDOVER -> DONE ----------------
    @Transactional
    public WarrantyClaimResponse scStaffToggleDone(String claimId, String staffId, boolean done) {
        WarrantyClaim claim = warrantyClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found: " + claimId));

        if (claim.getServiceCenterStaff() == null ||
                !claim.getServiceCenterStaff().getAccountId().equalsIgnoreCase(staffId)) {
            throw new BusinessLogicException("Staff không có quyền thao tác claim này.");
        }

        if (claim.getStatus() != WarrantyClaimStatus.HANDOVER) {
            throw new BusinessLogicException("Staff chỉ có thể hoàn tất khi claim ở HANDOVER.");
        }

        claim.setScStaffDone(done);
        if (done) {
            claim.setStatus(WarrantyClaimStatus.DONE);
            logProgress(claim, WarrantyClaimStatus.DONE);
        }

        warrantyClaimRepository.save(claim);
        return mapToResponse(claim);
    }

    // ---------------- EVM thêm mô tả -> DECIDE -> REPAIR ----------------
    @Transactional
    public WarrantyClaimResponse updateEvmDescription(String claimId, String evmId, String description) {
        WarrantyClaim claim = warrantyClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found: " + claimId));

        if (claim.getEvm() == null ||
                !claim.getEvm().getAccountId().equalsIgnoreCase(evmId)) {
            throw new BusinessLogicException("EVM không được phép chỉnh claim này.");
        }

        if (claim.getStatus() != WarrantyClaimStatus.DECIDE) {
            throw new BusinessLogicException("Chỉ có thể thêm mô tả khi claim đang ở DECIDE.");
        }

        claim.setEvmDescription(description);
        claim.setStatus(WarrantyClaimStatus.REPAIR);
        logProgress(claim, WarrantyClaimStatus.REPAIR);
        repairPartService.handleRepairParts(claimId);

        warrantyClaimRepository.save(claim);
        return mapToResponse(claim);
    }

    // ---------------- Technician bỏ qua sửa chữa -> CHECK -> HANDOVER ----------------
    @Transactional
    public WarrantyClaimResponse technicianSkipRepair(String claimId, String technicianId) {
        WarrantyClaim claim = warrantyClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found: " + claimId));

        if (claim.getServiceCenterTechnician() == null ||
                !claim.getServiceCenterTechnician().getAccountId().equalsIgnoreCase(technicianId)) {
            throw new BusinessLogicException("Technician không có quyền thao tác claim này.");
        }

        if (claim.getStatus() != WarrantyClaimStatus.CHECK) {
            throw new BusinessLogicException("Chỉ có thể bỏ qua sửa chữa khi claim đang ở CHECK.");
        }

        claim.setIsRepair(false);
        claim.setTechnicianDone(true);
        claim.setStatus(WarrantyClaimStatus.HANDOVER);
        logProgress(claim, WarrantyClaimStatus.HANDOVER);

        warrantyClaimRepository.save(claim);
        return mapToResponse(claim);
    }

    // ---------------- Staff bỏ qua EVM -> DECIDE -> HANDOVER ----------------
    @Transactional
    public WarrantyClaimResponse scStaffSkipEvmAndHandover(String claimId, String staffId) {
        WarrantyClaim claim = warrantyClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Claim: " + claimId));

        if (claim.getServiceCenterStaff() == null ||
                !claim.getServiceCenterStaff().getAccountId().equalsIgnoreCase(staffId)) {
            throw new BusinessLogicException("Staff không có quyền thao tác claim này.");
        }

        if (claim.getStatus() != WarrantyClaimStatus.DECIDE) {
            throw new BusinessLogicException("Chỉ có thể bỏ qua EVM khi claim đang ở DECIDE.");
        }

        if (claim.getEvm() != null) {
            throw new BusinessLogicException("Claim đã có EVM, không thể bỏ qua.");
        }

        claim.setStatus(WarrantyClaimStatus.HANDOVER);
        logProgress(claim, WarrantyClaimStatus.HANDOVER);

        warrantyClaimRepository.save(claim);
        return mapToResponse(claim);
    }

    // ---------------- EVM quyết định bỏ qua sửa -> DECIDE -> HANDOVER ----------------
    @Transactional
    public WarrantyClaimResponse evmDecisionToHandover(String claimId, String evmId, String description) {
        WarrantyClaim claim = warrantyClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found: " + claimId));

        if (claim.getEvm() == null ||
                !claim.getEvm().getAccountId().equalsIgnoreCase(evmId)) {
            throw new BusinessLogicException("EVM không có quyền thao tác claim này.");
        }

        if (claim.getStatus() != WarrantyClaimStatus.DECIDE) {
            throw new BusinessLogicException("Chỉ có thể chuyển DECIDE -> HANDOVER.");
        }

        if (description != null && !description.isBlank())
            claim.setEvmDescription(description);

        claim.setStatus(WarrantyClaimStatus.HANDOVER);
        logProgress(claim, WarrantyClaimStatus.HANDOVER);

        warrantyClaimRepository.save(claim);
        return mapToResponse(claim);
    }

    // ---------------- Ghi lại tiến trình ----------------
    private void logProgress(WarrantyClaim claim, WarrantyClaimStatus newStatus) {
        List<WarrantyClaimProgress> history =
                progressRepository.findByWarrantyClaim_ClaimIdOrderByTimestampAsc(claim.getClaimId());

        String duration = null;
        if (!history.isEmpty()) {
            WarrantyClaimProgress prev = history.get(history.size() - 1);
            Duration diff = Duration.between(prev.getTimestamp(), LocalDateTime.now());
            long seconds = diff.getSeconds();
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long remainingSeconds = seconds % 60;

            if (hours > 0) duration = hours + "h";
            else if (minutes > 0) duration = minutes + "m";
            else duration = remainingSeconds + "s";
        }

        WarrantyClaimProgress progress = new WarrantyClaimProgress();
        progress.setWarrantyClaim(claim);
        progress.setStatus(newStatus.name());
        progress.setTimestamp(LocalDateTime.now());
        progress.setDurationFromPrevious(duration);
        progressRepository.save(progress);
    }


    // ---------------- Map entity -> response ----------------
    private WarrantyClaimResponse mapToResponse(WarrantyClaim claim) {
        WarrantyClaimResponse res = modelMapper.map(claim, WarrantyClaimResponse.class);
        res.setStatus(claim.getStatus().name());

        List<WarrantyClaimProgress> progressList =
                progressRepository.findByWarrantyClaim_ClaimIdOrderByTimestampAsc(claim.getClaimId());

        if (!progressList.isEmpty()) {
            List<String> timeline = new ArrayList<>();

            for (int i = 0; i < progressList.size(); i++) {
                WarrantyClaimProgress current = progressList.get(i);
                timeline.add(current.getStatus() + " : " + current.getTimestamp());

                if (i > 0) {
                    WarrantyClaimProgress prev = progressList.get(i - 1);
                    Duration diff = Duration.between(prev.getTimestamp(), current.getTimestamp());

                    long seconds = diff.getSeconds();
                    long hours = seconds / 3600;
                    long minutes = (seconds % 3600) / 60;
                    long remainingSeconds = seconds % 60;

                    String timeDiff;
                    if (hours > 0) timeDiff = hours + "h";
                    else if (minutes > 0) timeDiff = minutes + "m";
                    else timeDiff = remainingSeconds + "s";

                    timeline.add(prev.getStatus() + " -> " + current.getStatus() + " : " + timeDiff);
                }
            }

            res.setTimeline(timeline);
        }


        return res;
    }
}
