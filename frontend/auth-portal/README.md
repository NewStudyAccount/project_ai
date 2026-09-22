# auth-portal

统一认证前端（Vue3 + Vite + TypeScript + Element Plus）。

## 脚本

```bash
npm install
npm run dev      # 本地 5173，/auth 代理到 8081
npm run build    # 产出 dist/，由 Nginx 静态托管
npm run type-check
```

## 能力

- 账密登录 → `POST /auth/login`
- 登出 → `POST /auth/logout`
- 静默续期 → `POST /auth/refresh`（401 时轮转）
- SSO：URL 带 `client_id`/`return_url` 登录后跳 `/auth/sso/authorize`
- 回调页 `/sso/callback?code&client_id` → `POST /auth/sso/token`

## 约定

- **禁止**存储/打印 password；Token 不写 console
- sid 为服务端 HttpOnly Cookie，前端不读
- 构建产物部署：Nginx 静态 + `/auth` 反代 auth-service（见 `deploy/nginx/auth.conf`）
