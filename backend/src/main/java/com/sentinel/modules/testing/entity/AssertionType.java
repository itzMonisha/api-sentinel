package com.sentinel.modules.testing.entity;

public enum AssertionType {
    STATUS_CODE_EQUALS,
    RESPONSE_TIME_BELOW,
    HEADER_EXISTS,
    CONTENT_TYPE_MATCHES,
    JSON_FIELD_EXISTS,
    JSON_FIELD_EQUALS,
    OPENAPI_SCHEMA_MATCH
}
