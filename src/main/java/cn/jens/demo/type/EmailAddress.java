package cn.jens.demo.type;

import java.util.Objects;

/** 用于演示自定义 TypeHandler 的邮箱值对象。 */
public record EmailAddress(String value) {

    public EmailAddress {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Email address must not be blank");
        }
    }
}
