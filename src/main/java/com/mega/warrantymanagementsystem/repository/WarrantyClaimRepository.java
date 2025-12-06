package com.mega.warrantymanagementsystem.repository;

import com.mega.warrantymanagementsystem.entity.WarrantyClaim;
import com.mega.warrantymanagementsystem.entity.entity.WarrantyClaimStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, String> {

    // Tìm theo từng thuộc tính riêng
    List<WarrantyClaim> findByServiceCenterStaff_AccountId(String accountId);

    List<WarrantyClaim> findByServiceCenterTechnician_AccountId(String accountId);

    List<WarrantyClaim> findByEvm_AccountId(String accountId);

    List<WarrantyClaim> findByClaimDate(LocalDate claimDate);

    List<WarrantyClaim> findByStatus(WarrantyClaimStatus status);

    WarrantyClaim findByClaimId(String claimId);

    // Tìm theo VIN (Vehicle)
    List<WarrantyClaim> findByVehicle_Vin(String vin);

    @Query(value = """
        SELECT MONTH(claim_date) AS month, COUNT(*) AS total
        FROM warranty_claim
        WHERE YEAR(claim_date) = :year
        GROUP BY MONTH(claim_date)
        ORDER BY month
        """, nativeQuery = true)
    List<Object[]> countClaimsByMonth(@Param("year") int year);


    @Query(value = """
        SELECT claim_date AS date, COUNT(*) AS total
        FROM warranty_claim
        WHERE YEAR(claim_date) = :year
          AND MONTH(claim_date) = :month
        GROUP BY claim_date
        ORDER BY claim_date
        """, nativeQuery = true)
    List<Object[]> countClaimsByDay(
            @Param("year") int year,
            @Param("month") int month
    );

    // Locked SELECT toàn bộ DECIDE claims chưa có EVM (FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM WarrantyClaim c
        WHERE c.status = com.mega.warrantymanagementsystem.entity.entity.WarrantyClaimStatus.DECIDE
          AND c.evm IS NULL
        ORDER BY c.claimId
        """)
    List<WarrantyClaim> lockAllPendingDecideClaims();


    // Lock 1 claim đơn lẻ nếu sau này cần xử lý batch nhỏ
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM WarrantyClaim c
        WHERE c.claimId = :claimId
        """)
    WarrantyClaim lockClaimById(@Param("claimId") String claimId);


}
