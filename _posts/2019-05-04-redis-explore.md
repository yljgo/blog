---
layout: post
title: 浅谈Redis
subtitle: ""
author: "Paul"
header-style: text
tags:
  - redis
  - 多路复用
---

> Redis 是一个事件驱动的内存数据库

### Redis数据结构

>Redis 数据库里面的每个键值对都是由对象组成的，其中数据库的键总是一个字符串对象（string object），数据库的值则可以使**字符串**对象、**列表**对象（list object）、**哈希**对象（hash object）、**集合**对象（set object）和**有序集合**对象（sorted object）这五种数据结构。下面我们一起来看下这些数据对象在 Redis 的内部是怎么实现的，以及 Redis 是怎么选择合适的数据结构进行存储等。

**简单动态字符串（SDS）**

SDS （Simple Dynamic String）是 Redis 最基础的数据结构。直译过来就是”简单的动态字符串“。Redis 自己实现了一个动态的字符串，而不是直接使用了 C 语言中的字符串。

SDS的数据结构：
```c
struct __attribute__ ((__packed__)) sdshdr64 {
    struct sdshdr {
    
    // buf 中已占用空间的长度
    int len;

    // buf 中剩余可用空间的长度
    int free;

    // 数据空间
    char buf[];
};
```

![](/img/redis-sds.jpg)
所以我们看到，sds 包含3个参数。buf 的长度 len，buf 的剩余长度，以及buf。

为什么这么设计呢？

- 可以直接获取字符串长度。
C 语言中，获取字符串的长度需要用指针遍历字符串，时间复杂度为 O(n)，而 SDS 的长度，直接从len 获取复杂度为 O(1)。

- 杜绝缓冲区溢出。
由于C 语言不记录字符串长度，如果增加一个字符传的长度，如果没有注意就可能溢出，覆盖了紧挨着这个字符的数据。对于SDS 而言增加字符串长度需要验证 free的长度，如果free 不够就会扩容整个 buf，防止溢出。

- 减少修改字符串长度时造成的内存再次分配。
redis 作为高性能的内存数据库，需要较高的相应速度。字符串也很大概率的频繁修改。 SDS 通过未使用空间这个参数，将字符串的长度和底层buf的长度之间的额关系解除了。buf的长度也不是字符串的长度。基于这个分设计 SDS 实现了空间的预分配和惰性释放。

  - 预分配
    如果对 SDS 修改后，如果 len 小于 1MB 那 len = 2 * len + 1byte。 这个 1 是用于保存空字节。
    如果 SDS 修改后 len 大于 1MB 那么 len = 1MB + len + 1byte。
  - 惰性释放
    如果缩短 SDS 的字符串长度，redis并不是马上减少 SDS 所占内存。只是增加 free 的长度。同时向外提供 API 。真正需要释放的时候，才去重新缩小 SDS 所占的内存

- 二进制安全。
C 语言中的字符串是以 ”\0“ 作为字符串的结束标记。而 SDS 是使用 len 的长度来标记字符串的结束。所以SDS 可以存储字符串之外的任意二进制流。因为有可能有的二进制流在流中就包含了”\0“造成字符串提前结束。也就是说 SDS 不依赖 “\0” 作为结束的依据。

- 兼容C语言
SDS 按照惯例使用 ”\0“ 作为结尾的管理。部分普通C 语言的字符串 API 也可以使用。

**链表**

链表提供了高效的节点排重能力，以及顺序性的节点访问方式，而且可以通过增加节点来灵活地调整链表的长度。它是一种常用的数据结构，被内置在很多高级语言中。因为C语言并没有内置这种数据结构，所以 Redis 构建了自己的链表实现。 

```c
// 链表的节点
typedef struct listNode {

    // 前置节点
    struct listNode *prev;

    // 后置节点
    struct listNode *next;

    // 节点的值
    void *value;

} listNode;
非常典型的双向链表的数据结构。

同时为双向链表提供了如下操作的函数：
```c
/*
 * 双端链表迭代器
 */
typedef struct listIter {

    // 当前迭代到的节点
    listNode *next;

    // 迭代的方向
    int direction;

} listIter;

/*
 * 双端链表结构
 */
