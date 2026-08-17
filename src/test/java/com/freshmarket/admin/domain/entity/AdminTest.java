package com.freshmarket.admin.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminTest {

    @Test
    void 이미_비활성화된_계정을_다시_비활성화하면_예외가_발생한다() {
        // given
        Admin admin = AdminFixture.inactive("admin.kim", "hash", AdminRole.ADMIN);

        // when, then
        assertThatThrownBy(() -> admin.deactivate(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 활성_계정을_비활성화하면_상태와_리프레시_토큰이_함께_정리된다() {
        // given
        Admin admin = AdminFixture.active("admin.kim", "hash", AdminRole.ADMIN);
        admin.issueRefreshToken("a".repeat(64), LocalDateTime.now().plusDays(1));

        // when
        admin.deactivate(LocalDateTime.now());

        // then
        assertThat(admin.isActive()).isFalse();
        assertThat(admin.getRefreshTokenHash()).isNull();
        assertThat(admin.getRefreshTokenExpiresAt()).isNull();
    }
}