# 错接线矩阵可视化仿真系统

一个用于三相电压/电流错接线教学演示的前后端分离项目。系统通过断相矩阵 `D`、符号矩阵 `S`、置换矩阵 `P` 模拟接线故障，并实时展示总矩阵 `M = P · S · D` 与测量结果 `y = M · x`。

## 技术栈

- 后端：Java 17、Spring Boot 3.5、Maven
- 前端：Vue 3、Vite、Composition API、原生 CSS、lucide-vue-next
- 架构：REST API + 前端可视化仪表盘

## 项目结构

```text
wrong-wiring-visualizer/
  backend/
    pom.xml
    src/main/java/com/wrongwiring/visualizer/
      config/CorsConfig.java
      controller/SimulationController.java
      model/
      service/SimulationService.java
      WrongWiringVisualizerApplication.java
    src/main/resources/application.yml
    src/test/java/com/wrongwiring/visualizer/service/SimulationServiceTest.java
  frontend/
    package.json
    vite.config.js
    src/
      api/simulation.js
      components/
        HeaderBar.vue
        FaultPanel.vue
        WiringDiagram.vue
        MatrixCard.vue
        VectorCard.vue
        DiagnosisPanel.vue
        StatusBadge.vue
      views/SimulatorView.vue
      App.vue
      main.js
      styles.css
  README.md
```

## 启动方式

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

访问：

```text
http://127.0.0.1:5173
```

## API 示例

`POST /api/simulate`

```json
{
  "type": "voltage",
  "amplitude": 220,
  "phaseAngles": { "A": 0, "B": -120, "C": 120 },
  "broken": { "A": false, "B": true, "C": false },
  "reversed": { "A": true, "B": false, "C": false },
  "phaseOrder": ["A", "B", "C"],
  "noiseEnabled": false,
  "amplitudeNoisePercent": 1.0,
  "angleNoiseDegree": 0.5
}
```

返回内容包含 `D`、`S`、`P`、`M`、理论向量 `x`、测量向量 `y`、接线连接状态、诊断文本与是否正确接线。

## 数学原理

正确接线下：

```text
D = I
S = I
P = I
M = P · S · D = I
y = x
```

发生故障时：

```text
y = P · S · D · x
```

- `D`：断相矩阵，对角线为 `0` 的相被强制为零。
- `S`：符号矩阵，对角线为 `-1` 的相表示极性反接。
- `P`：置换矩阵，表示测量通道读取的相序变化。

## 页面功能

- 电压/电流仿真对象切换。
- 幅值、断相、反接、换相、噪声参数配置。
- SVG 接线图：正常线、故障线、断线虚线、反接标记、悬停说明。
- 三相相量坐标图：叠加显示理论向量 `x` 与测量向量 `y`。
- D/S/P/M 矩阵卡片：0、1、-1 分色展示。
- 理论向量与测量向量复数/极坐标展示。
- 故障诊断说明。
- 当前结果导出 JSON，矩阵导出 CSV。

## 三相相量坐标图怎么看

- 横轴 `Re` 表示复数实部，纵轴 `Im` 表示复数虚部。
- 箭头长度表示幅值，例如 220V 的向量会比 0V 的断相向量长。
- 箭头方向表示相角：A 相电压默认在 `0°`，B 相在 `-120°`，C 相在 `120°`。
- 青色 `A/B/C` 是理论正确向量 `x`，红色 `y1/y2/y3` 是测量通道看到的向量 `y`。
- 正确接线时，红色测量向量会和青色理论向量重合或方向一致。
- 断相时，对应测量向量幅值为 0，会落在坐标原点。
- 反接时，对应向量会旋转 `180°`，例如 `220∠0°` 会变成 `220∠180°`。
- 换相时，`y1/y2/y3` 的源相会改变，例如 A/B 换线后，通道 1 会读到 B 相，通道 2 会读到 A 相。

## 测试

后端单元测试：

```bash
cd backend
mvn test
```

前端构建检查：

```bash
cd frontend
npm run build
```

## 已实现功能

- 完整 Spring Boot REST API。
- 自研复数、三相向量、3x3 矩阵计算。
- 按 `P · S · D` 顺序计算总矩阵。
- Vue 3 深色教学仿真界面。
- 接线图与三相相量坐标轴双可视化。
- 随机故障、重置、JSON/CSV 导出。

## 简化说明

- 教学模式保留随机故障入口，没有加入“用户猜测后揭晓答案”的完整答题流程。
- 噪声由后端在每次计算时即时随机扰动，没有持久化随机种子。
