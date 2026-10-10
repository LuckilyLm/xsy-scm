-- 拒绝事件保留审计证据，但不占用渠道事件 ID；有效事件仍由数据库保证并发幂等。
DROP INDEX xsy_v2.uk_payment_callback_event;

CREATE UNIQUE INDEX uk_payment_callback_event
    ON xsy_v2.payment_callback_event (provider, provider_event_id)
    WHERE signature_verified = TRUE AND process_status IN ('RECEIVED', 'APPLIED');
