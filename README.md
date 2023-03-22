# 简介：
一个极简状态栏工具类，可以直接把类拷出来用，用于设置沉浸式状态栏，状态栏字体颜色等等，

# 使用效果展示：
![沉浸式状态栏效果图](https://user-images.githubusercontent.com/68986693/226783722-66205490-7072-43d8-b60a-c4d3baf4a2da.jpg)
# 添加依赖：
## build.gradle
```
  implementation 'com.github.wenju999:little-utils-for-statusbar:1.0.0'
```
## settings.gradle
```
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```
-----
## 简单使用方法

```
  StatusBarUtils.translucent(this)
  StatusBarUtils.setStatusBarLightMode(this)
```
