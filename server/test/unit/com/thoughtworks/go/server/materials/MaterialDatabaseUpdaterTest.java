/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
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
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.server.materials;

import com.thoughtworks.go.config.materials.dependency.DependencyMaterial;
import com.thoughtworks.go.config.materials.git.GitMaterial;
import com.thoughtworks.go.domain.materials.Material;
import com.thoughtworks.go.helper.MaterialsMother;
import com.thoughtworks.go.server.cache.GoCache;
import com.thoughtworks.go.server.persistence.MaterialRepository;
import com.thoughtworks.go.server.service.MaterialExpansionService;
import com.thoughtworks.go.server.transaction.TransactionTemplate;
import com.thoughtworks.go.serverhealth.HealthStateScope;
import com.thoughtworks.go.serverhealth.HealthStateType;
import com.thoughtworks.go.serverhealth.ServerHealthService;
import com.thoughtworks.go.serverhealth.ServerHealthState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MaterialDatabaseUpdaterTest {
    @Mock private MaterialRepository materialRepository;
    @Mock private ServerHealthService healthService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private GoCache goCache;
    @Mock private DependencyMaterialUpdater dependencyMaterialUpdater;
    @Mock private ScmMaterialUpdater scmMaterialUpdater;
    @Mock private PackageMaterialUpdater packageMaterialUpdater;
    @Mock private PluggableSCMMaterialUpdater pluggableSCMMaterialUpdater;
    @Mock private MaterialExpansionService materialExpansionService;

    private MaterialDatabaseUpdater materialDatabaseUpdater;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        materialDatabaseUpdater = new MaterialDatabaseUpdater(materialRepository, healthService, transactionTemplate, goCache, dependencyMaterialUpdater, scmMaterialUpdater,
                packageMaterialUpdater, pluggableSCMMaterialUpdater, materialExpansionService);
    }

    @Test
    public void shouldNotRunAnUpdateOnADependencyMaterialWhichHasAlreadyBeenSeen() throws Exception {
        DependencyMaterial material = MaterialsMother.dependencyMaterial("pipeline1", "stage1");

        String expectedKeyToCheck = DependencyMaterialUpdater.cacheKeyForDependencyMaterial(material);
        when(goCache.isKeyInCache(expectedKeyToCheck)).thenReturn(true);

        materialDatabaseUpdater.updateMaterial(material);

        verifyZeroInteractions(materialRepository, healthService, transactionTemplate, dependencyMaterialUpdater, scmMaterialUpdater);
        verify(goCache, times(1)).isKeyInCache(expectedKeyToCheck);
        verifyNoMoreInteractions(goCache);
    }

    @Test
    public void shouldThrowExceptionWithLongDescriptionOfMaterialWhenUpdateFails() throws Exception {
        Material material = new GitMaterial("url", "branch");
        Exception exception = new RuntimeException("failed");
        String message = "Modification check failed for material: " + material.getLongDescription();
        ServerHealthState error = ServerHealthState.error(message, exception.getMessage(), HealthStateType.general(HealthStateScope.forMaterial(material)));
        when(materialRepository.findMaterialInstance(material)).thenThrow(exception);
        try {
            materialDatabaseUpdater.updateMaterial(material);
            fail("should have thrown exception");
        } catch (Exception e) {
            assertThat(e, is(exception));
        }
        verify(healthService).update(error);
    }

    @Test
    public void shouldGetCorrectUpdaterForMaterials() throws Exception {
        assertThat(materialDatabaseUpdater.updater(MaterialsMother.dependencyMaterial()), is((MaterialUpdater) dependencyMaterialUpdater));
        assertThat(materialDatabaseUpdater.updater(MaterialsMother.svnMaterial()), is((MaterialUpdater) scmMaterialUpdater));
        assertThat(materialDatabaseUpdater.updater(MaterialsMother.packageMaterial()), is((MaterialUpdater) packageMaterialUpdater));
        assertThat(materialDatabaseUpdater.updater(MaterialsMother.pluggableSCMMaterial()), is((MaterialUpdater) pluggableSCMMaterialUpdater));
    }

    @Test
    public void shouldFailWithAReasonableMessageWhenExceptionMessageIsNull() throws Exception {
        Material material = new GitMaterial("url", "branch");
        Exception exceptionWithNullMessage = new RuntimeException(null, new RuntimeException("Inner exception has non-null message"));
        String message = "Modification check failed for material: " + material.getLongDescription();

        when(materialRepository.findMaterialInstance(material)).thenThrow(exceptionWithNullMessage);

        try {
            materialDatabaseUpdater.updateMaterial(material);
            fail("should have thrown exception");
        } catch (Exception e) {
            assertThat(e, is(exceptionWithNullMessage));
        }

        verify(healthService).update(ServerHealthState.error(message, "Unknown error", HealthStateType.general(HealthStateScope.forMaterial(material))));
    }
}
