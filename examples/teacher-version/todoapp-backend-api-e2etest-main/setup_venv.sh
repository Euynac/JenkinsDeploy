#!/bin/bash

# 虚拟环境设置脚本
# 用于在本地开发或 CI 环境中快速设置 Python 虚拟环境

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

VENV_DIR="venv"
REQUIREMENTS_FILE="requirements.txt"

echo -e "${BLUE}=== 设置 Python 虚拟环境 ===${NC}\n"

# 检查 Python
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}错误: 未找到 python3${NC}"
    echo -e "${YELLOW}请先安装 Python 3.8 或更高版本${NC}"
    exit 1
fi

PYTHON_VERSION=$(python3 --version)
echo -e "${GREEN}Python 版本: ${PYTHON_VERSION}${NC}"

# 检查是否已存在虚拟环境
if [ -d "$VENV_DIR" ]; then
    echo -e "${YELLOW}虚拟环境已存在: ${VENV_DIR}${NC}"
    read -p "是否重新创建? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}删除现有虚拟环境...${NC}"
        rm -rf "$VENV_DIR"
    else
        echo -e "${GREEN}使用现有虚拟环境${NC}"
        exit 0
    fi
fi

# 创建虚拟环境
echo -e "${YELLOW}创建虚拟环境: ${VENV_DIR}${NC}"
python3 -m venv "$VENV_DIR"

# 激活虚拟环境
echo -e "${YELLOW}激活虚拟环境...${NC}"
source "$VENV_DIR/bin/activate"

# 升级 pip
echo -e "${YELLOW}升级 pip...${NC}"
pip install --upgrade pip

# 安装依赖
if [ -f "$REQUIREMENTS_FILE" ]; then
    echo -e "${YELLOW}安装依赖: ${REQUIREMENTS_FILE}${NC}"
    pip install -r "$REQUIREMENTS_FILE"
    echo -e "${GREEN}依赖安装完成${NC}"
else
    echo -e "${RED}警告: 未找到 ${REQUIREMENTS_FILE}${NC}"
fi

# 显示已安装的包
echo -e "\n${BLUE}已安装的包:${NC}"
pip list

echo -e "\n${GREEN}✅ 虚拟环境设置完成！${NC}\n"
echo -e "${BLUE}使用说明:${NC}"
echo -e "  激活虚拟环境: ${YELLOW}source ${VENV_DIR}/bin/activate${NC}"
echo -e "  停用虚拟环境: ${YELLOW}deactivate${NC}"
echo -e "  运行测试: ${YELLOW}source ${VENV_DIR}/bin/activate && pytest${NC}"

