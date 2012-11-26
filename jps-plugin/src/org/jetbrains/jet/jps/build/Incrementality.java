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

package org.jetbrains.jet.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.ModuleBuildTarget;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface Incrementality {
    @NotNull
    List<File> getSourcesToCompile(
            @NotNull ModuleBuildTarget target,
            @NotNull DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder
    ) throws IOException;

    @NotNull
    Set<File> getExcludedClasspathDirectories(@NotNull ModuleBuildTarget target);

    Incrementality NOT_INCREMENTAL = new Incrementality() {
        @NotNull
        @Override
        public List<File> getSourcesToCompile(
                @NotNull ModuleBuildTarget target,
                @NotNull DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder
        ) {
            return KotlinSourceFileCollector.getAllKotlinSourceFiles(target);
        }

        @NotNull
        @Override
        public Set<File> getExcludedClasspathDirectories(@NotNull ModuleBuildTarget target) {
            // this excludes the output directory from the class path, to be removed for true incremental compilation
            return Collections.singleton(target.getOutputDir());
        }
    };

    Incrementality INCREMENTAL = new Incrementality() {
        @NotNull
        @Override
        public List<File> getSourcesToCompile(
                @NotNull ModuleBuildTarget target,
                @NotNull DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder
        ) throws IOException {
            return KotlinSourceFileCollector.getDirtySourceFiles(dirtyFilesHolder);
        }

        @NotNull
        @Override
        public Set<File> getExcludedClasspathDirectories(@NotNull ModuleBuildTarget target) {
            return Collections.emptySet();
        }
    };
}
