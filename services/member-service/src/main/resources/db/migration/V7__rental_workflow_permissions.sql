INSERT INTO access_permissions(code, description) VALUES
 ('RENT_VIEW','View rent payment records'),('RENT_MANAGE','Record or update rent payments'),
 ('MAINTENANCE_VIEW','View maintenance requests'),('MAINTENANCE_CREATE','Submit a maintenance request'),('MAINTENANCE_MANAGE','Update or close maintenance requests'),
 ('LEASE_VIEW','View lease and renewal records'),('LEASE_MANAGE','Create leases and manage renewal or move-out workflows'),
 ('VIEWING_VIEW','View available viewing slots and bookings'),('VIEWING_BOOK','Book a property viewing'),('VIEWING_MANAGE','Manage property availability and viewing bookings');
INSERT INTO access_roles(code,description,system_role) VALUES ('PROPERTY_MANAGER','Property manager',TRUE);
INSERT INTO role_permissions SELECT r.code,p.code,'SOCIETY' FROM access_roles r CROSS JOIN access_permissions p
 WHERE r.code IN ('ADMIN','SOCIETY_ADMIN','PROPERTY_MANAGER') AND p.code IN ('RENT_VIEW','RENT_MANAGE','MAINTENANCE_VIEW','MAINTENANCE_CREATE','MAINTENANCE_MANAGE','LEASE_VIEW','LEASE_MANAGE','VIEWING_VIEW','VIEWING_BOOK','VIEWING_MANAGE');
INSERT INTO role_permissions VALUES ('PROPERTY_MANAGER','AUDIT_VIEW','SOCIETY');
INSERT INTO role_permissions SELECT r.code,p.code,'OWN_FLATS' FROM access_roles r CROSS JOIN access_permissions p
 WHERE r.code IN ('FLAT_OWNER') AND p.code IN ('RENT_VIEW','RENT_MANAGE','MAINTENANCE_VIEW','MAINTENANCE_MANAGE','LEASE_VIEW','LEASE_MANAGE','VIEWING_VIEW','VIEWING_MANAGE');
INSERT INTO role_permissions SELECT r.code,p.code,'OWN_FLATS' FROM access_roles r CROSS JOIN access_permissions p
 WHERE r.code IN ('TENANT') AND p.code IN ('RENT_VIEW','MAINTENANCE_VIEW','MAINTENANCE_CREATE','LEASE_VIEW','VIEWING_VIEW','VIEWING_BOOK');
