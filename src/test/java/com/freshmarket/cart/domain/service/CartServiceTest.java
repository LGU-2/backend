package com.freshmarket.cart.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freshmarket.cart.domain.dto.CartItemCreateRequest;
import com.freshmarket.cart.domain.dto.CartItemUpdateRequest;
import com.freshmarket.cart.domain.dto.CartResponse;
import com.freshmarket.cart.domain.entity.Cart;
import com.freshmarket.cart.domain.entity.CartItem;
import com.freshmarket.cart.domain.exception.CartException;
import com.freshmarket.cart.domain.repository.CartItemRepository;
import com.freshmarket.cart.domain.repository.CartRepository;
import com.freshmarket.member.MemberRegisteredEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final ProductOptionInfo PURCHASABLE_OPTION =
            new ProductOptionInfo(11L, "감귤", "1kg", 12900, true);

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductApi productApi;

    private CartService sut;

    @BeforeEach
    void setUp() {
        sut = new CartService(cartRepository, cartItemRepository, productApi);
    }

    @Test
    void 신규_회원_이벤트를_받으면_빈_카트를_생성한다() {
        sut.createCartForNewMember(new MemberRegisteredEvent(1L));

        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void 내_카트와_항목_수량_합계를_조회한다() {
        Cart cart = cart(1L, 10L);
        CartItem first = item(10L, 11L, 2, 100L);
        CartItem second = item(10L, 12L, 3, 101L);
        when(cartRepository.findByMemberId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(first, second));
        when(productApi.findOption(11L)).thenReturn(Optional.of(PURCHASABLE_OPTION));
        when(productApi.findOption(12L)).thenReturn(Optional.of(
                new ProductOptionInfo(12L, "사과", "2kg", 15000, true)));

        CartResponse result = sut.getCart(1L);

        assertThat(result.cartId()).isEqualTo(10L);
        assertThat(result.totalQty()).isEqualTo(5);
        assertThat(result.items()).extracting("productName").containsExactly("감귤", "사과");
    }

    @Test
    void 같은_옵션을_다시_담으면_기존_수량에_더한다() {
        Cart cart = cart(1L, 10L);
        CartItem existing = item(10L, 11L, 2, 100L);
        when(cartRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(productApi.findOption(11L)).thenReturn(Optional.of(PURCHASABLE_OPTION));
        when(cartItemRepository.findByCartIdAndProductOptionId(10L, 11L)).thenReturn(Optional.of(existing));

        var result = sut.addItem(1L, new CartItemCreateRequest(11L, 3));

        assertThat(existing.getQty()).isEqualTo(5);
        assertThat(result.qty()).isEqualTo(5);
    }

    @Test
    void 새_옵션을_카트에_담는다() {
        Cart cart = cart(1L, 10L);
        when(cartRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(productApi.findOption(11L)).thenReturn(Optional.of(PURCHASABLE_OPTION));
        when(cartItemRepository.findByCartIdAndProductOptionId(10L, 11L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = sut.addItem(1L, new CartItemCreateRequest(11L, 2));

        assertThat(result.productOptionId()).isEqualTo(11L);
        assertThat(result.qty()).isEqualTo(2);
    }

    @Test
    void 구매할_수_없는_옵션은_담을_수_없다() {
        when(cartRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(cart(1L, 10L)));
        when(productApi.findOption(11L)).thenReturn(Optional.of(
                new ProductOptionInfo(11L, "감귤", "1kg", 12900, false)));

        assertThatThrownBy(() -> sut.addItem(1L, new CartItemCreateRequest(11L, 1)))
                .isInstanceOf(CartException.class);
    }

    @Test
    void 없는_상품_옵션은_담을_수_없다() {
        when(cartRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(cart(1L, 10L)));
        when(productApi.findOption(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.addItem(1L, new CartItemCreateRequest(999L, 1)))
                .isInstanceOf(CartException.class);
    }

    @Test
    void 내_카트의_항목만_수량을_바꾼다() {
        Cart cart = cart(1L, 10L);
        CartItem existing = item(10L, 11L, 2, 100L);
        when(cartRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(100L, 10L)).thenReturn(Optional.of(existing));
        when(productApi.findOption(11L)).thenReturn(Optional.of(PURCHASABLE_OPTION));

        var result = sut.updateItem(1L, 100L, new CartItemUpdateRequest(4));

        assertThat(existing.getQty()).isEqualTo(4);
        assertThat(result.qty()).isEqualTo(4);
    }

    @Test
    void 내_카트의_항목만_삭제한다() {
        Cart cart = cart(1L, 10L);
        CartItem existing = item(10L, 11L, 2, 100L);
        when(cartRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(100L, 10L)).thenReturn(Optional.of(existing));

        sut.deleteItem(1L, 100L);

        verify(cartItemRepository).delete(existing);
    }

    @Test
    void 카트가_없으면_조회할_수_없다() {
        when(cartRepository.findByMemberId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getCart(1L))
                .isInstanceOf(CartException.class);
    }

    @Test
    void 카트에_없는_항목은_수정할_수_없다() {
        when(cartRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(cart(1L, 10L)));
        when(cartItemRepository.findByIdAndCartId(100L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.updateItem(1L, 100L, new CartItemUpdateRequest(1)))
                .isInstanceOf(CartException.class);
    }

    private static Cart cart(Long memberId, Long id) {
        Cart cart = Cart.create(memberId);
        setId(cart, id);
        return cart;
    }

    private static CartItem item(Long cartId, Long optionId, int qty, Long id) {
        CartItem item = CartItem.add(cartId, optionId, qty);
        setId(item, id);
        return item;
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getSuperclass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
