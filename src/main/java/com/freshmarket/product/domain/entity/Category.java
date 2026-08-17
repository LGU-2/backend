package com.freshmarket.product.domain.entity;

import com.freshmarket.common.entity.BaseMutableTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category")
@AttributeOverride(name = "id", column = @Column(name = "category_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseMutableTimeEntity {

    private static final int NAME_MAX_LENGTH = 50;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    private Category(String name, Long parentId) {
        validateName(name);
        this.name = name;
        this.parentId = parentId;
    }

    public static Category register(String name) {
        return new Category(name, null);
    }

    public static Category register(String name, Long parentId) {
        return new Category(name, parentId);
    }

    public void rename(String newName) {
        validateName(newName);
        this.name = newName;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 은 필수다");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "name 은 " + NAME_MAX_LENGTH + "자를 넘을 수 없다: " + name.length());
        }
    }
}