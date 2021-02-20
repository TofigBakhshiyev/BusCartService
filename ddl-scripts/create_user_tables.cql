USE buscartservice;
CREATE TABLE IF NOT EXISTS user_projection (
  cartId text,
  userId text,
  zone text,
  bus_number int,
  time int,
  PRIMARY KEY (userId));