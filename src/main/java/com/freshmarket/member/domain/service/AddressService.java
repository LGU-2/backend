package com.freshmarket.member.domain.service;

import com.freshmarket.member.domain.entity.Address;
import com.freshmarket.member.domain.repository.AddressRepository;
import com.freshmarket.member.dto.AddressRequest;
import com.freshmarket.member.exception.MemberErrorCode;
import com.freshmarket.member.exception.MemberException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// (2026-08-18 10:49) com.freshmarket.address.domain.service에서 이동 — domain-map.md 기준
// address는 member 도메인 소유 테이블이라 별도 최상위 도메인일 이유가 없었다. 로직 변경 없음.
// (2026-08-18 12:50) docs/api/member.md 기준 AddressErrorCode/AddressException을 없애고
// MemberErrorCode.ADDRESS_FORBIDDEN(MEMBER-003)으로 합쳤다.
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<Address> findMyAddresses(Long memberId) {
        return addressRepository.findByMemberIdOrderedByDefaultFirst(memberId);
    }

    @Transactional
    public Address create(Long memberId, AddressRequest request) {
        boolean isFirstAddress = addressRepository.countByMemberId(memberId) == 0;
        boolean shouldBeDefault = isFirstAddress || request.isDefault();

        if (shouldBeDefault) {
            addressRepository.clearDefaultForMember(memberId);
        }

        Address address = Address.register(
                memberId, request.recipient(), request.phone(), request.zipcode(),
                request.roadAddress(), request.detailAddress(), shouldBeDefault);

        return addressRepository.save(address);
    }

    @Transactional
    public Address update(Long memberId, Long addressId, AddressRequest request) {
        Address address = getOwned(memberId, addressId);

        address.update(request.recipient(), request.phone(), request.zipcode(),
                request.roadAddress(), request.detailAddress());

        if (request.isDefault() && !address.isDefault()) {
            addressRepository.clearDefaultForMember(memberId);
            address.markAsDefault();
        }

        return address;
    }

    @Transactional
    public void delete(Long memberId, Long addressId) {
        Address address = getOwned(memberId, addressId);
        boolean wasDefault = address.isDefault();

        addressRepository.delete(address);

        if (wasDefault) {
            // 방금 지운 게 기본 배송지였다면 남은 것 중 isDefault=true인 게 없다 — 정렬 1순위가
            // 무의미해지고 사실상 createdAt desc로만 고르는 것과 같다(최근 등록 순).
            addressRepository.findByMemberIdOrderedByDefaultFirst(memberId).stream()
                    .findFirst()
                    .ifPresent(Address::markAsDefault);
        }
    }

    private Address getOwned(Long memberId, Long addressId) {
        return addressRepository.findByIdAndMemberId(addressId, memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.ADDRESS_FORBIDDEN));
    }
}
