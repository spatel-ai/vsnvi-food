package com.vsnvi.vsnvi_food.menu.domain;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;

public class CategoryNotEmptyException extends DomainException {

    public CategoryNotEmptyException() {
        super(ErrorCode.CATEGORY_NOT_EMPTY, "Delete or move this category's items first");
    }
}
