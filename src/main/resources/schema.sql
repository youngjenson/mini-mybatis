CREATE TABLE IF NOT EXISTS user (
    id INT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    age INT NOT NULL,
    email VARCHAR(128)
);

INSERT INTO user (id, name, age)
VALUES (1, 'Alice', 20),
       (2, 'Bob', 25);
