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
package com.oceanbase.odc.service.collaboration.project;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import com.oceanbase.odc.core.authority.model.DefaultSecurityResource;
import com.oceanbase.odc.core.authority.permission.Permission;
import com.oceanbase.odc.core.authority.permission.ResourceRoleBasedPermission;
import com.oceanbase.odc.core.authority.util.Authenticated;
import com.oceanbase.odc.core.authority.util.PreAuthenticate;
import com.oceanbase.odc.core.authority.util.SkipAuthorize;
import com.oceanbase.odc.core.shared.PreConditions;
import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.constant.ResourceRoleName;
import com.oceanbase.odc.core.shared.constant.ResourceType;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.core.shared.exception.NotFoundException;
import com.oceanbase.odc.metadb.collaboration.ProjectEntity;
import com.oceanbase.odc.metadb.collaboration.ProjectRepository;
import com.oceanbase.odc.metadb.collaboration.ProjectSpecs;
import com.oceanbase.odc.metadb.connection.DatabaseRepository;
import com.oceanbase.odc.metadb.iam.UserEntity;
import com.oceanbase.odc.metadb.iam.UserRepository;
import com.oceanbase.odc.service.collaboration.project.model.Project;
import com.oceanbase.odc.service.collaboration.project.model.Project.ProjectMember;
import com.oceanbase.odc.service.collaboration.project.model.QueryProjectParams;
import com.oceanbase.odc.service.collaboration.project.model.SetArchivedReq;
import com.oceanbase.odc.service.common.model.InnerUser;
import com.oceanbase.odc.service.iam.HorizontalDataPermissionValidator;
import com.oceanbase.odc.service.iam.ResourceRoleService;
import com.oceanbase.odc.service.iam.UserOrganizationService;
import com.oceanbase.odc.service.iam.UserPermissionService;
import com.oceanbase.odc.service.iam.auth.AuthenticationFacade;
import com.oceanbase.odc.service.iam.auth.AuthorizationFacade;
import com.oceanbase.odc.service.iam.model.UserResourceRole;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author: Lebie
 * @Date: 2023/4/20 17:31
 * @Description: []
 */
@Validated
@Slf4j
@Service
@Authenticated
public class ProjectService {
    @Autowired
    private ProjectRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationFacade authenticationFacade;

    @Autowired
    private HorizontalDataPermissionValidator permissionValidator;

    @Autowired
    private UserOrganizationService userOrganizationService;

    @Autowired
    private ResourceRoleService resourceRoleService;

    @Autowired
    private AuthorizationFacade authorizationFacade;

    @Autowired
    private UserPermissionService userPermissionService;

    @Autowired
    private DatabaseRepository databaseRepository;


    private ProjectMapper projectMapper = ProjectMapper.INSTANCE;

    @PreAuthenticate(actions = "create", resourceType = "ODC_PROJECT", isForAll = true)
    @Transactional(rollbackFor = Exception.class)
    public Project create(@NotNull @Valid Project project) {
        preCheck(project);

        /**
         * save project entity
         */
        project.setOrganizationId(currentOrganizationId());
        project.setCreator(currentInnerUser());
        project.setLastModifier(currentInnerUser());
        project.setArchived(false);
        project.setBuiltin(false);
        ProjectEntity saved = repository.save(modelToEntity(project));

        /**
         * save project members as resource roles
         */
        List<UserResourceRole> userResourceRoles = resourceRoleService.saveAll(
                project.getMembers().stream()
                        .map(member -> member2UserResourceRole(member, saved.getId()))
                        .collect(Collectors.toList()));

        grantPermissions(userResourceRoles, currentOrganizationId());

        return entityToModel(saved, userResourceRoles);
    }

