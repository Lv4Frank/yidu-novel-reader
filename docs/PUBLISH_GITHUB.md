# 把这个项目发布到 GitHub

本仓库已经准备好 `.gitignore`、`LICENSE`、`SECURITY.md` 和 Release 说明。仓库中**不应包含**：

- `.tools/`（JDK + Android SDK，约 700MB）
- `build/`、`*.apk`、`*.idsig`
- `keystore/`、`*.jks`、签名密码
- `screenshots/`、`tools/mock_requests.log`、测试日志

这些都在 `.gitignore` 中。APK 请通过 **GitHub Releases** 上传，而不是提交进源码。

---

## 0. 发布前检查

在项目根目录确认文件：

```
app/           源码
preview/       截图（README 使用）
tools/         开发/测试工具（不含 classes、日志、jar）
README.md
build.ps1
LICENSE
SECURITY.md
RELEASE_NOTES.md
.gitignore
```

确认 `keystore/` 不会被提交（它应该只在你的本机保留）。签名密码只放在密码管理器里，不要写进 README、Git 或聊天截图。

---

## 1. （可选）在 GitHub 创建空仓库

如果使用 **GitHub Desktop → Publish repository**，可以跳过这一步。选择命令行 B1 方式时：

1. 打开 https://github.com/new
2. **Repository name**：例如 `yidu-novel-reader`
3. **Description**：
   `Android TXT 小说阅读器，接入大模型 API 实时流式翻译，支持双语对照、TPM、术语表与按原文结构导出`
4. 选择 **Public**（想先私有也可以，之后可改）
5. **不要**勾选 “Add a README file / .gitignore / license”，因为你本地已有这些文件
6. 点 **Create repository**

---

## 2. 把本地代码推上去

本地目录**已经执行过 `git init` 并暂存了待提交文件**（没有提交，方便你用 GitHub Desktop 或自己的身份提交）。先确认：

```powershell
cd "C:\AI工作区\txt实时翻译apk"
git status
```

不应看到 `.tools/`、`keystore/`、`*.apk`、`screenshots/`。本机 Git 位于 `C:\Program Files\Git\cmd\git.exe`（已安装）；如果新开终端找不到 `git`，请把 `C:\Program Files\Git\cmd` 加入 PATH 或重开终端。

### 方式 A：GitHub Desktop（推荐给不熟悉命令行的用户）

1. 安装 https://desktop.github.com/ 并登录 GitHub 账号。
2. **File → Add local repository** → 选择 `C:\AI工作区\txt实时翻译apk`。
3. 左侧会显示已暂存的新文件；填写提交信息，例如：
   `YiDuNovel 1.2.1: deterministic punctuation preservation for streaming LLM translation`
   点 **Commit to main**。
4. 顶部点 **Publish repository** → 选择仓库名（如 `yidu-novel-reader`）、是否 Public、是否忽略大文件（无需勾选）→ **Publish repository**。
5. 完成第 3 步的 Release 上传。

> GitHub Desktop 首次提交时若提示作者信息，使用你的 GitHub 账号邮箱即可。

### 方式 B：Git 命令行

先设置提交身份（只需一次）：

```powershell
cd "C:\AI工作区\txt实时翻译apk"
git config user.name  "你的名字"
git config user.email "你的GitHub邮箱"
git commit -m "YiDuNovel 1.2.1: deterministic punctuation preservation for streaming LLM translation"
git branch -M main
```

然后二选一：

**B1. 在网页先创建空仓库**（不要勾选 README/.gitignore/license），再：

```powershell
git remote add origin https://github.com/<你的GitHub用户名>/<仓库名>.git
git push -u origin main
```

**B2. 使用 GitHub CLI 一条命令创建并推送：**

```powershell
winget install --id GitHub.cli -e
gh auth login
gh repo create <仓库名> --public --source=. --remote=origin --push
```

首次 push 会弹出浏览器登录 GitHub，或要求用户名 + Personal Access Token（PAT，权限勾选 `repo`）。

---

## 3. 创建 Release 并上传 APK

仓库源码里不提交 APK。请在 GitHub 上：

1. 打开仓库 → 右侧 **Releases** → **Draft a new release**
2. **Choose a tag**：输入 `v1.2.1` → Create new tag
3. **Release title**：`v1.2.1 · 修复保留原文标点失效`
4. 描述可以直接粘贴 [`RELEASE_NOTES.md`](../RELEASE_NOTES.md) 的内容
5. **Attach binaries**：上传
   - `YiDuNovel-1.2.1.apk`
   - `YiDuNovel-1.2.1.apk.sha256`（可选，方便校验）
6. 点 **Publish release**

> 如果你用 GitHub CLI：
> ```powershell
> gh release create v1.2.1 `
>   YiDuNovel-1.2.1.apk `
>   YiDuNovel-1.2.1.apk.sha256 `
>   --title "v1.2.1 · 修复保留原文标点失效" `
>   --notes-file RELEASE_NOTES.md
> ```

---

## 4. 仓库设置建议

在仓库首页 **About** 右侧 ⚙ 设置：

- **Description**：见上面第 1 步
- **Topics**：`android`、`java`、`txt-reader`、`novel`、`translation`、`llm`、`ai`、`deepseek`、`openai`、`siliconflow`、`a6api`
- **License**：MIT（仓库已有 `LICENSE`）
- 如需要，开启 **Discussions / Issues**

README 已经包含功能、截图、构建方式、测试记录和使用步骤。

---

## 5. 安全与后续发布

- **不要提交** `keystore/`、`*.jks`、`$env:YIDU_KS_PASS`、API Key、`.tools/`、`.apk`。
- 官方 APK 使用作者私钥签名，该私钥不在仓库中；请自行离线备份。若私钥丢失，将无法再发布同包名覆盖升级包。
- 如果你 Fork 后自行发布，请使用自己的签名密钥：
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\create-keystore.ps1
  $env:YIDU_KS_PASS = "你的密码"
  powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
  ```
  用不同密钥签名的 APK 无法覆盖安装官方版，需要先卸载或修改包名。
- 以后更新版本时：修改 `AndroidManifest.xml` 里的 `versionCode/versionName` → 构建 → 提交源码 → 新建 tag/Release 上传新 APK。
- 如果误提交了密钥，立即删除该密钥并更换；GitHub 历史记录仍能找回，务必视为已泄露。

---

## 6. 可选：发布前清理本地临时文件

这些无需删除（`.gitignore` 已忽略），但如果想让工作目录更干净，可以手动删除：

- `build/`、`tmpbuild/`、`screenshots/`、`ui.xml`、`*.idsig`
- `tools/qa-core/classes/`、`tools/mock_requests.log`

`preview/` 和 `tools/samples/` 需要保留（README 和测试使用）。
