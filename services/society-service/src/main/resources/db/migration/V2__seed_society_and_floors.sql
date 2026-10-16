INSERT INTO societies (id, society_code, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'EMERALD', 'Emerald Housing Society');

INSERT INTO floors (id, society_id, floor_number)
SELECT ('10000000-0000-0000-0000-' || LPAD(floor_number::text, 12, '0'))::uuid,
       '00000000-0000-0000-0000-000000000001'::uuid, floor_number
FROM generate_series(1, 18) AS configured_floors(floor_number);
