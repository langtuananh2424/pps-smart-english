-- D-25: ghi danh theo khóa (course), không theo level. Gộp các dòng cũ của cùng (user, khóa) thành một dòng:
-- giữ dòng id nhỏ nhất; hạn = muộn nhất, null (vô thời hạn) nếu có dòng nào vô thời hạn.
ALTER TABLE user_course_enrollments ADD COLUMN course_id bigint;

UPDATE user_course_enrollments
   SET course_id = (SELECT cl.course_id FROM course_levels cl WHERE cl.id = user_course_enrollments.level_id);

UPDATE user_course_enrollments e
   SET expires_at = CASE
           WHEN EXISTS (SELECT 1 FROM user_course_enrollments x
                         WHERE x.user_id = e.user_id AND x.course_id = e.course_id AND x.expires_at IS NULL) THEN NULL
           ELSE (SELECT MAX(x.expires_at) FROM user_course_enrollments x
                  WHERE x.user_id = e.user_id AND x.course_id = e.course_id)
       END
 WHERE e.id IN (SELECT MIN(id) FROM user_course_enrollments GROUP BY user_id, course_id);

DELETE FROM user_course_enrollments e
 WHERE EXISTS (SELECT 1 FROM user_course_enrollments x
                WHERE x.user_id = e.user_id AND x.course_id = e.course_id AND x.id < e.id);

ALTER TABLE user_course_enrollments DROP CONSTRAINT uq_enrollments_user_level;
ALTER TABLE user_course_enrollments DROP COLUMN level_id;
ALTER TABLE user_course_enrollments ALTER COLUMN course_id SET NOT NULL;
ALTER TABLE user_course_enrollments ADD CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id);
ALTER TABLE user_course_enrollments ADD CONSTRAINT uq_enrollments_user_course UNIQUE (user_id, course_id);
