package com.project2.ism.Service;

import com.project2.ism.DTO.NotificationCallbackDtos.BulkCreateRequest;
import com.project2.ism.DTO.NotificationCallbackDtos.CreateRequest;
import com.project2.ism.DTO.NotificationCallbackDtos.UpdateRequest;
import com.project2.ism.DTO.NotificationCallbackDtos.View;
import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationCallback;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseOrMerchantNotificationCallbackRepository;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.project2.ism.DTO.NotificationCallbackDtos.DIRECT_MERCHANT;
import static com.project2.ism.DTO.NotificationCallbackDtos.FRANCHISE;
import static com.project2.ism.DTO.NotificationCallbackDtos.FRANCHISE_MERCHANT;

/**
 * Admin > Other > Notification URL: add, change, list and delete the URL a franchise or merchant
 * is notified on (table franchise_or_merchant_notification_callback, unchanged).
 *
 * How a row is stored, so the existing lookups (and Razorpay / Mosambee) keep working:
 *   - franchise URL           : only franchise_id is set
 *   - merchant URL (direct or franchise merchant): only merchant_id is set
 * and there is at most ONE row per franchise and ONE per merchant (those lookups return a single
 * row, so a duplicate would break them).
 */
@Service
public class NotificationCallbackService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCallbackService.class);

    /** The column is a plain varchar(255). */
    static final int MAX_URL_LENGTH = 255;

    /** Most merchants that can be given the same URL in one go. */
    static final int MAX_BULK = 200;

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private final FranchiseOrMerchantNotificationCallbackRepository repository;
    private final FranchiseRepository franchiseRepository;
    private final MerchantRepository merchantRepository;

    public NotificationCallbackService(FranchiseOrMerchantNotificationCallbackRepository repository,
                                       FranchiseRepository franchiseRepository,
                                       MerchantRepository merchantRepository) {
        this.repository = repository;
        this.franchiseRepository = franchiseRepository;
        this.merchantRepository = merchantRepository;
    }

    // =====================================================================
    // Read
    // =====================================================================

    /**
     * All URLs, newest change first. Optional filters: type, franchise (the franchise itself or
     * its merchants) and a search text (URL or names).
     */
    @Transactional(readOnly = true)
    public List<View> list(String type, Long franchiseId, String search) {
        String wantedType = type == null || type.isBlank() || "ALL".equalsIgnoreCase(type.trim())
                ? null : type.trim().toUpperCase(Locale.ROOT);
        String text = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);

        return repository.findAllWithOwners().stream()
                .map(this::toView)
                .filter(v -> wantedType == null || wantedType.equals(v.type()))
                .filter(v -> franchiseId == null || franchiseId.equals(v.franchiseId()))
                .filter(v -> text == null
                        || contains(v.callback(), text)
                        || contains(v.merchantName(), text)
                        || contains(v.franchiseName(), text))
                .toList();
    }

    // =====================================================================
    // Create
    // =====================================================================

    @Transactional
    public View create(CreateRequest request, String adminEmail) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        String url = validateCallbackUrl(request.callback());
        String type = request.type() == null ? "" : request.type().trim().toUpperCase(Locale.ROOT);

        FranchiseOrMerchantNotificationCallback row = new FranchiseOrMerchantNotificationCallback();
        row.setCallback(url);

        switch (type) {
            case FRANCHISE -> {
                if (request.franchiseId() == null) {
                    throw new IllegalArgumentException("Please select a franchise");
                }
                if (request.merchantId() != null) {
                    throw new IllegalArgumentException("A franchise URL cannot also have a merchant");
                }
                Franchise franchise = franchiseRepository.findById(request.franchiseId())
                        .orElseThrow(() -> new IllegalArgumentException("Franchise not found"));
                if (repository.existsByFranchise_Id(franchise.getId())) {
                    throw conflict("This franchise already has a notification URL. Edit or delete it instead.");
                }
                row.setFranchise(franchise);
            }
            case FRANCHISE_MERCHANT, DIRECT_MERCHANT -> {
                if (request.merchantId() == null) {
                    throw new IllegalArgumentException("Please select a merchant");
                }
                Merchant merchant = merchantRepository.findById(request.merchantId())
                        .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));

                Problem problem = merchantProblem(type, request.franchiseId(), merchant);
                if (problem != null) {
                    if (problem.duplicate()) {
                        throw conflict(problem.message());
                    }
                    throw new IllegalArgumentException(problem.message());
                }
                // only the merchant is stored (the franchise is implied by the merchant), so the
                // existing franchise lookup never finds this row
                row.setMerchant(merchant);
            }
            default -> throw new IllegalArgumentException(
                    "Type must be FRANCHISE, FRANCHISE_MERCHANT or DIRECT_MERCHANT");
        }

        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        FranchiseOrMerchantNotificationCallback saved = repository.save(row);

        log.info("Notification URL created | id={} | type={} | franchiseId={} | merchantId={} | url={} | by={}",
                saved.getId(), type, request.franchiseId(), request.merchantId(), url, adminEmail);
        return toView(saved);
    }

    /**
     * The same URL for several merchants (for example 3 of a franchise's 10) in one go. Each
     * merchant gets its own row, exactly as if added one by one. All or nothing: every merchant
     * is checked first, and if any fails (already has a URL, wrong franchise, ...) nothing is
     * saved and the message says which merchants are the problem.
     */
    @Transactional
    public List<View> createMany(BulkCreateRequest request, String adminEmail) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        String url = validateCallbackUrl(request.callback());
        String type = request.type() == null ? "" : request.type().trim().toUpperCase(Locale.ROOT);
        if (!FRANCHISE_MERCHANT.equals(type) && !DIRECT_MERCHANT.equals(type)) {
            throw new IllegalArgumentException("Type must be FRANCHISE_MERCHANT or DIRECT_MERCHANT");
        }

        List<Long> ids = request.merchantIds() == null ? List.of()
                : request.merchantIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one merchant");
        }
        if (ids.size() > MAX_BULK) {
            throw new IllegalArgumentException("You can add at most " + MAX_BULK + " merchants at a time");
        }

        List<String> problems = new ArrayList<>();
        boolean onlyDuplicates = true;
        List<Merchant> accepted = new ArrayList<>();

        for (Long id : ids) {
            Merchant merchant = merchantRepository.findById(id).orElse(null);
            if (merchant == null) {
                problems.add("Merchant #" + id + ": not found");
                onlyDuplicates = false;
                continue;
            }
            Problem problem = merchantProblem(type, request.franchiseId(), merchant);
            if (problem == null) {
                accepted.add(merchant);
            } else {
                problems.add(merchant.getBusinessName() + ": " + problem.message());
                onlyDuplicates &= problem.duplicate();
            }
        }

        if (!problems.isEmpty()) {
            String message = "Nothing was saved. " + String.join("; ", problems);
            if (onlyDuplicates) {
                throw conflict(message);
            }
            throw new IllegalArgumentException(message);
        }

        LocalDateTime now = LocalDateTime.now();
        List<FranchiseOrMerchantNotificationCallback> rows = new ArrayList<>();
        for (Merchant merchant : accepted) {
            FranchiseOrMerchantNotificationCallback row = new FranchiseOrMerchantNotificationCallback();
            row.setCallback(url);
            row.setMerchant(merchant);   // only the merchant, as for a single add
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            rows.add(row);
        }
        List<View> views = repository.saveAll(rows).stream().map(this::toView).toList();

        log.info("Notification URL created for {} merchants | type={} | merchantIds={} | url={} | by={}",
                views.size(), type, ids, url, adminEmail);
        return views;
    }

    // =====================================================================
    // Update / delete
    // =====================================================================

    @Transactional
    public View update(Long id, UpdateRequest request, String adminEmail) {
        FranchiseOrMerchantNotificationCallback row = find(id);
        String url = validateCallbackUrl(request == null ? null : request.callback());

        String previous = row.getCallback();
        row.setCallback(url);
        row.setUpdatedAt(LocalDateTime.now());
        FranchiseOrMerchantNotificationCallback saved = repository.save(row);

        log.info("Notification URL updated | id={} | from={} | to={} | by={}", id, previous, url, adminEmail);
        return toView(saved);
    }

    @Transactional
    public void delete(Long id, String adminEmail) {
        FranchiseOrMerchantNotificationCallback row = find(id);
        repository.delete(row);
        log.info("Notification URL deleted | id={} | url={} | by={}", id, row.getCallback(), adminEmail);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private FranchiseOrMerchantNotificationCallback find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification URL not found"));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** Why a merchant cannot get a URL of this type; duplicate = it already has one. */
    private record Problem(String message, boolean duplicate) {
    }

    /** The checks a merchant must pass for a FRANCHISE_MERCHANT / DIRECT_MERCHANT URL, or null when it passes. */
    private Problem merchantProblem(String type, Long franchiseId, Merchant merchant) {
        Franchise owner = merchant.getFranchise();

        if (FRANCHISE_MERCHANT.equals(type)) {
            if (owner == null) {
                return new Problem("This merchant does not belong to a franchise. Choose Direct Merchant instead.", false);
            }
            if (franchiseId != null && !franchiseId.equals(owner.getId())) {
                return new Problem("This merchant does not belong to the selected franchise", false);
            }
        } else if (owner != null) {
            return new Problem("This merchant belongs to a franchise. Choose Franchise Merchant instead.", false);
        }
        if (repository.existsByMerchant_Id(merchant.getId())) {
            return new Problem("This merchant already has a notification URL. Edit or delete it instead.", true);
        }
        return null;
    }

    /** What kind of account the row is for, and a warning for old rows that do not fit the rules. */
    View toView(FranchiseOrMerchantNotificationCallback c) {
        Merchant merchant = c.getMerchant();
        Franchise rowFranchise = c.getFranchise();

        String type;
        Franchise franchise;
        String warning = null;

        if (merchant != null) {
            Franchise owner = merchant.getFranchise();
            type = owner != null ? FRANCHISE_MERCHANT : DIRECT_MERCHANT;
            franchise = owner;
            if (rowFranchise != null) {
                warning = "This old record has both a franchise and a merchant. It may be picked up by the "
                        + "franchise lookup too. Delete it and add it again.";
            }
        } else if (rowFranchise != null) {
            type = FRANCHISE;
            franchise = rowFranchise;
        } else {
            type = "UNKNOWN";
            franchise = null;
            warning = "This record is not linked to any franchise or merchant, so it is never used.";
        }

        return new View(
                c.getId(),
                type,
                franchise != null ? franchise.getId() : null,
                franchise != null ? franchise.getFranchiseName() : null,
                merchant != null ? merchant.getId() : null,
                merchant != null ? merchant.getBusinessName() : null,
                c.getCallback(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                warning);
    }

    private static boolean contains(String value, String lowerText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerText);
    }

    /**
     * The URL must be a normal http(s) address that fits the column. Addresses that only make
     * sense inside our own network (localhost, private and link-local IPs such as the cloud
     * metadata address) are refused, because this server will call the URL.
     *
     * @return the trimmed URL
     */
    static String validateCallbackUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Notification URL is required");
        }
        String url = raw.trim();

        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Notification URL must be " + MAX_URL_LENGTH + " characters or less");
        }
        if (url.chars().anyMatch(ch -> Character.isWhitespace(ch) || Character.isISOControl(ch))) {
            throw new IllegalArgumentException("Notification URL cannot contain spaces");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Notification URL is not a valid URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Notification URL must start with http:// or https://");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Notification URL must have a host name, for example https://example.com/notify");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Do not put a user name or password in the Notification URL");
        }
        if (isInternalHost(host)) {
            throw new IllegalArgumentException("Notification URL must be a public address (not localhost or a private IP)");
        }
        return url;
    }

    /** localhost-style names, and IP addresses that are loopback / private / link-local. No DNS lookups. */
    private static boolean isInternalHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) {
            return true;
        }
        boolean ipv4Literal = IPV4.matcher(h).matches();
        boolean ipv6Literal = h.startsWith("[") && h.endsWith("]");
        if (!ipv4Literal && !ipv6Literal) {
            return false;
        }
        if (ipv4Literal) {
            for (String octet : h.split("\\.")) {
                if (Integer.parseInt(octet) > 255) {
                    return true; // not a real address; refuse instead of letting it be looked up as a name
                }
            }
        }
        try {
            InetAddress address = InetAddress.getByName(ipv6Literal ? h.substring(1, h.length() - 1) : h);
            return address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress();
        } catch (Exception e) {
            return true; // looks like an address but is not one: refuse
        }
    }
}
