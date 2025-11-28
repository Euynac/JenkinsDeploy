FROM jenkins/jenkins:lts

# 设置维护者信息
LABEL maintainer="your-team@example.com"
LABEL description="Jenkins Master offline deployment image with pre-installed plugins"
LABEL version="1.0"

# 切换到root用户安装系统依赖
USER root

# 配置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 安装基础工具和依赖
RUN apt-get update && apt-get install -y \
    vim \
    curl \
    wget \
    git \
    unzip \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 切换回jenkins用户
USER jenkins

# 禁用Jenkins启动向导（首次启动跳过初始化配置）
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

# 设置Jenkins主目录
ENV JENKINS_HOME=/var/jenkins_home

# 复制插件列表文件
COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt

# 安装插件（使用jenkins-plugin-cli工具）
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# 复制自定义配置文件（如果有）
# COPY --chown=jenkins:jenkins config/ /usr/share/jenkins/ref/

# 暴露Jenkins端口
# 8080: Web UI端口
# 50000: Agent连接端口
EXPOSE 8080 50000

# 容器启动命令（继承自基础镜像）
# ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/jenkins.sh"]
