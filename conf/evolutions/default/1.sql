# Add question and answer

# --- !Ups
create table question (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  content TEXT NOT NULL
);

create table answer (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  FOREIGN KEY(question_id) REFERENCES question(id)
);

# --- !Downs
drop table question;
drop table answer;
