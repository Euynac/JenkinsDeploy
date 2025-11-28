# Jenkins Agent - Vue/Node.js 构建环境
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
    ca-certificates \
    gnupg \
    && rm -rf /var/lib/apt/lists/*

# Node.js 版本
ENV NODE_VERSION=18.19.0

# 安装 Node.js 18 LTS
RUN curl -fsSL https://deb.nodesource.com/setup_18.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# 安装 Yarn
RUN npm install -g yarn

# 安装 pnpm
RUN npm install -g pnpm

# 安装常用的前端构建工具
RUN npm install -g \
    @vue/cli \
    @angular/cli \
    create-react-app \
    vite

# 配置 npm 使用内网 Nexus（可选，部署时修改）
# RUN npm config set registry http://nexus.internal.com/repository/npm-group/
# RUN yarn config set registry http://nexus.internal.com/repository/npm-group/

# 验证安装
RUN node -v && \
    npm -v && \
    yarn -v && \
    pnpm -v && \
    git --version

# 创建工作目录
RUN mkdir -p /home/jenkins/agent
WORKDIR /home/jenkins/agent

# 调整权限
RUN chown -R jenkins:jenkins /home/jenkins

# 切换回 jenkins 用户
USER jenkins

# 配置 npm 缓存目录
RUN npm config set cache /home/jenkins/.npm --global

LABEL maintainer="devops-team@example.com"
LABEL description="Jenkins Agent with Node.js 18, npm, yarn, pnpm, Vue CLI"
LABEL version="1.0"
