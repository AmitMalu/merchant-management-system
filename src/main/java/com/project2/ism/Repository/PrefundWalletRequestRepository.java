package com.project2.ism.Repository;

import com.project2.ism.Enum.RequestStatus;
import com.project2.ism.Enum.RequestedType;
import com.project2.ism.Model.PrefundWalletRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrefundWalletRequestRepository extends JpaRepository<PrefundWalletRequest, Long>, JpaSpecificationExecutor<PrefundWalletRequest> {

}
