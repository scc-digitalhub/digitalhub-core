CREATE TABLE IF NOT EXISTS runnable
(
    id      VARCHAR(255) NOT NULL PRIMARY KEY,
    created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data    BINARY LARGE OBJECT,
    clazz   VARCHAR(255),
    updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
