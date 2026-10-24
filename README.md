# 小白条 (Xiaobaitiao)

> **本项目移植自 [Art-Chen/MIUI-Extra](https://github.com/Art-Chen/MIUI-Extra)（作者 Art_Chen），并在其基础上继续适配。**
>
> 原项目 MIUI-Extra 是一个功能丰富的 HyperOS 增强模块，涵盖小白条动画、控制中心、壁纸缩放、省电策略等一整套功能。
> 本仓库只把其中「小白条 / 手势条（Home Handle）」这一部分单独拆出来，做成一个只干一件事的独立模块，
> 并针对 HyperOS 4 做了新一轮适配。小白条的动画与手势逻辑来自原作者，**后续的拆分、裁剪、
> HyperOS 4 兼容修复与 system_server 侧的「应用内跟随」功能由本仓库继续维护**。
>
> 本模块不是小米官方产品，也不是原作者的官方分支；出问题请到本仓库反馈，不要打扰原作者。

## 功能

- 小白条丝滑动画：透明度、缩放、位移，普通 / 按下 / 桌面三种状态各自独立可调
- 小白条动画增强模式（用于低端机型强制开启完整动画）
- **应用内上划时小白条跟随应用**（HyperOS 4 专属，需在设置里单独开启，见下文）
- 配置备份 / 恢复

## 兼容性

| 系统 | 状态 |
| --- | --- |
| HyperOS 2 / 3 (Android 14 / 15) | 沿用原项目逻辑 |
| HyperOS 4 | 已适配（探测式分支，同一个 APK 同时兼容新旧系统） |

最低支持 Android 12（API 31），依赖 LSPosed。

## HyperOS 4 适配说明

本模块用「探测类是否存在」的方式判断系统版本分支，而不是按系统版本号硬猜，因此同一个 APK 可以在
OS2 / OS3 / OS4 上运行。适配过程中确认的系统行为如下：

- **应用内上划时小白条跟随应用**：HyperOS 4 把官方开关
  `config_attachNavBarToAppDuringTransition`（framework-res `0x0111003c`）改成了 `false`，
  于是 `com.android.server.wm.DisplayPolicy` 里的字段
  `mShouldAttachNavBarToAppDuringTransition` 恒为 false，
  `Transition#handleLegacyRecentsStartBehavior` 不再把导航栏挂到应用窗口上，
  表现就是「应用内上划时小白条不跟着走」。
  本模块在 **system_server** 进程里把该字段强制置为 true，恢复系统原本的行为。
  这是系统进程级修改，**开关改动后必须重启整机**才生效。
- **跟手细节由系统实现**：上划过程中的跟手位移与回弹动画由桌面进程的 **Rust** 手势服务
  （`com.rust.hyper_launcher.recents.TouchInteractionService`）配合 Flutter 绘制的小米桌面完成。
  Xposed 可以复用这段动画，但无法在模块里单独重写它。
- **沉浸式手势栏**：依赖窗口 Insets 回写，该操作在 OS 4 会引发 native 层窗口管理崩溃
  （没有 Java 异常，日志里也看不到堆栈），因此 OS 4 上禁用了窗口改写。
  这个开关在 OS 4 上不生效，但也不会崩。
- **指纹触摸监听**：OS 4 上 `MiuiGestureMonitor` 的触摸监听会直接崩掉输入管线，
  已改为走官方 `InputManagerGlobal` 的路径，并默认关闭旧路径。

## 使用

1. 安装并激活 LSPosed
2. 启用本模块，作用域勾选 **SystemUI** 与 **系统框架**（后者用于「应用内跟随」功能）
3. 重启 SystemUI 生效
4. 如需「应用内上划时小白条跟随应用」：在模块设置里打开对应开关，然后**重启整机**

> 所有新增功能开关默认关闭。关闭时模块不改动任何系统进程行为，表现与只开小白条动画时一致。

## 已知限制

- 应用内上划的跟手位移由桌面进程的 Rust 服务负责，模块只能恢复「导航栏跟随窗口」这一层，
  不能单独调节手指位移幅度或回弹曲线。
- 自绘小白条（在应用之上盖一条自己的白条来模拟跟手）的方案做过验证并放弃：
  手势期间系统会把真正的 `NavigationHandle` 置为 INVISIBLE，整条导航栏被挂到应用窗口上，
  自绘还要重新处理取色、深色强度、与系统动画的时序，收益太小。
- OS 4 上沉浸式手势栏开关无效（原因见上）。

## 相对原项目 MIUI-Extra 的改动

按 AGPL-3.0 的要求，这里列出对本项目源码所做的主要改动：

1. 拆分为独立应用：包名改为 `moe.chenxy.xiaobaitiao`，移除小白条之外的其它功能模块；
2. 按 HyperOS 4 的类路径变更做探测式适配（导航栏布局/视图、`BarTransitions`、
   指纹手势监听等私有类在新系统里换了包名甚至被 R8 裁掉）；
3. 修复若干在新系统上必崩的问题：动态注册广播缺少导出标志、反射失败抛 `Error` 时未捕获、
   OS 4 窗口 Insets 回写触发 native 崩溃、动画回调递归重入导致栈溢出、触摸监听崩输入管线；
4. 新增 system_server 侧的「应用内跟随」开关（`SystemServerMainHooker`）；
5. 移除原项目的模糊（blur）材质相关逻辑与相关依赖；
6. 新增 XLog（同时写 logcat 与 LSPosed 日志）——HyperOS 的 system_server 会丢弃普通 `Log` 输出。

## 构建

需要 JDK 17 与 Android SDK（compileSdk 35）：

```bash
./gradlew assembleRelease
```

未配置签名时会产出未签名的 `app/build/outputs/apk/release/app-release.apk`。

GitHub Actions（`.github/workflows/build.yml`）会自动构建：配置了签名密钥就产出签名 APK 并在打 tag 时
自动发布 Release，没配置就只上传未签名 APK 作为构建产物。签名相关的仓库 Secrets：

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_BASE64` | 签名密钥库文件的 base64 内容 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

## 致谢

- 原作者 [Art_Chen](https://github.com/Art-Chen)：小白条动画与手势逻辑来自他的
  [MIUI-Extra](https://github.com/Art-Chen/MIUI-Extra)
- [YukiHookAPI](https://github.com/fankes/YukiHookAPI) by fankes
- [LSPosed](https://github.com/LSPosed/LSPosed)

## 许可证

本项目沿用原项目的 **AGPL-3.0**，详见 [LICENSE](LICENSE)。

AGPL-3.0 要求：分发本模块的任何修改版本或二进制文件时，必须同时提供完整对应源码。
如果你 fork 本项目做了修改并分发，请同样开源。