typedef struct list {

    // 表头节点
    listNode *head;

    // 表尾节点
    listNode *tail;

    // 节点值复制函数
    void *(*dup)(void *ptr);

    // 节点值释放函数
    void (*free)(void *ptr);

    // 节点值对比函数
    int (*match)(void *ptr, void *key);

    // 链表所包含的节点数量
    unsigned long len;

} list;
```
链表的结构比较简单，数据结构如下：
![](/img/redis-list.jpg)

总结一下性质：

- 双向链表，某个节点寻找上一个或者下一个节点时间复杂度 O(1)。
- list 记录了 head 和 tail，寻找 head 和 tail 的时间复杂度为 O(1)。
- 获取链表的长度 len 时间复杂度 O(1)。


**字典**

字典数据结构极其类似 java 中的 Hashmap。

Redis的字典由三个基础的数据结构组成。最底层的单位是哈希表节点。结构如下：
```c
typedef struct dictEntry {
    
    // 键
    void *key;

    // 值
    union {
        void *val;
        uint64_t u64;
        int64_t s64;
    } v;

    // 指向下个哈希表节点，形成链表
    struct dictEntry *next;

} dictEntry;
```
实际上哈希表节点就是一个单项列表的节点。保存了一下下一个节点的指针。 key 就是节点的键，v是这个节点的值。这个 v 既可以是一个指针，也可以是一个 uint64_t或者 int64_t 整数。*next 指向下一个节点。

通过一个哈希表的数组把各个节点链接起来：
```c
typedef struct dictht {
    
    // 哈希表数组
    dictEntry **table;

    // 哈希表大小
    unsigned long size;
    
    // 哈希表大小掩码，用于计算索引值
    // 总是等于 size - 1
    unsigned long sizemask;

    // 该哈希表已有节点的数量
    unsigned long used;

} dictht;
```
![](/img/redis-dictht.jpg)

实际上，如果对java 的基本数据结构了解的同学就会发现，这个数据结构和 java 中的 HashMap 是很类似的，就是数组加链表的结构。

字典的数据结构：
```c
typedef struct dict {

    // 类型特定函数
    dictType *type;

    // 私有数据
    void *privdata;

    // 哈希表
    dictht ht[2];

    // rehash 索引
    // 当 rehash 不在进行时，值为 -1
    int rehashidx; /* rehashing not in progress if rehashidx == -1 */

    // 目前正在运行的安全迭代器的数量
    int iterators; /* number of iterators currently running */

} dict;
```
其中的dictType 是一组方法，代码如下：
```c
/*
 * 字典类型特定函数
 */
typedef struct dictType {

    // 计算哈希值的函数
    unsigned int (*hashFunction)(const void *key);

    // 复制键的函数
    void *(*keyDup)(void *privdata, const void *key);

    // 复制值的函数
    void *(*valDup)(void *privdata, const void *obj);

    // 对比键的函数
    int (*keyCompare)(void *privdata, const void *key1, const void *key2);

    // 销毁键的函数
    void (*keyDestructor)(void *privdata, void *key);
    
    // 销毁值的函数
    void (*valDestructor)(void *privdata, void *obj);

} dictType;
```
字典的数据结构如下图：
![](/img/redis-dictType.jpg)
这里我们可以看到一个dict 拥有两个 dictht。一般来说只使用 ht[0],当扩容的时候发生了rehash的时候，ht[1]才会被使用。

当我们观察或者研究一个hash结构的时候偶我们首先要考虑的这个 dict 如何插入一个数据？

我们梳理一下插入数据的逻辑。

- 计算Key 的 hash 值。找到 hash 映射到 table 数组的位置。

- 如果数据已经有一个 key 存在了。那就意味着发生了 hash 碰撞。新加入的节点，就会作为链表的一个节点接到之前节点的 next 指针上。

- 如果 key 发生了多次碰撞，造成链表的长度越来越长。会使得字典的查询速度下降。为了维持正常的负载。Redis 会对 字典进行 rehash 操作。来增加 table 数组的长度。所以我们要着重了解一下 Redis 的 rehash。步骤如下：

  - 根据ht[0] 的数据和操作的类型（扩大或缩小），分配 ht[1] 的大小。
  - 将 ht[0] 的数据 rehash 到 ht[1] 上。
  - rehash 完成以后，将ht[1] 设置为 ht[0]，生成一个新的ht[1]备用。

- 渐进式的 rehash 。
其实如果字典的 key 数量很大，达到千万级以上，rehash 就会是一个相对较长的时间。所以为了字典能够在 rehash 的时候能够继续提供服务。Redis 提供了一个渐进式的 rehash 实现，rehash的步骤如下：

  - 分配 ht[1] 的空间，让字典同时持有 ht[1] 和 ht[0]。
  - 在字典中维护一个 rehashidx，设置为 0 ，表示字典正在 rehash。
  - 在rehash期间，每次对字典的操作除了进行指定的操作以外，都会根据 ht[0] 在 rehashidx 上对应的键值对 rehash 到 ht[1]上。
  - 随着操作进行， ht[0] 的数据就会全部 rehash 到 ht[1] 。设置ht[0] 的 rehashidx 为 -1，渐进的 rehash 结束。这样保证数据能够平滑的进行 rehash。防止 rehash 时间过久阻塞线程。

- 在进行 rehash 的过程中，如果进行了 delete 和 update 等操作，会在两个哈希表上进行。如果是 find 的话优先在ht[0] 上进行，如果没有找到，再去 ht[1] 中查找。如果是 insert 的话那就只会在 ht[1]中插入数据。这样就会保证了 ht[1] 的数据只增不减，ht[0]的数据只减不增。


**跳跃表**

跳跃表是一种有序的链性数据结构，通过维护层级 (level) 来达到快速访问节点的目的。平均查找复杂度为 O(logN)，最坏 O(N)。因为是链性结构，还支持顺序性操作。
跳表是由一个zskiplist和多个zskiplistNode组成。我们先看看他们的结构：
```c
/* ZSETs use a specialized version of Skiplists */
/*
 * 跳跃表节点
 */
