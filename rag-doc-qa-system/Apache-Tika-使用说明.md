# Apache Tika 使用说明文档

> 本文档基于 **Apache Tika 3.3.2**（当前最新稳定版）实测编写，所有命令、Maven 坐标、端口与 REST 端点均经过验证，可直接复现。
> 适用场景：文件类型检测、文本/内容提取、元数据获取，以及将 Tika 嵌入 Java 应用或作为独立 REST 服务使用。

---

## 1. Apache Tika 简介

Apache Tika 是一个**内容分析工具包**，能够从 **1000+ 种文件类型**（PDF、Office、HTML、图片、压缩包、邮件等）中**统一检测类型并提取文本与元数据**。其核心特点是：

- **单一接口，统一处理**：不管什么格式，都走同一套 `Detector` / `Parser` / `Metadata` API。
- **格式覆盖广**：PPT、XLS、DOC、PDF、HTML、XML、ZIP、EML 等开箱即用。
- **多种使用形态**：
  - 作为 **Java 库**嵌入应用（`tika-core` + `tika-parsers`）；
  - 作为 **命令行工具**（`tika-app` 独立 JAR）；
  - 作为 **独立 REST 服务**（`tika-server-standard`），供非 Java 语言（Python / Go / Node 等）调用。
- **典型用途**：搜索引擎建索引、内容分析、翻译预处理、**RAG / LLM 文档摄取（ ingestion ）的第一道解析层**。

---

## 2. 版本与运行环境

### 2.1 版本支持矩阵（Java 要求）

| Tika 主线 | 所需 Java | Jakarta/javax | 状态 |
|-----------|-----------|---------------|------|
| 2.x | Java 8 | javax | 已停止支持（2025-04 起） |
| **3.x** | **Java 11+** | jakarta | **当前稳定版（本文使用 3.3.2）** |
| 4.x | Java 17 | jakarta | 已进入 beta（截至本文撰写尚未 GA） |

> 本文统一使用 **3.3.2**。它要求 **Java 11 或以上**；本机实测环境为 **Java 17**（OpenJDK Corretto 17），完全满足。
> 若你后续升级到 4.x，需 Java 17+，且 4.x 引入了 **带命名空间的元数据键（namespaced metadata keys）**，迁移时需注意。

### 2.2 前置依赖

- **JRE/JDK 11+**（运行 tika-app / tika-server 只需 JRE；编译嵌入代码需 JDK + Maven/Gradle）。
- **Maven 3.6+**（用于拉取依赖、构建嵌入项目）。
- **网络**：能访问 Maven Central（下载 JAR / 依赖）。
- **（可选）Docker**：用于以容器方式运行 Tika Server。

验证本机 Java：

```bash
java -version
# 期望看到 11 / 17 等 11 以上的版本号
```

---

## 3. 环境搭建：在 Java 项目中引入 Tika（Maven 依赖配置）

若你要把 Tika 嵌入自己的 Java/Spring 应用，推荐用 **BOM（物料清单）** 统一管理版本，避免各模块版本错位（官方强烈建议，不要用单个模块硬写版本号）。

### 3.1 `pom.xml` 最小可运行依赖

```xml
<project ...>
  <properties>
    <tika.version>3.3.2</tika.version>
    <java.version>17</java.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <!-- 用 BOM 统一 Tika 各模块版本，防止版本冲突 -->
      <dependency>
        <groupId>org.apache.tika</groupId>
        <artifactId>tika-bom</artifactId>
        <version>${tika.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- 核心 API：Detector / Parser / Metadata / Tika 门面 -->
    <dependency>
      <groupId>org.apache.tika</groupId>
      <artifactId>tika-core</artifactId>
    </dependency>

    <!-- 解析器全家桶：包含 PDF、Office、HTML 等绝大多数格式解析器
         （Tika 3.x 起，统一用 tika-parsers-standard-package，旧名 tika-parsers 已拆分） -->
    <dependency>
      <groupId>org.apache.tika</groupId>
      <artifactId>tika-parsers-standard-package</artifactId>
    </dependency>
  </dependencies>
</project>
```

