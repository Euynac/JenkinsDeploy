# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JenkinsDeploy is an enterprise-grade Jenkins CI/CD solution featuring Docker Compose-based master-slave architecture with support for .NET, Java, Vue, and other technology stacks. The project includes complete E2E testing environments and SonarQube integration.

**Key Features:**
- Docker-based Jenkins master with JCasC (Jenkins Configuration as Code)
- Layered Jenkins agent images (base → docker → language-specific)
- Docker-outside-of-Docker (DooD) architecture for containerized builds
- Integrated E2E testing with Python + pytest-bdd
- SonarQube code quality analysis
- Offline deployment support for air-gapped environments

## Essential Build Commands

### Jenkins Master

```bash
# Build and start Jenkins Master
cd master
docker compose -f docker-compose-test.yml up -d

# View logs
docker logs jenkins-master-test

# Check health status
docker ps | grep jenkins-master-test
```

### Jenkins Agent Images (Layered Build)

**Critical**: Agent images must be built in strict order due to layer dependencies:

```bash
cd /mnt/d/Repositories/JenkinsDeploy

# Layer 1: Base agent with Jenkins dependencies
docker build -f agents/base/Dockerfile.agent-base -t jenkins-agent-base:1.0 agents/base

# Layer 2: Docker agent (adds Docker CLI + Docker Compose)
docker build -f agents/base/Dockerfile.agent-docker -t jenkins-agent-docker:1.0 agents/base

# Layer 3: .NET agent (adds .NET SDK 8.0 + Python 3.13 + SonarScanner)
docker build -f agents/dotnet/Dockerfile.dotnet -t jenkins-agent-dotnet:2.0 agents/dotnet

# Start .NET agent
cd agents/dotnet
docker compose -f docker-compose-test-dotnet.yml up -d
```

### Testing

```bash
# Test .NET project build
cd examples/todoapp-backend-api-main
dotnet restore
dotnet build
dotnet test

# Run E2E tests locally
cd examples/todoapp-backend-api-e2etest-main
python -m pytest -v

# Check agent connection
docker logs jenkins-agent-dotnet-test | grep "Connected"
```

### SonarQube (Code Quality Analysis)

```bash
# Start SonarQube services
cd components/sonarqube
docker compose up -d

# Wait for startup (2-3 minutes)
docker logs -f sonarqube

# Access UI at http://localhost:9000 (admin/admin)
```

## Architecture

### Layered Agent Image Structure

```
jenkins-agent-base:1.0
├── Jenkins agent runtime
├── Git, curl, wget
└── Basic tools

jenkins-agent-docker:1.0  (inherits from base)
├── All base functionality
├── Docker CLI + Docker Compose
└── Docker socket access via group_add

jenkins-agent-dotnet:2.0  (inherits from docker)
├── All docker functionality
├── .NET SDK 8.0
├── Python 3.13 + pytest
├── dotnet-sonarscanner
└── NuGet cache optimization
```

**Why Layered?** Reduces build time and image size through layer reuse. Other language-specific agents (Java, Vue) can inherit from `agent-docker` layer.

### Network Architecture

All containers share the `jenkinsdeploy_default` network for internal communication:

```
jenkins-master-test (port 8080, 50000)
    ↓
jenkins-agent-dotnet-test
    ↓ (DooD via /var/run/docker.sock)
    └── Spawns test containers (e.g., todoapp-postgres-test)
```

The agent also connects to `sonarqube-network` for code analysis.

### Docker Socket Permissions (DooD)

The agent uses `group_add` in docker-compose to access the host Docker socket:

```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
group_add:
  - "1001"  # Must match host's docker.sock GID
```

**To find your GID:** `stat -c '%g' /var/run/docker.sock`

**Critical**: If Docker commands fail with permission errors, verify:
1. The GID in `agents/dotnet/docker-compose-test-dotnet.yml` matches your host
2. Check actual groups: `docker exec jenkins-agent-dotnet-test cat /proc/1/status | grep Groups`

## E2E Testing Architecture

E2E tests run entirely inside the Jenkins agent container:

