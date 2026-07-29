# AutoBanRobot for Safari

Safari 版本与 Chrome/Edge 版本使用同一枚猫咪助手图标；`Resources/icon.png`
与仓库根目录的 `icon.png` 保持完全一致。

当前版本：`1.5.2`。三个内置内容规则——“仅单个 Emoji”、
“Emoji + 英文 + Emoji”及“五段式 Emoji 时间”——均可在扩展弹窗中
独立启用或停用，保存后立即生效。

This directory contains the macOS Safari adaptation of AutoBanRobot. It is maintained only on the repository’s `safari` branch.

本目录是 AutoBanRobot 的 macOS Safari 适配版本，仅在仓库的 `safari` 分支维护。

## License / 许可

本项目采用 [PolyForm Noncommercial License 1.0.0](../LICENSE)：
源码公开并允许协议范围内的非商业使用；商业使用须另行取得项目所有者授权。

This project uses the [PolyForm Noncommercial License 1.0.0](../LICENSE).
Commercial use requires a separate license from the project owner.

## Support boundary / 支持范围

- Supported: macOS Safari 17.1 or later.
- Not currently supported: Safari on iPhone or iPad. AutoBanRobot needs `webRequest` to capture Twitter/X session headers, and Apple documents that `webRequest` is unavailable to Safari Web Extensions on iOS.
- Chrome and Microsoft Edge continue to share the same Chromium source and release package on the `main` branch.

- 支持：macOS Safari 17.1 及以上版本。
- 暂不支持：iPhone 和 iPad 上的 Safari。AutoBanRobot 需要使用 `webRequest` 捕获 Twitter/X 会话请求头，而 Apple 明确说明 Safari Web Extension 的 `webRequest` 在 iOS 上不可用。
- Chrome 与 Microsoft Edge 继续在 `main` 分支共用完全相同的 Chromium 源码和发布包。

## Directory layout / 目录结构

- `Resources/`: loadable Safari Web Extension source.
- `Xcode/`: generated macOS containing app and Safari Web Extension project.
- `package-safari.sh`: rebuilds the Xcode project from `Resources/`.

## Temporary installation / 临时安装

On current macOS Safari versions:

1. Open Safari Settings and enable developer features.
2. Open the Developer tab.
3. Choose “Add Temporary Extension…”.
4. Select the `safari/Resources` folder.
5. Grant access to `x.com` and `twitter.com`.

Temporary extensions are removed when Safari quits or after 24 hours.

在当前 macOS Safari 中：

1. 打开 Safari 设置并启用开发者功能。
2. 打开“开发者”标签。
3. 选择“添加临时扩展…”。
4. 选择 `safari/Resources` 目录。
5. 授予 `x.com` 和 `twitter.com` 网站访问权限。

临时扩展会在退出 Safari或 24 小时后被移除。

## Xcode build / Xcode 构建

Run:

```bash
./safari/package-safari.sh
```

Then open the generated project in `safari/Xcode`, select the macOS app scheme, configure signing if needed, and run it once to install the extension in Safari.

执行上述脚本后，打开 `safari/Xcode` 中生成的工程，选择 macOS App Scheme，根据需要配置签名并运行一次，即可将扩展安装到 Safari。

## Compatibility changes / 兼容性改动

- Uses Safari’s `browser` WebExtension namespace with a `chrome` fallback.
- Uses Safari Manifest V3 `background.scripts`.
- Runs the bridge and detector scripts in the same Safari content-script world.
- Keeps the persistent asynchronous Ban queue, 500 ms pacing, followed-account protection, confirmed-block history, editable presets, and configurable pattern rules from the Chromium edition.

- 优先使用 Safari 的 `browser` WebExtension 命名空间，并保留 `chrome` 兼容回退。
- 使用 Safari Manifest V3 的 `background.scripts`。
- Bridge 与检测脚本在同一个 Safari 内容脚本环境中运行。
- 保留 Chromium 版的持久化异步 Ban 队列、500ms 间隔、关注账号保护、确认成功清单、可删除预设及可配置模式规则。
