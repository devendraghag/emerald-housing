INSERT INTO access_permissions(code, description)
VALUES ('FLAT_ACTIVATE', 'Reactivate flats')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code, scope)
SELECT code, 'FLAT_ACTIVATE', 'SOCIETY'
FROM access_roles
WHERE code IN ('ADMIN', 'SOCIETY_ADMIN')
ON CONFLICT (role_code, permission_code) DO NOTHING;
