package com.freshmarket.cart.domain.service;

import com.freshmarket.cart.domain.dto.CartItemCreateRequest;
import com.freshmarket.cart.domain.dto.CartItemResponse;
import com.freshmarket.cart.domain.dto.CartItemUpdateRequest;
import com.freshmarket.cart.domain.dto.CartResponse;
import com.freshmarket.cart.domain.entity.Cart;
import com.freshmarket.cart.domain.entity.CartItem;
import com.freshmarket.cart.domain.exception.CartErrorCode;
import com.freshmarket.cart.domain.exception.CartException;
import com.freshmarket.cart.domain.repository.CartItemRepository;
import com.freshmarket.cart.domain.repository.CartRepository;
import com.freshmarket.member.MemberRegisteredEvent;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.freshmarket.product.ProductApi;
import com.freshmarket.product.ProductOptionInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private static final int MAX_CART_ITEMS = 99;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductApi productApi;

    // 회원 생성 트랜잭션 안에서 동기로 실행된다. 실패는 예외로 전파되어 회원 생성도 함께 롤백된다.
    @EventListener
    @Transactional
    public void createCartForNewMember(MemberRegisteredEvent event) {
        cartRepository.save(Cart.create(event.memberId()));
    }

    public CartResponse getCart(Long memberId) {
        Cart cart = findCart(memberId);
        List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderByCreatedAtDesc(cart.getId());
        Map<Long, ProductOptionInfo> optionsById = productApi.findOptionInfos(cartItems.stream()
                        .map(CartItem::getProductOptionId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ProductOptionInfo::productOptionId, Function.identity()));
        List<CartItemResponse> items = cartItems.stream()
                .map(item -> CartItemResponse.from(item, findOptionInfo(optionsById, item.getProductOptionId())))
                .toList();
        return CartResponse.from(cart, items);
    }

    @Transactional
    public CartItemResponse addItem(Long memberId, CartItemCreateRequest request) {
        Cart cart = findCartForUpdate(memberId);
        ProductOptionInfo option = findOptionInfo(request.productOptionId());
        if (!option.purchasable()) {
            throw new CartException(CartErrorCode.PRODUCT_OPTION_NOT_PURCHASABLE);
        }

        CartItem item = cartItemRepository.findByCartIdAndProductOptionId(cart.getId(), request.productOptionId())
                .map(existing -> {
                    existing.increaseQty(request.qty());
                    return existing;
                })
                .orElseGet(() -> addNewItem(cart.getId(), request));
        return CartItemResponse.from(item, option);
    }

    @Transactional
    public CartItemResponse updateItem(Long memberId, Long cartItemId, CartItemUpdateRequest request) {
        Cart cart = findCartForUpdate(memberId);
        CartItem item = findItem(cart.getId(), cartItemId);
        item.changeQty(request.qty());
        return toResponse(item);
    }

    @Transactional
    public void deleteItem(Long memberId, Long cartItemId) {
        Cart cart = findCartForUpdate(memberId);
        cartItemRepository.delete(findItem(cart.getId(), cartItemId));
    }

    private Cart findCart(Long memberId) {
        return cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CartException(CartErrorCode.CART_NOT_FOUND));
    }

    private Cart findCartForUpdate(Long memberId) {
        return cartRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new CartException(CartErrorCode.CART_NOT_FOUND));
    }

    private CartItem findItem(Long cartId, Long cartItemId) {
        return cartItemRepository.findByIdAndCartId(cartItemId, cartId)
                .orElseThrow(() -> new CartException(CartErrorCode.CART_ITEM_NOT_FOUND));
    }

    private CartItem addNewItem(Long cartId, CartItemCreateRequest request) {
        if (cartItemRepository.countByCartId(cartId) >= MAX_CART_ITEMS) {
            throw new CartException(CartErrorCode.CART_ITEM_LIMIT_EXCEEDED);
        }
        return cartItemRepository.save(CartItem.add(cartId, request.productOptionId(), request.qty()));
    }

    private CartItemResponse toResponse(CartItem item) {
        return CartItemResponse.from(item, findOptionInfo(item.getProductOptionId()));
    }

    private ProductOptionInfo findOptionInfo(Long productOptionId) {
        return productApi.findOptionInfo(productOptionId)
                .orElseThrow(() -> new CartException(CartErrorCode.PRODUCT_OPTION_NOT_PURCHASABLE));
    }

    private ProductOptionInfo findOptionInfo(Map<Long, ProductOptionInfo> optionsById, Long productOptionId) {
        ProductOptionInfo optionInfo = optionsById.get(productOptionId);
        if (optionInfo == null) {
            throw new CartException(CartErrorCode.PRODUCT_OPTION_NOT_PURCHASABLE);
        }
        return optionInfo;
    }
}
