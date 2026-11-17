# 绘课表

中南林业科技大学学生的**个人课表 / 日程**小应用：从学校融合门户（portal.csuft.edu.cn）读取课表，展示成周课表，并提供日程、自建任务和桌面小组件。

> 非官方应用，与学校无关。所有数据只保存在本机，不会上传到任何服务器。

## 功能

- **门户导入**：在 App 内置 WebView 里登录统一身份认证，登录成功后自动抓取课表并导入（一次可覆盖约 20 周）
- **周课表**：按真实日期渲染，色块高度 = 上课时长；左右滑动翻页，点击标题可跳转任意周
- **教学周**：导入时按周向门户查询真实教学周，超出范围的周不显示编号
- **今天**：下一节课、当天课程与日程
- **自建任务**：多选节次（跨节自动拆段）、自选颜色，与课程重叠时整块叠加混色、文字错开
- **日程**：一次性或每周重复
- **桌面小组件**（2×3）：整周课表缩略图，今天高亮，每天零点自动重绘

## 目录

```
app/                 Android 工程（无 Gradle，使用仓库根目录的 build.sh 打包）
  src/               Java 源码（MainActivity / PortalActivity / TodayWidget / Diag）
  res/               布局、图标、小组件配置
  assets/index.html  内置的网页版界面（构建前会从 web/ 同步）
web/                 网页版（可独立用浏览器打开）
build.sh             aapt2 + javac + d8 + apksigner 的打包脚本
docs/                截图
```

## 构建

需要 JDK 17 与 Android SDK build-tools 34（`aapt2`、`d8`、`apksigner`）、platform `android-34/android.jar`。

```bash
./build.sh 0.9.47 61     # 版本名 版本号，产物在 out/
```

> 原作者的构建环境是 Android 手机上的 Ubuntu(proot)，`aapt2` 是 x86_64 二进制，通过 `qemu-x86_64-static` 运行；在普通 x86_64 电脑上直接去掉脚本里的 `$QEMU` 即可。

## 说明

- 课表数据来自学校门户的 `v1/calendar/share/schedule/*` 接口，需要登录态；App 不保存账号密码，登录在系统 WebView 内完成
- 小组件数据通过 `SharedPreferences` 从 App 同步，打开 App 时刷新
- 仅用于个人学习与方便查看课表，请勿用于任何商业用途

## 许可

MIT
