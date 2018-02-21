# Add question, answer, user, password

# --- !Ups

create table password (
  id IDENTITY PRIMARY KEY,
  hash VARCHAR NOT NULL,
  salt VARCHAR,
  hasher VARCHAR
);

create table user (
  id IDENTITY PRIMARY KEY,
  name VARCHAR NOT NULL UNIQUE,
  password_id BIGINT UNIQUE,
  role VARCHAR,
  FOREIGN KEY(password_id) REFERENCES password(id)
);

create table question (
  id IDENTITY PRIMARY KEY,
  content VARCHAR NOT NULL
);

create table answer (
  id IDENTITY PRIMARY KEY,
  question_id BIGINT NOT NULL,
  content VARCHAR NOT NULL,
  user_id BIGINT NOT NULL,
  FOREIGN KEY(question_id) REFERENCES question(id),
  FOREIGN KEY(user_id) REFERENCES user(id)
);


# --- !Downs

drop table question;
drop table answer;
drop table user;
drop table password;
