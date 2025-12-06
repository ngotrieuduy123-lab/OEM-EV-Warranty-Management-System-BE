package com.mega.warrantymanagementsystem.service.v2;

import com.mega.warrantymanagementsystem.entity.Account;
import com.mega.warrantymanagementsystem.entity.EvmAssignmentState;
import com.mega.warrantymanagementsystem.entity.WarrantyClaim;
import com.mega.warrantymanagementsystem.entity.WarrantyClaimProgress;
import com.mega.warrantymanagementsystem.entity.entity.RoleName;
import com.mega.warrantymanagementsystem.entity.entity.WarrantyClaimStatus;
import com.mega.warrantymanagementsystem.exception.exception.ResourceNotFoundException;
import com.mega.warrantymanagementsystem.model.response.WarrantyClaimResponse;
import com.mega.warrantymanagementsystem.repository.*;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvmClaimAutoAssignService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private WarrantyClaimRepository warrantyClaimRepository;

    @Autowired
    private EvmAssignmentStateRepository stateRepository;

    @Autowired
    private WarrantyClaimProgressRepository progressRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Transactional
    public List<WarrantyClaimResponse> autoAssignDecideClaimsToEvm() {

        List<Account> evmStaffs = accountRepository
                .findByRole_RoleNameAndEnabledTrue(RoleName.EVM_STAFF);

        evmStaffs.sort(Comparator.comparing(Account::getAccountId));

        if (evmStaffs.isEmpty()) {
            throw new ResourceNotFoundException("Không có EVM Staff nào đang hoạt động.");
        }

        // LẤY CLAIMS BẰNG LOCK
        List<WarrantyClaim> pendingClaims = warrantyClaimRepository.lockAllPendingDecideClaims();

        if (pendingClaims.isEmpty()) {
            throw new ResourceNotFoundException("Không có WarrantyClaim nào ở trạng thái DECIDE để gán.");
        }

        // LẤY STATE
        EvmAssignmentState state = stateRepository.findById("EVM_ASSIGNMENT_TRACKER")
                .orElseGet(() -> {
                    EvmAssignmentState s = new EvmAssignmentState();
                    s.setLastIndex(-1);
                    return s;
                });

        int index = state.getLastIndex();

        List<WarrantyClaimResponse> result = new ArrayList<>();

        for (WarrantyClaim claim : pendingClaims) {

            index = (index + 1) % evmStaffs.size();
            Account selectedEvm = evmStaffs.get(index);

            // SET EVM
            claim.setEvm(selectedEvm);

            // LƯU CLAIM
            warrantyClaimRepository.save(claim);

            // LOG PROGRESS
            logProgress(claim, WarrantyClaimStatus.DECIDE);

            WarrantyClaimResponse response = modelMapper.map(claim, WarrantyClaimResponse.class);
            response.setStatus(claim.getStatus().name());
            result.add(response);
        }

        state.setLastIndex(index);
        stateRepository.save(state);

        return result;
    }

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

}