typedef struct zskiplistNode {

    // 成员对象
    robj *obj;

    // 分值
    double score;

    // 后退指针
    struct zskiplistNode *backward;

    // 层
    struct zskiplistLevel {

        // 前进指针
        struct zskiplistNode *forward;

        // 跨度
        unsigned int span;

    } level[];

} zskiplistNode;

/*
 * 跳跃表
 */
typedef struct zskiplist {

    // 表头节点和表尾节点
    struct zskiplistNode *header, *tail;

    // 表中节点的数量
    unsigned long length;

    // 表中层数最大的节点的层数
    int level;

} zskiplist;
```
所以根据这个代码我们可以画出如下的结构图：
![](/img/redis-zskiplist.jpg)

其实跳表就是一个利用空间换时间的数据结构，利用 level 作为链表的索引。

之前有人问过 Redis 的作者 为什么使用跳跃表，而不是 tree 来构建索引？作者的回答是：

- 省内存。
- 服务于 ZRANGE 或者 ZREVRANGE 是一个典型的链表场景。时间复杂度的表现和平衡树差不多。
- 最重要的一点是跳跃表的实现很简单就能达到 O(logN)的级别。

**整数集合**

当一个集合只包含整数，且这个集合的元素不多的时候，Redis 就会使用整数集合 intset 。首先看 intset 的数据结构：
```c
typedef struct intset {
    
    // 编码方式
    uint32_t encoding;

    // 集合包含的元素数量
    uint32_t length;

    // 保存元素的数组
    int8_t contents[];
} intset;
```
其实 intset 的数据结构比较好理解。一个数据保存元素，length 保存元素的数量，也就是contents的大小，encoding 用于保存数据的编码方式。

通过代码我们可以知道，encoding 的编码类型包括了：
```c
#define INTSET_ENC_INT16 (sizeof(int16_t))
#define INTSET_ENC_INT32 (sizeof(int32_t))
#define INTSET_ENC_INT64 (sizeof(int64_t))
```
实际上我们可以看出来。 Redis encoding的类型，就是指数据的大小。作为一个内存数据库，采用这种设计就是为了节约内存。

既然有从小到大的三个数据结构，在插入数据的时候尽可能使用小的数据结构来节约内存，如果插入的数据大于原有的数据结构，就会触发扩容。

扩容有三个步骤：

1. 根据新元素的类型，修改整个数组的数据类型，并重新分配空间
2. 将原有的的数据，装换为新的数据类型，重新放到应该在的位置上，且保存顺序性
3. 再插入新元素

整数集合不支持降级操作，一旦升级就不能降级了。


**压缩列表**

压缩链表 Redis 作者的介绍是，为了尽可能节约内存设计出来的双向链表。

对于一个压缩列表代码里注释给出的数据结构如下：
![](/img/reids-zl.jpg)

zlbytes 表示的是整个压缩列表使用的内存字节数
zltail 指定了压缩列表的尾节点的偏移量
zllen 是压缩列表 entry 的数量
entry 就是 ziplist 的节点
zlend 标记压缩列表的末端

这个列表中还有单个指针：
ZIPLIST_ENTRY_HEAD 列表开始节点的头偏移量
ZIPLIST_ENTRY_TAIL 列表结束节点的头偏移量
ZIPLIST_ENTRY_END 列表的尾节点结束的偏移量

再看看一个 entry 的结构：
```c
/*
 * 保存 ziplist 节点信息的结构
 */
