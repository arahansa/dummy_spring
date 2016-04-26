package slipp.yoonsung;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ChildBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.AbstractAutowireCapableBeanFactory;

import java.util.*;

public class DefaultListableBeanFactory extends AbstractBeanFactory implements ListableBeanFactory, BeanDefinitionRegistry {

    private Map<String, BeanDefinition> beanDefinitionHash = new HashMap();
    private List<String> beanDefinitionNames = new ArrayList<>(10);

    //---------------------------------------------------------------------
    // Implementation of BeanDefinitionRegistry
    //---------------------------------------------------------------------
    @Override
    public int getBeanDefinitionCount() {
        return beanDefinitionHash.size();
    }

    @Override
    public String[] getBeanDefinitionNames() {
        Set keys = beanDefinitionHash.keySet();
        String[] names = new String[keys.size()];
        Iterator itr = keys.iterator();
        int i = 0;
        while (itr.hasNext()) {
            names[i++] = (String) itr.next();
        }
        return names;
    }

    @Override
    public boolean containsBeanDefinition(String name) {
        return beanDefinitionHash.containsKey(name);
    }

    // 빈 정의를 키만으로 돌려준다
    @Override
    public BeanDefinition getBeanDefinition(String beanName) throws BeansException {
        BeanDefinition bd = this.beanDefinitionHash.get(beanName);
        if (bd == null) {
            throw new NoSuchBeanDefinitionException(beanName, toString());
        }
        return bd;
    }


    // 빈을 등록한다
    @Override
    public void registerBeanDefinition(String id, BeanDefinition beanDefinition) {
        beanDefinitionHash.put(id, beanDefinition);
    }

    //
    @Override
    public String[] getAliases(String name) throws NoSuchBeanDefinitionException {
        return new String[0];
    }

    @Override
    public void registerAlias(String name, String alias) throws BeansException {

    }

    //---------------------------------------------------------------------
    // End of Implementation of BeanDefinitionRegistry
    //---------------------------------------------------------------------



    protected void preInstantiate() {
        String[] beanNames = getBeanDefinitionNames();
        for (int i = 0; i < getBeanDefinitionCount(); i++) {
            getBean(beanNames[i]);
        }
    }





    public DefaultListableBeanFactory(){
        super(null);
    }

    public DefaultListableBeanFactory(BeanFactory parentBeanFactory) {
        super(parentBeanFactory);
    }




    //@Override
//    public BeanDefinition getBeanDefinition(String key) {
//        return beanDefinitionHash.get(key);
//    }
}
