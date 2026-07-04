FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S sandbox && adduser -S sandbox -G sandbox
USER sandbox
WORKDIR /work

ENTRYPOINT ["java"]
