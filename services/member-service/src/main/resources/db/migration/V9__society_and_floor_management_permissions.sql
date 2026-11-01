INSERT INTO access_permissions(code, description) VALUES
 ('SOCIETY_UPDATE','Update society profile'),
 ('FLOOR_CREATE','Create floors'),
 ('FLOOR_UPDATE','Change floor numbers')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code, scope)
SELECT r.code, p.code, 'SOCIETY'
FROM access_roles r
CROSS JOIN access_permissions p
WHERE r.code IN ('ADMIN', 'SOCIETY_ADMIN')
  AND p.code IN ('SOCIETY_UPDATE', 'FLOOR_CREATE', 'FLOOR_UPDATE')
ON CONFLICT (role_code, permission_code) DO NOTHING;
