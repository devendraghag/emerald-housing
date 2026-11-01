ALTER TABLE floors DROP CONSTRAINT IF EXISTS floors_floor_number_check;
ALTER TABLE floors ADD CONSTRAINT ck_floors_number_range CHECK (floor_number BETWEEN 1 AND 99);
