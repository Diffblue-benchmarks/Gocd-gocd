/*
 * Copyright 2018 ThoughtWorks, Inc.
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

package com.thoughtworks.go.plugin.access.elastic;

import com.thoughtworks.go.domain.JobIdentifier;
import com.thoughtworks.go.plugin.access.PluginRequestHelper;
import com.thoughtworks.go.plugin.access.elastic.models.AgentMetadata;
import com.thoughtworks.go.plugin.access.elastic.v5.ElasticAgentExtensionV5;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.validation.ValidationError;
import com.thoughtworks.go.plugin.api.response.validation.ValidationResult;
import com.thoughtworks.go.plugin.domain.common.Image;
import com.thoughtworks.go.plugin.domain.common.Metadata;
import com.thoughtworks.go.plugin.domain.common.PluginConfiguration;
import com.thoughtworks.go.plugin.domain.common.PluginConstants;
import com.thoughtworks.go.plugin.domain.elastic.Capabilities;
import com.thoughtworks.go.plugin.infra.PluginManager;
import com.thoughtworks.go.plugin.infra.plugininfo.GoPluginDescriptor;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.thoughtworks.go.plugin.access.elastic.v5.ElasticAgentPluginConstantsV5.*;
import static com.thoughtworks.go.plugin.domain.common.PluginConstants.ELASTIC_AGENT_EXTENSION;
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ElasticAgentExtensionV5Test {
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private static final String PLUGIN_ID = "cd.go.example.plugin";
    @Mock
    private PluginManager pluginManager;
    @Mock
    private GoPluginDescriptor descriptor;
    private ArgumentCaptor<GoPluginApiRequest> requestArgumentCaptor;
    private ElasticAgentExtensionV5 extensionV5;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        requestArgumentCaptor = ArgumentCaptor.forClass(GoPluginApiRequest.class);
        final List<String> goSupportedVersions = Arrays.asList("3.0", "4.0", "5.0");

        when(descriptor.id()).thenReturn(PLUGIN_ID);

        when(pluginManager.getPluginDescriptorFor(PLUGIN_ID)).thenReturn(descriptor);
        when(pluginManager.isPluginOfType(ELASTIC_AGENT_EXTENSION, PLUGIN_ID)).thenReturn(true);
        when(pluginManager.resolveExtensionVersion(PLUGIN_ID, ELASTIC_AGENT_EXTENSION, goSupportedVersions)).thenReturn("5.0");

        final PluginRequestHelper pluginRequestHelper = new PluginRequestHelper(pluginManager, goSupportedVersions, ELASTIC_AGENT_EXTENSION);
        extensionV5 = new ElasticAgentExtensionV5(pluginRequestHelper);
    }

    @Test
    public void shouldGetPluginIcon() {
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success("{\"content_type\":\"image/png\",\"data\":\"Zm9vYmEK\"}"));
        final Image icon = extensionV5.getIcon(PLUGIN_ID);

        assertThat(icon.getContentType(), is("image/png"));
        assertThat(icon.getData(), is("Zm9vYmEK"));

        assertExtensionRequest("5.0", REQUEST_GET_PLUGIN_SETTINGS_ICON, null);
    }

    @Test
    public void shouldGetCapabilitiesOfAPlugin() {
        final String responseBody = "{\"supports_status_report\":\"true\", \"supports_agent_status_report\":\"true\"}";
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final Capabilities capabilities = extensionV5.getCapabilities(PLUGIN_ID);

        assertTrue(capabilities.supportsStatusReport());
        assertTrue(capabilities.supportsAgentStatusReport());
    }

    @Test
    public void shouldGetProfileMetadata() {
        String responseBody = "[{\"key\":\"Username\",\"metadata\":{\"required\":true,\"secure\":false}},{\"key\":\"Password\",\"metadata\":{\"required\":true,\"secure\":true}}]";
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final List<PluginConfiguration> metadata = extensionV5.getElasticProfileMetadata(PLUGIN_ID);

        assertThat(metadata, hasSize(2));
        assertThat(metadata, containsInAnyOrder(
                new PluginConfiguration("Username", new Metadata(true, false)),
                new PluginConfiguration("Password", new Metadata(true, true))
        ));

        assertExtensionRequest("5.0", REQUEST_GET_PROFILE_METADATA, null);
    }

    @Test
    public void shouldGetProfileView() {
        String responseBody = "{ \"template\": \"<div>This is profile view snippet</div>\" }";
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final String view = extensionV5.getElasticProfileView(PLUGIN_ID);

        assertThat(view, is("<div>This is profile view snippet</div>"));

        assertExtensionRequest("5.0", REQUEST_GET_PROFILE_VIEW, null);
    }

    @Test
    public void shouldValidateProfile() {
        String responseBody = "[{\"message\":\"Url must not be blank.\",\"key\":\"Url\"},{\"message\":\"SearchBase must not be blank.\",\"key\":\"SearchBase\"}]";
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final ValidationResult result = extensionV5.validateElasticProfile(PLUGIN_ID, Collections.emptyMap());

        assertThat(result.isSuccessful(), is(false));
        assertThat(result.getErrors(), containsInAnyOrder(
                new ValidationError("Url", "Url must not be blank."),
                new ValidationError("SearchBase", "SearchBase must not be blank.")
        ));

        assertExtensionRequest("5.0", REQUEST_VALIDATE_PROFILE, "{}");
    }


    @Test
    public void shouldGetClusterProfileMetadata() {
        String responseBody = "[{\"key\":\"Username\",\"metadata\":{\"required\":true,\"secure\":false}},{\"key\":\"Password\",\"metadata\":{\"required\":true,\"secure\":true}}]";
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final List<PluginConfiguration> metadata = extensionV5.getClusterProfileMetadata(PLUGIN_ID);

        assertThat(metadata, hasSize(2));
        assertThat(metadata, containsInAnyOrder(
                new PluginConfiguration("Username", new Metadata(true, false)),
                new PluginConfiguration("Password", new Metadata(true, true))
        ));

        assertExtensionRequest("5.0", REQUEST_GET_CLUSTER_PROFILE_METADATA, null);
    }

    @Test
    public void shouldGetClusterProfileView() {
        String responseBody = "{ \"template\": \"<div>This is profile view snippet</div>\" }";
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final String view = extensionV5.getClusterProfileView(PLUGIN_ID);

        assertThat(view, is("<div>This is profile view snippet</div>"));

        assertExtensionRequest("5.0", REQUEST_GET_CLUSTER_PROFILE_VIEW, null);
    }

    @Test
    public void shouldValidateClusterProfile() {
        String responseBody = "[{\"message\":\"Url must not be blank.\",\"key\":\"Url\"},{\"message\":\"SearchBase must not be blank.\",\"key\":\"SearchBase\"}]";
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final ValidationResult result = extensionV5.validateClusterProfile(PLUGIN_ID, Collections.emptyMap());

        assertThat(result.isSuccessful(), is(false));
        assertThat(result.getErrors(), containsInAnyOrder(
                new ValidationError("Url", "Url must not be blank."),
                new ValidationError("SearchBase", "SearchBase must not be blank.")
        ));

        assertExtensionRequest("5.0", REQUEST_VALIDATE_CLUSTER_PROFILE, "{}");
    }

    @Test
    public void shouldMakeCreateAgentCall() {
        final Map<String, String> profile = Collections.singletonMap("ServerURL", "https://example.com/go");
        final JobIdentifier jobIdentifier = new JobIdentifier("up42", 2, "Test", "up42_stage", "10", "up42_job");
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(null));

        extensionV5.createAgent(PLUGIN_ID, "auto-registration-key", "test-env", profile, jobIdentifier);

        String expectedRequestBody = "{\n" +
                "  \"auto_register_key\": \"auto-registration-key\",\n" +
                "  \"properties\": {\n" +
                "    \"ServerURL\": \"https://example.com/go\"\n" +
                "  },\n" +
                "  \"environment\": \"test-env\",\n" +
                "  \"job_identifier\": {\n" +
                "    \"pipeline_name\": \"up42\",\n" +
                "    \"pipeline_label\": \"Test\",\n" +
                "    \"pipeline_counter\": 2,\n" +
                "    \"stage_name\": \"up42_stage\",\n" +
                "    \"stage_counter\": \"10\",\n" +
                "    \"job_name\": \"up42_job\",\n" +
                "    \"job_id\": -1\n" +
                "  }\n" +
                "}";
        assertExtensionRequest("5.0", REQUEST_CREATE_AGENT, expectedRequestBody);
    }

    @Test
    public void shouldMakeJobCompletionCall() {
        final String elasticAgentId = "ea1";
        final JobIdentifier jobIdentifier = new JobIdentifier("up42", 2, "Test", "up42_stage", "10", "up42_job");
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(null));

        extensionV5.jobCompletion(PLUGIN_ID, elasticAgentId, jobIdentifier);

        String expectedRequestBody = "{\n" +
                "  \"elastic_agent_id\": \"ea1\",\n" +
                "  \"job_identifier\": {\n" +
                "    \"pipeline_name\": \"up42\",\n" +
                "    \"pipeline_label\": \"Test\",\n" +
                "    \"pipeline_counter\": 2,\n" +
                "    \"stage_name\": \"up42_stage\",\n" +
                "    \"stage_counter\": \"10\",\n" +
                "    \"job_name\": \"up42_job\",\n" +
                "    \"job_id\": -1\n" +
                "  }\n" +
                "}";

        assertExtensionRequest("5.0", REQUEST_JOB_COMPLETION, expectedRequestBody);
    }

    @Test
    public void shouldSendServerPing() {
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(null));

        extensionV5.serverPing(PLUGIN_ID);

        assertExtensionRequest("5.0", REQUEST_SERVER_PING, null);
    }

    @Test
    public void shouldMakeShouldAssignWorkCall() {
        final Map<String, String> profile = Collections.singletonMap("ServerURL", "https://example.com/go");
        final AgentMetadata agentMetadata = new AgentMetadata("foo-agent-id", "Idle", "Idle", "Enabled");
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success("true"));

        final boolean shouldAssignWork = extensionV5.shouldAssignWork(PLUGIN_ID, agentMetadata, "test-env", profile, new JobIdentifier());

        assertTrue(shouldAssignWork);

        String expectedRequestBody = "{\n" +
                "  \"properties\": {\n" +
                "    \"ServerURL\": \"https://example.com/go\"\n" +
                "  },\n" +
                "  \"environment\": \"test-env\",\n" +
                "  \"agent\": {\n" +
                "    \"agent_id\": \"foo-agent-id\",\n" +
                "    \"agent_state\": \"Idle\",\n" +
                "    \"build_state\": \"Idle\",\n" +
                "    \"config_state\": \"Enabled\"\n" +
                "  },\n" +
                "  \"job_identifier\": {}\n" +
                "}";

        assertExtensionRequest("5.0", REQUEST_SHOULD_ASSIGN_WORK, expectedRequestBody);
    }

    @Test
    public void shouldGetStatusReport() {
        final String responseBody = "{\"view\":\"<div>This is a status report snippet.</div>\"}";
        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        final String statusReportView = extensionV5.getPluginStatusReport(PLUGIN_ID);

        assertThat(statusReportView, is("<div>This is a status report snippet.</div>"));
        assertExtensionRequest("5.0", REQUEST_STATUS_REPORT, null);
    }

    @Test
    public void shouldGetAgentStatusReport() {
        final String responseBody = "{\"view\":\"<div>This is a status report snippet.</div>\"}";
        final JobIdentifier jobIdentifier = new JobIdentifier("up42", 2, "Test", "up42_stage", "10", "up42_job");

        when(pluginManager.submitTo(eq(PLUGIN_ID), eq(ELASTIC_AGENT_EXTENSION), requestArgumentCaptor.capture())).thenReturn(DefaultGoPluginApiResponse.success(responseBody));

        extensionV5.getAgentStatusReport(PLUGIN_ID, jobIdentifier, "GoCD193659b3b930480287b898eeef0ade37");

        final String requestBody = "{\n" +
                "  \"job_identifier\": {\n" +
                "    \"pipeline_name\": \"up42\",\n" +
                "    \"pipeline_label\": \"Test\",\n" +
                "    \"pipeline_counter\": 2,\n" +
                "    \"stage_name\": \"up42_stage\",\n" +
                "    \"stage_counter\": \"10\",\n" +
                "    \"job_name\": \"up42_job\",\n" +
                "    \"job_id\": -1\n" +
                "  },\n" +
                "  \"elastic_agent_id\": \"GoCD193659b3b930480287b898eeef0ade37\"\n" +
                "}";

        assertExtensionRequest("5.0", REQUEST_AGENT_STATUS_REPORT, requestBody);
    }

    @Test
    public void allRequestMustHaveRequestPrefix() {
        assertThat(REQUEST_PREFIX, is("cd.go.elastic-agent"));

        assertThat(REQUEST_CREATE_AGENT, Matchers.startsWith(REQUEST_PREFIX));
        assertThat(REQUEST_SERVER_PING, Matchers.startsWith(REQUEST_PREFIX));
        assertThat(REQUEST_SHOULD_ASSIGN_WORK, Matchers.startsWith(REQUEST_PREFIX));

        assertThat(REQUEST_GET_PROFILE_METADATA, Matchers.startsWith(REQUEST_PREFIX));
        assertThat(REQUEST_GET_PROFILE_VIEW, Matchers.startsWith(REQUEST_PREFIX));
        assertThat(REQUEST_VALIDATE_PROFILE, Matchers.startsWith(REQUEST_PREFIX));
        assertThat(REQUEST_GET_PLUGIN_SETTINGS_ICON, Matchers.startsWith(REQUEST_PREFIX));
    }

    private void assertExtensionRequest(String extensionVersion, String requestName, String requestBody) {
        final GoPluginApiRequest request = requestArgumentCaptor.getValue();
        Assert.assertThat(request.requestName(), Matchers.is(requestName));
        Assert.assertThat(request.extensionVersion(), Matchers.is(extensionVersion));
        Assert.assertThat(request.extension(), Matchers.is(PluginConstants.ELASTIC_AGENT_EXTENSION));
        assertThatJson(requestBody).isEqualTo(request.requestBody());
    }
}

