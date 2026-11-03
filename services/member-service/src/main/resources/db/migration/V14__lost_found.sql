CREATE TABLE lost_found_reports (
  id uuid PRIMARY KEY,
  reporter_id uuid NOT NULL REFERENCES admin_users(id),
  report_type varchar(20) NOT NULL CHECK (report_type IN ('LOST','FOUND')),
  item_type varchar(40) NOT NULL,
  item_other varchar(120),
  location varchar(240) NOT NULL,
  occurred_at timestamptz NOT NULL,
  description varchar(2000) NOT NULL,
  photo_key varchar(255),
  contact_preference varchar(40) NOT NULL,
  contact_other varchar(240),
  status varchar(24) NOT NULL CHECK (status IN ('SUBMITTED','UNDER_REVIEW','PUBLISHED','CLAIM_RECEIVED','RESOLVED','REJECTED','EXPIRED','CLOSED_UNCLAIMED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE lost_found_claims (
  id uuid PRIMARY KEY,
  report_id uuid NOT NULL REFERENCES lost_found_reports(id),
  claimant_id uuid NOT NULL REFERENCES admin_users(id),
  identifying_details varchar(2000) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'SUBMITTED',
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(report_id, claimant_id)
);
CREATE INDEX lost_found_board_idx ON lost_found_reports(status, created_at DESC);
CREATE TABLE lost_found_event_outbox (
  event_id uuid PRIMARY KEY,
  report_id uuid NOT NULL,
  event_type varchar(80) NOT NULL,
  actor_id uuid NOT NULL,
  occurred_at timestamptz NOT NULL,
  attempts integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE in_app_notifications (
  event_id uuid NOT NULL,
  recipient_id uuid NOT NULL REFERENCES admin_users(id),
  report_id uuid NOT NULL,
  event_type varchar(80) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  read_at timestamptz,
  PRIMARY KEY(event_id, recipient_id)
);
CREATE INDEX in_app_notifications_recipient_idx ON in_app_notifications(recipient_id, created_at DESC);
INSERT INTO access_permissions(code, description) VALUES
 ('LOST_FOUND_ADMIN','Review and manage Lost and Found reports'),
 ('LOST_FOUND_REPORT','Create Lost and Found reports and claims'),
 ('LOST_FOUND_BOARD','View published Lost and Found notices')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions(role_code, permission_code, scope)
SELECT r.code,p.code,'SOCIETY' FROM access_roles r CROSS JOIN access_permissions p
WHERE r.code IN ('ADMIN','SOCIETY_ADMIN') AND p.code='LOST_FOUND_ADMIN'
ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role_code, permission_code, scope)
SELECT r.code,p.code,'SOCIETY' FROM access_roles r CROSS JOIN access_permissions p
WHERE r.enabled AND r.code IN ('ADMIN','SOCIETY_ADMIN','COMMITTEE','RESIDENT','FLAT_OWNER','FAMILY_MEMBER','TENANT')
AND p.code IN ('LOST_FOUND_REPORT','LOST_FOUND_BOARD')
ON CONFLICT DO NOTHING;
