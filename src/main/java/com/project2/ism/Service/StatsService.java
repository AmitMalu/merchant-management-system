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

    public List<SettledUnsettledReportDto> getSettledUnsettled(Boolean settled, LocalDateTime fromDate, LocalDateTime toDate, String dateType) {
        return vendorTransactionsRepository.getSettledUnsettledReports(settled, fromDate, toDate, dateType);
    }

}
