package com.media.bus.reservation.contract.service;

import com.media.bus.reservation.contract.dto.request.CreateContractRequest;
import com.media.bus.reservation.contract.dto.response.MemberInfo;
import com.media.bus.reservation.contract.entity.Contract;
import com.media.bus.reservation.contract.entity.ContractDetail;
import com.media.bus.reservation.contract.repository.BaseEntityRepository;
import com.media.bus.reservation.contract.repository.ContractDetailRepository;
import com.media.bus.reservation.reservation.dto.response.StopInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 계약 도메인의 DB 저장을 담당하는 서비스.
/// 설계 의도:
/// - S2S 외부 호출은 ContractFacade에서 처리하고, 이 클래스는 트랜잭션 내 DB 작업만 담당합니다.
/// - Contract 저장 후 ContractDetail을 저장하여 FK 참조 무결성을 보장합니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final BaseEntityRepository contractRepository;
    private final ContractDetailRepository contractDetailRepository;

    /// Contract와 ContractDetail을 함께 저장합니다.
    /// 단일 트랜잭션 내에서 실행되어 부분 저장을 방지합니다.
    ///
    /// @param memberInfo IAM DB에서 재검증된 회원 정보
    /// @param stopInfo   stop 서비스에서 확인된 정류소 정보
    /// @param request    계약 생성 요청 DTO
    /// @return 저장된 Contract 엔티티
    @Transactional
    public Contract createContract(MemberInfo memberInfo, StopInfo stopInfo, CreateContractRequest request) {

        // Contract 저장
        Contract contract = Contract.create(memberInfo, stopInfo, request);
        contractRepository.save(contract);

        // ContractDetail 저장
        ContractDetail detail = ContractDetail.create(contract, request);
        contractDetailRepository.save(detail);

        log.debug("[ContractService] 계약 저장 완료: contractId={}, memberId={}, stopId={}",
                contract.getId(), memberInfo.id(), stopInfo.id());

        return contract;
    }
}