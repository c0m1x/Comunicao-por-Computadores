FROM ghcr.io/eivarin/coreemu:coreemu-9.2.1

# Dependências do sistema + GTK + Firefox deps
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        wget \
        bzip2 \
        ca-certificates \
        libgtk-3-0 \
        libdbus-glib-1-2 \
        libxt6 \
        libx11-xcb1 \
        libxcomposite1 \
        libxdamage1 \
        libxrandr2 \
        libasound2 \
        libpangocairo-1.0-0 \
        libpango-1.0-0 \
        libatspi2.0-0 \
        libxfixes3 \
        libcups2 \
        libxss1 \
        libnss3 \
        libnspr4 \
        libx11-6 \
        libxext6 \
        libxtst6 \
        libxcb1 \
        libxrender1 \
        libappindicator3-1 \
        libdbusmenu-glib4 \
        libdbusmenu-gtk3-4 \
        libdbusmenu-gtk4 \
        libnotify4 \
        fonts-dejavu && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Instalar Java 21 (OpenJDK)
RUN mkdir -p /usr/lib/jvm && \
    wget -qO- https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jre_x64_linux_hotspot_21.0.5_11.tar.gz \
        | tar -xz -C /usr/lib/jvm

# Instalar Firefox
RUN mkdir -p /temp && \
    wget -O /temp/firefox.tar.bz2 "https://download-installer.cdn.mozilla.net/pub/firefox/releases/115.0/linux-x86_64/pt-BR/firefox-115.0.tar.bz2" && \
    tar -xjf /temp/firefox.tar.bz2 -C /opt/ && \
    ln -s /opt/firefox/firefox /usr/bin/firefox && \
    rm /temp/firefox.tar.bz2

ENV JAVA_HOME=/usr/lib/jvm/jdk-21.0.5+11-jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN java -version