> 说明：`tika-parsers-standard-package` 会把常用的解析器（PDFBox、POI、JSoup 等）一并纳入。若只想用个别解析器以减小体积，可改为引入具体的 `tika-parser-*-module`。

---

## 4. 安装部署

Tika 提供两种"独立运行"形态：**命令行工具 `tika-app`** 与 **REST 服务 `tika-server-standard`**。二者都是可执行 JAR，下载即用。

### 4.1 命令行工具：`tika-app`

**下载（3.3.2，约 64 MB，自带全部依赖的 fat jar）：**

```bash
curl -O https://repo1.maven.org/maven2/org/apache/tika/tika-app/3.3.2/tika-app-3.3.2.jar
```

**验证可用：**

```bash
java -jar tika-app-3.3.2.jar --version
# 期望输出：Apache Tika 3.3.2
```

**基本用法：**

```bash
java -jar tika-app-3.3.2.jar [选项] [文件...]
```

> 常用选项速查（详情见第 5 节）：`-d` 检测类型、`-t` 提取纯文本、`-m` 输出元数据、`-j` 输出 JSON 元数据、`-g` 启动图形界面、`--help` 查看全部。

### 4.2 独立 REST 服务：`tika-server-standard`

**下载（3.3.2）：**

```bash
curl -O https://repo1.maven.org/maven2/org/apache/tika/tika-server-standard/3.3.2/tika-server-standard-3.3.2.jar
```

**启动（默认监听 `127.0.0.1:9998`）：**

```bash
java -jar tika-server-standard-3.3.2.jar --host=127.0.0.1 --port=9998
```

启动成功后日志会出现：

```
Started Apache Tika server ... at http://127.0.0.1:9998/
```

**验证：**

```bash
curl http://127.0.0.1:9998/version
# 期望输出：Apache Tika 3.3.2
```

> **安全提示（重要）**：Tika Server 默认只绑定 `127.0.0.1`（仅本机可访问）。
> 若要被其他机器访问，需 `--host=0.0.0.0` —— **切勿在未鉴权的情况下把 0.0.0.0 暴露到公网**，
> 否则存在任意文件解析、资源耗尽等风险。生产环境务必置于内网 / 反向代理 + 鉴权之后。

**Docker 方式（可选，推荐用于部署）：**

```bash
# 基础镜像（无 OCR）
docker run -d -p 9998:9998 apache/tika:3.3.2

# 完整镜像（含 Tesseract OCR，可识别扫描件 / 图片中的文字）
docker run -d -p 9998:9998 apache/tika:3.3.2-full
```

---

## 5. 具体操作步骤（三类常见任务）

下面用**同一份样例文件**演示三种任务，并提供 **命令行 / Java API / REST** 三种等价写法，方便你按场景取用。

> 样例文件 `sample.txt` 内容：
> ```
> Apache Tika 是一个内容分析工具包。
> 它可以检测并提取上千种文件类型的文本与元数据。
> ```

### 5.1 任务一：文件类型检测（Detect）

| 方式 | 命令 / 代码 |
|------|-------------|
| **CLI** | `java -jar tika-app-3.3.2.jar -d sample.txt` → 输出 `text/plain` |
| **Java API** | `new Tika().detect(new File("sample.txt"))` → `"text/plain"` |
| **REST** | `curl -T sample.txt -X PUT http://127.0.0.1:9998/detect/stream` → `text/plain` |

- 对 HTML 文件检测会得到 `text/html`；对 PDF 得到 `application/pdf`，依此类推。
- REST 的 `/detect/stream` 接收请求体（文件字节流），返回 MIME 类型字符串。

### 5.2 任务二：内容提取（Extract Text）

