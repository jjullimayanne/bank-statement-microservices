FROM golang:1.21 AS builder

WORKDIR /app

RUN apt-get update && apt-get install -y \
    gcc \
    librdkafka-dev \
    libsasl2-dev

COPY go.mod go.sum ./

RUN go mod download

COPY . .

RUN CGO_ENABLED=1 go build -o transaction-service .

FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y \
    librdkafka1 \
    libsasl2-2 \
    ca-certificates && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/transaction-service .

EXPOSE 8083

CMD ["./transaction-service"]