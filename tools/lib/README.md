# tools/lib

JVM 测试（`tools/qa-core/CoreHarness.java`、`NetHarness.java`）需要官方 org.json 库，但不把 jar 提交进仓库。

下载方式（PowerShell）：

```powershell
Invoke-WebRequest `
  -Uri https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar `
  -OutFile tools/lib/json-20240303.jar
```

该 jar 已被 `.gitignore` 忽略。测试编译/运行命令见 `tools/qa-core/REPORT.md` 与 `tools/qa-core/NET-REPORT.md`。
