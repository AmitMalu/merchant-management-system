package com.project2.ism.Controller;

import com.project2.ism.DTO.PrefundWalletRequestDTO;
import com.project2.ism.Enum.RequestStatus;
import com.project2.ism.Enum.RequestedType;
import com.project2.ism.Model.PrefundWalletRequest;
import com.project2.ism.Service.PrefundWalletRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/prefund")
public class PrefundWalletRequestController {

    private final PrefundWalletRequestService prefundWalletRequestService;

    public PrefundWalletRequestController(PrefundWalletRequestService prefundWalletRequestService) {
        this.prefundWalletRequestService = prefundWalletRequestService;
    }

    @PostMapping(
            value = "/request",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> raisePrefundRequest(
            @ModelAttribute @Valid PrefundWalletRequestDTO dto
    ) {

        PrefundWalletRequest response =
                prefundWalletRequestService.raisePrefundRequest(dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public Page<PrefundWalletRequest> getRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestedType,
            @RequestParam(required = false) String depositDate,
            @RequestParam int page,
            @RequestParam int size
    ) {

        RequestStatus requestStatus = null;
        RequestedType reqType = null;
        LocalDate parsedDate = null;

        if (status != null && !status.equalsIgnoreCase("ALL")) {
            requestStatus = RequestStatus.valueOf(status.toUpperCase());
        }

        if (requestedType != null && !requestedType.equalsIgnoreCase("ALL")) {
            reqType = RequestedType.valueOf(requestedType.toUpperCase());
        }

        if (depositDate != null && !depositDate.isBlank()) {
            parsedDate = LocalDate.parse(depositDate);
        }

        return prefundWalletRequestService.getRequests(
                requestStatus,
                reqType,
                parsedDate,
                page,
                size
        );
    }

    // Get by id
    @GetMapping("/{id}")
    public PrefundWalletRequest getById(@PathVariable Long id) {
        return prefundWalletRequestService.getById(id);
    }

    @PutMapping("/{id}/action")
    public ResponseEntity<String> updatePrefundStatus(
            @PathVariable Long id,
            @RequestParam RequestStatus action
    ) {
        prefundWalletRequestService.processPrefundAction(id, action);
        return ResponseEntity.ok("Prefund request " + action + " successfully");
    }

}
