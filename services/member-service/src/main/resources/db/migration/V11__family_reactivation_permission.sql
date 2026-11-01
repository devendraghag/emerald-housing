INSERT INTO access_permissions(code, description)
VALUES ('FAMILY_REACTIVATE', 'Restore removed family records')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code, scope)
SELECT 'FLAT_OWNER', 'FAMILY_REACTIVATE', 'OWN_FLATS'
WHERE EXISTS (SELECT 1 FROM access_roles WHERE code = 'FLAT_OWNER')
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code, scope)
SELECT r.code, 'FAMILY_REACTIVATE', 'SOCIETY'
FROM access_roles r
WHERE r.code IN ('ADMIN', 'SOCIETY_ADMIN')
ON CONFLICT (role_code, permission_code) DO NOTHING;
