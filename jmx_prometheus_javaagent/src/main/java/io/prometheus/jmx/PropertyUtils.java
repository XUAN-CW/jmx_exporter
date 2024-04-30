package io.prometheus.jmx;

import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;

/**
 * @author xuanchengwei
 */
public class PropertyUtils {
    public static String getProperty(String propertyName) throws Exception {
        ApplicationContext ctx = getApplicationContext();
        Environment env = ctx.getBean(Environment.class);
        String propertyValue = env.getProperty(propertyName);
        if (propertyValue == null) {
            throw new NoSuchFieldException("Property " + propertyName + " not found in configuration");
        }
        return propertyValue;
    }


    private static ApplicationContext getApplicationContext() throws Exception {
        Class<?> clazz = getApplicationContextProvider();
        try {
            Method getApplicationContextMethod = clazz.getDeclaredMethod("getApplicationContext");
            return (ApplicationContext)getApplicationContextMethod.invoke(null);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new Exception("Failed to access getApplicationContext");
        }
    }


    private static Class<?> getApplicationContextProvider() throws Exception {
        final String[] applicationContextProviderArray = new String[]{
                "com.example.jmx_prometheus_javaagent_springboot.ApplicationContextProvider"
        };
        for (String className : applicationContextProviderArray) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {}
        }
        throw new Exception("Failed to access ApplicationContextProvider");
    }
}
