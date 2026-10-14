# GitHub Pages 部署

网页端是纯静态页面，不需要构建或后端服务。仓库中的 [deploy-pages.yml](../.github/workflows/deploy-pages.yml) 会将 `web/` 目录直接发布到 GitHub Pages。

## 首次启用

1. 将包含 `.github/workflows/deploy-pages.yml` 的变更推送到 `master`。
2. 打开 GitHub 仓库的 **Settings → Pages**。
3. 在 **Build and deployment → Source** 中选择 **GitHub Actions**。
4. 打开仓库的 **Actions** 页面，等待 **Deploy web to GitHub Pages** 工作流成功。

之后，推送对 `web/` 的变更会自动触发部署。也可以在 **Actions** 页面手动运行 `Deploy web to GitHub Pages`。

此仓库的默认项目站点地址为：

<https://xiao-ranbei.github.io/nihongo-doushi/>

## 学习记录说明

网页端的错题、统计和打卡保存在浏览器 `localStorage`，不会上传到 GitHub。部署后的站点 origin 与本地 `file://` 页面不同，因此本地浏览器中的既有记录不会自动迁移；每个访客的记录也只保存在自己的浏览器中。

## 官方文档

- [Using custom workflows with GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
- [Configuring a publishing source for your GitHub Pages site](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site#publishing-with-a-custom-github-actions-workflow)
