INSERT INTO role_permissions(role_code, permission_code, scope)
SELECT code, 'MEMBER_UPDATE', 'SELF'
FROM access_roles
WHERE code IN ('RESIDENT', 'FLAT_OWNER', 'FAMILY_MEMBER', 'TENANT')
ON CONFLICT (role_code, permission_code) DO NOTHING;
