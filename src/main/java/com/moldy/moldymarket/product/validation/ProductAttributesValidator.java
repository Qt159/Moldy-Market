package com.moldy.moldymarket.product.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

public class ProductAttributesValidator
        implements ConstraintValidator <ValidProductAttributes, Map<String, Object>> {

    private static final int MAX_ENTRIES = 20;
    private static final int MAX_KEY_LENGTH = 50;
    private static final int MAX_VALUE_LENGTH = 200;

    @Override
    public boolean isValid(Map<String, Object> attributes, ConstraintValidatorContext context) {
        if (attributes == null) {
            return true;
        }

        if (attributes.size() > MAX_ENTRIES) {
            return fail(context, "At most 20 attributes are allowed");
        }

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
                return fail(context, "Attribute names must be 1 to 50 characters");
            }

            Object value = entry.getValue();
            boolean isNumberOrBoolean = value instanceof Number || value instanceof Boolean;
            boolean isShortText = value instanceof String text && text.length() <= MAX_VALUE_LENGTH;

            // Cấm object lồng nhau, mảng và null
            if (!isNumberOrBoolean && !isShortText) {
                return fail(context, "Attribute values must be short text (max 200 characters), a number or a boolean");
            }
        }
        return true;
    }

    private boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
