# -*- coding: utf-8 -*-
"""在 Jenkins 创建/更新 9 条流水线 Job（内嵌 Pipeline，Jenkinsfile 不入仓）。

触发参数：BRANCH（必选分支）。
环境约定：
  - 后端 Spring profile 以各模块 application.yml 的 spring.profiles.active 为准（代码指定），流水线不注入。
  - 前端为构建期 VITE_*（.env* / docker build-arg），API 走同源相对路径 /api，流水线只 npm run build。
"""
from __future__ import annotations

import http.cookiejar
import json
import sys
import urllib.error
import urllib.request
from base64 import b64encode

JENKINS = "http://192.168.99.100:8080"
JUSER = "qjj"
JPASS = "Qjj327846@!!"

GIT_URL = "https://github.com/NewStudyAccount/project_ai.git"
GIT_CRED_ID = "github-token"
DEFAULT_BRANCH = "feature-base-design"

DEPLOY_HOST = "192.168.99.100"
DEPLOY_USER = "root"
SSH_CRED = "ssh-deploy-test"
REGISTRY = "192.168.99.100:5000"
REGISTRY_CRED = "registry-push-test"

BASIC = b64encode(f"{JUSER}:{JPASS}".encode("ascii")).decode("ascii")

# 同一 Cookie 会话：crumb 与后续 POST 必须共享 JSESSIONID
COOKIE_JAR = http.cookiejar.CookieJar()
OPENER = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(COOKIE_JAR))


def api(method: str, path: str, body: bytes | None = None, content_type: str = "application/xml") -> bytes:
    crumb_req = urllib.request.Request(
        f"{JENKINS}/crumbIssuer/api/json",
        headers={"Authorization": f"Basic {BASIC}"},
    )
    with OPENER.open(crumb_req, timeout=30) as resp:
        crumb = json.loads(resp.read().decode("utf-8"))
    headers = {
        "Authorization": f"Basic {BASIC}",
        crumb["crumbRequestField"]: crumb["crumb"],
    }
    if body is not None:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(f"{JENKINS}{path}", data=body, headers=headers, method=method)
    try:
        with OPENER.open(req, timeout=60) as resp:
            return resp.read()
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")[:2000]
        raise RuntimeError(f"{method} {path} -> HTTP {e.code}: {detail}") from e


def job_exists(name: str) -> bool:
    try:
        api("GET", f"/job/{name}/api/json")
        return True
    except RuntimeError as e:
        if "HTTP 404" in str(e):
            return False
        raise


def pipeline_xml(description: str, script: str) -> bytes:
    """Job 触发参数：BRANCH（字符串，可填任意远程分支）。"""
    xml = f"""<?xml version='1.1' encoding='UTF-8'?>
<flow-definition plugin="workflow-job">
  <description>{description}</description>
  <keepDependencies>false</keepDependencies>
  <properties>
    <hudson.model.ParametersDefinitionProperty>
      <parameterDefinitions>
        <hudson.model.StringParameterDefinition>
          <name>BRANCH</name>
          <defaultValue>{DEFAULT_BRANCH}</defaultValue>
          <description>Git branch to build and deploy</description>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
      </parameterDefinitions>
    </hudson.model.ParametersDefinitionProperty>
  </properties>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition" plugin="workflow-cps">
    <script><![CDATA[{script}]]></script>
    <sandbox>true</sandbox>
  </definition>
  <triggers/>
  <disabled>false</disabled>
</flow-definition>
"""
    return xml.encode("utf-8")


