package com.moldy.moldymarket.appraiser.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AppraiserCategoryId implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID appraiserId;
    private UUID categoryId;
}
