# Add question, answer, user, password

# --- !Ups
create table question (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  content VARCHAR NOT NULL
);

create table answer (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT NOT NULL,
  content VARCHAR NOT NULL,
  FOREIGN KEY(question_id) REFERENCES question(id)
);

create table password (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hash VARCHAR NOT NULL,
  salt VARCHAR,
  hasher VARCHAR
);

create table user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR NOT NULL UNIQUE,
  password_id BIGINT UNIQUE,
  FOREIGN KEY(password_id) REFERENCES password(id)
);


# --- !Downs
drop table question;
drop table answer;
drop table user;