typedef struct zlentry {

    // prevrawlen ：前置节点的长度
    // prevrawlensize ：编码 prevrawlen 所需的字节大小
    unsigned int prevrawlensize, prevrawlen;

    // len ：当前节点值的长度
    // lensize ：编码 len 所需的字节大小
    unsigned int lensize, len;

    // 当前节点 header 的大小
    // 等于 prevrawlensize + lensize
    unsigned int headersize;

    // 当前节点值所使用的编码类型
    unsigned char encoding;

    // 指向当前节点的指针
    unsigned char *p;

} zlentry;
```
依次解释一下这几个参数。

prevrawlen 前置节点的长度，这里多了一个 size，其实是记录了 prevrawlen 的尺寸。Redis 为了节约内存并不是直接使用默认的 int 的长度，而是逐渐升级的。
同理 len 记录的是当前节点的长度，lensize 记录的是 len 的长度。
headersize 就是前文提到的两个 size 之和。
encoding 就是这个节点的数据类型。这里注意一下 encoding 的类型只包括整数和字符串。
p 节点的指针，不用过多的解释。

需要注意一点，因为每个节点都保存了前一个节点的长度，如果发生了更新或者删除节点，则这个节点之后的数据也需要修改，有一种最坏的情况就是如果每个节点都处于需要扩容的零界点，就会造成这个节点之后的节点都要修改 size 这个参数，引发连锁反应。这个时候就是 压缩链表最坏的时间复杂度 O(n^2)。不过所有节点都处于临界值，这样的概率可以说比较小。

**总结**

Redis 内部是由一系列对象组成的，字符串对象、列表对象、哈希表对象、集合对象有序集合对象。

字符串对象是唯一一个可以应用在上面所以对象中的，所以我们看到向一些 keys exprice 这种命令可以在针对所有 key 使用，因为所有 key 都是采用的字符串对象。 列表对象默认使用压缩列表为底层实现，当对象保存的元素数量大于 512 个或者是长度大于64字节的时候会转换为双端链表。

哈希对象也是优先使用压缩列表键值对在压缩列表中连续储存着，当对象保存的元素数量大于 512 个或者是长度大于64字节的时候会转换为哈希表。

集合对象可以采用整数集合或者哈希表，当对象保存的元素数量大于 512 个或者是有元素非整数的时候转换为哈希表。

有序集合默认采用压缩列表，当集合元素数量大于 128 个或者是元素成员长度大于 64 字节的时候转换为跳跃表。


### redis“单线程”

**IO模型**

IO模型的根本差异在于以下两个过程的处理方式不同：
1. kernel等待足够的数据到达。
2. kernel将数据从内核空间拷贝到用户空间。

**同步阻塞IO**

上述两个过程都是阻塞的，进程会一直阻塞直到数据到达并从内核空间拷贝到用户空间。

**异步IO**

以上两个过程都是非阻塞的，当进程发起系统调用之后会马上返回，可以继续干别的事情，当数据在用户空间准备好后，内核会给用户进程一个signal。

**信号驱动IO**

第一个过程是非阻塞的，当数据在内核中准备好后会给进程发送一个signal。但之后用户进程必须以阻塞的方式从内核中把数据拷贝到用户空间，因此本质上还是同步IO。

**Redis 中的事件驱动模型**

Redis 是一个事件驱动的内存数据库，服务器需要处理两种类型的事件。
- 文件事件
- 时间事件

**文件事件**

Redis 服务器通过 socket 实现与客户端（或其他redis服务器）的交互,文件事件就是服务器对 socket 操作的抽象。 Redis 服务器，通过监听这些 socket 产生的文件事件并处理这些事件，实现对客户端调用的响应。

**Reactor**

Redis 基于 Reactor 模式开发了自己的事件处理器。
这里就先展开讲一讲 Reactor 模式。看下图：
![](/img/Reactor.jpg)

“I/O 多路复用模块”会监听多个 FD ，当这些FD产生，accept，read，write 或 close 的文件事件。会向“文件事件分发器（dispatcher）”传送事件。

文件事件分发器（dispatcher）在收到事件之后，会根据事件的类型将事件分发给对应的 handler。

我们顺着图，从上到下的逐一讲解 Redis 是怎么实现这个 Reactor 模型的。

**I/O 多路复用模块**
Redis 的 I/O 多路复用模块，其实是封装了操作系统提供的 select，epoll，avport 和 kqueue 这些基础函数。向上层提供了一个统一的接口，屏蔽了底层实现的细节。

一般而言 Redis 都是部署到 Linux 系统上，所以我们就看看使用 Redis 是怎么利用 linux 提供的 epoll 实现I/O 多路复用。

首先看看 epoll 提供的三个方法：
```c
/*
 * 创建一个epoll的句柄，size用来告诉内核这个监听的数目一共有多大
 */
int epoll_create(int size)；

/*
 * 可以理解为，增删改 fd 需要监听的事件
 * epfd 是 epoll_create() 创建的句柄。
 * op 表示 增删改
 * epoll_event 表示需要监听的事件，Redis 只用到了可读，可写，错误，挂断 四个状态
 */
int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event)；

/*
 * 可以理解为查询符合条件的事件
 * epfd 是 epoll_create() 创建的句柄。
 * epoll_event 用来存放从内核得到事件的集合
 * maxevents 获取的最大事件数
 * timeout 等待超时时间
 */
int epoll_wait(int epfd, struct epoll_event * events, int maxevents, int timeout);
```

再看 Redis 对文件事件，封装epoll向上提供的接口：
```c
/*
 * 事件状态
 */
typedef struct aeApiState {

    // epoll_event 实例描述符
    int epfd;

    // 事件槽
    struct epoll_event *events;

} aeApiState;

/*
 * 创建一个新的 epoll 
 */
static int  aeApiCreate(aeEventLoop *eventLoop)
/*
 * 调整事件槽的大小
 */
static int  aeApiResize(aeEventLoop *eventLoop, int setsize)
/*
 * 释放 epoll 实例和事件槽
 */
static void aeApiFree(aeEventLoop *eventLoop)
/*
 * 关联给定事件到 fd
 */
