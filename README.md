# cvs-client

### 使用方式
```
CVS JAVA客户端 (多服务器配置版) ubuntu系统
支持 pserver 协议和配置文件管理

配置管理:
  jcvs config add <标签> <CVSROOT> [描述]    - 添加服务器配置
  jcvs config remove <标签>                 - 删除服务器配置
  jcvs config set-password <标签>           - 设置服务器密码
  jcvs list                                 - 列出所有服务器配置

使用标签操作:
  jcvs login  <标签>                        - 测试登录
  jcvs checkout <标签> <模块> <目录>        - 检出模块
  jcvs commit <标签> <工作目录> -m <信息>   - 提交修改
  jcvs diff <标签> <工作目录>               - 查看差异
  jcvs update <标签> <工作目录>             - 更新工作副本
  jcvs add <标签> <工作目录/文件>         - 添加文件
  jcvs status <标签> <工作目录/文件>             - 查看状态
  jcvs log <标签> <工作目录/文件>                - 查看日志

CVSROOT 格式: :pserver:用户名@主机名[:端口]/仓库路径
例如: :pserver:anoncvs@example.com:2401/repo

配置文件:
  ~/.cvs/.cvs-servers.properties - 主配置文件
  ~/.cvs/.cvs-servers.secure.properties - 安全配置文件（存储密码）

```

### 项目结构
```
cvs-client/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── lobsterxie/
                    └── handler             #处理器
│                   └── cvs/
│                       └── CVSClient.java    # 客户端主程序
└── README.md                                     # 项目说明
```


### 运行样例（配置文件，多CVSROOT模式(多tag)）：
- ./jcvs config add bossdep :pserver:anoncvs@mirc.rsna.org:2401/RSNA rsna
- ./jcvs config set-password 123456
- ./jcvs checkout bossdep module_name local_root_path
- ./jcvs add bossdep workdir workdir/file
- ./jcvs commit bossdep workdir/file -m "test"
- ./jcvs update bossdep workdir/file
- ./jcvs log bossdep workdir/file
- ./jcvs revert bossdep workdir/file 1.2
- ./jcvs status bossdep workdir/file

