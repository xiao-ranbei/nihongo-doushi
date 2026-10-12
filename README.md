# 日语动词活用练习

一个独立的离线学习小工具，专注于日语动词变形，不依赖外部服务或第三方题库。

## 使用

用浏览器打开 `index.html` 即可开始练习。输入答案时可以使用汉字、假名或常见罗马字；答对／答错统计按每次提交累计，并与错题一起保存在当前浏览器中。练习区可单独清零统计，不会删除错题。

## 练习内容

- 五段、一段和不规则动词
- ます词干、ます形、否定、过去、て形、条件形、意向形、命令形等活用
- 可能、受身、使役、短使役、使役受身等派生形
- 答对／答错次数统计、逐题变化提示、活用形式与语态的大意说明、完整活用速查与错题重练
- 随机题每轮不重复相同的动词与变形组合；尽量避免最近 3 题内重复同一动词，题池用完后重新洗牌

题目和活用规则由本工具独立实现；不使用目标网站的源代码、素材或动词数据。

## 测试

安装 Node.js 后，在项目目录运行：

```text
node --test tests/verb-engine.test.js tests/styles.test.js
```

`verb-engine.test.js` 覆盖变形规则、答案归一化与罗马字转换；`styles.test.js` 是样式契约守卫（令牌必须已定义、组件内禁止硬编码色值）。

## 字体

页面使用霞鹜楷体（`font/LXGWZhenKaiGB-Regular.ttf`，约 16.7MB）。运行时实际加载的是按当前词库与界面文本生成的子集 `LXGWZhenKaiGB-Regular.subset.woff2`（161KB，599 个字符，字形轮廓与步进宽度与原字体一致）。新增动词或界面文本后，重新生成子集：

```text
pip install fonttools brotli
python font/subset.py
```

脚本会收集字符、生成 woff2、校验字形覆盖与轮廓一致性，并把 `font/fonts.js` 指向新文件。全量字体只在该脚本运行时需要。

## 开发工具（可选，运行时不依赖）

| 工具 | 用途 |
| --- | --- |
| `font/subset.py` | 字体子集化与校验 |
| `tools/color-audit.py` | 统计并聚类 `styles.css` 中的硬编码色值 |
| `tools/tokenize-colors.py` | 把硬编码色值替换为语义令牌并校验变量定义 |
| `tools/visual-check.js` | 用 Playwright 截取 6 个关键界面状态 |
| `tools/visual-diff.py` | 逐像素对比两组截图，输出差异热图 |

