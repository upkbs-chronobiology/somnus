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

# --- !Downs

drop table "user_session";
