## Running the sample code

1. Start a first node:

    ```
    sbt -Dconfig.resource=local1.conf run
    ```

2. (Optional) Start another node with different ports:

    ```
    sbt -Dconfig.resource=local2.conf run
    ```

3. Check for service readiness

    ```
    curl http://localhost:9101/ready
    ```

4. Start docker:

    ```
    docker-compose up -d
    ```

5. test commands:

    ```
    grpcurl -d '{"cartId":"c1", "userId": "c1", "amount": 40}' -plaintext 127.0.0.1:8101 buscart.BusCartService.AddAmount
    ```

    ```
    grpcurl -d '{"cartId":"c1", "userId": "c1", "fee": 20, "zone": "London street", "bus_number": 3, "time": 8}' -plaintext 127.0.0.1:8101 buscart.BusCartService.ExtractAmount
    ```

    grpcurl -d '{"cartId":"c1", "userId": "c1"}' -plaintext 127.0.0.1:8101 buscart.BusCartService.GetCart