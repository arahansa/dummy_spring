package slipp.yoonsung;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.*;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class AbstractBeanFactory implements BeanFactory {

    private final BeanFactory parentBeanFactory;
    /**
     * Map of Bean objects, keyed by id attribute
     */
    private Map beanHash = new HashMap();

    public abstract BeanDefinition getBeanDefinition(String key);

    public AbstractBeanFactory(BeanFactory parentBeanFactory) {
        this.parentBeanFactory = parentBeanFactory;
    }

    @Override
    public <T> T getBean(String key, Class<T> clazz) {
        return (T) getBean(key);
    }

    @Override
    public Object getBean(String key) {
        return getBeanInternal(key);
    }

    private Object getBeanInternal(String key) {
        if (key == null)
            throw new IllegalArgumentException("Bean name null is not allowed");
        if (beanHash.containsKey(key)) {
            return beanHash.get(key);
        } else {
            RootBeanDefinition rbd = getMergedBeanDefinition(key, false);
            log.debug("RootBeanDefinition :: {} ", rbd);
            if (rbd != null) {
                Object newlyCreatedBean = createBean(key);
                if(rbd.isSingleton())
                    beanHash.put(key, newlyCreatedBean);
                return newlyCreatedBean;
            } else {
                if (this.parentBeanFactory == null)
                    throw new IllegalArgumentException("Cannot instantiate [bean name : " + key + "]; is not exist");
                return parentBeanFactory.getBean(key);
            }
        }
    }
    public RootBeanDefinition getMergedBeanDefinition(String beanName, boolean includingAncestors)
            throws BeansException {
        try {
            return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
        }
        catch (org.springframework.beans.NoSuchBeanDefinitionException ex) {
            throw ex;
        }
    }

    protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd) {
        if (bd instanceof RootBeanDefinition) {
            return (RootBeanDefinition) bd;
        }
        else if (bd instanceof ChildBeanDefinition) {
            ChildBeanDefinition cbd = (ChildBeanDefinition) bd;
            // deep copy
            RootBeanDefinition rbd = new RootBeanDefinition(getMergedBeanDefinition(cbd.getParentName(), true));
            // override properties
            for (int i = 0; i < cbd.getPropertyValues().getPropertyValues().length; i++) {
                rbd.getPropertyValues().addPropertyValue(cbd.getPropertyValues().getPropertyValues()[i]);
            }
            // override settings
            rbd.setSingleton(cbd.isSingleton());
            rbd.setLazyInit(cbd.isLazyInit());
            rbd.setResourceDescription(cbd.getResourceDescription());
            return rbd;
        }
        else {
            throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
                    "Definition is neither a RootBeanDefinition nor a ChildBeanDefinition");
        }
    }

    private Object createBean(String key) {
        try {
            RootBeanDefinition rbd = getMergedBeanDefinition(key, false);
            final MutablePropertyValues propertyValues = rbd.getPropertyValues();
            BeanWrapper instanceWrapper = new BeanWrapperImpl(rbd.getBeanClass());

            Object newlyCreatedBean = instanceWrapper.getWrappedInstance();
            if(rbd.isSingleton())
                beanHash.put(key, newlyCreatedBean);
            populateBean(key, rbd, instanceWrapper);

            //applyPropertyValues(beanDefinition, propertyValues, newlyCreatedBean, key);
            callLifecycleMethodsIfNecessary(newlyCreatedBean);
            return newlyCreatedBean;
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Cannot instantiate [bean name : " + key + "]; is it an interface or an abstract class?");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Cannot instantiate [bean name : " + key + "]; has class definition changed? Is there a public constructor?");
        } catch(Exception e){
            e.printStackTrace();
            throw new BeanCreationException("key : "+key + "failed create bean");
        }
    }

    protected void populateBean(String beanName, RootBeanDefinition mergedBeanDefinition, BeanWrapper bw) {
        PropertyValues pvs = mergedBeanDefinition.getPropertyValues();
        applyPropertyValues(beanName, mergedBeanDefinition, bw, pvs);
    }

    protected void applyPropertyValues(String beanName, RootBeanDefinition mergedBeanDefinition, BeanWrapper bw,
                                       PropertyValues pvs) throws BeansException {
        if (pvs == null) {
            return;
        }
        MutablePropertyValues deepCopy = new MutablePropertyValues(pvs);
        PropertyValue[] pvals = deepCopy.getPropertyValues();
        for (int i = 0; i < pvals.length; i++) {
            Object value = resolveValueIfNecessary(beanName, mergedBeanDefinition,
                    pvals[i].getName(), pvals[i].getValue());
            PropertyValue pv = new PropertyValue(pvals[i].getName(), value);
            // update mutable copy
            deepCopy.setPropertyValueAt(pv, i);
        }
        // set our (possibly massaged) deepCopy
        try {
            // synchronize if custom editors are registered
            // necessary because PropertyEditors are not thread-safe
            bw.setPropertyValues(deepCopy);
        }
        catch (BeansException ex) {
            // improve the message by showing the context
            throw new BeanCreationException(mergedBeanDefinition.getResourceDescription(), beanName,
                    "Error setting property values", ex);
        }
    }

    protected Object resolveValueIfNecessary(String beanName, RootBeanDefinition mergedBeanDefinition,
                                             String argName, Object value) throws BeansException {
        // We must check each PropertyValue to see whether it
        // requires a runtime reference to another bean to be resolved.
        // If it does, we'll attempt to instantiate the bean and set the reference.
        if (value instanceof AbstractBeanDefinition) {
            BeanDefinition bd = (BeanDefinition) value;
            if (bd instanceof AbstractBeanDefinition) {
                // an inner bean should never be cached as singleton
                ((AbstractBeanDefinition) bd).setSingleton(false);
            }
            String innerBeanName = "(inner bean for property '" + beanName + "." + argName + "')";
            Object bean = createBean(beanName);
            return bean; //getObjectForSharedInstance(innerBeanName, bean);
        }
        else if (value instanceof RuntimeBeanReference) {
            RuntimeBeanReference ref = (RuntimeBeanReference) value;
            return resolveReference(mergedBeanDefinition, beanName, argName, ref);
        }
        else {
            // no need to resolve value
            return value;
        }
    }
    protected Object resolveReference(RootBeanDefinition mergedBeanDefinition, String beanName,
                                      String argName, RuntimeBeanReference ref) throws BeansException {
        try {
            log.debug("Resolving reference from property '" + argName + "' in bean '" +
                    beanName + "' to bean '" + ref.getBeanName() + "'");
            return getBean(ref.getBeanName());
        }
        catch (BeansException ex) {
            throw new BeanCreationException(mergedBeanDefinition.getResourceDescription(), beanName,
                    "Can't resolve reference to bean '" + ref.getBeanName() +
                            "' while setting property '" + argName + "'", ex);
        }
    }

    private void applyPropertyValues(BeanDefinition beanDefinition, MutablePropertyValues propertyValues, Object bean, String beanName) {
        Class clazz = ((RootBeanDefinition)beanDefinition).getBeanClass();

        final org.springframework.beans.PropertyValue[] propertyValues1 = propertyValues.getPropertyValues();
        for (int i = 0; i < propertyValues1.length; ++i) {
            org.springframework.beans.PropertyValue property = propertyValues1[i];
            try {
                Field field = clazz.getDeclaredField(property.getName());
                String propertyName = property.getName();

                Method method = clazz.getMethod("set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1), new Class[]{field.getType()});

                //Integer와 String 필드에 대해서만 동작
                log.info("fieldType : {} ", field.getType().getName());

                String fieldName = field.getType().getName();
                if ("java.lang.Integer".equals(fieldName) || "int".equals(fieldName)) {
                    method.invoke(bean, Integer.parseInt(property.getValue().toString()));
                } else {
                    method.invoke(bean, property.getValue().toString());
                }
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Cannot instantiate [bean name : " + beanName + "]; is not have field [" + property.getName() + "]");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Cannot instantiate [bean name : " + beanName + "]; Cannot access field [" + property.getName() + "]");
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Cannot instantiate [bean name : " + beanName + "]; Cannot access field, set method not defined [" + property.getName() + "]");
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private void callLifecycleMethodsIfNecessary(Object bean) throws Exception {
        //이전 테스트와 깨지지 않게 유지하기 위해서 step4의 InitializingBean을 import합니다.
        if (bean instanceof slipp.yoonsung.InitializingBean) {
            ((slipp.yoonsung.InitializingBean) bean).afterPropertiesSet();
        }
        if (bean instanceof org.springframework.beans.factory.InitializingBean) {
            ((org.springframework.beans.factory.InitializingBean) bean).afterPropertiesSet();
        }


    }
}