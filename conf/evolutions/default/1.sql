# Add question, answer and user

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

create table user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR NOT NULL UNIQUE
);

# --- !Downs
drop table question;
drop table answer;
drop table user;
