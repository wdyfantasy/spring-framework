/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aot.generate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.javapoet.ClassName;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Generate unique class names based on target {@link Class} and a feature
 * name. This class is stateful so the same instance should be used for all
 * name generation.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 6.0
 */
public final class ClassNameGenerator {

	private static final String SEPARATOR = "__";

	private static final String AOT_FEATURE = "Aot";

	private final Class<?> defaultTarget;

	private final String featureNamePrefix;

	private final Map<String, AtomicInteger> sequenceGenerator;

	/**
	 * Create a new instance using the specified {@code defaultTarget} and no
	 * feature name prefix.
	 * @param defaultTarget the default target class to use
	 */
	public ClassNameGenerator(Class<?> defaultTarget) {
		this(defaultTarget, "");
	}

	/**
	 * Create a new instance using the specified {@code defaultTarget} and
	 * feature name prefix.
	 * @param defaultTarget the default target class to use
	 * @param featureNamePrefix the prefix to use to qualify feature names
	 */
	public ClassNameGenerator(Class<?> defaultTarget, String featureNamePrefix) {
		this(defaultTarget, featureNamePrefix, new ConcurrentHashMap<>());
	}

	private ClassNameGenerator(Class<?> defaultTarget, String featureNamePrefix,
			Map<String, AtomicInteger> sequenceGenerator) {
		this.defaultTarget = defaultTarget;
		this.featureNamePrefix = (!StringUtils.hasText(featureNamePrefix) ? "" : featureNamePrefix);
		this.sequenceGenerator = sequenceGenerator;
	}


	/**
	 * Generate a unique {@link ClassName} based on the specified
	 * {@code featureName} and {@code target}. If the {@code target} is
	 * {@code null}, the configured main target of this instance is used.
	 * <p>The class name is a suffixed version of the target. For instance, a
	 * {@code com.example.Demo} target with an {@code Initializer} feature name
	 * leads to a {@code com.example.Demo__Initializer} generated class name.
	 * The feature name is qualified by the configured feature name prefix,
	 * if any.
	 * <p>Generated class names are unique. If such a feature was already
	 * requested for this target, a counter is used to ensure uniqueness.
	 * @param target the class the newly generated class relates to, or
	 * {@code null} to use the main target
	 * @param featureName the name of the feature that the generated class
	 * supports
	 * @return a unique generated class name
	 */
	public ClassName generateClassName(@Nullable Class<?> target, String featureName) {
		return generateSequencedClassName(getClassName(target, featureName));
	}

	/**
	 * Return a class name based on the specified {@code target} and
	 * {@code featureName}. This uses the same algorithm as
	 * {@link #generateClassName(Class, String)} but does not register
	 * the class name, nor add a unique suffix to it if necessary.
	 * @param target the class the newly generated class relates to, or
	 * {@code null} to use the main target
	 * @param featureName the name of the feature that the generated class
	 * supports
	 * @return the class name
	 */
	String getClassName(@Nullable Class<?> target, String featureName) {
		Assert.hasLength(featureName, "'featureName' must not be empty");
		featureName = clean(featureName);
		Class<?> targetToUse = (target != null ? target : this.defaultTarget);
		String featureNameToUse = this.featureNamePrefix + featureName;
		return targetToUse.getName().replace("$", "_")
				+ SEPARATOR + StringUtils.capitalize(featureNameToUse);
	}

	/**
	 * Return a new {@link ClassNameGenerator} instance for the specified
	 * feature name prefix, keeping track of all the class names generated
	 * by this instance.
	 * @param featureNamePrefix the feature name prefix to use
	 * @return a new instance for the specified feature name prefix
	 */
	ClassNameGenerator usingFeatureNamePrefix(String featureNamePrefix) {
		return new ClassNameGenerator(this.defaultTarget, featureNamePrefix,
				this.sequenceGenerator);
	}

	private String clean(String name) {
		StringBuilder clean = new StringBuilder();
		boolean lastNotLetter = true;
		for (char ch : name.toCharArray()) {
			if (!Character.isLetter(ch)) {
				lastNotLetter = true;
				continue;
			}
			clean.append(lastNotLetter ? Character.toUpperCase(ch) : ch);
			lastNotLetter = false;
		}
		return (!clean.isEmpty()) ? clean.toString() : AOT_FEATURE;
	}

	private ClassName generateSequencedClassName(String name) {
		name = addSequence(name);
		return ClassName.get(ClassUtils.getPackageName(name),
				ClassUtils.getShortName(name));
	}

	private String addSequence(String name) {
		int sequence = this.sequenceGenerator
				.computeIfAbsent(name, key -> new AtomicInteger()).getAndIncrement();
		return (sequence > 0) ? name + sequence : name;
	}

}