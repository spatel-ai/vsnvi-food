package com.vsnvi.vsnvi_food.user.api;

/** Input for {@link UserApi#createUser}. The caller has already validated the fields. */
public record NewUser(String email, String fullName, String phone, Role role) {}
