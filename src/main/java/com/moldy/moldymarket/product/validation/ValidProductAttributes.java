package com.moldy.moldymarket.product.validation;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ProductAttributesValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidProductAttributes {
    String message() default "Invalid product attributes";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

}
