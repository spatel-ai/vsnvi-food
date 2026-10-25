package com.vsnvi.vsnvi_food.user.api.web;

/** Shared validation patterns for user input. */
final class UserValidation {

    /** E.164: a plus sign, then 8 to 15 digits, no leading zero. */
    static final String PHONE = "^\\+[1-9]\\d{7,14}$";

    /** Indian PIN code. */
    static final String POSTAL_CODE = "^[1-9]\\d{5}$";

    private UserValidation() {}
}
