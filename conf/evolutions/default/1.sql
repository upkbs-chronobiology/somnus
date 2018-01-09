# Add question

# --- !Ups
create table question (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  content TEXT NOT NULL
)

# --- !Downs
drop table question
