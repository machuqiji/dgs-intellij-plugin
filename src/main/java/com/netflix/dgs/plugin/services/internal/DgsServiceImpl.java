/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.dgs.plugin.services.internal;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiModificationTracker;
import com.netflix.dgs.plugin.services.DgsComponentIndex;
import com.netflix.dgs.plugin.services.DgsDataProcessor;
import com.netflix.dgs.plugin.services.DgsService;

import java.util.Set;

public class DgsServiceImpl implements DgsService, Disposable {
    private final Project project;
    private final Set<String> annotations = Set.of(
            "DgsQuery",
            "DgsMutation",
            "DgsSubscription",
            "DgsData",
            "DgsEntityFetcher");
    private volatile DgsComponentIndex cachedComponentIndex;

    public DgsServiceImpl(Project project) {
        this.project = project;

//        var topic = GraphQLSchemaChangeListener.TOPIC;
//        project.getMessageBus().connect(this).subscribe(
//                topic,
//                version -> {
//                    cachedComponentIndex = null;
//                    DaemonCodeAnalyzer.getInstance(project).restart();
//                }
//        );
//
//
//        project.getMessageBus().connect(this).subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
//            @Override
//            public void beforeDocumentSaving(@NotNull Document document) {
//                var file = FileDocumentManager.getInstance().getFile(document);
//
//                if (JavaFileType.INSTANCE == file.getFileType() || KotlinFileType.INSTANCE == file.getFileType()) {
//                    System.out.println("Clearing component cache");
//                    cachedComponentIndex = null;
//
//                    DaemonCodeAnalyzer.getInstance(project).restart(PsiManager.getInstance(project).findFile(file));
//                }
//            }
//        });
    }

    private volatile long modificationCount;

    @Override
    public DgsComponentIndex getDgsComponentIndex() {
        ModificationTracker modificationTracker = PsiModificationTracker.SERVICE.getInstance(project).forLanguage(JavaLanguage.INSTANCE);
        if(cachedComponentIndex != null && modificationCount == modificationTracker.getModificationCount()) {
            return cachedComponentIndex;
        } else {
            modificationCount = modificationTracker.getModificationCount();
            StubIndex stubIndex = StubIndex.getInstance();

            long startTime = System.currentTimeMillis();
            DgsComponentIndex dgsComponentIndex = new DgsComponentIndex();
            var processor = new DgsDataProcessor(project.getService(GraphQLSchemaRegistry.class), dgsComponentIndex);

            annotations.forEach(dataFetcherAnnotation -> {
                stubIndex.processElements(JavaStubIndexKeys.ANNOTATIONS, dataFetcherAnnotation, project, GlobalSearchScope.projectScope(project), PsiAnnotation.class, annotation -> {
                    processor.process(annotation);
                    return true;
                });
            });

                cachedComponentIndex = dgsComponentIndex;

            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("DGS indexing took " + totalTime + " ms");

            return dgsComponentIndex;
        }
    }

    @Override
    public void clearCache() {
//        cachedComponentIndex = null;
    }

    @Override
    public void dispose() {

    }
}
