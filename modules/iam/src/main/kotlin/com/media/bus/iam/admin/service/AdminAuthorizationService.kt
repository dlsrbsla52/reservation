package com.media.bus.iam.admin.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.iam.admin.dto.*
import com.media.bus.iam.auth.entity.MemberRoleEntity
import com.media.bus.iam.auth.entity.RolePermissionEntity
import com.media.bus.iam.auth.repository.MemberRoleRepository
import com.media.bus.iam.auth.repository.PermissionRepository
import com.media.bus.iam.auth.repository.RolePermissionRepository
import com.media.bus.iam.auth.repository.RoleRepository
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 어드민 권한 관리 비즈니스 로직 서비스
 *
 * 역할/권한 조회, 역할-권한 매핑 관리, 회원-역할 변경 기능을 제공한다.
 * 모든 메서드는 `ADMIN_MASTER` + `MANAGE` 권한이 필요하다 (컨트롤러에서 인가).
 */
@Service
class AdminAuthorizationService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val memberRoleRepository: MemberRoleRepository,
    private val memberRepository: MemberRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 전체 역할 목록을 조회한다. */
    @Transactional(readOnly = true)
    fun findAllRoles(): List<RoleResponse> =
        roleRepository.findAll().map { RoleResponse.of(it) }

    /** 역할 상세 정보를 조회한다 (연결된 권한 포함). */
    @Transactional(readOnly = true)
    fun findRoleDetail(roleId: UUID): RoleDetailResponse {
        val role = roleRepository.findById(roleId)
            ?: throw BaseException(AuthResult.ROLE_NOT_FOUND)

        val permissions = rolePermissionRepository.findByRoleId(roleId)
            .map { it.permission }

        return RoleDetailResponse.of(role, permissions)
    }

    /** 전체 권한 목록을 조회한다. */
    @Transactional(readOnly = true)
    fun findAllPermissions(): List<PermissionResponse> =
        permissionRepository.findAll().map { PermissionResponse.of(it) }

    /** 역할에 할당된 권한 목록을 조회한다. */
    @Transactional(readOnly = true)
    fun findPermissionsByRoleId(roleId: UUID): List<PermissionResponse> {
        roleRepository.findById(roleId)
            ?: throw BaseException(AuthResult.ROLE_NOT_FOUND)

        return rolePermissionRepository.findByRoleId(roleId)
            .map { PermissionResponse.of(it.permission) }
    }

    /** 역할에 권한을 할당한다. */
    @Transactional
    fun assignPermissionToRole(roleId: UUID, request: AssignPermissionRequest) {
        val role = roleRepository.findById(roleId)
            ?: throw BaseException(AuthResult.ROLE_NOT_FOUND)

        val permission = permissionRepository.findByName(request.permissionName)
            ?: throw BaseException(AuthResult.PERMISSION_NOT_FOUND)

        // 이미 할당된 권한인지 확인
        rolePermissionRepository.findByRoleIdAndPermissionName(roleId, request.permissionName)
            ?.let { throw BaseException(AuthResult.ROLE_PERMISSION_ALREADY_EXISTS) }

        RolePermissionEntity.of(role, permission)

        log.info(
            "[AdminAuthorizationService.assignPermissionToRole] 역할에 권한 할당 완료. roleId={}, permissionName={}",
            roleId, request.permissionName,
        )
    }

    /** 역할에서 권한을 해제한다. */
    @Transactional
    fun revokePermissionFromRole(roleId: UUID, permissionName: String) {
        roleRepository.findById(roleId)
            ?: throw BaseException(AuthResult.ROLE_NOT_FOUND)

        val rolePermission = rolePermissionRepository.findByRoleIdAndPermissionName(roleId, permissionName)
            ?: throw BaseException(AuthResult.ROLE_PERMISSION_NOT_FOUND)

        rolePermission.delete()

        log.info(
            "[AdminAuthorizationService.revokePermissionFromRole] 역할에서 권한 해제 완료. roleId={}, permissionName={}",
            roleId, permissionName,
        )
    }

    /** 회원의 역할 및 권한 정보를 조회한다. */
    @Transactional(readOnly = true)
    fun findMemberRole(memberId: UUID): MemberRoleResponse {
        val memberRole = memberRoleRepository.findByMemberId(memberId)
            ?: throw BaseException(AuthResult.MEMBER_ROLE_NOT_FOUND)

        val role = memberRole.role
        val permissions = rolePermissionRepository.findByRoleId(role.id.value)
            .map { it.permission }

        return MemberRoleResponse.of(memberId, role, permissions)
    }

    /** 회원의 역할을 변경한다. 기존 역할이 없으면 새로 할당한다. */
    @Transactional
    fun changeMemberRole(memberId: UUID, request: ChangeMemberRoleRequest) {
        val member = memberRepository.findById(memberId)
            ?: throw BaseException(AuthResult.MEMBER_ROLE_NOT_FOUND)

        val newRole = roleRepository.findByName(request.roleName)
            ?: throw BaseException(AuthResult.ROLE_NOT_FOUND)

        val existingMemberRole = memberRoleRepository.findByMemberId(memberId)

        if (existingMemberRole != null) {
            // 기존 역할이 있으면 변경
            existingMemberRole.role = newRole
        } else {
            // 역할이 없으면 새로 할당
            MemberRoleEntity.of(member, newRole)
        }

        log.info(
            "[AdminAuthorizationService.changeMemberRole] 회원 역할 변경 완료. memberId={}, roleName={}",
            memberId, request.roleName,
        )
    }
}
