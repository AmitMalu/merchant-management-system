package com.project2.ism.Helper;

import com.project2.ism.Enum.RequestStatus;
import com.project2.ism.Enum.RequestedType;
import com.project2.ism.Model.PrefundWalletRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PrefundWalletRequestSpecification {

    public static Specification<PrefundWalletRequest> filter(
            RequestStatus status,
            RequestedType requestedType,
            LocalDate depositDate
    ) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("requestStatus"), status));
            }

            if (requestedType != null) {
                predicates.add(cb.equal(root.get("requestedType"), requestedType));
            }

            if (depositDate != null) {
                predicates.add(cb.equal(root.get("depositDate"), depositDate));
            }

            query.orderBy(cb.desc(root.get("id")));

            // 🔥 IMPORTANT FIX
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}
