/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.indices.template.put;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexTemplateV2;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetaDataIndexTemplateService;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;

public class TransportPutIndexTemplateV2Action
    extends TransportMasterNodeAction<PutIndexTemplateV2Action.Request, AcknowledgedResponse> {

    private final MetaDataIndexTemplateService indexTemplateService;

    @Inject
    public TransportPutIndexTemplateV2Action(TransportService transportService, ClusterService clusterService,
                                               ThreadPool threadPool, MetaDataIndexTemplateService indexTemplateService,
                                               ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver) {
        super(PutIndexTemplateV2Action.NAME, transportService, clusterService, threadPool, actionFilters,
            PutIndexTemplateV2Action.Request::new, indexNameExpressionResolver);
        this.indexTemplateService = indexTemplateService;
    }

    @Override
    protected String executor() {
        // we go async right away
        return ThreadPool.Names.SAME;
    }

    @Override
    protected AcknowledgedResponse read(StreamInput in) throws IOException {
        return new AcknowledgedResponse(in);
    }

    @Override
    protected ClusterBlockException checkBlock(PutIndexTemplateV2Action.Request request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected void masterOperation(Task task, final PutIndexTemplateV2Action.Request request, final ClusterState state,
                                   final ActionListener<AcknowledgedResponse> listener) {
        IndexTemplateV2 indexTemplate = request.indexTemplate();
        Template template = indexTemplate.template();
        // Normalize the index settings if necessary
        if (template.settings() != null) {
            Settings.Builder settings = Settings.builder().put(template.settings()).normalizePrefix(IndexMetaData.INDEX_SETTING_PREFIX);
            template = new Template(settings.build(), template.mappings(), template.aliases());
            indexTemplate = new IndexTemplateV2(indexTemplate.indexPatterns(), template, indexTemplate.composedOf(),
                indexTemplate.priority(), indexTemplate.version(), indexTemplate.metadata());
        }
        indexTemplateService.putIndexTemplateV2(request.cause(), request.create(), request.name(), request.masterNodeTimeout(),
            indexTemplate, listener);
    }
}
