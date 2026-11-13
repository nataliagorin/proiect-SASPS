INSERT INTO account (id, owner, balance)
SELECT n, 'Alpha Bank customer ' || n, 1000.00
FROM generate_series(1, 100) AS n;

SELECT setval(pg_get_serial_sequence('account', 'id'), 100);
