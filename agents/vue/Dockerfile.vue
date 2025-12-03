# Jenkins Agent - Vue.js 构建环境
# 基于 agent-docker 镜像，添加 Node.js 和前端工具链
FROM jenkins-agent-docker:1.0

USER root

# 安装 Node.js 24.x (LTS)
RUN curl -fsSL https://deb.nodesource.com/setup_24.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# 验证安装
RUN node --version && \
    npm --version

# 配置 npm 使用国内镜像（可选，加速下载）
# RUN npm config set registry https://registry.npmmirror.com

# 全局安装常用前端工具
RUN npm install -g @vue/cli \
    && npm install -g sonarqube-scanner

# 创建 npm 缓存目录并设置权限
RUN mkdir -p /home/jenkins/.npm /home/jenkins/.cache \
    && chown -R jenkins:jenkins /home/jenkins/.npm /home/jenkins/.cache

# 创建 docker 组（GID 1001），消除 groups 命令的警告
# 注意：GID 必须与宿主机 docker.sock 的 GID 匹配
RUN groupadd -g 1001 docker 2>/dev/null || true

# 切换回 jenkins 用户
USER jenkins

# 设置环境变量
ENV NODE_ENV=production
ENV NPM_CONFIG_LOGLEVEL=info

LABEL maintainer="devops-team@example.com"
LABEL description="Jenkins Agent with Node.js 20 and Vue.js toolchain (based on agent-docker)"
LABEL version="1.0"

# 继承 agent-docker 的 entrypoint
# ENTRYPOINT 已在 agent-docker 镜像中定义
