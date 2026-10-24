ALTER TABLE admin_users DROP CONSTRAINT admin_users_role_check;
CREATE TABLE access_roles (code VARCHAR(32) PRIMARY KEY, description VARCHAR(200) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE, system_role BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0);
CREATE TABLE access_permissions (code VARCHAR(64) PRIMARY KEY, description VARCHAR(200) NOT NULL);
CREATE TABLE role_permissions (role_code VARCHAR(32) REFERENCES access_roles(code), permission_code VARCHAR(64) REFERENCES access_permissions(code), scope VARCHAR(12) NOT NULL CHECK(scope IN ('SOCIETY','OWN_FLATS','SELF')), PRIMARY KEY(role_code,permission_code,scope));
CREATE TABLE account_roles (account_id UUID REFERENCES admin_users(id) ON DELETE CASCADE, role_code VARCHAR(32) REFERENCES access_roles(code), PRIMARY KEY(account_id,role_code));
ALTER TABLE role_permissions ADD CONSTRAINT uq_role_permission UNIQUE(role_code,permission_code);
CREATE TABLE access_control_lock (id INTEGER PRIMARY KEY);
INSERT INTO access_control_lock VALUES (1);
INSERT INTO access_roles(code,description,system_role) VALUES
 ('ADMIN','Administrator',TRUE),('SOCIETY_ADMIN','Society administrator',TRUE),('COMMITTEE','Committee member',FALSE),('RESIDENT','Resident',FALSE),('FLAT_OWNER','Registered flat owner',FALSE),('FAMILY_MEMBER','Family member',FALSE),('TENANT','Tenant',FALSE);
INSERT INTO access_permissions(code,description) VALUES
 ('USER_VIEW','View login accounts'),('USER_CREATE','Create login accounts'),('USER_UPDATE','Update account links and status'),('USER_ASSIGN_ROLE','Assign roles to accounts'),
 ('ROLE_VIEW','View roles'),('ROLE_CREATE','Create roles'),('ROLE_UPDATE','Update roles'),('ROLE_ASSIGN_PERMISSION','Assign permissions to roles'),('PERMISSION_VIEW','View permissions'),('PERMISSION_MANAGE','Register permission descriptions'),
 ('OWNER_REGISTER','Register owners and assign flat relationships'),('OWNER_END_OWNERSHIP','End flat relationships'),
 ('MEMBER_VIEW','View person records'),('MEMBER_CREATE','Register person records'),('MEMBER_UPDATE','Update person records'),('MEMBER_DEACTIVATE','Deactivate person records'),
 ('FLAT_VIEW','View flats'),('FLAT_CREATE','Create flats'),('FLAT_UPDATE','Update flats'),('FLAT_DEACTIVATE','Deactivate flats'),('SOCIETY_VIEW','View society and floors'),
 ('FAMILY_VIEW','View family records'),('FAMILY_ADD','Add family records'),('FAMILY_UPDATE','Update family records'),('FAMILY_DEACTIVATE','Deactivate family records'),('AUDIT_VIEW','View audit history');
INSERT INTO role_permissions SELECT r.code,p.code,'SOCIETY' FROM access_roles r CROSS JOIN access_permissions p WHERE r.code IN ('ADMIN','SOCIETY_ADMIN');
INSERT INTO role_permissions VALUES
 ('COMMITTEE','MEMBER_VIEW','SOCIETY'),('COMMITTEE','FLAT_VIEW','SOCIETY'),('COMMITTEE','SOCIETY_VIEW','SOCIETY');
INSERT INTO role_permissions SELECT r.code,p.code,CASE WHEN p.code='SOCIETY_VIEW' THEN 'SOCIETY' WHEN p.code='FLAT_VIEW' THEN 'OWN_FLATS' ELSE 'SELF' END FROM access_roles r CROSS JOIN access_permissions p WHERE r.code IN ('RESIDENT','FLAT_OWNER','FAMILY_MEMBER','TENANT') AND p.code IN ('MEMBER_VIEW','FLAT_VIEW','SOCIETY_VIEW');
INSERT INTO role_permissions SELECT 'FLAT_OWNER',p.code,'OWN_FLATS' FROM access_permissions p WHERE p.code LIKE 'FAMILY_%';
INSERT INTO role_permissions SELECT r.code,'FAMILY_VIEW','SELF' FROM access_roles r WHERE r.code IN ('RESIDENT','FAMILY_MEMBER','TENANT');
INSERT INTO account_roles SELECT id,role FROM admin_users;
ALTER TABLE admin_users ADD CONSTRAINT account_primary_role_fk FOREIGN KEY(role) REFERENCES access_roles(code);
CREATE TABLE family_members (
 id UUID PRIMARY KEY, owner_membership_id UUID NOT NULL REFERENCES flat_memberships(id), flat_id UUID NOT NULL,
 full_name VARCHAR(160) NOT NULL, relationship VARCHAR(40) NOT NULL, mobile_number VARCHAR(20), email VARCHAR(254), linked_member_id UUID REFERENCES members(id),
 active BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_family_flat ON family_members(flat_id,created_at,id);
CREATE INDEX ix_family_owner ON family_members(owner_membership_id);
CREATE INDEX ix_family_linked_person ON family_members(linked_member_id) WHERE active;
