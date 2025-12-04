#!/bin/bash
set -e

echo "=== Starting Docker Offline Installation ==="

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo "Please run as root"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Extract Docker binaries
echo "Extracting Docker binaries..."
tar xzf docker-29.1.2.tgz

# Install Docker binaries
echo "Installing Docker binaries to /usr/bin/..."
cp docker/* /usr/bin/
chmod +x /usr/bin/docker*

# Install Docker Compose
echo "Installing Docker Compose..."
chmod +x docker-compose
cp docker-compose /usr/local/bin/docker-compose
ln -sf /usr/local/bin/docker-compose /usr/bin/docker-compose

# Create Docker group
echo "Creating docker group..."
groupadd docker 2>/dev/null || true

# Create Docker systemd service
echo "Creating Docker systemd service..."
cat > /etc/systemd/system/docker.service <<'EOF'
[Unit]
Description=Docker Application Container Engine
Documentation=https://docs.docker.com
After=network-online.target firewalld.service containerd.service
Wants=network-online.target
Requires=containerd.service

[Service]
Type=notify
ExecStart=/usr/bin/dockerd -H fd:// --containerd=/run/containerd/containerd.sock
ExecReload=/bin/kill -s HUP $MAINPID
LimitNOFILE=1048576
LimitNPROC=infinity
LimitCORE=infinity
TimeoutStartSec=0
Delegate=yes
KillMode=process
Restart=on-failure
StartLimitBurst=3
StartLimitInterval=60s

[Install]
WantedBy=multi-user.target
EOF

# Create containerd systemd service
echo "Creating containerd systemd service..."
cat > /etc/systemd/system/containerd.service <<'EOF'
[Unit]
Description=containerd container runtime
Documentation=https://containerd.io
After=network.target

[Service]
ExecStartPre=/sbin/modprobe overlay
ExecStart=/usr/bin/containerd
Restart=always
RestartSec=5
Delegate=yes
KillMode=process
OOMScoreAdjust=-999
LimitNOFILE=1048576
LimitNPROC=infinity
LimitCORE=infinity

[Install]
WantedBy=multi-user.target
EOF

# Create Docker daemon config
echo "Creating Docker daemon configuration..."
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  },
  "storage-driver": "overlay2"
}
EOF

# Reload systemd and start services
echo "Starting Docker services..."
systemctl daemon-reload
systemctl enable containerd
systemctl enable docker
systemctl start containerd
sleep 2
systemctl start docker

# Wait for Docker to be ready
echo "Waiting for Docker to be ready..."
for i in {1..30}; do
    if docker info >/dev/null 2>&1; then
        echo "Docker is ready!"
        break
    fi
    echo "Waiting for Docker... ($i/30)"
    sleep 1
done

# Verify installation
echo ""
echo "=== Installation Complete ==="
docker --version
docker-compose --version
docker info | grep -E "Server Version|Storage Driver"

echo ""
echo "Docker has been successfully installed!"
echo "You can now use 'docker' and 'docker-compose' commands."
