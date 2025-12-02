#!/bin/bash

# 虚拟环境设置脚本

set -e

echo "创建 Python 虚拟环境..."

python3 -m venv venv

echo "激活虚拟环境..."
source venv/bin/activate

echo "升级 pip..."
pip install --upgrade pip

echo "安装依赖..."
pip install -r requirements.txt

echo "虚拟环境设置完成！"
echo "使用以下命令激活虚拟环境:"
echo "  source venv/bin/activate"

