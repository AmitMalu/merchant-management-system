package com.project2.ism.Service;

import com.project2.ism.DTO.PrefundWalletRequestDTO;
import com.project2.ism.Enum.RequestStatus;
import com.project2.ism.Enum.RequestedType;
import com.project2.ism.Helper.PrefundWalletRequestSpecification;
import com.project2.ism.Model.*;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PrefundWalletRequestService {

    private static final String UPLOAD_DIR = "uploads/prefund";

    private final PrefundWalletRequestRepository prefundWalletRequestRepository;
    private final FileStorageService fileStorageService;
    private final WalletAdjustmentService walletAdjustmentService;

    public PrefundWalletRequestService(PrefundWalletRequestRepository prefundWalletRequestRepository, FileStorageService fileStorageService, WalletAdjustmentService walletAdjustmentService) {
        this.prefundWalletRequestRepository = prefundWalletRequestRepository;
        this.fileStorageService = fileStorageService;
        this.walletAdjustmentService = walletAdjustmentService;
    }

    public PrefundWalletRequest raisePrefundRequest(PrefundWalletRequestDTO dto) {

        String imagePath = null;

        if (dto.getDepositImage() != null && !dto.getDepositImage().isEmpty()) {
            imagePath = fileStorageService.store(dto.getDepositImage(), "Deposite Image");
        }

        PrefundWalletRequest request = new PrefundWalletRequest();

        request.setRequestedType(dto.getRequestedType());
        request.setRequestedById(dto.getRequestedById());

        request.setBankAccountName(dto.getBankAccountName());
        request.setBankHolderName(dto.getBankHolderName());
        request.setDepositAmount(dto.getDepositAmount());
        request.setPaymentMode(dto.getPaymentMode());
        request.setBankAccountNumber(dto.getBankAccountNumber());
        request.setTranxId(dto.getTranxId());
        request.setNarration(dto.getNarration());

        request.setDepositImage(imagePath);
        request.setDepositDate(dto.getDepositeDate());
        request.setDepositeTime(LocalTime.now());

        return prefundWalletRequestRepository.save(request);
    }

    public Page<PrefundWalletRequest> getRequests(
            RequestStatus status,
            RequestedType requestedType,
            LocalDate depositDate,
            int page,
            int size
    )
    {
        Pageable pageable = PageRequest.of(page, size);
        return prefundWalletRequestRepository.findAll(
                PrefundWalletRequestSpecification.filter(status, requestedType, depositDate),
                pageable
        );
    }

    public PrefundWalletRequest getById(Long id) {
        return prefundWalletRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));
    }

    @Transactional
    public void processPrefundAction(Long id, RequestStatus action) {

        PrefundWalletRequest request = prefundWalletRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (request.getRequestStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        if (action == RequestStatus.REJECTED) {
            request.setRequestStatus(RequestStatus.REJECTED);
            request.setApproveRejectDate(LocalDate.now());
            prefundWalletRequestRepository.save(request);
            return;
        }

        BigDecimal amount = request.getDepositAmount();

        String remark = "Prefund request approved. Request ID: " + request.getId();

        if (request.getRequestedType() == RequestedType.MERCHANT) {

            walletAdjustmentService.adjustMerchantWallet(
                    request.getRequestedById(),
                    "CREDIT",
                    amount,
                    remark
            );

        } else if (request.getRequestedType() == RequestedType.FRANCHISE) {

            walletAdjustmentService.adjustFranchiseWallet(
                    request.getRequestedById(),
                    "CREDIT",
                    amount,
                    remark
            );
        }

        request.setRequestStatus(RequestStatus.APPROVED);
        request.setApproveRejectDate(LocalDate.now());
        prefundWalletRequestRepository.save(request);
    }

}
