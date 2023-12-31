/*
 * Copyright (c) 2023 OceanBase.
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
package com.oceanbase.odc.config;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;

import com.oceanbase.odc.common.i18n.I18n;
import com.oceanbase.odc.core.shared.constant.AuditEventAction;
import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.constant.ResourceType;
import com.oceanbase.odc.core.shared.exception.UnsupportedException;
import com.oceanbase.odc.service.collaboration.project.ProjectService;
import com.oceanbase.odc.service.iam.UserService;
import com.oceanbase.odc.service.regulation.approval.ApprovalFlowConfigService;
import com.oceanbase.odc.service.regulation.risklevel.RiskLevelService;
import com.oceanbase.odc.service.regulation.risklevel.model.RiskLevel;

import lombok.extern.slf4j.Slf4j;

/**
 * Hook configuration
 * 
 * @author yizhou.xw
 * @version : HookConfiguration.java, v 0.1 2022-05-19 16:51， move from {@link BeanConfiguration}
 */
@Slf4j
@Configuration
public class HookConfiguration {

    @Autowired
    private UserService userService;

    @Autowired
    private ApprovalFlowConfigService approvalFlowConfigService;

    @Autowired
    private RiskLevelService riskLevelService;

    @Autowired
    private ProjectService projectService;

    @PostConstruct
    public void init() {
        userService.addPostUserDeleteHook(event -> {
            Long userId = event.getUserId();
            projectService.deleteUserRelatedProjectRoles(userId);
        });
        log.info("PostUserDeleteHook added");

        approvalFlowConfigService.addPreApprovalFlowConfigDeleteHook(event -> {
            approvalFlowConfigUsageCheck(event.getId());
        });
        log.info("PreApprovalFlowConfigDeleteHook added");
    }

    private void approvalFlowConfigUsageCheck(Long flowConfigId) {
        List<RiskLevel> relatedRiskLevels = riskLevelService.listRelatedRiskLevelByFlowConfigId(flowConfigId);
        if (relatedRiskLevels.size() > 0) {
            String names = relatedRiskLevels.stream()
                    .map(e -> I18n.translate(e.getName().substring(2, e.getName().length() - 1), null, e.getName(),
                            LocaleContextHolder.getLocale()))
                    .collect(Collectors.joining(", "));
            String errorMessage = String.format(
                    "Approval flow config id=%s cannot be %s because it has been referenced to following risk level: {%s}",
                    flowConfigId, AuditEventAction.DELETE_FLOW_CONFIG, names);
            throw new UnsupportedException(ErrorCodes.CannotOperateDueReference,
                    new Object[] {AuditEventAction.DELETE_FLOW_CONFIG.getLocalizedMessage(),
                            ResourceType.ODC_APPROVAL_FLOW_CONFIG.getLocalizedMessage(), "name", names,
                            ResourceType.ODC_RISK_LEVEL.getLocalizedMessage()},
                    errorMessage);
        }
    }

}
