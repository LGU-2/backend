package com.freshmarket.cart.domain.repository;

import com.freshmarket.cart.domain.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findAllByCartIdOrderByCreatedAtDesc(Long cartId);

    Optional<CartItem> findByCartIdAndProductOptionId(Long cartId, Long productOptionId);

    Optional<CartItem> findByIdAndCartId(Long cartItemId, Long cartId);
}
