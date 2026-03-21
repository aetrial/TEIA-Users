package com.teia.users.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Provides static access to the Spring ApplicationContext.
 *
 * This is necessary for JPA AttributeConverters, which are instantiated by
 * Hibernate and not managed by Spring. The converter can use this class to
 * obtain Spring-managed beans like EncryptionService.
 *
 * Usage:
 *   EncryptionService service = ApplicationContextProvider.getBean(EncryptionService.class);
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    /**
     * Get a bean from the Spring application context.
     *
     * @param beanClass The class of the bean to retrieve
     * @param <T> The type of the bean
     * @return The bean instance
     * @throws IllegalStateException if the application context is not initialized
     */
    public static <T> T getBean(Class<T> beanClass) {
        if (applicationContext == null) {
            throw new IllegalStateException(
                "ApplicationContext has not been initialized. " +
                "This typically means the application is still starting up."
            );
        }
        return applicationContext.getBean(beanClass);
    }

    /**
     * Check if the application context has been initialized.
     *
     * @return true if the context is available
     */
    public static boolean isContextAvailable() {
        return applicationContext != null;
    }
}
