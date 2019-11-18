# --- !Ups

create table "user_session" (
  "id" VARCHAR PRIMARY KEY,
  "username" VARCHAR NOT NULL,
  /* using timestamps with time zone because we want absolute time stamps (instants) */
  "last_used" TIMESTAMP WITH TIME ZONE NOT NULL,
  "expiry" TIMESTAMP WITH TIME ZONE NOT NULL,
  /* seconds */
  "idle_timeout" BIGINT
--   "token" VARCHAR NOT NULL -- TODO: remove if not needed
);

alter table "question" alter column "answer_type"
    ENUM('text', 'range-continuous', 'range-discrete', 'multiple-choice-single', 'multiple-choice-many', 'time-of-day', 'date') NOT NULL;

# --- !Downs

drop table "user_session";
