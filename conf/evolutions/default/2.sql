# --- !Ups

create table `user_session` (
  `id` VARCHAR(500) PRIMARY KEY,
  `username` VARCHAR(255) NOT NULL,
  /* TODO: Use stronger type than VARCHAR for this (H2 had TIMESTAMP WITH TIME ZONE). */
  /* Previous comment: Using timestamps with time zone because we want absolute time stamps (instants) */
  `last_used` VARCHAR(50) NOT NULL,
  `expiry` VARCHAR(50) NOT NULL,
  /* seconds */
  `idle_timeout` BIGINT
);

/* ACL for researchers */
create table `study_access` (
    `user_id` BIGINT NOT NULL,
    `study_id` BIGINT NOT NULL,
    `level` ENUM('read', 'write', 'own') NOT NULL DEFAULT 'read',
    FOREIGN KEY(`user_id`) REFERENCES `user`(`id`),
    FOREIGN KEY(`study_id`) REFERENCES `study`(`id`),
    PRIMARY KEY(`user_id`, `study_id`)
);

alter table `question` modify column `answer_type`
    ENUM('text', 'range-continuous', 'range-discrete', 'multiple-choice-single', 'multiple-choice-many', 'time-of-day', 'date') NOT NULL;

# --- !Downs

drop table if exists `user_session`;
drop table if exists `study_access`;
