INSERT INTO access_permissions(code, description)
VALUES ('USER_RESET_PASSWORD', 'Reset account passwords')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code, scope)
SELECT r.code, 'USER_RESET_PASSWORD', 'SOCIETY'
FROM access_roles r
WHERE r.code IN ('ADMIN', 'SOCIETY_ADMIN')
ON CONFLICT (role_code, permission_code) DO NOTHING;
