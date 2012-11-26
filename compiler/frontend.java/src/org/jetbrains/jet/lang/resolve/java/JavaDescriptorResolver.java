/*
 * Copyright 2010-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.lang.resolve.java;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.java.provider.ClassPsiDeclarationProvider;
import org.jetbrains.jet.lang.resolve.java.provider.PsiDeclarationProvider;
import org.jetbrains.jet.lang.resolve.java.resolver.*;
import org.jetbrains.jet.lang.resolve.java.scope.JavaBaseScope;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.resolve.scopes.ChainedScope;
import org.jetbrains.jet.lang.resolve.scopes.JetScope;
import org.jetbrains.jet.lang.types.DependencyClassByQualifiedNameResolver;
import org.jetbrains.jet.lang.types.JetType;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.jet.lang.resolve.java.DescriptorSearchRule.ERROR_IF_FOUND_IN_KOTLIN;
import static org.jetbrains.jet.lang.resolve.java.DescriptorSearchRule.IGNORE_IF_FOUND_IN_KOTLIN;
import static org.jetbrains.jet.lang.resolve.java.resolver.JavaNamespaceResolver.FAKE_ROOT_MODULE;

/**
 * @author abreslav
 */
public class JavaDescriptorResolver implements DependencyClassByQualifiedNameResolver {

    public static final Name JAVA_ROOT = Name.special("<java_root>");

    public static Visibility PACKAGE_VISIBILITY = new Visibility("package", false) {
        @Override
        protected boolean isVisible(@NotNull DeclarationDescriptorWithVisibility what, @NotNull DeclarationDescriptor from) {
            NamespaceDescriptor parentPackage = DescriptorUtils.getParentOfType(what, NamespaceDescriptor.class);
            NamespaceDescriptor fromPackage = DescriptorUtils.getParentOfType(from, NamespaceDescriptor.class, false);
            assert parentPackage != null;
            return parentPackage.equals(fromPackage);
        }

        @Override
        protected Integer compareTo(@NotNull Visibility visibility) {
            if (this == visibility) return 0;
            if (visibility == Visibilities.PRIVATE) return 1;
            return -1;
        }
    };

    private JavaPropertyResolver propertiesResolver;
    private JavaClassResolver classResolver;
    private JavaConstructorResolver constructorResolver;
    private JavaFunctionResolver functionResolver;
    private JavaNamespaceResolver namespaceResolver;
    private JavaInnerClassResolver innerClassResolver;

    @Inject
    public void setFunctionResolver(JavaFunctionResolver functionResolver) {
        this.functionResolver = functionResolver;
    }

    @Inject
    public void setClassResolver(JavaClassResolver classResolver) {
        this.classResolver = classResolver;
    }

    @Inject
    public void setNamespaceResolver(JavaNamespaceResolver namespaceResolver) {
        this.namespaceResolver = namespaceResolver;
    }

    @Inject
    public void setPropertiesResolver(JavaPropertyResolver propertiesResolver) {
        this.propertiesResolver = propertiesResolver;
    }

    @Inject
    public void setConstructorResolver(JavaConstructorResolver constructorResolver) {
        this.constructorResolver = constructorResolver;
    }

    @Inject
    public void setInnerClassResolver(JavaInnerClassResolver innerClassResolver) {
        this.innerClassResolver = innerClassResolver;
    }

    @Nullable
    public ClassDescriptor resolveClass(@NotNull FqName qualifiedName, @NotNull DescriptorSearchRule searchRule) {
        return classResolver.resolveClass(qualifiedName, searchRule);
    }

    @Override
    public ClassDescriptor resolveClass(@NotNull FqName qualifiedName) {
        return classResolver.resolveClass(qualifiedName);
    }

    @NotNull
    public Collection<ConstructorDescriptor> resolveConstructors(
            @NotNull ClassPsiDeclarationProvider classData, @NotNull ClassDescriptor classDescriptor
    ) {
        return constructorResolver.resolveConstructors(classData, classDescriptor);
    }

    @Nullable
    public NamespaceDescriptor resolveNamespace(
            @NotNull FqName qualifiedName,
            @NotNull ModuleDescriptor parentModule,
            @NotNull DescriptorSearchRule searchRule) {
        return namespaceResolver.resolveNamespace(qualifiedName, parentModule, searchRule);
    }

    @Override
    public NamespaceDescriptor resolveNamespace(@NotNull FqName qualifiedName) {
        return resolveNamespace(qualifiedName, FAKE_ROOT_MODULE, ERROR_IF_FOUND_IN_KOTLIN);
    }

    @Nullable
    public JetScope getJavaPackageScope(@NotNull NamespaceDescriptor namespaceDescriptor) {
        //TODO: search rule
        Map<ModuleDescriptor, NamespaceDescriptor> map =
                namespaceResolver.resolveNamespace(DescriptorUtils.getFQName(namespaceDescriptor).toSafe(), IGNORE_IF_FOUND_IN_KOTLIN);
        if (map.isEmpty()) {
            return null;
        }
        Collection<JetScope> scopes = Collections2.transform(map.values(), new Function<NamespaceDescriptor, JetScope>() {
            @Override
            public JetScope apply(@Nullable NamespaceDescriptor namespace) {
                return namespace.getMemberScope();
            }
        });
        return new ChainedScope(map.keySet().iterator().next(), scopes.toArray(new JetScope[scopes.size()]));
    }

    @NotNull
    public Set<VariableDescriptor> resolveFieldGroupByName(
            @NotNull Name name,
            @NotNull PsiDeclarationProvider data,
            @NotNull ClassOrNamespaceDescriptor ownerDescriptor
    ) {
        return propertiesResolver.resolveFieldGroupByName(name, data, ownerDescriptor);
    }

    @Nullable
    public ClassDescriptor resolveClass(@NotNull FqName name, @NotNull DescriptorSearchRule searchRule, @NotNull PostponedTasks tasks) {
        return classResolver.resolveClass(name, searchRule, tasks);
    }

    public static class ValueParameterDescriptors {
        private final JetType receiverType;
        private final List<ValueParameterDescriptor> descriptors;

        public ValueParameterDescriptors(@Nullable JetType receiverType, @NotNull List<ValueParameterDescriptor> descriptors) {
            this.receiverType = receiverType;
            this.descriptors = descriptors;
        }

        @Nullable
        public JetType getReceiverType() {
            return receiverType;
        }

        @NotNull
        public List<ValueParameterDescriptor> getDescriptors() {
            return descriptors;
        }
    }

    @NotNull
    public Set<FunctionDescriptor> resolveFunctionGroup(
            @NotNull Name methodName,
            @NotNull ClassPsiDeclarationProvider scopeData,
            @NotNull ClassOrNamespaceDescriptor ownerDescriptor
    ) {
        return functionResolver.resolveFunctionGroup(methodName, scopeData, ownerDescriptor);
    }

    @NotNull
    public List<ClassDescriptor> resolveInnerClasses(
            @NotNull DeclarationDescriptor owner,
            @NotNull ClassPsiDeclarationProvider declarationProvider)
    {
        return innerClassResolver.resolveInnerClasses(owner, declarationProvider);
    }
}
