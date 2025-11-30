# Dockerfile baseado na imagem COREEMU com Java instalado
FROM ghcr.io/eivarin/coreemu:coreemu-9.2.1

# Instalar Java 21 (OpenJDK 21) via Eclipse Temurin
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget \
    ca-certificates \
    && mkdir -p /usr/lib/jvm \
    && wget -qO- https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jre_x64_linux_hotspot_21.0.5_11.tar.gz | tar -xz -C /usr/lib/jvm \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Definir JAVA_HOME para Java 21
ENV JAVA_HOME=/usr/lib/jvm/jdk-21.0.5+11-jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Verificar instalação do Java
RUN java -version
