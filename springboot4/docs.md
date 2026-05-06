## springboot4 项目 UI 执行顺序与逻辑分析

### 一、整体架构概览

```
┌──────────────────────────────────────────────────┐
│                  前端 (index.html)                │
│  Vanilla JS + Fetch API + 内联CSS                │
│         │  HTTP 请求 (GET/POST/PUT/DELETE)       │
│         ▼                                        │
│  ┌─────────────────────────────────────┐         │
│  │  后端 - Controller 层               │         │
│  │  EmployeeController (REST)          │         │
│  │  DepartmentController (REST)        │         │
│  └──────────┬──────────────────────────┘         │
│             │ 调用 Service                        │
│  ┌──────────▼──────────────────────────┐         │
│  │  后端 - Service 层                  │         │
│  │  EmployeeService                    │         │
│  │  DepartmentService                  │         │
│  └──────────┬──────────────────────────┘         │
│             │ 调用 JPA Repository                 │
│  ┌──────────▼──────────────────────────┐         │
│  │  后端 - Repository 层 (Spring Data) │         │
│  │  EmployeeRepository                  │         │
│  │  DepartmentRepository                │         │
│  └──────────┬──────────────────────────┘         │
│             │ JPA / Hibernate                    │
│  ┌──────────▼──────────────────────────┐         │
│  │  数据库 (H2 MySQL)                  │         │
│  │  department / employee 表           │         │
│  └─────────────────────────────────────┘         │
└──────────────────────────────────────────────────┘
```

---

### 二、启动阶段执行顺序

#### 1. 应用启动 (`EmployeeApplication.main`)
- 标注 `@SpringBootApplication`，启动自动配置
- Spring Boot 4.0.0 + Java 21
- 激活 **虚拟线程**（`spring.threads.virtual.enabled: true`），所有请求处理线程自动变为虚拟线程

#### 2. 数据库初始化
```
application-h2.yml 配置（默认激活 H2 内存库）：
  └─ schema-h2.sql → 创建 department + employee 表
  └─ data.sql      → 插入 3 个部门 + 10 个员工初始数据
  └─ JPA ddl-auto: create-drop → 每次启动重建表
```

#### 3. Bean 注册顺序
```
WebConfig          → 配置 CORS (允许跨域访问 /api/**)
GlobalExceptionHandler → 注册全局异常处理器（@RestControllerAdvice）
DepartmentController   → 注入 DepartmentService
EmployeeController     → 注入 EmployeeService
DepartmentService      → 注入 DepartmentRepository
EmployeeService        → 注入 EmployeeRepository + DepartmentRepository
DepartmentRepository   → JPA 自动实现 CRUD
EmployeeRepository     → JPA 自动实现 CRUD + 自定义查询方法
```

---

### 三、页面加载时的执行流程

当用户在浏览器访问 `http://localhost:8080/` 时，Spring Boot 自动提供 `static/index.html`。

`DOMContentLoaded` 事件触发后，依次执行 4 个并行初始化函数：

```
document.addEventListener('DOMContentLoaded', () => {
    loadStats();          // ① 加载顶部统计数据
    loadDepartments();    // ② 加载部门列表
    loadEmployees();      // ③ 加载员工列表
    loadDepartmentOptions(); // ④ 加载部门下拉筛选选项
});
```

#### ① `loadStats()` — 统计数据加载（3个请求并行）

```
┌─ fetch(GET /api/departments?page=0&size=1) ─→ 获取总记录数(totalElements)
├─ fetch(GET /api/employees?page=0&size=1)   ─→ 获取总记录数(totalElements)
└─ fetch(GET /actuator/health)               ─→ 获取健康状态 (UP/DOWN)
        ↓
Promise.all 并行等待 3 个请求全部完成
        ↓
更新 DOM:
  #deptCount = department.totalElements  → 显示部门总数
  #empCount  = employee.totalElements   → 显示员工总数
  #status    = health.status            → 显示"正常"或"异常"
```

