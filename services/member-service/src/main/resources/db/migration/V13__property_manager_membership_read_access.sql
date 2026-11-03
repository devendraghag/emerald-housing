INSERT INTO role_permissions (role_code, permission_code, scope)
SELECT 'PROPERTY_MANAGER', permission_code, 'SOCIETY'
FROM (VALUES ('MEMBER_VIEW'), ('FLAT_VIEW')) AS requested(permission_code)
WHERE EXISTS (
    SELECT 1 FROM access_roles WHERE code = 'PROPERTY_MANAGER'
)
  AND EXISTS (
    SELECT 1 FROM access_permissions WHERE code = requested.permission_code
)
ON CONFLICT (role_code, permission_code) DO NOTHING;
