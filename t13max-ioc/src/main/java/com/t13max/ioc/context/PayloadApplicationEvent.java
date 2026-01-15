package com.t13max.ioc.context;

import com.t13max.ioc.core.ResolvableType;
import com.t13max.ioc.utils.Assert;

/**
 * 携带任意有效载荷的事件
 *
 * @Author: t13max
 * @Since: 21:07 2026/1/15
 */
public class PayloadApplicationEvent<T> extends ApplicationEvent {

    private final T payload;

    private final ResolvableType payloadType;

    public PayloadApplicationEvent(Object source, T payload) {
        this(source, payload, null);
    }

    public PayloadApplicationEvent(Object source, T payload, ResolvableType payloadType) {
        super(source);
        Assert.notNull(payload, "Payload must not be null");
        this.payload = payload;
        this.payloadType = (payloadType != null ? payloadType : ResolvableType.forInstance(payload));
    }

    public T getPayload() {
        return this.payload;
    }
}