static int  aeApiAddEvent(aeEventLoop *eventLoop, int fd, int mask)
/*
 * 从 fd 中删除给定事件
 */
static void aeApiDelEvent(aeEventLoop *eventLoop, int fd, int mask)
/*
 * 获取可执行事件
 */
static int  aeApiPoll(aeEventLoop *eventLoop, struct timeval *tvp)
```
所以看看这个ae_peoll.c 如何对 epoll 进行封装的：

- aeApiCreate() 是对 epoll.epoll_create() 的封装。
- aeApiAddEvent()和aeApiDelEvent() 是对 epoll.epoll_ctl()的封装。
- aeApiPoll() 是对 epoll_wait()的封装。

这样 Redis 的利用 epoll 实现的 I/O 复用器就比较清晰了。

再往上一层次我们需要看看 ea.c 是怎么封装的？

首先需要关注的是事件处理器的数据结构：
```c
typedef struct aeFileEvent {

    // 监听事件类型掩码，
    // 值可以是 AE_READABLE 或 AE_WRITABLE ，
    // 或者 AE_READABLE | AE_WRITABLE
    int mask; /* one of AE_(READABLE|WRITABLE) */

    // 读事件处理器
    aeFileProc *rfileProc;

    // 写事件处理器
    aeFileProc *wfileProc;

    // 多路复用库的私有数据
    void *clientData;

} aeFileEvent;
```
mask 就是可以理解为事件的类型。

除了使用 ae_peoll.c 提供的方法外,ae.c 还增加 “增删查” 的几个 API。

- 增:aeCreateFileEvent
- 删:aeDeleteFileEvent
- 查: 查包括两个维度 aeGetFileEvents 获取某个 fd 的监听类型和aeWait等待某个fd 直到超时或者达到某个状态。

**事件分发器（dispatcher）**

Redis 的事件分发器 ```ae.c/aeProcessEvents``` 不但处理文件事件还处理时间事件，所以这里只贴与文件分发相关的出部分代码，dispather 根据 mask 调用不同的事件处理器。
```c
//从 epoll 中获关注的事件
numevents = aeApiPoll(eventLoop, tvp);
for (j = 0; j < numevents; j++) {
    // 从已就绪数组中获取事件
    aeFileEvent *fe = &eventLoop->events[eventLoop->fired[j].fd];

    int mask = eventLoop->fired[j].mask;
    int fd = eventLoop->fired[j].fd;
    int rfired = 0;

    // 读事件
    if (fe->mask & mask & AE_READABLE) {
        // rfired 确保读/写事件只能执行其中一个
        rfired = 1;
        fe->rfileProc(eventLoop,fd,fe->clientData,mask);
    }
    // 写事件
    if (fe->mask & mask & AE_WRITABLE) {
        if (!rfired || fe->wfileProc != fe->rfileProc)
            fe->wfileProc(eventLoop,fd,fe->clientData,mask);
    }

    processed++;
}
```
可以看到这个分发器，根据 mask 的不同将事件分别分发给了读事件和写事件。

**文件事件处理器的类型**

Redis 有大量的事件处理器类型，我们就讲解处理一个简单命令涉及到的三个处理器：

- acceptTcpHandler 连接应答处理器，负责处理连接相关的事件，当有client 连接到Redis的时候们就会产生 AE_READABLE 事件。引发它执行。
- readQueryFromClinet 命令请求处理器，负责读取通过 sokect 发送来的命令。
- sendReplyToClient 命令回复处理器，当Redis处理完命令，就会产生 AE_WRITEABLE 事件，将数据回复给 client。

**文件事件实现总结**

我们按照开始给出的 Reactor 模型，从上到下讲解了文件事件处理器的实现，下面将会介绍时间时间的实现。

**时间事件**

Reids 有很多操作需要在给定的时间点进行处理，时间事件就是对这类定时任务的抽象。

先看时间事件的数据结构：
```c
/* Time event structure
 *
 * 时间事件结构
 */
typedef struct aeTimeEvent {

    // 时间事件的唯一标识符
    long long id; /* time event identifier. */

    // 事件的到达时间
    long when_sec; /* seconds */
    long when_ms; /* milliseconds */

    // 事件处理函数
    aeTimeProc *timeProc;

    // 事件释放函数
    aeEventFinalizerProc *finalizerProc;

    // 多路复用库的私有数据
    void *clientData;

    // 指向下个时间事件结构，形成链表
    struct aeTimeEvent *next;

} aeTimeEvent;
```
我们就知道这个 aeTimeEvent 是一个链表结构。看图：
![](/img/aeTimeEvent.jpg)
注意这是一个按照id倒序排列的链表，并没有按照事件顺序排序。

**processTimeEvent**

Redis 使用这个函数处理所有的时间事件，我们整理一下执行思路：

1. 记录最新一次执行这个函数的时间，用于处理系统时间被修改产生的问题。
2. 遍历链表找出所有 when_sec 和 when_ms 小于现在时间的事件。
3. 执行事件对应的处理函数。
4. 检查事件类型，如果是周期事件则刷新该事件下一次的执行事件。
5. 否则从列表中删除事件。

**综合调度器（aeProcessEvents）**

综合调度器是 Redis 统一处理所有事件的地方。我们梳理一下这个函数的简单逻辑：
```c
// 1. 获取离当前时间最近的时间事件
shortest = aeSearchNearestTimer(eventLoop);

