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
  created TIMESTAMP NOT NULL DEFAULT current_timestamp(),
  FOREIGN KEY(password_id) REFERENCES password(id)
);

create table pw_reset (
  id IDENTITY PRIMARY KEY,
  token VARCHAR NOT NULL UNIQUE,
  expiry TIMESTAMP NOT NULL,
  user_id BIGINT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES user(id)
);

create table study (
  id IDENTITY PRIMARY KEY,
  name VARCHAR NOT NULL UNIQUE
);

create table study_participants (
  user_id BIGINT NOT NULL,
  study_id BIGINT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES user(id),
  FOREIGN KEY(study_id) REFERENCES study(id)
);

create table questionnaire (
  id IDENTITY PRIMARY KEY,
  name VARCHAR NOT NULL,
  study_id BIGINT,
  FOREIGN KEY(study_id) REFERENCES study(id)
);

create table schedule (
  id IDENTITY PRIMARY KEY,
  questionnaire_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  frequency INT NOT NULL CHECK(frequency >= 0),
  FOREIGN KEY(questionnaire_id) REFERENCES questionnaire(id),
  FOREIGN KEY(user_id) REFERENCES user(id)
);

create table question (
  id IDENTITY PRIMARY KEY,
  content VARCHAR NOT NULL,
  answer_type ENUM('text', 'range-continuous', 'range-discrete', 'multiple-choice-single', 'multiple-choice-many') NOT NULL,
  answer_labels VARCHAR,
  /* "<min>,<max>", inclusive */
  answer_range VARCHAR,
  questionnaire_id BIGINT,
  FOREIGN KEY(questionnaire_id) REFERENCES questionnaire(id)
);

create table answer (
  id IDENTITY PRIMARY KEY,
  question_id BIGINT NOT NULL,
  content VARCHAR NOT NULL,
  user_id BIGINT NOT NULL,
  created TIMESTAMP NOT NULL DEFAULT current_timestamp(),
  FOREIGN KEY(question_id) REFERENCES question(id),
  FOREIGN KEY(user_id) REFERENCES user(id)
);


# --- !Downs

drop table answer;
drop table question;
drop table schedule;
drop table questionnaire;
drop table study_participants;
drop table study;
drop table pw_reset;
drop table user;
drop table password;