1. Agent spawns test database via DooD: `docker compose -f docker-compose.test.yml up -d`
2. Wait for DNS registration: `getent hosts todoapp-postgres-test`
3. Start API in background with `nohup env VAR=value dotnet run &` (NOT `export` + `nohup`)
4. Wait for API readiness: `curl http://localhost:5085/swagger/index.html`
5. Run pytest: `pytest --alluredir=test-results/allure-results -v`
6. Cleanup: Kill API process and stop test containers

**Why `nohup env`?** Environment variables set with `export` are lost in nohup subprocesses. Always use `nohup env VAR=value command &`.

**DNS Wait is Critical**: Docker DNS registration takes 1-2 seconds. Always wait for `getent hosts <container-name>` before connecting.

## Important Configuration Files

### Agent Configuration
- `agents/dotnet/docker-compose-test-dotnet.yml` - .NET agent deployment config
  - **JENKINS_SECRET**: Copy from Jenkins UI after creating agent node
  - **NO_PROXY**: Must include `sonarqube` to avoid proxy errors
  - **group_add**: Docker socket GID (system-specific)

### Test Database
- `examples/todoapp-backend-api-e2etest-main/docker-compose.test.yml`
  - Uses `tmpfs` for automatic cleanup
  - Must connect to `jenkinsdeploy_default` network
  - Container name must be DNS-resolvable from agent

### Pipeline Examples
- `examples/backend.groovy` - Full .NET backend pipeline with SonarQube
- `examples/frontend.groovy` - Vue.js frontend pipeline
- `examples/quick-test-pipeline.groovy` - Quick validation pipeline

## Common Issues and Solutions

### 1. SonarQube Connection Fails (502 Bad Gateway)

**Symptom**: `Http status code is BadGateway` during SonarQube analysis

**Root Cause**: Agent's HTTP proxy is intercepting requests to internal `sonarqube` hostname

**Solution**: Add `sonarqube` and `sonarqube-db` to `NO_PROXY` in `agents/dotnet/docker-compose-test-dotnet.yml`:

```yaml
environment:
  NO_PROXY: "localhost,127.0.0.1,jenkins-master-test,sonarqube,sonarqube-db,172.16.0.0/12,192.168.0.0/16,172.19.0.0/16"
  no_proxy: "localhost,127.0.0.1,jenkins-master-test,sonarqube,sonarqube-db,172.16.0.0/12,192.168.0.0/16,172.19.0.0/16"
```

**Verify**: `docker exec jenkins-agent-dotnet-test curl -v http://sonarqube:9000/api/server/version`

### 2. Docker Permission Denied

**Symptom**: `permission denied while trying to connect to Docker socket`

**Solution**: See "Docker Socket Permissions" section above. Update `group_add` GID to match host.

### 3. DNS Resolution Failed in E2E Tests

**Symptom**: `System.Net.Sockets.SocketException: Name or service not known`

**Root Cause**: Container DNS registration hasn't completed

**Solution**: Always add DNS wait logic in pipelines:

```bash
MAX_DNS_RETRIES=30
while [ $DNS_RETRY_COUNT -lt $MAX_DNS_RETRIES ]; do
    if getent hosts todoapp-postgres-test > /dev/null 2>&1; then
        break
    fi
    sleep 1
    DNS_RETRY_COUNT=$((DNS_RETRY_COUNT + 1))
done
```

### 4. API Process Exits Immediately

**Symptom**: API PID check fails in pipeline

**Root Cause**: `dotnet run` compiles then replaces the process, invalidating the original PID

**Solution**: Don't check PID. Use port-based health check:

```bash
# ❌ Wrong
API_PID=$!
if ! ps -p $API_PID > /dev/null; then error; fi

# ✅ Correct
while [ $RETRY_COUNT -lt 60 ]; do
    if curl -s -f http://localhost:5085/swagger/index.html > /dev/null 2>&1; then
        break
    fi
    sleep 1
done
```

### 5. `groups` Command Warning

**Message**: `jenkins groups: cannot find name for group ID 1001`

**Explanation**: This is NOT an error. `group_add` only adds the GID to the process, not to `/etc/group`. This is normal Docker behavior.

