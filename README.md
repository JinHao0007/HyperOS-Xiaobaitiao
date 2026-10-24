# 小白条 v1.0.0

HyperOS 的独立「小白条 / 手势条」增强模块，从 [Art-Chen/MIUI-Extra](https://github.com/Art-Chen/MIUI-Extra) 中把小白条部分单独拆出来，并针对 HyperOS 4 重新做了适配。

> 不是小米官方产品，也不是原作者的官方分支。有问题请到本仓库反馈。

## 功能

- **小白条丝滑动画**：透明度、缩放、位移，普通 / 按下 / 桌面三种状态可分别调节
- **动画增强模式**：用于被系统判定为「低端机」而阉割了动画的机型，强制开启完整动画
- **应用内上划时小白条跟随应用**（HyperOS 4 专属，需在设置里单独开启）
- **配置备份 / 恢复**

## HyperOS 4 适配

同一个 APK 同时兼容 HyperOS 2 / 3 / 4，用「探测类是否存在」的方式切换实现，不按系统版本号硬猜。适配过程中处理掉的问题：

- 系统把 `config_attachNavBarToAppDuringTransition`（framework-res `0x0111003c`）改成了 `false`，导致应用内上划时导航栏不再跟随应用窗口。模块在 **system_server** 里把 `DisplayPolicy` 的对应字段强制置为 `true`，恢复原厂行为
- 导航栏相关私有类整体换了包名（导航栏布局/视图、`BarTransitions`、指纹手势监听等），改为探测式分支
- 动态注册广播缺少导出标志导致的 `SecurityException`
- 反射失败抛 `Error` 时未被捕获导致的崩溃
- OS 4 上回写窗口 Insets 会触发 native 层窗口管理崩溃（没有 Java 堆栈），已禁用该路径
- 动画回调递归重入导致的栈溢出
- 指纹触摸监听在 OS 4 上会崩掉输入管线，已改用官方 `InputManagerGlobal` 路径并默认关闭旧路径

## 安装

1. 安装并激活 **LSPosed**
2. 启用本模块，作用域勾选 **SystemUI** 与 **系统框架**（后者只有「应用内跟随」功能需要）
3. 重启 SystemUI 生效
4. 如需「应用内上划时小白条跟随应用」：在模块设置里打开对应开关，然后 **重启整机**

所有新增开关默认关闭，关闭时模块不会改动任何系统进程行为。

## 已知限制

- 应用中上划小白条不触发跟手动画。OS 4 的桌面是 Flutter（`libapp.so` 里的 Dart 机器码）、手势服务是 Rust（`com.rust.hyper_launcher.recents.TouchInteractionService`），两者都是编译后的原生机器码，没有 ART 方法可供 Xposed 改写。模块能恢复的只有「导航栏跟随应用窗口」这一层，位移曲线与回弹节奏仍由系统决定
- 沉浸式手势栏开关在 OS 4 上无效（依赖窗口 Insets 回写，会触发 native 崩溃）

完整的尝试记录与技术分析写在仓库 README 的「技术瓶颈」一节。

## 注意

- 本版本使用新的签名密钥。如果你装过调试版或其它签名版本，需要**先卸载再安装**
- 这是首个正式版本，向下兼容从下一个版本开始

## 致谢

- 原作者 [Art_Chen](https://github.com/Art-Chen)：小白条动画与手势逻辑来自他的 MIUI-Extra
- [YukiHookAPI](https://github.com/fankes/YukiHookAPI) · [LSPosed](https://github.com/LSPosed)

## 许可证

AGPL-3.0，源码：https://github.com/JinHao0007/HyperOS-Xiaobaitiao
