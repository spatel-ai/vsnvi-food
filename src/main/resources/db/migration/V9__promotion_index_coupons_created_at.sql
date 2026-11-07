-- The admin coupon list sorts all coupons by created_at (newest first) by default.
CREATE INDEX idx_coupons_created_at ON coupons (created_at);
