# Overflow 开发文档

# 开发版使用说明

由于当前项目进度缓慢，将来可能有越来越多人有开发需求，故暂时将包发布到了 Github Packages。如需在开发环境中使用 Overflow，添加以下仓库

```kotlin
repositories {
    maven("https://maven.pkg.github.com/MrXiaoM/Overflow") {
        credentials {
            username = "MrXiaoM"
            password = "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" // 该 token 仅有 package:read 权限
        }
    }
}
```
开发版仓库中依赖版本的格式为 `${miraiVersion}-${shortCommitHash}`，如 `2.16.0-0abcdef`，如果需要保持依赖为最新版，则使用 `2.16.0+`

# 在 mirai-console 中开发

> 在项目正式发布到 Maven Central 之前，此方法不可用。  
> 你也可以先拉取本项目，执行 `publishToMavenLocal` 任务，再在自己项目的仓库中添加 `mavenLocal()` 来使用此方法。

此方法仅 Gradle 可用。

插件模板另请参见 [overflow-console-plugin-template](https://github.com/project-tRNA/overflow-console-plugin-template)

此方法需要安装 mirai-console-gradle-plugin，即
```kotlin
plugins {
    id("net.mamoe.mirai-console") version "2.16.0"
}
```
在构建脚本 `build.gradle(.kts)` 中加入以下内容即可，其中，需要将 `$VERSION` 替换为 overflow 版本号

```kotlin
mirai {
    noTestCore = true
    setupConsoleTestRuntime {
        // 移除 mirai-core 依赖
        classpath = classpath.filter {
            !it.nameWithoutExtension.startsWith("mirai-core-jvm")
        }
    }
}
dependencies {
    // 若需要使用 Overflow 的接口，请取消注释下面这行
    // compileOnly("top.mrxiaom:overflow-core-api:$VERSION")
    
    testConsoleRuntime("top.mrxiaom:overflow-core:$VERSION")
}
```

# 在 mirai-core 中开发

> 在项目正式发布到 Maven Central 之前，此方法不可用。  
> 你也可以先拉取本项目，执行 `publishToMavenLocal` 任务，再在自己项目的仓库中添加 `mavenLocal()` 来使用此方法。

将依赖 `net.mamoe:mirai-core` 替换为 `top.mrxiaom:overflow-core` 即可。

```kotlin
dependencies {
    implementation("top.mrxiaom:overflow-core:$VERSION")
}
```
```xml
<dependency>
    <groupId>top.mrxiaom</groupId>
    <artifactId>overflow-core</artifactId>
    <version>$VERSION</version>
    <scope>compile</scope>
</dependency>
```

# 向 Onebot 发送自定义 action

预设的 action 类型列表另请参见 [ActionPathEnum.java](/onebot/src/main/java/cn/evole/onebot/sdk/enums/ActionPathEnum.java) (以下应当填写的是字符串 path 的值)

```kotlin
val onebot = bot as RemoteBot
// 第一个参数 path 为请求 action 类型
// 第二个参数 params 为请求参数，是 JsonObject 格式字符串，可为空(null)
// 返回值是 JsonObject 字符串
val response = onebot.executeAction("get_forward_msg", "{\"id\":\"转发消息ID\"}")
```

# 手动刷新数据

```kotlin
// 目前 Bot, Group, Member 都实现了 Updatable
val updatable = group as Updatable
updatable.queryUpdate() // suspend
val remoteGroup = group as RemoteGroup
remoteGroup.updateGroupMemberList() // suspend
```

# 资源相关消息说明

正如[用户手册](/docs/README.md#资源相关消息说明)所说，为减少运行内存占用，你可以使用以下方法来上传图片、语音、短视频来减少其造成的资源占用

```kotlin
val image = OverflowAPI.get().imageFromFile("https://xxxxx")
val audio = OverflowAPI.get().audioFromFile("https://xxxxx")
val video = OverflowAPI.get().videoFromFile("https://xxxxx")

// 其中的链接与 Onebot 的 file 参数相同，有三种格式
// 本地文件: file:///path/file
// 网络文件: http(s)://xxxx
// Base64: base64://b3ZlcmZsb3c=
```

为了兼容 mirai 已有的部分插件等可能已停止更新的业务逻辑，Overflow 添加了 [FileService](/overflow-core-api/src/main/kotlin/top/mrxiaom/overflow/spi/FileService.kt)，使用示例另请参见 [overflow-shamrock-ext](https://github.com/MrXiaoM/overflow-shamrock-ext)
