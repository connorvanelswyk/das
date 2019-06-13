/*
 * Copyright 2015-2019 Connor Van Elswyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.findupon.commons.utilities;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;


@Component
public class SpringUtils implements ApplicationContextAware {

	private static ApplicationContext currentContext;

	@Override
	public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
		currentContext = applicationContext;
	}

	public static ApplicationContext getCurrentContext() {
		return currentContext;
	}

	public static Object getBean(String name) {
		return currentContext.getBean(name);
	}

	public static void autowire(Object bean) {
		String name = bean.getClass().getSimpleName();
		AutowireCapableBeanFactory factory = currentContext.getAutowireCapableBeanFactory();
		factory.autowireBean(bean);
		factory.initializeBean(bean, Character.toLowerCase(name.charAt(0)) + name.substring(1));
	}

	public static Class<?> resolveGenericTypeArg(Class<?> clazz, Class<?> generic, String typeName) {
		ResolvableType[] types = ResolvableType.forClass(clazz).as(generic).getGenerics();
		if(types.length == 0) {
			return null;
		}
		if(types.length == 1) {
			return types[0].resolve();
		}
		for(ResolvableType type : types) {
			if(type.getType().getTypeName().equalsIgnoreCase(typeName)) {
				return type.resolve();
			}
		}
		return null;
	}
}
