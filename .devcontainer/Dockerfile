FROM mcr.microsoft.com/devcontainers/base:trixie

ARG DEBIAN_FRONTEND=noninteractive
ARG JAVA_PACKAGE=openjdk-21-jdk-headless
ARG BABASHKA_VERSION=v1.12.217

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        git \
        rlwrap \
        unzip \
        zip \
        direnv \
        ${JAVA_PACKAGE} \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/linux-install.sh \
        https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh \
    && chmod +x /tmp/linux-install.sh \
    && /tmp/linux-install.sh \
    && rm -f /tmp/linux-install.sh

RUN arch="$(dpkg --print-architecture)" \
    && case "${arch}" in \
        amd64) bb_arch="amd64" ;; \
        arm64) bb_arch="aarch64" ;; \
        *) echo "Unsupported architecture: ${arch}" >&2; exit 1 ;; \
    esac \
    && curl -fsSL \
        "https://github.com/babashka/babashka/releases/download/${BABASHKA_VERSION}/babashka-${BABASHKA_VERSION#v}-linux-${bb_arch}-static.tar.gz" \
        | tar -xz -C /usr/local/bin bb

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH=${JAVA_HOME}/bin:${PATH}

USER vscode
