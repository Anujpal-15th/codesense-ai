FROM eclipse-temurin:17-jre-alpine

# python3: the Python execution path runs tracer.py in this same image via
# `docker run --entrypoint python3` (PythonExecutionHandler). Installed before
# dropping to the sandbox user.
RUN apk add --no-cache python3 \
    && addgroup -S sandbox && adduser -S sandbox -G sandbox
USER sandbox
WORKDIR /work

ENTRYPOINT ["java"]