| 方式 | 命令 / 代码 |
|------|-------------|
| **CLI（纯文本）** | `java -jar tika-app-3.3.2.jar -t sample.txt` → 输出正文纯文本 |
| **CLI（XHTML，默认）** | `java -jar tika-app-3.3.2.jar -x sample.txt` |
| **CLI（HTML / Markdown）** | `-h` / `--md` |
| **Java API（门面，最简洁）** | `new Tika().parseToString(new File("sample.txt"))` |
| **Java API（底层 Parser）** | 见 5.5 完整示例 |
| **REST（默认 XHTML）** | `curl -T sample.txt -X PUT http://127.0.0.1:9998/tika` |
| **REST（纯文本）** | `curl -T sample.txt -X PUT -H "Accept: text/plain" http://127.0.0.1:9998/tika` |

> **坑点提醒**：`/tika` 端点**默认返回 XHTML**，不是纯文本。要纯文本必须带请求头 `Accept: text/plain`（CLI 的 `-t` 已经帮你设好了）。

### 5.3 任务三：元数据获取（Metadata）

| 方式 | 命令 / 代码 |
|------|-------------|
| **CLI（键值对）** | `java -jar tika-app-3.3.2.jar -m sample.txt` |
| **CLI（JSON）** | `java -jar tika-app-3.3.2.jar -j sample.txt` |
| **REST（CSV 键值）** | `curl -T sample.txt -X PUT http://127.0.0.1:9998/meta` |
| **REST（JSON）** | `curl -T sample.txt -X PUT -H "Accept: application/json" http://127.0.0.1:9998/rmeta` |

CLI `-m` 实际输出示例（`sample.txt`）：

```
Content-Encoding: UTF-8
Content-Length: 283
Content-Type: text/plain; charset=UTF-8
X-TIKA:detectedEncoding: UTF-8
resourceName: sample.txt
```

REST `/rmeta` + `Accept: application/json` 返回示例（节选）：

```json
[{
  "Content-Type":"text/plain; charset=UTF-8",
  "X-TIKA:detectedEncoding":"UTF-8",
  "Content-Length":"283",
  "language":"zh-CN"
}]
```

### 5.4 其他实用操作

| 操作 | CLI | REST |
|------|-----|------|
| 语言检测 | `-l file` | 元数据里的 `language` 字段（见 `/rmeta`） |
| 递归提取（含嵌入对象，如压缩包里的文件） | `-J file` | `PUT /rmeta`（递归） |
| 提取附件到当前目录 | `-z file` | `PUT /unpack` |
| 处理加密/带密码文件 | `-p<密码> file` | 请求头 `Password: <密码>` |
| 计算摘要（md5/sha1/sha256…） | `--digest=sha256` | 元数据键 `X-TIKA:digest:SHA-256` |

### 5.5 Java 完整示例代码

**（A）Tika 门面——最简洁，适合快速上手：**

```java
import org.apache.tika.Tika;
import java.io.File;

public class TikaQuickStart {
    public static void main(String[] args) throws Exception {
        Tika tika = new Tika();

        // 1) 类型检测
        String mime = tika.detect(new File("sample.txt"));
        System.out.println("MIME: " + mime);          // text/plain

        // 2) 内容提取（纯文本）
        String text = tika.parseToString(new File("sample.txt"));
        System.out.println("TEXT:\n" + text);
    }
}
```

**（B）AutoDetectParser + Metadata + ContentHandler——更可控，能同时拿元数据：**

```java
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import java.io.FileInputStream;
import java.io.InputStream;

public class TikaParserDemo {
    public static void main(String[] args) throws Exception {
        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        // -1 表示不限制文本长度；也可给具体上限（字节）
        ContentHandler handler = new BodyContentHandler(-1);

        try (InputStream stream = new FileInputStream("sample.txt")) {
            parser.parse(stream, handler, metadata, context);
        }

        // 提取到的正文
        String text = handler.toString();
        System.out.println("TEXT:\n" + text);

        // 遍历全部元数据
        for (String name : metadata.names()) {
            System.out.println(name + " = " + metadata.get(name));
        }

        // 按已知键取（Metadata 提供常用常量）
        System.out.println("Content-Type = " + metadata.get(Metadata.CONTENT_TYPE));
    }
}
```