def frontend_script(app_dir: str, image_repo: str, container: str, app_port: str, health_url: str) -> str:
    # 前端环境在构建期：VITE_* 来自应用目录 .env / .env.production（或 docker build-arg）。
    # 流水线只执行 npm run build（vite 默认 production mode），不注入运行时环境开关。
    return f"""pipeline {{
  agent {{ label 'node24' }}
  parameters {{
    string(name: 'BRANCH', defaultValue: '{DEFAULT_BRANCH}', description: 'Git branch to build and deploy', trim: true)
  }}
  environment {{
    APP_DIR        = '{app_dir}'
    GIT_URL        = '{GIT_URL}'
    GIT_CRED_ID    = '{GIT_CRED_ID}'
    DEPLOY_HOST    = '{DEPLOY_HOST}'
    DEPLOY_USER    = '{DEPLOY_USER}'
    SSH_CRED       = '{SSH_CRED}'
    REGISTRY       = '{REGISTRY}'
    REGISTRY_CRED  = '{REGISTRY_CRED}'
    IMAGE_REPO     = '{image_repo}'
    CONTAINER_NAME = '{container}'
    APP_PORT       = '{app_port}'
    HEALTH_URL     = '{health_url}'
    GIT_BRANCH     = "${{params.BRANCH?.trim() ?: '{DEFAULT_BRANCH}'}}"
  }}
  stages {{
    stage('Checkout') {{
      steps {{
        echo "Build branch: ${{env.GIT_BRANCH}}"
        checkout([$class: 'GitSCM',
          branches: [[name: "*/${{env.GIT_BRANCH}}"]],
          userRemoteConfigs: [[url: env.GIT_URL, credentialsId: env.GIT_CRED_ID]]])
      }}
    }}
    stage('Quality') {{
      steps {{
        dir("${{APP_DIR}}") {{
          sh 'npm ci'
          sh 'npm run lint'
          sh 'npm run type-check'
          sh 'npm run build'
        }}
      }}
    }}
    stage('Image') {{
      steps {{
        script {{
          env.SHORT_COMMIT = sh(returnStdout: true, script: 'git rev-parse --short=12 HEAD').trim()
          sh \"\"\"
            set -e
            docker build -t "${{IMAGE_REPO}}:${{SHORT_COMMIT}}" -t "${{IMAGE_REPO}}:latest" "${{APP_DIR}}"
          \"\"\"
        }}
      }}
    }}
    stage('Deploy') {{
      steps {{
        sshagent(credentials: ["${{SSH_CRED}}"]) {{
          withCredentials([usernamePassword(credentialsId: "${{REGISTRY_CRED}}", usernameVariable: 'RUSER', passwordVariable: 'RPASS')]) {{
            sh \"\"\"
              set -e
              echo "$RPASS" | docker login "${{REGISTRY}}" -u "$RUSER" --password-stdin
              docker push "${{IMAGE_REPO}}:${{SHORT_COMMIT}}"
              docker push "${{IMAGE_REPO}}:latest"
              ssh "${{DEPLOY_USER}}@${{DEPLOY_HOST}}" "
                docker network create project-ai 2>/dev/null || true
                docker rm -f {container} 2>/dev/null || true
                docker pull {image_repo}:${{SHORT_COMMIT}}
                docker run -d --name {container} --restart unless-stopped \\
                  --network project-ai -p {app_port}:80 \\
                  {image_repo}:${{SHORT_COMMIT}}
              "
            \"\"\"
          }}
        }}
      }}
    }}
    stage('Smoke') {{
      steps {{
        sshagent(credentials: ["${{SSH_CRED}}"]) {{
          sh \"\"\"
            set -e
            ssh "${{DEPLOY_USER}}@${{DEPLOY_HOST}}" "curl -fsS --retry 5 --retry-delay 3 --max-time 20 '{health_url}'"
          \"\"\"
        }}
      }}
    }}
  }}
  post {{
    failure {{
      echo "Failed: ${{env.JOB_NAME}} #${{env.BUILD_NUMBER}} branch=${{env.GIT_BRANCH}}"
    }}
  }}
}}
"""


