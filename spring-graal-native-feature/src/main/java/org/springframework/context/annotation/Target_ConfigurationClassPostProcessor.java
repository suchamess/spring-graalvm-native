package org.springframework.context.annotation;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.commons.logging.Log;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.internal.svm.OnlyPresent;
import org.springframework.util.ClassUtils;

@TargetClass(className = "org.springframework.context.annotation.ConfigurationClassPostProcessor", onlyWith = OnlyPresent.class)
public final class Target_ConfigurationClassPostProcessor {

	@Alias
	private ClassLoader beanClassLoader;

	@Alias
	private Log logger;

	@Alias
	private static String IMPORT_REGISTRY_BEAN_NAME;

	@Substitute
	public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
		Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			Object configClassAttr = beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE);
			MethodMetadata methodMetadata = null;
			if (beanDef instanceof AnnotatedBeanDefinition) {
				methodMetadata = ((AnnotatedBeanDefinition) beanDef).getFactoryMethodMetadata();
			}
			if ((configClassAttr != null || methodMetadata != null) && beanDef instanceof AbstractBeanDefinition) {
				// Configuration class (full or lite) or a configuration-derived @Bean method
				// -> resolve bean class at this point...
				AbstractBeanDefinition abd = (AbstractBeanDefinition) beanDef;
				if (!abd.hasBeanClass()) {
					try {
						abd.resolveBeanClass(this.beanClassLoader);
					}
					catch (Throwable ex) {
						throw new IllegalStateException(
								"Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
					}
				}
			}
			if (ConfigurationClassUtils.CONFIGURATION_CLASS_FULL.equals(configClassAttr)) {
				if (!(beanDef instanceof AbstractBeanDefinition)) {
					throw new BeanDefinitionStoreException("Cannot enhance @Configuration bean definition '" +
							beanName + "' since it is not stored in an AbstractBeanDefinition subclass");
				}
				else if (logger.isInfoEnabled() && beanFactory.containsSingleton(beanName)) {
					logger.info("Cannot enhance @Configuration bean definition '" + beanName +
							"' since its singleton instance has been created too early. The typical cause " +
							"is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor " +
							"return type: Consider declaring such methods as 'static'.");
				}
				configBeanDefs.put(beanName, (AbstractBeanDefinition) beanDef);
			}
		}
	}
}

@TargetClass(className = "org.springframework.context.annotation.ConfigurationClassPostProcessor", innerClass = "ImportAwareBeanPostProcessor", onlyWith = OnlyPresent.class)
final class Target_ImportAwareBeanPostProcessor {

	@Alias
	private BeanFactory beanFactory;

	@Substitute
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		if (bean instanceof ImportAware) {
			ImportRegistry ir = this.beanFactory.getBean(ConfigurationClassPostProcessor.class.getName() + ".importRegistry", ImportRegistry.class);
			AnnotationMetadata importingClass = ir.getImportingClassFor(ClassUtils.getUserClass(bean).getName());
			if (importingClass != null) {
				((ImportAware) bean).setImportMetadata(importingClass);
			}
		}
		return bean;
	}
}
