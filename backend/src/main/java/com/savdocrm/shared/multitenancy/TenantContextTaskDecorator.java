package com.savdocrm.shared.multitenancy;

import org.springframework.core.task.TaskDecorator;

import java.util.Optional;

public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Optional<TenantContext> capturedContext = TenantContextHolder.getContext();
        return () -> {
            try {
                capturedContext.ifPresent(TenantContextHolder::setContext);
                runnable.run();
            } finally {
                TenantContextHolder.clear();
            }
        };
    }
}
