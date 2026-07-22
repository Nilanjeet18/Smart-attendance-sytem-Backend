show databases;

USE smart_attendance_db;

show tables;

SELECT * FROM students;

delete from students
where id = 1;

truncate table students;

SET FOREIGN_KEY_CHECKS = 0;

truncate table students;

SELECT * FROM attendance_records;

truncate table attendance_records;

SELECT * FROM attendance_sessions;

truncate table attendance_sessions;

SET FOREIGN_KEY_CHECKS = 1;

SELECT * FROM courses;

truncate table courses;

SELECT * FROM qr_tokens;

truncate table qr_tokens;

SELECT * FROM teachers;

delete from teachers
where id = 6;



INSERT INTO teachers
(name, email, password, employee_id, department, phone_number, role, is_active)
VALUES
(
'System Admin',
'admin@gmail.com',
'$2b$10$Ybd6YYkf/FfLtAs69ZdzvuTRRPAnUpEh1nrM9oqRz0TAD.94iDwfq',
'ADM001',
'Administration',
'9999999999',
'ADMIN',
true
);

-- Email: admin@gmail.com
-- Password: admin123

SET FOREIGN_KEY_CHECKS = 1;

desc courses;

select *from courses;

truncate table courses;


SELECT
id,
course_id,
teacher_id,
topic,
session_date
FROM attendance_sessions;


SELECT id, name, email, role
FROM teachers;