package com.project2.ism.Service;

import com.project2.ism.Model.Payment.PaymentMode;
import com.project2.ism.Repository.PaymentModeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentModeService {

    private final PaymentModeRepository paymentModeRepository;

    public PaymentModeService(PaymentModeRepository paymentModeRepository) {
        this.paymentModeRepository = paymentModeRepository;
    }

    @Transactional
    public PaymentMode createMode(PaymentMode mode) {

        if (paymentModeRepository.existsByCode(mode.getCode())) {
            throw new IllegalArgumentException("Mode already exists: " + mode.getCode());
        }

        return paymentModeRepository.save(mode);
    }

    @Transactional
    public PaymentMode updateMode(Long id, PaymentMode updated) {
        PaymentMode existing = paymentModeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mode not found"));

        existing.setCode(updated.getCode());
        existing.setDescription(updated.getDescription());
        existing.setStatus(updated.getStatus());

        return paymentModeRepository.save(existing);
    }

    @Transactional
    public void deleteMode(Long id) {
        paymentModeRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public PaymentMode getModeById(Long id) {
        return paymentModeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mode not found"));
    }

    @Transactional(readOnly = true)
    public List<PaymentMode> getAllModes() {
        return paymentModeRepository.findAll();
    }
}