def backend_script(sys_dir: str, module: str, image_repo: str, container: str, app_port: str, health_url: str) -> str:
    # 后端环境在代码里：application.yml spring.profiles.active（如 local/test）。
    # docker run 只启动 jar，不传 --spring.profiles.active。
    return f"""pipeline {{
  agent {{ label 'jdk21' }}
  parameters {{
    string(name: 'BRANCH', defaultValue: '{DEFAULT_BRANCH}', description: 'Git branch to build and deploy', trim: true)
  }}
  environment {{
    SYS_DIR        = '{sys_dir}'
    MODULE         = '{module}'
    GIT_URL        = '{GIT_URL}'
    GIT_CRED_ID    = '{GIT_CRED_ID}'
    DEPLOY_HOST    = '{DEPLOY_HOST}'
    DEPLOY_USER    = '{DEPLOY_USER}'
    SSH_CRED       = '{SSH_CRED}'
    REGISTRY       = '{REGISTRY}'
    REGISTRY_CRED  = '{REGISTRY_CRED}'
    IMAGE_REPO     = '{image_repo}'
    CONTAINER_NAME = '{container}'
    APP_PORT       = '{app_port}'
    HEALTH_URL     = '{health_url}'
    GIT_BRANCH     = "${{params.BRANCH?.trim() ?: '{DEFAULT_BRANCH}'}}"
  }}
  stages {{
    stage('Checkout') {{
      steps {{
        echo "Build branch: ${{env.GIT_BRANCH}}"
        checkout([$class: 'GitSCM',
          branches: [[name: "*/${{env.GIT_BRANCH}}"]],
          userRemoteConfigs: [[url: env.GIT_URL, credentialsId: env.GIT_CRED_ID]]])
      }}
    }}
    stage('Test') {{
      steps {{
        dir("${{SYS_DIR}}") {{
          sh "mvn -pl ${{MODULE}} -am test"
        }}
      }}
    }}
    stage('Package') {{
      steps {{
        dir("${{SYS_DIR}}") {{
          sh "mvn -pl ${{MODULE}} -am package -DskipTests"
        }}
      }}
    }}
    stage('Image') {{
      steps {{
        script {{
          env.SHORT_COMMIT = sh(returnStdout: true, script: 'git rev-parse --short=12 HEAD').trim()
          sh \"\"\"
            set -e
            docker build -t "${{IMAGE_REPO}}:${{SHORT_COMMIT}}" -t "${{IMAGE_REPO}}:latest" "${{SYS_DIR}}/${{MODULE}}"
          \"\"\"
        }}
      }}
    }}
    stage('Deploy') {{
      steps {{
        sshagent(credentials: ["${{SSH_CRED}}"]) {{
          withCredentials([usernamePassword(credentialsId: "${{REGISTRY_CRED}}", usernameVariable: 'RUSER', passwordVariable: 'RPASS')]) {{
            sh \"\"\"
              set -e
              echo "$RPASS" | docker login "${{REGISTRY}}" -u "$RUSER" --password-stdin
              docker push "${{IMAGE_REPO}}:${{SHORT_COMMIT}}"
              docker push "${{IMAGE_REPO}}:latest"
              ssh "${{DEPLOY_USER}}@${{DEPLOY_HOST}}" "
                docker network create project-ai 2>/dev/null || true
                docker rm -f {container} 2>/dev/null || true
                docker pull {image_repo}:${{SHORT_COMMIT}}
                docker run -d --name {container} --restart unless-stopped \\
                  --network project-ai -p {app_port}:{app_port} \\
                  {image_repo}:${{SHORT_COMMIT}}
              "
            \"\"\"
          }}
        }}
      }}
    }}
    stage('Smoke') {{
      steps {{
        sshagent(credentials: ["${{SSH_CRED}}"]) {{
          sh \"\"\"
            set -e
            ssh "${{DEPLOY_USER}}@${{DEPLOY_HOST}}" "curl -fsS --retry 5 --retry-delay 3 --max-time 20 '{health_url}'"
          \"\"\"
        }}
      }}
    }}
  }}
  post {{
    failure {{
      echo "Failed: ${{env.JOB_NAME}} #${{env.BUILD_NUMBER}} branch=${{env.GIT_BRANCH}}"
    }}
  }}
}}
"""


