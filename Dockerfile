FROM container-registry.oracle.com/os/oraclelinux:8-slim as builder
WORKDIR /opensearch
COPY . .

ARG OPENSEARCH_VERSION
ENV JAVA_HOME=/usr/share/opensearch/jdk \
    GRADLE_USER_HOME=/opensearch/.gradle

RUN microdnf install gzip tar findutils && \
    groupadd -g 1000 opensearch && \
    adduser -u 1000 -g 1000 -G 0 -d /opensearch opensearch && \
    chmod 0775 /opensearch && \
    chown -R 1000:0 /opensearch && \
    mkdir -p /usr/share/opensearch/jdk && \
    chown -R 1000:0 /usr/share/opensearch

USER 1000

RUN curl https://download.oracle.com/java/17/archive/jdk-17.0.7_linux-x64_bin.tar.gz | tar -C /usr/share/opensearch/jdk -xz --strip-components=1

RUN ./gradlew -Dopensearch.version=$OPENSEARCH_VERSION -Dbuild.snapshot=false assemble
