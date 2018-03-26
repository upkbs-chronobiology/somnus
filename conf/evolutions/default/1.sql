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

create table question (
  id IDENTITY PRIMARY KEY,
  content VARCHAR NOT NULL,
  /* range-continous: [0, 1] over ℝ, range-discrete-5: [1, 5] over ℕ */
  answer_type ENUM('text', 'range-continuous', 'range-discrete-5') NOT NULL,
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

drop table question;
drop table answer;
drop table user;
drop table password;
