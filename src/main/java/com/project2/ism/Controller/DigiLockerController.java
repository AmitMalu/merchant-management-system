package com.project2.ism.Controller;


import com.project2.ism.DTO.DigiLockerDTO.DigilockerInitializeRequestDTO;
import com.project2.ism.Service.DigiLockerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/digi-locker")
public class DigiLockerController {


    private final DigiLockerService digilockerService;

    public DigiLockerController(DigiLockerService digilockerService) {
        this.digilockerService = digilockerService;
    }

    @PostMapping("/resend")
    public ResponseEntity<String> resend(

            @RequestBody

            DigilockerInitializeRequestDTO dto

    ){

        digilockerService.resend(dto);

        return ResponseEntity.ok(

                "Digilocker link sent successfully"

        );

    }

}
