package com.media.bus.auth.modules.auth.entity;

import java.io.Serializable;

/**
 * RolePermission 복합 기본키 식별자.
 * JPA @IdClass에서 사용합니다.
 * role_name + permission_name 조합이 PK입니다.
 */
public record RolePermissionId(String roleName, String permissionName) implements Serializable {}
