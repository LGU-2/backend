package com.freshmarket.member.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freshmarket.member.domain.entity.Address;
import com.freshmarket.member.domain.repository.AddressRepository;
import com.freshmarket.member.dto.AddressRequest;
import com.freshmarket.member.exception.MemberErrorCode;
import com.freshmarket.member.exception.MemberException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// (2026-08-18 10:49) com.freshmarket.address.domain.service에서 이동 — 프로덕션 패키지가
// member.domain.service로 옮겨져 TestPlacementTest의 "프로덕션_패키지를_미러링한다" 규칙에 맞춰
// 테스트도 같이 옮겼다. 테스트 내용 자체는 변경 없음.
@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    private AddressService sut;

    @BeforeEach
    void setUp() {
        sut = new AddressService(addressRepository);
    }

    @Test
    void 내_배송지_목록을_리포지토리가_정렬한_그대로_돌려준다() {
        // (2026-08-18 18:40) 기본 배송지 우선 정렬은 AddressRepository의 @Query가 한다 —
        // 이 서비스 단위 테스트는 리포지토리가 돌려준 순서를 그대로 전달하는지만 본다. "기본
        // 배송지가 먼저 온다"는 실제 정렬 자체는 목(mock)으로 못 잡으므로 통합 테스트가 봐야 한다.
        Address address = newAddress(1L, false);
        when(addressRepository.findByMemberIdOrderedByDefaultFirst(1L)).thenReturn(List.of(address));

        List<Address> result = sut.findMyAddresses(1L);

        assertThat(result).containsExactly(address);
    }

    @Test
    void 첫_배송지는_기본으로_요청하지_않아도_기본_배송지가_된다() {
        when(addressRepository.countByMemberId(1L)).thenReturn(0L);
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddressRequest request = new AddressRequest("홍길동", "010-1234-5678", "12345", "서울시", null, false);
        Address result = sut.create(1L, request);

        assertThat(result.isDefault()).isTrue();
        verify(addressRepository).clearDefaultForMember(1L);
    }

    @Test
    void 두번째_배송지는_기본으로_요청하지_않으면_기본이_아니다() {
        when(addressRepository.countByMemberId(1L)).thenReturn(1L);
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddressRequest request = new AddressRequest("홍길동", "010-1234-5678", "12345", "서울시", null, false);
        Address result = sut.create(1L, request);

        assertThat(result.isDefault()).isFalse();
        verify(addressRepository, never()).clearDefaultForMember(1L);
    }

    @Test
    void 두번째_배송지도_기본으로_요청하면_기존_기본을_해제하고_새_배송지가_기본이_된다() {
        when(addressRepository.countByMemberId(1L)).thenReturn(1L);
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddressRequest request = new AddressRequest("홍길동", "010-1234-5678", "12345", "서울시", null, true);
        Address result = sut.create(1L, request);

        assertThat(result.isDefault()).isTrue();
        verify(addressRepository).clearDefaultForMember(1L);
    }

    @Test
    void 본인_소유가_아닌_배송지를_수정하려_하면_예외() {
        when(addressRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.empty());

        AddressRequest request = new AddressRequest("홍길동", "010-1234-5678", "12345", "서울시", null, false);

        assertThatThrownBy(() -> sut.update(1L, 1L, request))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.ADDRESS_FORBIDDEN);
    }

    @Test
    void 배송지_수정_시_입력값이_반영된다() {
        Address address = newAddress(1L, false);
        when(addressRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.of(address));

        AddressRequest request = new AddressRequest("새수령인", "010-0000-0000", "54321", "부산시", "101호", false);
        Address result = sut.update(1L, 1L, request);

        assertThat(result.getRecipient()).isEqualTo("새수령인");
        assertThat(result.getZipcode()).isEqualTo("54321");
        assertThat(result.getDetailAddress()).isEqualTo("101호");
    }

    @Test
    void 기본이_아니던_배송지를_기본으로_바꾸면_기존_기본을_해제한다() {
        Address address = newAddress(1L, false);
        when(addressRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.of(address));

        AddressRequest request = new AddressRequest("홍길동", "010-1234-5678", "12345", "서울시", null, true);
        Address result = sut.update(1L, 1L, request);

        assertThat(result.isDefault()).isTrue();
        verify(addressRepository).clearDefaultForMember(1L);
    }

    @Test
    void 이미_기본인_배송지를_다시_기본으로_요청하면_해제_쿼리를_또_보내지_않는다() {
        Address address = newAddress(1L, true);
        when(addressRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.of(address));

        AddressRequest request = new AddressRequest("홍길동", "010-1234-5678", "12345", "서울시", null, true);
        sut.update(1L, 1L, request);

        verify(addressRepository, never()).clearDefaultForMember(1L);
    }

    @Test
    void 본인_소유가_아닌_배송지를_삭제하려_하면_예외() {
        when(addressRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.delete(1L, 1L))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.ADDRESS_FORBIDDEN);
    }

    @Test
    void 기본_배송지를_삭제하면_남은_배송지_중_첫번째가_새_기본이_된다() {
        Address deleted = newAddress(1L, true);
        Address remaining = newAddress(1L, false);
        when(addressRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.of(deleted));
        when(addressRepository.findByMemberIdOrderedByDefaultFirst(1L)).thenReturn(List.of(remaining));

        sut.delete(1L, 1L);

        verify(addressRepository).delete(deleted);
        assertThat(remaining.isDefault()).isTrue();
    }

    @Test
    void 기본이_아닌_배송지를_삭제하면_다른_배송지의_기본_여부를_건드리지_않는다() {
        Address deleted = newAddress(1L, false);
        when(addressRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.of(deleted));

        sut.delete(1L, 1L);

        verify(addressRepository).delete(deleted);
        verify(addressRepository, never()).findByMemberIdOrderedByDefaultFirst(1L);
    }

    private Address newAddress(Long memberId, boolean isDefault) {
        return Address.register(memberId, "홍길동", "010-1234-5678", "12345", "서울시", null, isDefault);
    }
}
