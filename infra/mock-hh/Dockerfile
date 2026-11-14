FROM rust:1.90-slim AS build
WORKDIR /app
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo build --release --locked

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/release/mock-hh /usr/local/bin/mock-hh
EXPOSE 8090
ENTRYPOINT ["/usr/local/bin/mock-hh"]
