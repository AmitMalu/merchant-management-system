package com.project2.ism.Service;


import com.project2.ism.DTO.*;
import com.project2.ism.DTO.ReportDTO.*;
import com.project2.ism.DTO.Vendor.VendorStatsDTO;
import com.project2.ism.Repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StatsService {

    private final FranchiseRepository franchiseRepository;
    private final MerchantRepository merchantRepository;
    private final OutwardTransactionRepository outwardTransactionRepository;
    private final ProductSerialsRepository productSerialsRepository;
    private final ProductRepository productRepository;
    private final FranchiseService franchiseService;
    private final MerchantService merchantService;

    private final VendorRepository vendorRepository;

    private final VendorRatesRepository vendorRatesRepository;

    private final InwardTransactionRepository inwardTransactionRepository;
    private final FranchiseWalletRepository franchiseWalletRepository;
    private final MerchantWalletRepository merchantWalletRepository;
    private final VendorTransactionsRepository vendorTransactionsRepository;



    public StatsService(FranchiseRepository franchiseRepository, MerchantRepository merchantRepository, OutwardTransactionRepository outwardTransactionRepository, ProductSerialsRepository productSerialsRepository, ProductRepository productRepository, FranchiseService franchiseService, MerchantService merchantService, VendorRepository vendorRepository, VendorRatesRepository vendorRatesRepository, InwardTransactionRepository inwardTransactionRepository, FranchiseWalletRepository franchiseWalletRepository, MerchantWalletRepository merchantWalletRepository, VendorTransactionsRepository vendorTransactionsRepository) {
        this.franchiseRepository = franchiseRepository;
        this.merchantRepository = merchantRepository;
        this.outwardTransactionRepository = outwardTransactionRepository;
        this.productSerialsRepository = productSerialsRepository;
        this.productRepository = productRepository;
        this.franchiseService = franchiseService;
        this.merchantService = merchantService;
        this.vendorRepository = vendorRepository;
        this.vendorRatesRepository = vendorRatesRepository;
        this.inwardTransactionRepository = inwardTransactionRepository;
        this.franchiseWalletRepository = franchiseWalletRepository;
        this.merchantWalletRepository = merchantWalletRepository;
        this.vendorTransactionsRepository = vendorTransactionsRepository;
    }

    public FranchiseStatsDTO getFranchiseStats(Long franchiseId) {
        Long merchantCount = merchantRepository.countByFranchiseId(franchiseId);
        Long outwardCount = outwardTransactionRepository.countByFranchiseId(franchiseId);
        BigDecimal wallet = franchiseWalletRepository.findAvailableBalanceById(franchiseId)
                .orElse(BigDecimal.ZERO);

        // ✅ fetch product-level stats
        List<FranchiseProductSummaryDTO> productSummary = franchiseService.getProductsOfFranchise(franchiseId);

        return new FranchiseStatsDTO(franchiseId, merchantCount, outwardCount, wallet, productSummary);
    }

    public MerchantStatsDTO getMerchantStats(Long merchantId) {
        Long outwardCount = outwardTransactionRepository.countByMerchantId(merchantId);
        Long allocatedProducts = productSerialsRepository.countByMerchantId(merchantId);
        BigDecimal wallet = merchantWalletRepository.findAvailableBalanceById(merchantId)
                .orElse(BigDecimal.ZERO);

        // ✅ fetch product-level stats
        List<MerchantProductSummaryDTO> productSummary = merchantService.getProductsOfMerchant(merchantId);

        return new MerchantStatsDTO(merchantId, outwardCount,  allocatedProducts,productSummary);
    }

    public InventoryTransactionStatsDTO getTransactionStats() {
        InventoryTransactionStatsDTO dto = new InventoryTransactionStatsDTO();

        // basic counts
        dto.totalInwardTransactions = inwardTransactionRepository.count();
        dto.totalOutwardTransactions = outwardTransactionRepository.count();
        //dto.totalReturnTransactions = returnRepo.count();
        dto.totalProductSerials = productSerialsRepository.count();

        // inward grouped by vendor
        dto.inwardByVendor = inwardTransactionRepository.countGroupByVendor();

        // outward grouped by customer type
        dto.outwardByCustomer = outwardTransactionRepository.countByCustomerType();

        // return reasons distribution
        //dto.returnReasons = returnRepo.countGroupByReason();

        // serial number status
        dto.productSerialStatus = productSerialsRepository.countByStatus();

        return dto;
    }
    public VendorStatsDTO getVendorStats() {
        VendorStatsDTO dto = new VendorStatsDTO();

        // Vendors
        dto.totalVendors = vendorRepository.count();
        dto.activeVendors = vendorRepository.countByStatus(true);
        dto.inactiveVendors = vendorRepository.countByStatus(false);

        // Vendor Rates
        dto.totalVendorRates = vendorRatesRepository.count();
        LocalDate today = LocalDate.now();
        dto.activeVendorRates = vendorRatesRepository.countByEffectiveDateBeforeAndExpiryDateAfter(today, today);

        // Total Monthly Rent
        dto.totalMonthlyRent = vendorRatesRepository.sumActiveMonthlyRent(today, today);

        // Card Type Distribution
        dto.cardTypeDistribution = vendorRatesRepository.countGroupByCardType();

        return dto;
    }

    public VendorReportsDTO getVendorReports() {
        VendorReportsDTO dto = new VendorReportsDTO();

        List<VendorDetailDTO> vendorDetails = vendorRepository.findAll().stream()
                .map(vendor -> {
                    VendorDetailDTO vdto = new VendorDetailDTO();
                    vdto.setVendorId(vendor.getId());
                    vdto.setVendorName(vendor.getName());
                    vdto.setAgreementStartDate(vendor.getAgreementStartDate());
                    vdto.setAgreementEndDate(vendor.getAgreementEndDate());
                    vdto.setCreditPeriodDays(vendor.getCreditPeriodDays());

                    // total products under this vendor
                    Long totalProducts = productRepository.countByVendorId(vendor.getId());

                    // total devices = sum of devices for all products under this vendor
                    Long totalDevices = productRepository.findByVendorId(vendor.getId()).stream()
                            .mapToLong(p -> productSerialsRepository.countByProductId(p.getId()))
                            .sum();

                    vdto.setTotalProducts(totalProducts);
                    vdto.setTotalDevices(totalDevices);

                    return vdto;
                })
                .toList();

        dto.setVendors(vendorDetails);
        return dto;
    }

    public List<FranchiseReportsDTO> getFranchiseReports() {
        return franchiseRepository.getFranchiseReports();
    }

    public List<MerchantReportDTO> getMerchantReports(){
        return merchantRepository.getMerchantReports();
    }

    public List<SettledUnsettledReportDto> getSettledUnsettled(
            Boolean settled,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String dateType,
            Long merchantId
    ) {
        return getSettledUnsettled(settled, fromDate, toDate, dateType, merchantId, null, null);
    }

    /** The choices of the report's Acquiring Bank and Machine Model filters. */
    public java.util.Map<String, List<String>> getSettledUnsettledFilterOptions() {
        java.util.Map<String, List<String>> options = new java.util.LinkedHashMap<>();
        options.put("acquiringBanks", vendorTransactionsRepository.findDistinctAcquiringBanks());
        options.put("models", productSerialsRepository.findDistinctModels());
        return options;
    }

    /** Same, narrowed to one acquiring bank and / or one machine model (null or blank = any). */
    public List<SettledUnsettledReportDto> getSettledUnsettled(
            Boolean settled,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String dateType,
            Long merchantId,
            String acquiringBank,
            String model
    ) {

        String normalizedDateType =
                dateType == null || dateType.isBlank()
                        ? "TRANSACTION_DATE"
                        : dateType.trim().toUpperCase();

        if (!normalizedDateType.equals("TRANSACTION_DATE")
                && !normalizedDateType.equals("SETTLEMENT_DATE")) {

            throw new IllegalArgumentException(
                    "Invalid dateType. Allowed values are TRANSACTION_DATE and SETTLEMENT_DATE"
            );
        }

        List<SettledUnsettledReportDto> rows = vendorTransactionsRepository.getSettledUnsettledReports(
                settled,
                fromDate,
                toDate,
                normalizedDateType,
                merchantId,
                blankToNull(acquiringBank),
                blankToNull(model)
        );

        addMachineModels(rows);
        return rows;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim()) ? null : value.trim();
    }

    /** The model of the machine behind each row, found in inventory by MID and TID. */
    private void addMachineModels(List<SettledUnsettledReportDto> rows) {
        java.util.Set<String> mids = new java.util.HashSet<>();
        for (SettledUnsettledReportDto r : rows) {
            if (r.getMid() != null && !r.getMid().isBlank()) {
                mids.add(r.getMid());
            }
        }
        if (mids.isEmpty()) {
            return;
        }
        java.util.Map<String, String> modelByMidAndTid = new java.util.HashMap<>();
        for (Object[] m : productSerialsRepository.findModelsByMids(mids)) {
            modelByMidAndTid.putIfAbsent(m[0] + "|" + m[1], (String) m[2]);
        }
        for (SettledUnsettledReportDto r : rows) {
            r.setModel(modelByMidAndTid.get(r.getMid() + "|" + r.getTid()));
        }
    }

}