**后端调用链**（以部门为例）：
```
GET /api/departments?page=0&size=1
    → DepartmentController.list(page=0, size=1)
        → DepartmentService.list(page=0, size=1)
            → departmentRepository.findAll(PageRequest.of(0, 1, Sort.by(DESC, "id")))
                → JPA 自动生成 SELECT ... LIMIT 1
        ← 返回 Page<Department>（含 totalElements=3）
    ← 返回 ApiResponse.success(page)
```

#### ② `loadDepartments()` — 部门列表加载

```
fetch(GET /api/departments?page=0&size=10)
    ↓
解析 JSON → data.code === 200 ?
    ↓是
renderDepartmentTable(data.data.content)
    └─ 遍历 departments 数组，生成 HTML 表格：
       | ID | 部门名称 | 描述 | 创建时间 | 操作(编辑/删除) |
    ↓
renderPagination('deptPagination', data.data, page, loadDepartments)
    └─ 根据 totalPages 生成分页按钮
       ├─ 当前页高亮
       ├─ 显示前后 3 页的页码
       ├─ "..." 省略号
       └─ 上一页/下一页按钮
```

#### ③ `loadEmployees()` — 员工列表加载（支持筛选）

```
const deptId = document.getElementById('deptFilter').value  // 部门筛选值
const name   = document.getElementById('empSearch').value   // 姓名搜索词

url = /api/employees?page=0&size=10
if (deptId) url += &departmentId=deptId
if (name)   url += &name=encodeURIComponent(name)

fetch(url) → 后端处理
```

**后端查询逻辑**（`EmployeeService.list`）：
```java
// 三种查询分支 + 一个全查兜底
if (departmentId != null && name != null)    → findByDepartmentIdAndNameContaining()
else if (departmentId != null)               → findByDepartmentId()
else if (name != null)                       → findByNameContaining()
else                                         → findAll()
// 全部按 id DESC 排序
```

#### ④ `loadDepartmentOptions()` — 部门下拉框填充

```
fetch(GET /api/departments?page=0&size=100)
    ↓
遍历返回的部门列表 → 动态创建 <option> 元素
    ↓
追加到 <select id="deptFilter"> 中（"所有部门"之后）
```

---

### 四、用户交互触发流程

#### 4.1 搜索部门 / 员工

```
用户输入搜索词 → 点击"搜索"按钮
    ↓
searchDepartments()           searchEmployees()
    └─ loadDepartments(0)        └─ loadEmployees(0)
    （重置到第一页）              （带上搜索框和下拉框的值筛选）
    ↓                                  ↓
重新发起分页查询 → 重新渲染表格 + 分页
```

#### 4.2 分页切换

```
用户点击页码按钮 → 调用 loadDepartments(page) 或 loadEmployees(page)
    ↓
重新请求对应页数据 → 更新表格 + 高亮新页码
```

---

### 五、完整的数据流示意图

以"部门列表加载"为例展示完整调用链：

