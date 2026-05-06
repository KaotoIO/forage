package io.kaoto.forage.core.cxf;

import io.kaoto.forage.core.common.BeanProvider;

public interface CxfEndpointProvider extends BeanProvider<Object> {

    @Override
    default Object create() {
        return create(null);
    }

    @Override
    Object create(String id);
}
