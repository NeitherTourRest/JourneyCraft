# JourneyCraft AGENTS.md

## Project Overview

**JourneyCraft** — 智能旅游规划系统，后端 Spring Boot + 前端 Vue 3 + 高德地图。

- **后端**: Spring Boot 4.0.5, Java 17, MySQL 8.0, MongoDB 7.0, MyBatis-Plus 3.5.x, Spring Security
- **前端**: Vue 3.5, Vite 8, TypeScript 6.0, Element Plus 2.x, Pinia, Vue Router 4, Axios, 高德地图 JS API 2.0
- **目录结构**: Monorepo 风格，`JourneyCraft/` = 后端, `frontend/` = 前端

---

## Key Commands

```bash
# Frontend
npm run dev         # Vite dev server (port 5173)
npm run build       # vue-tsc -b && vite build (先类型检查后构建)
npm test            # vitest run
npm run test:watch  # vitest
npx vue-tsc -b --noEmit  # 仅类型检查

# Backend (Maven)
mvn compile         # 编译
mvn spring-boot:run # 启动 (port 8080)
```

## Architecture

### Frontend Entrypoints
- `frontend/src/main.ts` — Vue app init, Pinia, auth restore
- `frontend/src/router/routes.ts` — 11 路由（懒加载）
- `frontend/src/views/NavigationView.vue` — 核心导航页面（~2700 行）

### Frontend Directory Structure
```
src/
  api/           Axios HTTP 封装 + 各模块 API
  composables/   Vue composables (useNavigation, useAmap)
  components/    地图组件 (AmapContainer)
  layouts/       布局 (AppHeader)
  router/        路由 + guards
  stores/        Pinia (auth)
  styles/        SCSS (element-theme, global, variables)
  types/         TypeScript 接口定义
  utils/         工具 (coord 坐标转换, findNearestNode)
  views/         页面 (Login, Register, Navigation, PlaceholderPage, NotFound)
```

### Backend Directory Structure
```
JourneyCraft/src/main/java/org/dsgroup/journeycraft/
  auth/       认证（login/register/logout）
  user/       用户信息
  scenic/     景点/景区
  navigation/ 路网/路径规划
  diary/      日记
  recommend/  推荐
  favorite/   收藏
  history/    历史
  expense/    账单
  group/      多人协同
  file/       文件上传
  common/     全局异常处理、配置、拦截器、工具类
```

### Module Dependency Order
`user → auth/favorite/history → scenic/file → navigation/recommend/group → diary/expense`

---

## Critical Architecture Facts

### Authentication (已重构为用户名认证)
- **不需要 token**: `/api/auth/login`, `/api/auth/register`
- **用户认证**: 登录返回 `{ userId, username, nickname, avatarUrl }`
- **前端存储**: `stores/auth.ts` — Pinia store 存 `userId/username/nickname` 到 localStorage
- **请求**: 不再需要 `Authorization` header，直接传 `userId` 参数
- `api/request.ts` 已简化 — 无 token 注入，无 401 刷新逻辑

### Coordinate System (WGS-84 ↔ GCJ-02)
- **后端 API 返回 WGS-84** (OSM 数据原生)
- **高德地图使用 GCJ-02**
- 前端必须转换: `wgs84ToGcj02(lng, lat)` in `utils/coord.ts`
- 使用 `coordtransform` npm 包
- `pathNodesToGcj02()` 批量转换路线节点

### API Response Format
```typescript
interface ApiResponse<T> {
  code: number    // 200=成功
  message: string
  data: T
}
```

### Navigation Module (核心功能)
- **`useNavigation()` composable**: 管理路线规划状态 (scenicAreaId, startNodeId, endNodeIds, endScenicAreaId)
- **路线规划**: `POST /api/navigation/route` with query params (form-urlencoded → URL params)
- 支持 `endScenicAreaId`（以景区为终点，后端自动找入口节点）
- **多目标**: `POST /api/navigation/multi-route`
- **路网节点**: `GET /api/navigation/nodes/scenic/{id}` (返回景区关联的所有节点)
- **路径绘制**: `drawPolyline()` in AmapContainer.vue

### Unified Search (NavigationView)
- 一个 `el-autocomplete` 搜索框，搜索类型：景区、路网节点、设施、建筑、拍照点
- 无景区上下文时只搜景区；有上下文时并行搜所有类型
- 搜索结果类型徽章颜色：
  - 🏔️ 景区 #FF6B35 | 🚏 节点 #2196F3 | 🏪 设施 #4CAF50 | 🏛️ 建筑 #9C27B0 | 📸 拍照点 #E040FB
- 景区标记 `isPrimary` 节点 → 地图只显示该标记

### PathNode 字段
```typescript
interface PathNode {
  nodeId: number; name: string; sequence: number
  latitude: number; longitude: number
  action: 'start' | 'visit' | 'end'
  arrivalTime: string | null
  scenicAreaName?: string   // 显示回退
  scenicAreaId?: number     // 所属景区
  isPrimary?: boolean       // 最佳显示节点
}
```

---

## Development Gotchas

1. **PowerShell gotchas**: `&&` 在 PowerShell 5.1 不能用，必须用 `; if ($?) { }`。中文编码在 PowerShell 输出显示乱码，但文件内容正确（UTF-8）。
2. **Vite proxy**: `/api/*` → `http://localhost:8080` 开发时后端必须运行。
3. **TypeScript strict**: `vue-tsc -b` 检查严格，`TS6133` (unused variable) 也报错。前缀 `_` 抑制。
4. **Auto-import**: Element Plus 组件通过 `unplugin-vue-components` 自动按需引入，不需要手动 import。
5. **Auth store 关键顺序**: `main.ts` 中 `getStoredAuth()` 必须在 `router.use()` 之前调用。
6. **Amap loader**: `@amap/amap-jsapi-loader` 需要在 `window._AMapSecurityConfig` 设置后加载。
7. **Route planning params**: 前端发送 POST 但参数在 URL query string 中（非 body），因为后端用 `@RequestParam`。
8. **前端构建命令**: 先 `vue-tsc -b` 类型检查，再 `vite build`。单独检查用 `npx vue-tsc -b --noEmit`。

---

## Routes (Frontend)

| Path | Component | Auth Required |
|------|-----------|---------------|
| `/` | → redirect /scenic | - |
| `/login` | LoginView | No |
| `/register` | RegisterView | No |
| `/scenic` | PlaceholderPage | Yes |
| `/navigation/:scenicId?` | NavigationView | Yes |
| `/diary` | PlaceholderPage | Yes |
| `/favorites` | PlaceholderPage | Yes |
| `/profile` | PlaceholderPage | Yes |
| `/404` | NotFound | No |

---

## Database
- MySQL 8.0 (user: root, password: root123, database: journeycraft, port: 3306)
- MongoDB 7.0 (日记/评论)
- MinIO (文件存储，可选)
- OSM 数据: 420K+ 路网节点从 Changping.osm.pbf 导入

---

## Current State (May 2026)

- **前端**: NavigationView 核心导航已实现，包含统一搜索、路线规划、地图交互、拥挤度/设施/拍照点 tabs
- **后端**: TokenAuthInterceptor 已移除，改为用户名认证。路径规划支持 `endScenicAreaId`，景区关联多路网节点。
- **未实现**: Group, Expense, Recommend 模块无 API；室内导航和反向游览返回假数据
- **CS 课程设计**: 核心算法（最短路径、TSP、排序等）必须基于**自设计数据结构**实现，不能仅依赖现成库函数