    @PreAuthenticate(hasAnyResourceRole = {"OWNER", "DBA", "DEVELOPER"}, resourceType = "ODC_PROJECT",
            indexOfIdParam = 0)
    @Transactional(rollbackFor = Exception.class)
    public Project detail(@NotNull Long id) {
        ProjectEntity entity = repository.findByIdAndOrganizationId(id, currentOrganizationId())
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_PROJECT, "id", id));
        List<UserResourceRole> userResourceRoles = resourceRoleService.listByResourceId(entity.getId());
        return entityToModel(entity, userResourceRoles);
    }

    @SkipAuthorize("odc internal usage")
    public ProjectEntity nullSafeGet(@NotNull Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException(ResourceType.ODC_PROJECT, "id", id));
    }

    @PreAuthenticate(hasAnyResourceRole = {"OWNER"}, resourceType = "ODC_PROJECT", indexOfIdParam = 0)
    @Transactional(rollbackFor = Exception.class)
    public Project update(@NotNull Long id, @NotNull Project project) {

        ProjectEntity previous = repository.findByIdAndOrganizationId(id, currentOrganizationId())
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_PROJECT, "id", id));
        /**
         * not allowed to update a built-in project or an archived project
         */
        if (previous.getBuiltin() || previous.getArchived()) {
            return entityToModel(previous, resourceRoleService.listByResourceId(previous.getId()));
        }

        previous.setLastModifierId(authenticationFacade.currentUserId());
        previous.setDescription(project.getDescription());
        previous.setName(project.getName());

        /**
         * save project
         */
        ProjectEntity saved = repository.save(previous);
        return entityToModel(saved, resourceRoleService.listByResourceId(saved.getId()));
    }

    @PreAuthenticate(hasAnyResourceRole = {"OWNER"}, resourceType = "ODC_PROJECT", indexOfIdParam = 0)
    @Transactional(rollbackFor = Exception.class)
    public Project setArchived(Long id, @NotNull SetArchivedReq req) {
        ProjectEntity previous = repository.findByIdAndOrganizationId(id, currentOrganizationId())
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_PROJECT, "id", id));
        if (!req.getArchived()) {
            throw new BadRequestException("currently not allowed to recover projects");
        }
        previous.setArchived(req.getArchived());
        ProjectEntity saved = repository.save(previous);
        databaseRepository.setProjectIdToNull(id);
        return entityToModel(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("Internal usage")
    public Page<Project> list(@Valid QueryProjectParams params, @NotNull Pageable pageable) {
        params.setUserId(currentUserId());
        Page<ProjectEntity> projectEntities = innerList(params, pageable, UserResourceRole::isProjectMember);
        return projectEntities.map(project -> {
            List<UserResourceRole> members = resourceRoleService.listByResourceId(project.getId());
            return entityToModel(project, members);
        });
    }

    private Page<ProjectEntity> innerList(@Valid QueryProjectParams params, @NotNull Pageable pageable,
            @NotNull Predicate<UserResourceRole> predicate) {
        List<UserResourceRole> userResourceRoles =
                resourceRoleService.listByOrganizationIdAndUserId(currentOrganizationId(),
                        Objects.isNull(params.getUserId()) ? currentUserId() : params.getUserId());
        List<Long> joinedProjectIds =
                userResourceRoles.stream().filter(predicate)
                        .map(UserResourceRole::getResourceId).distinct().collect(Collectors.toList());

        Specification<ProjectEntity> specs =
                ProjectSpecs.nameLike(params.getName())
                        .and(ProjectSpecs.archivedEqual(params.getArchived()))
                        .and(ProjectSpecs.organizationIdEqual(currentOrganizationId()))
                        .and(ProjectSpecs.idIn(joinedProjectIds));
        return repository.findAll(specs, pageable);
    }

    @PreAuthenticate(hasAnyResourceRole = {"OWNER"}, resourceType = "ODC_PROJECT", indexOfIdParam = 0)
    @Transactional(rollbackFor = Exception.class)
    public Project createProjectMembers(@NonNull Long id, @NotEmpty List<ProjectMember> members) {
        ProjectEntity project = repository.findByIdAndOrganizationId(id, currentOrganizationId())
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_PROJECT, "id", id));
        checkMembersOrganization(members);
        List<UserResourceRole> userResourceRoles = resourceRoleService.saveAll(
                members.stream()
                        .map(member -> member2UserResourceRole(member, project.getId()))
                        .collect(Collectors.toList()));
        grantPermissions(userResourceRoles, currentOrganizationId());
        return entityToModel(project, userResourceRoles);
    }

    @SkipAuthorize("permission check inside")
    public Project createMembersSkipPermissionCheck(@NonNull Long projectId, @NonNull Long organizationId,
            @NotEmpty List<ProjectMember> members) {
        ProjectEntity project = repository.findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_PROJECT, "id", projectId));

        members.forEach(m -> PreConditions.validArgumentState(
                userOrganizationService.userBelongsToOrganization(m.getId(), organizationId),
                ErrorCodes.UnauthorizedDataAccess, null, null));

        List<UserResourceRole> userResourceRoles = resourceRoleService.saveAll(
                members.stream()
                        .map(member -> member2UserResourceRole(member, projectId))
                        .collect(Collectors.toList()));
        grantPermissions(userResourceRoles, organizationId);

        return entityToModel(project, userResourceRoles);
    }


    @PreAuthenticate(hasAnyResourceRole = {"OWNER"}, resourceType = "ODC_PROJECT", indexOfIdParam = 0)
    public boolean deleteProjectMember(@NonNull Long projectId, @NonNull Long userId) {
        if (currentUserId().longValue() == userId.longValue()) {
            throw new BadRequestException("Not allowed to delete yourself");
        }
        Set<Long> memberIds = resourceRoleService.listByResourceIdIn(Collections.singleton(projectId)).stream()
                .filter(Objects::nonNull)
                .map(UserResourceRole::getUserId).collect(
                        Collectors.toSet());
        if (!memberIds.contains(userId)) {
            throw new BadRequestException("User not belongs to this project");
        }
        resourceRoleService.deleteByUserIdAndResourceIdIn(userId, Collections.singleton(projectId));
        checkMemberRoles(detail(projectId).getMembers());
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal usage")
    public boolean deleteUserRelatedProjectRoles(@NonNull Long userId) {
        resourceRoleService.deleteByUserId(userId);
        return true;
    }


    @PreAuthenticate(hasAnyResourceRole = {"OWNER"}, resourceType = "ODC_PROJECT", indexOfIdParam = 0)
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProjectMember(@NonNull Long projectId, @NonNull Long userId,
            @NonNull List<ProjectMember> members) {
        ProjectEntity project = repository.findByIdAndOrganizationId(projectId, currentOrganizationId())
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_PROJECT, "id", projectId));
        Map<Long, List<UserResourceRole>> userId2ResourceRoles =
                resourceRoleService.listByResourceId(project.getId()).stream()
                        .collect(Collectors.groupingBy(UserResourceRole::getUserId));
        if (CollectionUtils.isEmpty(userId2ResourceRoles.keySet())) {
            return false;
        }
        if (!userId2ResourceRoles.keySet().contains(userId)) {
            throw new BadRequestException("User not belongs to this project");
        }
        resourceRoleService.deleteByResourceIdAndUserId(projectId, userId);
        if (CollectionUtils.isEmpty(members)) {
            return true;
        }
        List<UserResourceRole> updated = resourceRoleService.saveAll(
                members.stream().map(member -> {
                    member.setId(userId);
                    return member2UserResourceRole(member, project.getId());
                }).collect(Collectors.toList()));

        checkMemberRoles(detail(projectId).getMembers());

        grantPermissions(updated, currentOrganizationId());
        return true;
    }


    @SkipAuthorize("internal usage")
    public Map<Long, List<Project>> mapByIdIn(Set<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        return repository.findAllById(ids).stream().map(projectMapper::entityToModel)
                .collect(Collectors.groupingBy(Project::getId));
    }

    @SkipAuthorize("permission check inside")
    public boolean checkPermission(Long projectId, List<ResourceRoleName> resourceRoles) {
        if (Objects.isNull(projectId)) {
            return true;
        }
        if (CollectionUtils.isEmpty(resourceRoles)) {
            return false;
        }
        Permission permission = new ResourceRoleBasedPermission(
                new DefaultSecurityResource(projectId.toString(), "ODC_PROJECT"), resourceRoles);
        if (!authorizationFacade.isImpliesPermissions(authenticationFacade.currentUser(),
                Collections.singletonList(permission))) {
            return false;
        }
        return true;
    }

    @SkipAuthorize("permission check inside")
    public Map<Long, Set<ResourceRoleName>> getProjectId2ResourceRoleNames() {
        List<UserResourceRole> userResourceRoles =
                resourceRoleService.listByOrganizationIdAndUserId(currentOrganizationId(),
                        currentUserId());
        Map<Long, Set<ResourceRoleName>> projectId2Members =
                userResourceRoles.stream().collect(Collectors.groupingBy(UserResourceRole::getResourceId,
                        Collectors.mapping(UserResourceRole::getResourceRole, Collectors.toSet())));
        return projectId2Members;
    }

    @SkipAuthorize("internal usage")
    public Set<Long> getMemberProjectIds(Long userId) {
        return resourceRoleService.listByUserId(userId).stream().map(UserResourceRole::getResourceId)
                .collect(Collectors.toSet());
    }

    private void grantPermissions(List<UserResourceRole> userResourceRoles, Long organizationId) {
        if (CollectionUtils.isEmpty(userResourceRoles)) {
            return;
        }
        /**
         * grant create datasource permission to DBAs and Owners
         */
        userResourceRoles.stream()
                .filter(userResourceRole -> userResourceRole.getResourceRole() == ResourceRoleName.OWNER
                        || userResourceRole.getResourceRole() == ResourceRoleName.DBA)
                .forEach(userResourceRole -> userPermissionService.bindUserAndCreateDataSourcePermission(
                        userResourceRole.getUserId(), organizationId));
    }

    private Project entityToModel(ProjectEntity entity, List<UserResourceRole> userResourceRoles) {
        Project project = projectMapper.entityToModel(entity);
        project.setCreator(currentInnerUser());
        project.setLastModifier(currentInnerUser());
        project.setMembers(userResourceRoles.stream().map(this::fromUserResourceRole).filter(Objects::nonNull)
                .collect(Collectors.toList()));
        project.setCurrentUserResourceRoles(
                getProjectId2ResourceRoleNames().getOrDefault(project.getId(), Collections.EMPTY_SET));
        return project;
    }

    private Project entityToModel(ProjectEntity entity) {
        Project project = projectMapper.entityToModel(entity);
        project.setCreator(currentInnerUser());
        project.setLastModifier(currentInnerUser());
        return project;
    }

    private ProjectMember fromUserResourceRole(UserResourceRole userResourceRole) {
        Optional<UserEntity> userOpt = userRepository.findById(userResourceRole.getUserId());
        if (!userOpt.isPresent()) {
            return null;
        }
        UserEntity user = userOpt.get();
        ProjectMember member = new ProjectMember();
        member.setRole(userResourceRole.getResourceRole());
        member.setId(userResourceRole.getUserId());
        member.setName(user.getName());
        member.setAccountName(user.getAccountName());
        return member;
    }

    private ProjectEntity modelToEntity(Project project) {
        return projectMapper.modelToEntity(project);
    }

    private boolean exists(Long organizationId, String name) {
        ProjectEntity entity = new ProjectEntity();
        entity.setOrganizationId(organizationId);
        entity.setName(name);
        return repository.exists(Example.of(entity));
    }

    private void checkNoDuplicateProject(Project project) {
        PreConditions.validNoDuplicated(ResourceType.ODC_PROJECT, "name", project.getName(),
                () -> exists(currentOrganizationId(), project.getName()));
    }

    private void checkMemberOrganization(@NonNull ProjectMember member) {
        PreConditions.validArgumentState(userOrganizationService.userBelongsToOrganization(member.getId(),
                authenticationFacade.currentOrganizationId()), ErrorCodes.UnauthorizedDataAccess, null, null);
    }

    private void checkMemberRoles(@NonNull List<ProjectMember> members) {
        PreConditions.validArgumentState(
                members.stream().anyMatch(member -> member.getRole() == ResourceRoleName.OWNER),
                ErrorCodes.BadArgument, null, "please assign one project owner at least");
        PreConditions.validArgumentState(
                members.stream().anyMatch(member -> member.getRole() == ResourceRoleName.DBA),
                ErrorCodes.BadArgument, null, "please assign one project dba at least");
    }

    /**
     * 1. check if duplicate project name exists<br/>
     * 2. check if all project members belong to current organization<br/>
     * 3. check if at least one project owner and at least one project dba has been assigned<br/>
     */
    private void preCheck(Project project) {
        checkNoDuplicateProject(project);
        checkMembersOrganization(project.getMembers());
        checkMemberRoles(project.getMembers());
    }

    private void checkMembersOrganization(@NonNull List<ProjectMember> members) {
        members.stream().forEach(member -> checkMemberOrganization(member));
    }

    private UserResourceRole member2UserResourceRole(ProjectMember member, Long resourceId) {
        UserResourceRole userResourceRole = new UserResourceRole();
        userResourceRole.setResourceId(resourceId);
        userResourceRole.setUserId(member.getId());
        userResourceRole.setResourceRole(member.getRole());
        userResourceRole.setResourceType(ResourceType.ODC_PROJECT);
        return userResourceRole;
    }

    private Long currentOrganizationId() {
        return authenticationFacade.currentOrganizationId();
    }

    private Long currentUserId() {
        return authenticationFacade.currentUserId();
    }

    private InnerUser currentInnerUser() {
        return new InnerUser(authenticationFacade.currentUser(), null);
    }
}
