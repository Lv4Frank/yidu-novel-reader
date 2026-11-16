# 安全与隐私

## 不要提交的内容

本仓库是公开源码仓库。提交前请确认以下内容**不在** Git 跟踪范围内：

- `keystore/`、任何 `*.jks` / `*.keystore` / `*.p12` 签名文件
- `$env:YIDU_KS_PASS` 等签名密码、GitHub Token、API Key
- `.tools/` 本地 JDK/SDK、`build/` 构建产物、`*.apk`
- 含个人设备序列号、局域网 IP 或测试日志的文件（如 `tools/mock_requests.log`）

`.gitignore` 默认已忽略上述内容。发布前建议执行：

```powershell
git status
git ls-files
```

并确认没有意外被跟踪的密钥文件。

## API Key 的存储

- App 不内置任何 API Key，也不上传 Key 到任何中转服务器。
- 用户填写的 Key 通过 Android Keystore 生成的 AES-256-GCM 密钥加密后存入本机 App 私有存储；界面仅显示首尾各 4 位掩码。
- 小说原文只发送到用户自己配置的模型 API 地址。

## 签名密钥

- 官方发布 APK 使用作者私有的签名密钥；该密钥**不在本仓库中**。
- 如果你 Fork 并自行发布，请使用自己的 keystore（`tools/create-keystore.ps1`），不要尝试复用官方签名。
- 如果签名密钥泄露，任何持有者都能伪造同包名更新；应立即停止使用该密钥并更换包名/证书重新发布。

## 报告安全问题

请不要在公开 Issue 中粘贴 API Key、签名文件或可被利用的细节。请通过 GitHub 仓库的 **Security → Report a vulnerability**（私有安全公告）联系维护者，并附上复现步骤与影响范围。

## 用户内容与版权

本软件仅提供本地 TXT 阅读与调用第三方模型翻译的能力，不附带、不托管任何受版权保护的小说。用户应确保自己导入的 TXT 文件拥有合法使用权；模型翻译结果由相应服务商生成，请遵守其服务条款。