**Verify it works**: `docker exec jenkins-agent-dotnet-test docker ps` - if this succeeds, permissions are correct.

**To suppress warning**: Add `RUN groupadd -g 1001 docker` to Dockerfile and rebuild.

## Development Workflow

### Adding a New Language Agent

1. Inherit from `jenkins-agent-docker:1.0`
2. Install language-specific SDK/runtime
3. Configure package manager (npm, Maven, etc.) for internal Nexus if needed
4. Add to `agents/<language>/` directory
5. Create corresponding `docker-compose-test-<language>.yml`
6. Update README.md with build instructions

### Modifying Pipelines

- E2E test pipelines are complex - always test DNS waits and environment variable passing
- Use `agent { label 'dotnet' }` to target specific agent types
- For debugging, add `sh 'env | sort'` stage to see all environment variables
- Check agent logs: `docker exec jenkins-agent-dotnet-test cat /proc/1/status`

### Updating Jenkins Master

```bash
# Edit plugins.txt to add/update plugins
cd master
vim plugins.txt

# Rebuild image
docker compose -f docker-compose-test.yml down
./build.sh

# Restart with new image
docker compose -f docker-compose-test.yml up -d
```

## Project Structure

```
JenkinsDeploy/
├── master/                          # Jenkins Master container
│   ├── Dockerfile                   # Master image with 80+ plugins
│   ├── docker-compose-test.yml      # Master deployment
│   ├── plugins.txt                  # Plugin list
│   └── build.sh/import.sh           # Build/import scripts for offline
│
├── agents/                          # Jenkins Agent images (layered)
│   ├── base/
│   │   ├── Dockerfile.agent-base         # Layer 1: Base agent
│   │   ├── Dockerfile.agent-docker       # Layer 2: + Docker
│   │   └── entrypoint-*.sh
│   ├── dotnet/
│   │   ├── Dockerfile.dotnet             # Layer 3: + .NET SDK
│   │   ├── docker-compose-test-dotnet.yml
│   │   └── entrypoint-dotnet.sh
│   └── doc/
│       ├── DOCKER_SOCKET_CONFIG.md       # DooD permission guide
│       └── README.md
│
├── examples/                        # Test projects and pipelines
│   ├── backend.groovy                    # .NET backend pipeline
│   ├── frontend.groovy                   # Vue frontend pipeline
│   ├── quick-test-pipeline.groovy        # Quick validation
│   ├── todoapp-backend-api-main/         # .NET 8.0 API project
│   ├── todoapp-backend-api-e2etest-main/ # Python E2E tests
│   └── todoapp-frontend-vue2-main/       # Vue.js project
│
├── components/sonarqube/            # Code quality platform
│   ├── docker-compose.yml
│   └── README.md                    # Setup guide
│
└── docs/                            # Additional documentation
    ├── DOCKER_AGENT_GUIDE.md
    ├── DOCKER_AGENT_QUICKSTART.md
    └── GIT_MANAGEMENT_GUIDE.md
```

## Key Design Decisions

1. **Layered Images**: Reduces build time through layer reuse. Base layer (500MB) → Docker layer (+200MB) → Language layer (+300MB)

2. **DooD over DinD**: Docker-outside-of-Docker shares host Docker daemon, avoiding nested Docker overhead and storage issues

3. **Static Agents**: Long-running agent containers (not dynamic) for offline/air-gapped environments where pulling images per-build is impractical

4. **Shared Network**: All containers in `jenkinsdeploy_default` network enables DNS-based service discovery

5. **tmpfs for Test DBs**: E2E test databases use tmpfs mounts for automatic cleanup and better I/O performance

6. **nohup env Pattern**: Environment variables must be passed inline with `nohup env` to survive subprocess fork

## Additional Resources

- [README.md](README.md) - Full deployment guide with quick start
- [agents/doc/DOCKER_SOCKET_CONFIG.md](agents/doc/DOCKER_SOCKET_CONFIG.md) - Complete DooD permission guide
- [examples/README.md](examples/README.md) - Test project documentation
- [components/sonarqube/README.md](components/sonarqube/README.md) - SonarQube setup and troubleshooting