JOBS = [
    {
        "name": "user-admin-cd",
        "desc": "user-admin frontend docker static",
        "kind": "fe",
        "app_dir": "frontend/user-admin",
        "image": f"{REGISTRY}/project_ai/user-admin",
        "container": "user-admin",
        "port": "5173",
        "health": "http://127.0.0.1:5173/healthz",
    },
    {
        "name": "auth-admin-cd",
        "desc": "auth-admin frontend docker static",
        "kind": "fe",
        "app_dir": "frontend/auth-admin",
        "image": f"{REGISTRY}/project_ai/auth-admin",
        "container": "auth-admin",
        "port": "5175",
        "health": "http://127.0.0.1:5175/healthz",
    },
    {
        "name": "auth-portal-cd",
        "desc": "auth-portal frontend docker static",
        "kind": "fe",
        "app_dir": "frontend/auth-portal",
        "image": f"{REGISTRY}/project_ai/auth-portal",
        "container": "auth-portal",
        "port": "5174",
        "health": "http://127.0.0.1:5174/healthz",
    },
    {
        "name": "blog-admin-cd",
        "desc": "blog-admin frontend docker static",
        "kind": "fe",
        "app_dir": "frontend/blog-admin",
        "image": f"{REGISTRY}/project_ai/blog-admin",
        "container": "blog-admin",
        "port": "5176",
        "health": "http://127.0.0.1:5176/healthz",
    },
    {
        "name": "auth-service-cd",
        "desc": "auth-service docker jar",
        "kind": "be",
        "sys_dir": "backend/auth",
        "module": "auth-service",
        "image": f"{REGISTRY}/project_ai/auth-service",
        "container": "auth-service",
        "port": "9080",
        "health": "http://127.0.0.1:9080/actuator/health",
    },
    {
        "name": "blog-gateway-cd",
        "desc": "blog-gateway docker jar",
        "kind": "be",
        "sys_dir": "backend/blog",
        "module": "blog-gateway",
        "image": f"{REGISTRY}/project_ai/blog-gateway",
        "container": "blog-gateway",
        "port": "9100",
        "health": "http://127.0.0.1:9100/actuator/health",
    },
    {
        "name": "blog-service-cd",
        "desc": "blog-service docker jar",
        "kind": "be",
        "sys_dir": "backend/blog",
        "module": "blog-service",
        "image": f"{REGISTRY}/project_ai/blog-service",
        "container": "blog-service",
        "port": "19100",
        "health": "http://127.0.0.1:19100/actuator/health",
    },
    {
        "name": "user-gateway-cd",
        "desc": "user-gateway docker jar",
        "kind": "be",
        "sys_dir": "backend/user",
        "module": "user-gateway",
        "image": f"{REGISTRY}/project_ai/user-gateway",
        "container": "user-gateway",
        "port": "8173",
        "health": "http://127.0.0.1:8173/actuator/health",
    },
    {
        "name": "user-service-cd",
        "desc": "user-service docker jar",
        "kind": "be",
        "sys_dir": "backend/user",
        "module": "user-service",
        "image": f"{REGISTRY}/project_ai/user-service",
        "container": "user-service",
        "port": "18173",
        "health": "http://127.0.0.1:18173/actuator/health",
    },
]


def main() -> int:
    for j in JOBS:
        name = j["name"]
        print(f"==== {name} ====", flush=True)
        if j["kind"] == "fe":
            script = frontend_script(
                j["app_dir"], j["image"], j["container"], j["port"], j["health"]
            )
        else:
            script = backend_script(
                j["sys_dir"], j["module"], j["image"], j["container"], j["port"], j["health"]
            )
        xml = pipeline_xml(j["desc"], script)
        if job_exists(name):
            api("POST", f"/job/{name}/config.xml", body=xml)
            print(f"updated {name}")
        else:
            api("POST", f"/createItem?name={name}", body=xml)
            print(f"created {name}")

    listing = api("GET", "/api/json?tree=jobs[name]").decode("utf-8")
    print("==== jobs ====")
    print(listing)

    fe = api("GET", "/job/user-admin-cd/config.xml").decode("utf-8")
    be = api("GET", "/job/user-service-cd/config.xml").decode("utf-8")
    print("==== checks ====")
    print("FE has BRANCH:", "<name>BRANCH</name>" in fe)
    print("FE no DEPLOY_ENV:", "DEPLOY_ENV" not in fe)
    print("FE no profiles inject:", "spring.profiles" not in fe)
    print("BE has BRANCH:", "<name>BRANCH</name>" in be)
    print("BE no profiles inject:", "spring.profiles" not in be)
    print("BE no deployEnvOf:", "deployEnvOf" not in be)
    return 0


if __name__ == "__main__":
    sys.exit(main())
