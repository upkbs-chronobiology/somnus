# --- !Ups

create table "user_session" (
  "id" VARCHAR PRIMARY KEY,
  "username" VARCHAR NOT NULL,
  /* using timestamps with time zone because we want absolute time stamps (instants) */
  "last_used" TIMESTAMP WITH TIME ZONE NOT NULL,
  "expiry" TIMESTAMP WITH TIME ZONE NOT NULL,
  /* seconds */
  "idle_timeout" BIGINT
);

/* ACL for researchers */
create table "study_access" (
    "user_id" BIGINT NOT NULL,
    "study_id" BIGINT NOT NULL,
    "level" ENUM('read', 'write', 'own') NOT NULL DEFAULT 'read',
    FOREIGN KEY("user_id") REFERENCES "user"("id"),
    FOREIGN KEY("study_id") REFERENCES "study"("id"),
    PRIMARY KEY("user_id", "study_id")
);

alter table "question" alter column "answer_type"
    ENUM('text', 'range-continuous', 'range-discrete', 'multiple-choice-single', 'multiple-choice-many', 'time-of-day', 'date') NOT NULL;

# --- !Downs

drop table "user_session";
