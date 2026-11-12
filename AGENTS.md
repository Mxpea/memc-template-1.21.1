# Repository Guidelines

## 项目结构与模块组织

- Java 主代码位于 `src/main/java/com/mxpea/memc/`：模组入口、客户端逻辑和配置放在基础包；命令放入 `command/`，第三方集成按依赖划分子包（如 `integration/ae2/`）。
- 资源位于 `src/main/resources/`，当前包含 `assets/memc/lang/` 本地化文件；模组元数据模板位于 `src/main/templates/`。数据生成输出到 `src/generated/resources/`。
- Gradle 配置在根目录的 `build.gradle`、`gradle.properties` 和 `settings.gradle`；CI 构建工作流位于 `.github/workflows/build.yml`。`build/`、`.gradle/`、`run/` 属于生成或本地运行目录，不要提交。

## 构建、测试与本地开发

项目使用 Gradle Wrapper 和 Java 21。Windows 示例：

- `./gradlew.bat build`：编译并执行构建检查；CI 使用此命令。
- `./gradlew.bat test`：运行 `src/test/java/` 中的 JUnit Jupiter 测试。
- `./gradlew.bat runClient`：启动开发客户端；`runServer` 可启动开发服务器。
- `./gradlew.bat runGameTestServer`：执行 NeoForge GameTest（添加测试后使用）。
- `./gradlew.bat runData`：运行数据生成器；检查并提交预期的生成资源变更。

类 Unix 环境使用 `./gradlew`。

## 代码风格与命名

Java 文件使用 UTF-8、4 空格缩进；包名全小写，类名使用 `UpperCamelCase`，方法和字段使用 `lowerCamelCase`，常量使用 `UPPER_SNAKE_CASE`。新代码放入现有 `com.mxpea.memc` 包结构，并将可选依赖的专属逻辑隔离在对应集成子包。JSON 文件保持有效格式，资源命名空间使用小写 `memc`。项目未配置独立格式化或 lint 工具。

## 测试指南

提交前至少运行 `./gradlew.bat build`。新增纯逻辑测试使用 JUnit Jupiter，放入 `src/test/java/`，测试类以 `Test` 结尾；涉及 Minecraft 注册、命令或运行时交互时，优先补充 NeoForge GameTest，并用 `runGameTestServer` 验证。没有配置覆盖率门槛。

## 提交与 Pull Request

当前 Git 历史仅有初始化提交，尚无既定提交格式；建议使用简短、动词开头的说明，例如 `Add AE2 crafting integration`。PR 应说明目的、主要改动、验证命令及兼容性影响，关联相关问题；涉及界面或游戏内表现时附截图。提交前确认没有意外纳入构建产物、运行目录或本机配置。
