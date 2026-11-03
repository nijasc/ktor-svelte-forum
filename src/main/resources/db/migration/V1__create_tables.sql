CREATE TABLE visits (
    id SERIAL PRIMARY KEY
);

CREATE TABLE users (
    username VARCHAR(64) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL
);