// 2. 获取间隔时间
timeval = shortest - nowTime;

// 如果timeval 小于 0，说明已经有需要执行的时间事件了。
if(timeval < 0){
    timeval = 0
}

// 3. 在 timeval 时间内，取出文件事件。
numevents = aeApiPoll(eventLoop, timeval);

// 4.根据文件事件的类型指定不同的文件处理器
if (AE_READABLE) {
    // 读事件
    rfileProc(eventLoop,fd,fe->clientData,mask);
}
    // 写事件
if (AE_WRITABLE) {
    wfileProc(eventLoop,fd,fe->clientData,mask);
}
```
以上的伪代码就是整个 Redis 事件处理器的逻辑。

我们可以再看看谁执行了这个 aeProcessEvents:
```c
void aeMain(aeEventLoop *eventLoop) {

    eventLoop->stop = 0;

    while (!eventLoop->stop) {

        // 如果有需要在事件处理前执行的函数，那么运行它
        if (eventLoop->beforesleep != NULL)
            eventLoop->beforesleep(eventLoop);

        // 开始处理事件
        aeProcessEvents(eventLoop, AE_ALL_EVENTS);
    }
}
```

然后我们再看看是谁调用了 eaMain:
```c
int main(int argc, char **argv) {
    //一些配置和准备
    ...
    aeMain(server.el);
    
    //结束后的回收工作
    ...
}
```
我们在 Redis 的 main 方法中找个了它。

这个时候我们整理出的思路就是:

- Redis 的 main() 方法执行了一些配置和准备以后就调用 eaMain() 方法。
- eaMain() while(true) 的调用 aeProcessEvents()。

所以我们说 Redis 是一个事件驱动的程序，期间我们发现，Redis 没有 fork 过任何线程。所以也可以说 Redis 是一个基于事件驱动的单线程应用。

### redis多线程改进方案

Redis以其极高的性能以及支持丰富的数据结构而著称，在互联网行业应用广泛，尤其是KV缓存，以及类似索引的zset有序集合。然而随着服务器CPU核数的增加，Redis单线程的设计也被大家所诟病。

**几种多线程模型的对比**
- 首先是阿里云Redis采用的简化方案，增加多个reactor线程(IO线程)和一个worker线程
> 这个方案采取了折中的方式，只有一个worker线程负责所有的对数据库的读写操作，
这个就避免了很并行操作数据库的多线程安全问题。

![阿里云Redis](/img/multi_thread.jpg)

1. 主线程监听端口，当有请求到来时从accepted队列从取出已经就绪的连接描述符，将之加入到某个reactor线程的事件循环中，并指定可读时触发事件，与回调函数
2. 有多个reactor线程，里面都有各自的事件循环，从主线程绑定过来的连接描述符connfd可读时，会执行绑定的回调函数，在回调函数里读取数据，写入到c->querybuf中，并将连接对象添加到线程的无锁队列中，然后使用管道(socketpair)通知worker线程
3. 一个worker线程，也带有事件循环，绑定管道的可读事件，当reactor线程写管道时，会触发可读事件绑定的回调函数，回调函数中，从无锁队列中取出redisclient *c 对象，执行c->querybuf中的请求，将结果写入c->buf，最后将连接connfd再以可写触发类型绑定到reactor线程，由reactor将结果write(connfd)输出给客户端

- 唯品会实现的多线程版redis：vire
> vire的多线程模型类似于多线程不区分IO线程和工作线程，从IO到命令执行都在同一个线程，对数据库的并行操作同个一个比较粗粒度的锁来保证线程安全，(不过vire这个就是一个按照redis思路的一个全新实现了)

### redis管道技术

Redis客户端与服务器之间使用TCP协议进行通信，并且很早就支持管道（pipelining）技术了。在某些高并发的场景下，网络开销成了Redis速度的瓶颈，所以需要使用管道技术来实现突破。

在介绍管道之前，先来想一下单条命令的执行步骤：

- 客户端把命令发送到服务器，然后阻塞客户端，等待着从socket读取服务器的返回结果
- 服务器处理命令并将结果返回给客户端

按照这样的描述，
>每个命令的执行时间 = 客户端发送时间+服务器处理和返回时间+一个网络来回的时间

其中一个网络来回的时间是不固定的，它的决定因素有很多，比如客户端到服务器要经过多少跳，网络是否拥堵等等。但是这个时间的量级也是最大的，也就是说一个命令的完成时间的长度很大程度上取决于网络开销。如果我们的服务器每秒可以处理10万条请求，而网络开销是250毫秒，那么实际上每秒钟只能处理4个请求。最暴力的优化方法就是使客户端和服务器在一台物理机上，这样就可以将网络开销降低到1ms以下。但是实际的生产环境我们并不会这样做。而且即使使用这种方法，当请求非常频繁时，这个时间和服务器处理时间比较仍然是很长的。

**Redis Pipelining**

为了解决这种问题，Redis在很早就支持了管道技术。也就是说客户端可以一次发送多条命令，不用逐条等待命令的返回值，而是到最后一起读取返回结果，这样只需要一次网络开销，速度就会得到明显的提升。管道技术其实已经非常成熟并且得到广泛应用了，例如POP3协议由于支持管道技术，从而显著提高了从服务器下载邮件的速度。

在Redis中，如果客户端使用管道发送了多条命令，那么服务器就会将多条命令放入一个队列中，这一操作会消耗一定的内存，所以**管道中命令的数量并不是越大越好**（太大容易撑爆内存），而是应该有一个合理的值。


**深入理解Redis交互流程**

![Redis交互流程](/img/redis-flow.jpg)

管道并不只是用来网络开销延迟的一种方法，它实际上是会提升Redis服务器每秒操作总数的。在解释原因之前，需要更深入的了解Redis命令处理过程。

一个完整的交互流程如下：

1. 客户端进程调用write()把消息写入到操作系统内核为Socket分配的send buffer中
2. 操作系统会把send buffer中的内容写入网卡，网卡再通过网关路由把内容发送到服务器端的网卡
3. 服务端网卡会把接收到的消息写入操作系统为Socket分配的recv buffer
4. 服务器进程调用read()读取消息然后进行处理
5. 处理完成后调用write()把返回结果写入到服务器端的send buffer
6. 服务器操作系统再将send buffer中的内容写入网卡，然后发送到客户端
7. 客户端操作系统将网卡内容读到recv buffer中
8. 客户端进程调用read()从recv buffer中读取消息并返回

现在我们把命令执行的时间进一步细分：

>命令的执行时间 = 客户端调用write并写网卡时间+一次网络开销的时间+服务读网卡并调用read时间++服务器处理数据时间+服务端调用write并写网卡时间+客户端读网卡并调用read时间

这其中除了网络开销，花费时间最长的就是进行系统调用write()和read()了，这一过程需要操作系统由用户态切换到内核态，中间涉及到的上下文切换会浪费很多时间。

使用管道时，多个命令只会进行一次read()和wrtie()系统调用，因此使用管道会提升Redis服务器处理命令的速度，随着管道中命令的增多，服务器每秒处理请求的数量会线性增长，最后会趋近于不使用管道的10倍。

![](/img/redis-press.jpg)

**总结**

1. 使用管道技术可以显著提升Redis处理命令的速度，其原理就是将多条命令打包，只需要一次网络开销，在服务器端和客户端各一次read()和write()系统调用，以此来节约时间。
2. 管道中的命令数量要适当，并不是越多越好。
3. Redis2.6版本以后，脚本在大部分场景中的表现要优于管道。

### Redis 数据库、键过期的实现

**数据库的实现**

我们先看代码 server.h/redisServer
```c
struct redisServer{
    ...