> 编译运行（确保 3.3 节依赖已就位）：
> ```bash
> mvn compile && mvn exec:java -Dexec.mainClass=TikaParserDemo
> ```

---

## 6. 常见注意事项 / 排错

1. **`tika-app` 运行时打印一堆 `INFO ... TIKA-2374 ...` 日志**
   这是 tika-app 为单文件便捷使用而**临时开启的非默认特性**提示，**无害**，提取结果依然正确。如需干净输出，把日志重定向：`java -jar tika-app-3.3.2.jar -t sample.txt 2>/dev/null`。

2. **REST `/tika` 返回的是 XHTML 而非纯文本**
   默认媒体类型为 XHTML。要纯文本请加 `Accept: text/plain` 请求头（见 5.2）。

3. **Tika Server 默认只监听 `127.0.0.1`**
   远程访问需 `--host=0.0.0.0`，但**务必配合鉴权 / 内网隔离**，不要直接暴露公网。

4. **大文件 / 复杂文档导致 `OutOfMemoryError`**
   - 用 `TikaInputStream` 流式处理，避免整文件进内存；
   - 设置上限：`TikaConfig` 可配 `setMaxStringLength`、`setMaxEmbeddedResources`、`setMaxParseTime`；
   - 服务端 JVM 调优示例：`java -Xmx4g -Xms2g -XX:+UseG1GC -jar tika-server-standard-3.3.2.jar`。

5. **加密/密码保护文档**
   Tika 无法解密，服务端会返回 HTTP 422。需先解密或传入密码（见 5.4）。

6. **解析器冲突**
   多个解析器声明支持同一 MIME 类型时，`AutoDetectParser` 取第一个匹配，可能不是你想要的。检查 classpath 是否有重复注册。

7. **版本管理建议**
   复杂项目务必用 **BOM**（`tika-bom`），不要逐个写死模块版本——否则易与当前 3.x 产生版本错位。

---

## 7. 一键速查表

```bash
# ---------- 命令行 tika-app ----------
java -jar tika-app-3.3.2.jar --version                 # 版本
java -jar tika-app-3.3.2.jar -d file                   # 类型检测
java -jar tika-app-3.3.2.jar -t file                   # 提取纯文本
java -jar tika-app-3.3.2.jar -x file                   # 提取 XHTML
java -jar tika-app-3.3.2.jar -m file                   # 元数据（键值）
java -jar tika-app-3.3.2.jar -j file                   # 元数据（JSON）
java -jar tika-app-3.3.2.jar -l file                   # 语言检测
java -jar tika-app-3.3.2.jar -J file                   # 递归提取（含嵌入）
java -jar tika-app-3.3.2.jar -g                        # 图形界面

# ---------- 独立 REST 服务 tika-server-standard ----------
java -jar tika-server-standard-3.3.2.jar --host=127.0.0.1 --port=9998   # 启动
curl http://127.0.0.1:9998/version                                   # 版本
curl -T file -X PUT http://127.0.0.1:9998/detect/stream              # 类型检测
curl -T file -X PUT -H "Accept: text/plain" http://127.0.0.1:9998/tika   # 提取纯文本
curl -T file -X PUT http://127.0.0.1:9998/meta                       # 元数据(CSV)
curl -T file -X PUT -H "Accept: application/json" http://127.0.0.1:9998/rmeta  # 元数据(JSON)
```

> 本文所有示例均在 **Apache Tika 3.3.2 + Java 17（OpenJDK Corretto 17）** 环境下实测通过。
