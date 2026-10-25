package com.vsnvi.vsnvi_food.common.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

    private int max;

    @Override
    public void initialize(MaxUtf8Bytes annotation) {
        max = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || fits(value, max);
    }

    public static boolean fits(String value, int maxBytes) {
        return value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
