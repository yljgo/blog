---
layout: post
title: Markdown语法总结
subtitle: ""
author: "Paul"
header-style: text
tags:
  - markdown
---

0.目录（Table of Contents）
===

在需要目录出现的地方放置一个标记，这样会自动生成一个嵌套的包含所有标题的列表。默认的标记是 [TOC]。
```
[TOC]
```

1.标题（Headers）
===
1.1用1~6个#标记
---

```
# 一级标题
## 二级标题
### 三级标题
#### 四级标题
##### 五级标题
###### 六级标题
```
# 一级标题
## 二级标题
### 三级标题
#### 四级标题
##### 五级标题
###### 六级标题

1.2用=和-标记
---
```
一级标题
======

二级标题
----------
```
一级标题
======

二级标题
----------

2.列表（Lists）
===
2.1无序列表（Unordered Lists）
---
无序列表使用-、+和*作为列表标记：
```
-  Red
- Green
- Blue

* Red
* Green
*  Blue

+  Red
+ Green
+ Blue
```
-  Red
- Green
- Blue

* Red
* Green
*  Blue

+  Red
+ Green
+ Blue


2.2有序列表（Ordered Lists）
---
有序列表则使用数字加英文句点.来表示：
```
1.Red
2.Green
3.Blue
```
1.Red
2.Green
3.Blue

3.引用（Reference）
===
引用以>来表示，引用中支持多级引用、标题、列表、代码块、分割线等常规语法。

3.1常见的引用写法：
---
```
> 这是一段引用    //在`>`后面有 1 个空格
> 
>     这是引用的代码块形式    //在`>`后面有 5 个空格
```
> 这是一段引用    //在`>`后面有 1 个空格
> 
>     这是引用的代码块形式    //在`>`后面有 5 个空格

3.2分级引用
---
```
> 一级引用
> > 二级引用
> > > 三级引用

> #### 这是一个四级标题
> 
> 1. 这是第一行列表项
> 2. 这是第二行列表项
```
> 一级引用
> > 二级引用
> > > 三级引用

> #### 这是一个四级标题
> 
> 1. 这是第一行列表项
> 2. 这是第二行列表项

4.文字样式（Text Styling）
===
4.1. 基本语法
---
两个或-代表加粗，一个或-代表斜体，~~代表删除。
```
**加粗文本** 或者 __加粗文本__
*斜体文本*  或者　_斜体文本_
***斜粗体*** 或者 ___斜粗文本___
~~删除文本~~
 :==高亮 #807700==或者==高亮==
` 底纹 `
```
**加粗文本** 或者 __加粗文本__
*斜体文本*  或者　_斜体文本_
***斜粗体*** 或者 ___斜粗文本___
~~删除文本~~
 :==高亮 #807700==或者==高亮==
` 底纹 `

4.2.非基本语法
---
#### 4.2.1简书中，字体上、下标的语法为：
```
<sup>上标文字</sup>
<sub>下标文字</sub>
```
<sup>上标文字</sup>
<sub>下标文字</sub>

#### 4.2.2Typora 中，字体上、下标的语法为：
```
这是^上标文字^
这是~下标文字~
```
这是^上标文字^
这是~下标文字~

5.图片与链接（Images & Links）
===
```
图片：![]() ![图片描述(可忽略)](链接的地址)
链接：[]() [链接描述](链接的地址)

This is [an example](http://example.com/ "Title") inline link.
[This link](http://example.net/) has no title attribute.
```
图片：![]() ![图片描述(可忽略)](链接的地址)
链接：[]() [链接描述](链接的地址)

This is [an example](http://example.com/ "Title") inline link.
[This link](http://example.net/) has no title attribute.

```
这是行内式链接：[Mou](http://25.io/mou/)。

这是参考式链接：[Mou][url]，其中url为链接标记，可置于文中任意位置。

[url]: 25.io/mou/
```
这是行内式链接：[Mou](http://25.io/mou/)。

这是参考式链接：[Mou][url]，其中url为链接标记，可置于文中任意位置。

[url]: 25.io/mou/
```
这是自动链接：直接使用`<>`括起来<http://25.io/mou/>
```
这是自动链接：直接使用`<>`括起来<http://25.io/mou/>

```
这是图片：![][Mou icon]

[Mou icon]: http://mouapp.com/Mou_128.png
```
这是图片：![][Mou icon]

[Mou icon]: http://mouapp.com/Mou_128.png

6.代码块引用（Fenced Code Blocks）
===
6.1行内代码
---
```
`Hello world`
```
`Hello world`

6.2代码框
---

7.表格（Tables）
===
>表格对齐格式
居左：:----
居中：:----:或-----
居右：----:
例子：

```
|标题|标题|标题|
|:---|:---:|---:|
|居左文本|居中文本|居右文本|
```
|标题|标题|标题|
|:---|:---:|---:|
|居左文本|居中文本|居右文本|

8.分隔线
===
```
***
---
_____
* * *
```

***
---
_____
* * *

9.脚注|注解（Footnotes）
===
9.1使用[^]来定义脚注：
---
```
这是一个脚注的例子[^1]
[^1]: 这里是脚注
```
这是一个脚注的例子[^1]
[^1]: 这里是脚注

9.2注释
---
```
<!--注释-->
```
10.常用弥补Markdown的Html标签
===
10.1字体
---
```
<font face="微软雅黑" color="red" size="6">字体及字体颜色和大小</font>
<font color="#0000ff">字体颜色</font>
```
<font face="微软雅黑" color="red" size="6">字体及字体颜色和大小</font>
<font color="#0000ff">字体颜色</font>

10.2换行
---
```
使用html标签`<br/>`<br/>换行
```
使用html标签`<br/>`<br/>换行

10.3文本对齐方式
---
```
<p align="left">居左文本</p>
<p align="center">居中文本</p>
<p align="right">居右文本</p>
```
<p align="left">居左文本</p>
<p align="center">居中文本</p>
<p align="right">居右文本</p>

10.4下划线
---
```
<u>下划线文本</u>
```
<u>下划线文本</u>

11.任务列表
===
```
- [ ] [links](), **formatting**, and ~~tags~~ supported
- [x] list syntax required (any unordered or ordered list supported)
- [ ] this is a complete item
- [x] this is an incomplete item
```
- [ ] [links](), **formatting**, and ~~tags~~ supported
- [x] list syntax required (any unordered or ordered list supported)
- [ ] this is a complete item
- [x] this is an incomplete item

12.转义字符（Backslash Escapes）
===
```
\*literal asterisks\*
```
\*literal asterisks\*
