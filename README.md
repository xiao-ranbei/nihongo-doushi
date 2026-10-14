# 日语学习工具集（nihongo-doushi）

本仓库用于构建离线优先的日语学习工具。当前提供网页端的动词与形容词练习、速查和错题复习；未来可在同一工程中加入原生 Android 客户端及更多日语工具。

## 快速开始

- 网页端：用浏览器打开 [web/index.html](web/index.html)。
- 网页端使用方式、测试命令、字体维护和可选开发工具见 [web/README.md](web/README.md)。
- 产品方向、目标设计、当前实现与新增模块约定见[文档索引](docs/README.md)。
- GitHub Pages 首次启用与自动部署说明见[部署指南](docs/deployment-github-pages.md)。

## 仓库结构

```text
web/       当前网页客户端、静态资源、测试和开发工具
docs/      产品需求、目标设计、当前状态和扩展约定
android/   预留给后续 Kotlin 原生客户端；当前尚未创建
```

网页运行不依赖服务器、构建工具或第三方服务。Android 工程开始时会与网页作为并列客户端维护。