    //保存 db 的数组
    redisDb *db;
    
    //db 的数量
    int dbnum;

    ...
}
```
再看redisDb的代码：
```c
typedef struct redisDb {
    dict *dict;                 /* The keyspace for this DB */
    dict *expires;              /* Timeout of keys with a timeout set */
    dict *blocking_keys;        /* Keys with clients waiting for data (BLPOP)*/
    dict *ready_keys;           /* Blocked keys that received a PUSH */
    dict *watched_keys;         /* WATCHED keys for MULTI/EXEC CAS */
    int id;                     /* Database ID */
    long long avg_ttl;          /* Average TTL, just for stats */
} redisDb;
```
总体来说redis的 server 包含若干个（默认16个） redisDb 数据库。

![](/img/redisServer.jpg)

Redis 是一个 k-v 存储的键值对数据库。其中字典 dict 保存了数据库中的所有键值对，这个地方叫做 keyspace 直译过来就是“键空间”。

所以我们就可以这么认为，在 redisDb 中我们使用 dict（字典）来维护键空间。

- keyspace 的 kay 是数据库的 key，每一个key 是一个字符串对象。注意不是字符串，而是字符串对象。
- keyspace 的 value 是数据库的 value，这个 value 可以是 redis 的，字符串对象，列表对象，哈希表对象，集合对象或者有序对象中的一种。

**数据库读写操作**
所以对于数据的增删改查，就是对 keyspace 这个大 map 的增删改查。

当我们执行：
```
>redis SET mobile "13800000000"
```
实际上就是为 keyspace 增加了一个 key 是包含字符串“mobile”的字符串对象，value 为包含字符“13800000000”的字符串对象。
![](/img/redis-set.jpg)
对于删改查，没啥好说的。类似java 的 map 操作，大多数程序员应该都能理解。

需要特别注意的是，再执行对键的读写操作的时候，Redis 还要做一些额外的维护动作：

- 维护 hit 和 miss 两个计数器。用于统计 Redis 的缓存命中率。
- 更新键的 LRU 时间，记录键的最后活跃时间。
- 如果在读取的时候发现键已经过期，Redis 先删除这个过期的键然后再执行余下操作。
- 如果有客户对这个键执行了 WATCH 操作，会把这个键标记为 dirty，让事务注意到这个键已经被改过。
- 没修改一次 dirty 会增加1。
- 如果服务器开启了数据库通知功能，键被修改之后，会按照配置发送通知。

**键的过期实现**

Redis 作为缓存使用最主要的一个特性就是可以为键值对设置过期时间。就看看 Redis 是如果实现这一个最重要的特性的？

在 Redis 中与过期时间有关的命令

- EXPIRE 设置 key 的存活时间单位秒
- EXPIREAT 设置 key 的过期时间点单位秒
- PEXPIRE 设置 key 的存活时间单位毫秒
- PEXPIREAT 设置 key 的过期时间点单位毫秒

其实这些命令，底层的命令都是由 REXPIREAT 实现的。

在 redisDb 中使用了 dict *expires，来存储过期时间的。其中 key 指向了 keyspace 中的 key（c 语言中的指针）， value 是一个 long long 类型的时间戳，标定这个 key 过期的时间点，单位是毫秒。

如果我们为上文的 mobile 增加一个过期时间。
```
>redis PEXPIREAT mobile 1521469812000
```
这个时候就会在过期的 字典中增加一个键值对。如下图：
![](/img/redis-expires.jpg)

对于过期的判断逻辑就很简单：

- 在 字典 expires 中 key 是否存在。
- 如果 key 存在，value 的时间戳是否小于当前系统时间戳。
接下来就需要讨论一下过期的键的删除策略。

key的删除有三种策略：

1. 定时删除，Redis定时的删除内存里面所有过期的键值对，这样能够保证内存友好，过期的key都会被删除，但是如果key的数量很多，一次删除需要CPU运算，CPU不友好。
2. 惰性删除，只有 key 在被调用的时候才去检查键值对是否过期，但是会造成内存中存储大量的过期键值对，内存不友好，但是极大的减轻CPU 的负担。
3. 定时部分删除，Redis定时扫描过期键，但是只删除部分，至于删除多少键，根据当前 Redis 的状态决定。

这三种策略就是对时间和空间有不同的倾向。Redis为了平衡时间和空间，采用了后两种策略 惰性删除和定时部分删除。

惰性删除比较简单，不做过多介绍。主要讨论一下定时部分删除。

过期键的定时删除的策略由 expire.c/activeExpireCycle() 函数实现，server.c/serverCron() 定时的调用 activieExpireCycle() 。

activeExpireCycle 的大的操作原则是，如果过期的key比较少，则删除key的数量也比较保守，如果，过期的键多，删除key的策略就会很激进。

```c
static unsigned int current_db = 0; /* Last DB tested. */
static int timelimit_exit = 0;      /* Time limit hit in previous call? */
static long long last_fast_cycle = 0; /* When last fast cycle ran. */
```

- 首先三个 static 全局参数分别记录目前遍历的 db下标，上一次删除是否是超时退出的，上一次快速操作是什么时候进行的。
- 计算 timelimit = 1000000*ACTIVE_EXPIRE_CYCLE_SLOW_TIME_PERC/server.hz/100; 可以理解为 25% 的 cpu 时间。
- 如果 db 中 expire 的大小为0 不操作
- expire 占总 key 小于 1% 不操作
- num = dictSize(db->expires)；num 是 expire 使用的key的数量。
- slots = dictSlots(db->expires); slots 是 expire 字典的尺寸大小。
- 已使用的key（num） 大于 ACTIVE_EXPIRE_CYCLE_LOOKUPS_PER_LOOP 则设置为 ACTIVE_EXPIRE_CYCLE_LOOKUPS_PER_LOOP。也就是说每次只检查 ACTIVE_EXPIRE_CYCLE_LOOKUPS_PER_LOOP 个键。
- 随机获取带过期的 key。计算是否过期，如果过期就删除。
- 然后各种统计，包括删除键的次数，平均过期时间。
- 每遍历十六次，计算操作时间，如果超过 timelimit 结束返回。
- 如果删除的过期键大于 ACTIVE_EXPIRE_CYCLE_LOOKUPS_PER_LOOP 的 1\4 就跳出循环，结束。

步骤比较复杂，总结一下：（这里都是以默认配置描述）

1. redis 会用最多 25% 的 cpu 时间处理键的过期。
2. 遍历所有的 redisDb
3. 在每个 redisDb 中如果数据中没有过期键或者过期键比例过低就直接进入下一个 redisDb。
4. 否则，遍历 redisDb 中的过期键，如果删除的键达到有过期时间的的key 的25% ，或者操作时间大于 cpu 时间的     25% 就结束当前循环，进入下一个redisDb。