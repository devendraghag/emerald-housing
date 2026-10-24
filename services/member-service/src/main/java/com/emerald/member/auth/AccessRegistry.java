package com.emerald.member.auth;

import com.emerald.member.api.ApiFailure;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Database-backed grants. Callers performing changes hold the shared transaction lock. */
@Service
public class AccessRegistry {
    private final JdbcTemplate db;
    public AccessRegistry(JdbcTemplate db) { this.db = db; }
    public void lock() { db.queryForObject("SELECT id FROM access_control_lock WHERE id=1 FOR UPDATE", Integer.class); }
    public Set<String> roles(UUID account) {
        return new TreeSet<>(db.queryForList("SELECT r.code FROM access_roles r JOIN account_roles ar ON ar.role_code=r.code WHERE ar.account_id=? AND r.enabled", String.class, account));
    }
    public Set<String> assignedRoles(UUID account) {
        return new TreeSet<>(db.queryForList("SELECT role_code FROM account_roles WHERE account_id=?",String.class,account));
    }
    public Set<String> grants(UUID account) {
        return new TreeSet<>(db.queryForList("SELECT DISTINCT rp.permission_code || ':' || rp.scope FROM role_permissions rp JOIN access_roles r ON r.code=rp.role_code JOIN account_roles ar ON ar.role_code=r.code WHERE ar.account_id=? AND r.enabled", String.class, account));
    }
    public void validateRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty() || roles.size()>20) throw new ApiFailure(400,"INVALID_ROLES","Assign between 1 and 20 enabled roles");
        for (String role: roles) if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM access_roles WHERE code=? AND enabled)",Boolean.class,role)))
            throw new ApiFailure(400,"INVALID_ROLE","Unknown or disabled role: " + role);
    }
    public boolean requiresPerson(Set<String> roles) {
        return roles.stream().anyMatch(role->Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM role_permissions WHERE role_code=? AND scope IN ('SELF','OWN_FLATS'))",Boolean.class,role)));
    }
    public void assign(UUID account, Set<String> roles) {
        validateRoles(roles);
        db.update("DELETE FROM account_roles WHERE account_id=?",account);
        roles.stream().sorted().forEach(role -> db.update("INSERT INTO account_roles VALUES (?,?)",account,role));
    }
    public void requireAdministrator() {
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM admin_users a JOIN account_roles ar ON ar.account_id=a.id JOIN access_roles r ON r.code=ar.role_code WHERE a.enabled AND a.society_code='EMERALD' AND r.enabled AND r.code IN ('ADMIN','SOCIETY_ADMIN') AND (a.member_id IS NULL OR EXISTS(SELECT 1 FROM members m WHERE m.id=a.member_id AND m.status='ACTIVE')))",Boolean.class)))
            throw new ApiFailure(409,"LAST_ADMINISTRATOR","At least one active administrator must remain");
    }
    public JdbcTemplate database() { return db; }
}
