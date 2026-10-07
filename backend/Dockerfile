FROM golang:1.27.1-alpine AS build
WORKDIR /src
ENV CGO_ENABLED=0 GOMAXPROCS=1 GOMEMLIMIT=256MiB
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN go test -p 1 ./... && go build -p 1 -trimpath -ldflags="-s -w" -o /out/audioly-party .

FROM alpine:3.23
RUN apk add --no-cache ca-certificates
COPY --from=build /out/audioly-party /usr/local/bin/audioly-party
USER 65532:65532
EXPOSE 8000
ENTRYPOINT ["/usr/local/bin/audioly-party"]
