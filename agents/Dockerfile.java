# Jenkins Agent - Java 构建环境
FROM jenkins/inbound-agent:latest-jdk17

USER root

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 安装基础工具
RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    unzip \
    ca-certificates \
    apt-transport-https \
    && rm -rf /var/lib/apt/lists/*

# Maven 版本
ENV MAVEN_VERSION=3.9.6
ENV MAVEN_HOME=/opt/maven

# 安装 Maven
RUN wget https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && tar -xzf apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && mv apache-maven-${MAVEN_VERSION} ${MAVEN_HOME} \
    && rm apache-maven-${MAVEN_VERSION}-bin.tar.gz

# Gradle 版本
ENV GRADLE_VERSION=8.5
ENV GRADLE_HOME=/opt/gradle

# 安装 Gradle
RUN wget https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
    && unzip gradle-${GRADLE_VERSION}-bin.zip \
    && mv gradle-${GRADLE_VERSION} ${GRADLE_HOME} \
    && rm gradle-${GRADLE_VERSION}-bin.zip

# 配置环境变量
ENV PATH="${PATH}:${MAVEN_HOME}/bin:${GRADLE_HOME}/bin"

# 配置 Maven 使用内网 Nexus（可选，部署时修改）
# COPY settings.xml /root/.m2/settings.xml
# COPY settings.xml /home/jenkins/.m2/settings.xml

# 验证安装
RUN java -version && \
    mvn -version && \
    gradle -version && \
    git --version

# 创建工作目录
RUN mkdir -p /home/jenkins/agent
WORKDIR /home/jenkins/agent

# 调整权限
RUN chown -R jenkins:jenkins /home/jenkins

# 切换回 jenkins 用户
USER jenkins

# 预热 Maven（下载常用插件）
RUN mkdir -p /home/jenkins/.m2/repository

LABEL maintainer="devops-team@example.com"
LABEL description="Jenkins Agent with JDK 17, Maven 3.9.6, Gradle 8.5"
LABEL version="1.0"
