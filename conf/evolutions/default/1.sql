# Add initial tables

# --- !Ups

create table `password`
(
    `id`     BIGINT AUTO_INCREMENT PRIMARY KEY,
    `hash`   VARCHAR(255) NOT NULL,
    `salt`   VARCHAR(255),
    `hasher` VARCHAR(255)
);

create table `organization`
(
    `id`   BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(255) NOT NULL UNIQUE
);

create table `user`
(
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`            VARCHAR(255) NOT NULL UNIQUE,
    `password_id`     BIGINT UNIQUE,
    `role`            VARCHAR(255),
    `created`         TIMESTAMP    NOT NULL DEFAULT current_timestamp(),
    `organization_id` BIGINT,
    FOREIGN KEY (`password_id`) REFERENCES `password` (`id`),
    FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`)
);

create table `pw_reset`
(
    `id`      BIGINT AUTO_INCREMENT PRIMARY KEY,
    `token`   VARCHAR(255) NOT NULL UNIQUE,
    `expiry`  TIMESTAMP    NOT NULL,
    `user_id` BIGINT       NOT NULL,
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
);

create table `study`
(
    `id`   BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(255) NOT NULL UNIQUE
);

create table `study_participants`
(
    `user_id`  BIGINT NOT NULL,
    `study_id` BIGINT NOT NULL,
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    FOREIGN KEY (`study_id`) REFERENCES `study` (`id`)
);

create table `questionnaire`
(
    `id`       BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`     VARCHAR(255) NOT NULL,
    `study_id` BIGINT,
    FOREIGN KEY (`study_id`) REFERENCES `study` (`id`)
);

create table `schedule`
(
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `questionnaire_id` BIGINT NOT NULL,
    `user_id`          BIGINT NOT NULL,
    `start_date`       DATE   NOT NULL,
    `end_date`         DATE   NOT NULL,
    `start_time`       TIME   NOT NULL,
    `end_time`         TIME   NOT NULL,
    `frequency`        INT    NOT NULL CHECK (`frequency` >= 0),
    FOREIGN KEY (`questionnaire_id`) REFERENCES `questionnaire` (`id`),
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
);

create table `question`
(
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `content`          VARCHAR(500) NOT NULL,
    `answer_type`      ENUM ('text', 'range-continuous', 'range-discrete', 'multiple-choice-single', 'multiple-choice-many', 'time-of-day', 'date')
                                    NOT NULL,
    `answer_labels`    VARCHAR(500),
    /* `<min>,<max>`, inclusive */
    `answer_range`     VARCHAR(255),
    `questionnaire_id` BIGINT,
    FOREIGN KEY (`questionnaire_id`) REFERENCES `questionnaire` (`id`)
);

create table `answer`
(
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `question_id`   BIGINT       NOT NULL,
    `content`       VARCHAR(500) NOT NULL,
    `user_id`       BIGINT       NOT NULL,
    `created`       TIMESTAMP    NOT NULL DEFAULT current_timestamp(),
    /* TODO: Use stronger type than VARCHAR for this (H2 had TIMESTAMP WITH TIME ZONE) */
    `created_local` VARCHAR(50)  NOT NULL,
    FOREIGN KEY (`question_id`) REFERENCES `question` (`id`),
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
);

create table `user_session`
(
    `id`           VARCHAR(500) PRIMARY KEY,
    `username`     VARCHAR(255) NOT NULL,
    /* TODO: Use stronger type than VARCHAR for this (H2 had TIMESTAMP WITH TIME ZONE). */
    /* Previous comment: Using timestamps with time zone because we want absolute time stamps (instants) */
    `last_used`    VARCHAR(50)  NOT NULL,
    `expiry`       VARCHAR(50)  NOT NULL,
    /* seconds */
    `idle_timeout` BIGINT
);

/* ACL for researchers */
create table `study_access`
(
    `user_id`  BIGINT                        NOT NULL,
    `study_id` BIGINT                        NOT NULL,
    `level`    ENUM ('read', 'write', 'own') NOT NULL DEFAULT 'read',
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    FOREIGN KEY (`study_id`) REFERENCES `study` (`id`),
    PRIMARY KEY (`user_id`, `study_id`)
);


# --- !Downs

drop table if exists `study_access`;
drop table if exists `user_session`;
drop table if exists `answer`;
drop table if exists `question`;
drop table if exists `schedule`;
drop table if exists `questionnaire`;
drop table if exists `study_participants`;
drop table if exists `study`;
drop table if exists `pw_reset`;
drop table if exists `user`;
drop table if exists `organization`;
drop table if exists `password`;