```
┌─── 浏览器 ──────────────────────────────────────────────────────────┐
│                                                                      │
│  loadDepartments(0)                                                  │
│    ↓                                                                 │
│  fetch('http://localhost:8080/api/departments?page=0&size=10')       │
│    ↓                                                                 │
│  ┌─ HTTP GET 请求 ────────────────────────────────────────────────┐  │
│  │  Headers: Accept: application/json                            │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
┌─── Spring Boot ──────────▼─────────────────────────────────────────┐
│                                                                      │
│  DispatcherServlet 接收请求                                          │
│    ↓                                                                │
│  HandlerMapping 匹配路径 /api/departments                           │
│    → 匹配到 DepartmentController.list()                              │
│    ↓                                                                │
│  虚拟线程处理请求 (Project Loom)                                    │
│    ↓                                                                │
│  DepartmentController.list(page=0, size=10)                         │
│    ↓ 调用                                                            │
│  DepartmentService.list(0, 10)                                      │
│    ↓ 调用                                                            │
│  departmentRepository.findAll(                                      │
│    PageRequest.of(0, 10, Sort.by(DESC, "id"))                       │
│  )                                                                  │
│    ↓ 生成 SQL                                                        │
│  Hibernate: SELECT * FROM department                                │
│             ORDER BY id DESC LIMIT 10                               │
│    ↓                                                                │
│  返回 Page<Department> 包含:                                        │
│    ├─ content: [部门实体列表]                                        │
│    ├─ totalElements: 3                                              │
│    ├─ totalPages: 1                                                 │
│    ├─ number: 0                                                     │
│    └─ size: 10                                                      │
│    ↓                                                                │
│  包装为 ApiResponse.success(page) → JSON 序列化                     │
│    ↓                                                                │
│  返回 HTTP 200 Response                                             │
│  { "code": 200, "message": "success", "data": { "content": [...] } }│
│                                                                      │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
┌─── 浏览器 ───────────────▼──────────────────────────────────────────┐
│                                                                      │
│  收到 JSON 响应                                                      │
│    ↓                                                                │
│  data.code === 200 ?                                                │
│    ↓是                                                              │
│  renderDepartmentTable(data.data.content)                           │
│    └─ 遍历 departments → 拼接 <table> HTML                          │
│    └─ 插入到 <div id="deptTable">                                   │
│    ↓                                                                │
│  renderPagination('deptPagination', data.data, 0, loadDepartments)  │
│    └─ 生成分页按钮 HTML                                              │
│    └─ 插入到 <div id="deptPagination">                              │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

### 六、实体关系与数据库设计

```
┌──────────────────────────┐       ┌─────────────────────────────┐
│  Department               │       │  Employee                  │
├──────────────────────────┤       ├─────────────────────────────┤
│  id: BIGINT (PK, AUTO)   │◄──────┤  department_id: BIGINT (FK)│
│  name: VARCHAR(50)       │  1:N   │  id: BIGINT (PK, AUTO)    │
│  description: VARCHAR    │       │  name: VARCHAR(50)         │
│  create_time: DATETIME   │       │  email: VARCHAR(100)       │
│  update_time: DATETIME   │       │  phone: VARCHAR(20)        │
└──────────────────────────┘       │  create_time: DATETIME     │
                                   │  update_time: DATETIME     │
                                   └─────────────────────────────┘
```

- `Employee` 通过 `@ManyToOne(fetch=LAZY)` + `@JoinColumn(name="department_id")` 关联部门
- 序列化时自动输出 `departmentName`（Jackson 通过 `department.name` 属性路径输出）
- `@PrePersist` / `@PreUpdate` 自动维护 create_time 和 update_time

---

### 七、异常处理流程

```
前端请求
    ↓
Controller 接收请求
    ↓
Service 抛出 BusinessException
    ↓
GlobalExceptionHandler.handleBusinessException()
    ↓
返回 { "code": 500, "message": "员工不存在，ID: 99", "data": null }
    ↓
前端 fetch 正常返回（HTTP 200，但 code ≠ 200）
    ↓
JS 判断 data.code === 200 → 否 → showError()
```

三种异常处理：
| 异常类型 | HTTP Status | 响应体 |
|---|---|---|
| `BusinessException` | 400 | `{code:500, message:"业务异常"}` |
| 参数校验失败 | 400 | `{code:400, message:"字段名: 错误提示,..."}` |
| 其他未捕获异常 | 500 | `{code:500, message:"服务器内部错误: ..."}` |

---

### 八、总结

**应用的技术栈亮点**：
- **Spring Boot 4.0.0** + **Java 21** — 最新版本
- **虚拟线程** — 所有请求处理由虚拟线程执行，无需手动配置线程池
- **H2 内存数据库** — 启动即初始化，关闭即销毁
- **Vanilla JS 前端** — 无框架依赖，纯 Fetch API 完成所有 CRUD 交互
- **统一响应格式** — `ApiResponse<T>` 包裹所有返回数据，code/message/data 三元组
- **分页查询** — 前后端都支持 Spring Data 的 Pageable 分页
- **CORS 跨域** — 支持前后端分离部署（`WebConfig`）

**当前状态**：前端已实现**数据展示层**（统计卡片、部门/员工列表表格、分页、搜索筛选），但**增删改操作**仅通过 `alert()` 占位，尚未对接后端 API。需要完善的是 `showAddDeptModal`、`editDepartment`、`deleteDepartment` 等函数与后端 `POST/PUT/DELETE` 接口的打通。